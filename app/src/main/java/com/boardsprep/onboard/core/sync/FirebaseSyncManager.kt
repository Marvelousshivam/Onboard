package com.boardsprep.onboard.core.sync

import android.content.Context
import android.util.Log
import com.boardsprep.onboard.data.local.OnboardDatabase
import com.boardsprep.onboard.data.local.entities.ChapterMasteryEntity
import com.boardsprep.onboard.data.local.entities.MasteredItemEntity
import com.boardsprep.onboard.data.local.entities.QuizAttemptEntity
import com.boardsprep.onboard.data.local.entities.VideoProgressEntity
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class StreakInfo(
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val isStreakActiveToday: Boolean = false,
    val lastActiveDate: String = ""
)

data class UserAccountInfo(
    val uid: String = "",
    val email: String? = null,
    val displayName: String? = null,
    val isAnonymous: Boolean = true,
    val isSyncing: Boolean = false,
    val lastSyncedTime: Long = System.currentTimeMillis()
)

class FirebaseSyncManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "FirebaseSyncManager"

        @Volatile
        private var instance: FirebaseSyncManager? = null

        fun getInstance(context: Context): FirebaseSyncManager {
            return instance ?: synchronized(this) {
                instance ?: FirebaseSyncManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val db = OnboardDatabase.getInstance(context)

    private var firestore: FirebaseFirestore? = null
    private var auth: FirebaseAuth? = null

    private val _streakState = MutableStateFlow(StreakInfo())
    val streakState: StateFlow<StreakInfo> = _streakState.asStateFlow()

    private val _accountState = MutableStateFlow(UserAccountInfo())
    val accountState: StateFlow<UserAccountInfo> = _accountState.asStateFlow()

    private val listeners = mutableListOf<ListenerRegistration>()

    init {
        initializeFirebase()
    }

    private fun initializeFirebase() {
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                val options = FirebaseOptions.Builder()
                    .setApplicationId("1:47299135156:android:72065c9ea2759773e5f9e1")
                    .setApiKey("AIzaSyBrf_56b9Mb1tyaLIvpEbg4A_5RR7E_QWQ")
                    .setProjectId("onboards2027")
                    .setStorageBucket("onboards2027.firebasestorage.app")
                    .setGcmSenderId("47299135156")
                    .build()
                FirebaseApp.initializeApp(context, options)
                Log.d(TAG, "Firebase initialized with explicit options")
            } else {
                Log.d(TAG, "Firebase already initialized")
            }

            val fs = FirebaseFirestore.getInstance()
            val settings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(com.google.firebase.firestore.PersistentCacheSettings.newBuilder().build())
                .build()
            fs.firestoreSettings = settings
            firestore = fs

            auth = FirebaseAuth.getInstance()
            setupAuthAndListeners()
        } catch (e: Exception) {
            Log.e(TAG, "Firebase initialization error: ${e.message}", e)
        }
    }

    private fun setupAuthAndListeners() {
        val currentAuth = auth ?: return
        val user = currentAuth.currentUser
        if (user != null) {
            _accountState.value = UserAccountInfo(
                uid = user.uid,
                email = user.email,
                displayName = user.displayName,
                isAnonymous = user.isAnonymous,
                lastSyncedTime = System.currentTimeMillis()
            )
            Log.d(TAG, "Existing user logged in: ${user.uid}")
            attachFirestoreListeners(user.uid)
        } else {
            // Sign in anonymously so guest state syncs across web/app immediately
            currentAuth.signInAnonymously()
                .addOnSuccessListener { result ->
                    val uid = result.user?.uid ?: return@addOnSuccessListener
                    _accountState.value = UserAccountInfo(
                        uid = uid,
                        email = null,
                        displayName = "Guest Student",
                        isAnonymous = true,
                        lastSyncedTime = System.currentTimeMillis()
                    )
                    Log.d(TAG, "Signed in anonymously with UID: $uid")
                    attachFirestoreListeners(uid)
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Anonymous sign-in failed: ${e.message}")
                    _accountState.value = UserAccountInfo(
                        uid = "offline_guest",
                        email = null,
                        displayName = "Guest Student",
                        isAnonymous = true,
                        lastSyncedTime = System.currentTimeMillis()
                    )
                }
        }

        currentAuth.addAuthStateListener { firebaseAuth ->
            val u = firebaseAuth.currentUser
            if (u != null) {
                _accountState.value = UserAccountInfo(
                    uid = u.uid,
                    email = u.email,
                    displayName = u.displayName,
                    isAnonymous = u.isAnonymous,
                    lastSyncedTime = System.currentTimeMillis()
                )
                attachFirestoreListeners(u.uid)
            } else {
                _accountState.value = UserAccountInfo()
                detachListeners()
            }
        }
    }

    private fun detachListeners() {
        listeners.forEach { it.remove() }
        listeners.clear()
    }

    private fun attachFirestoreListeners(uid: String) {
        detachListeners()
        val fs = firestore ?: return

        // 1. Listen to Streak Updates
        val streakRef = fs.collection("users").document(uid).collection("stats").document("streak")
        val streakSub = streakRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "Streak listen error: ${error.message}")
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                val current = snapshot.getLong("currentStreak")?.toInt() ?: 0
                val longest = snapshot.getLong("longestStreak")?.toInt() ?: current
                val lastDate = snapshot.getString("lastActiveDate") ?: snapshot.getString("lastStudyDate") ?: ""
                val today = getTodayDateStr()
                val isActiveToday = lastDate == today

                _streakState.value = StreakInfo(
                    currentStreak = current,
                    longestStreak = longest,
                    isStreakActiveToday = isActiveToday,
                    lastActiveDate = lastDate
                )
            }
        }
        listeners.add(streakSub)

        // 2. Listen to Video Progress synced from Web
        val vpRef = fs.collection("users").document(uid).collection("video_progress")
        val vpSub = vpRef.addSnapshotListener { snapshots, error ->
            if (error != null || snapshots == null) return@addSnapshotListener
            scope.launch {
                for (change in snapshots.documentChanges) {
                    val doc = change.document
                    val videoId = doc.getString("videoId") ?: doc.id
                    val position = doc.getLong("positionMillis") ?: 0L
                    val duration = doc.getLong("durationMillis") ?: 0L
                    val completed = doc.getBoolean("isCompleted") ?: false
                    val chapterId = doc.getString("chapterId") ?: ""

                    // Compare with local and save if newer
                    val existing = db.videoProgressDao().getProgressOnce(videoId)
                    if (existing == null || existing.positionMillis < position || (!existing.isCompleted && completed)) {
                        db.videoProgressDao().saveProgress(
                            VideoProgressEntity(
                                videoId = videoId,
                                chapterId = chapterId,
                                positionMillis = position,
                                durationMillis = duration,
                                isCompleted = completed,
                                lastPlayed = System.currentTimeMillis()
                            )
                        )
                    }
                }
            }
        }
        listeners.add(vpSub)

        // 3. Listen to Chapter Mastery synced from Web
        val cmRef = fs.collection("users").document(uid).collection("chapter_mastery")
        val cmSub = cmRef.addSnapshotListener { snapshots, error ->
            if (error != null || snapshots == null) return@addSnapshotListener
            scope.launch {
                for (change in snapshots.documentChanges) {
                    val doc = change.document
                    val chId = doc.getString("chapterId") ?: doc.id
                    val theory = doc.getBoolean("theoryCompleted") ?: false
                    val ncert = doc.getBoolean("ncertCompleted") ?: false
                    val exemplar = doc.getBoolean("exemplarCompleted") ?: false
                    val pyq = doc.getBoolean("pyqCompleted") ?: false

                    db.masteryDao().saveMastery(
                        ChapterMasteryEntity(
                            chapterId = chId,
                            theoryCompleted = theory,
                            ncertCompleted = ncert,
                            exemplarCompleted = exemplar,
                            pyqCompleted = pyq,
                            lastUpdated = System.currentTimeMillis()
                        )
                    )
                }
            }
        }
        listeners.add(cmSub)

        // 4. Listen to Mastered Items (Derivations / Reactions)
        val miRef = fs.collection("users").document(uid).collection("mastered_items")
        val miSub = miRef.addSnapshotListener { snapshots, error ->
            if (error != null || snapshots == null) return@addSnapshotListener
            scope.launch {
                for (change in snapshots.documentChanges) {
                    val doc = change.document
                    val itemId = doc.getString("itemId") ?: doc.id
                    val category = doc.getString("category") ?: "derivation"
                    val isMastered = doc.getBoolean("isMastered") ?: true

                    if (isMastered) {
                        db.handbookDao().setMastered(
                            MasteredItemEntity(
                                itemId = itemId,
                                category = category,
                                isMastered = true
                            )
                        )
                    } else {
                        db.handbookDao().removeMastered(itemId)
                    }
                }
            }
        }
        listeners.add(miSub)

        // Initial push of any offline local data
        syncAllLocalToCloud()
    }

    // ─── PUSH: Synchronize Local Changes to Cloud Firestore ─────────────────

    fun syncVideoProgress(entity: VideoProgressEntity) {
        val uid = auth?.currentUser?.uid ?: return
        val fs = firestore ?: return
        scope.launch {
            try {
                val data = hashMapOf(
                    "videoId" to entity.videoId,
                    "chapterId" to entity.chapterId,
                    "positionMillis" to entity.positionMillis,
                    "durationMillis" to entity.durationMillis,
                    "isCompleted" to entity.isCompleted,
                    "lastPlayed" to FieldValue.serverTimestamp()
                )
                fs.collection("users").document(uid)
                    .collection("video_progress").document(entity.videoId)
                    .set(data, SetOptions.merge())
                    .await()

                // If played > 10s, update study streak
                if (entity.positionMillis >= 10000L) {
                    updateStudyStreak("lecture", 5000L)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to push video progress: ${e.message}")
            }
        }
    }

    fun syncChapterMastery(entity: ChapterMasteryEntity) {
        val uid = auth?.currentUser?.uid ?: return
        val fs = firestore ?: return
        scope.launch {
            try {
                val data = hashMapOf(
                    "chapterId" to entity.chapterId,
                    "theoryCompleted" to entity.theoryCompleted,
                    "ncertCompleted" to entity.ncertCompleted,
                    "exemplarCompleted" to entity.exemplarCompleted,
                    "pyqCompleted" to entity.pyqCompleted,
                    "lastUpdated" to FieldValue.serverTimestamp()
                )
                fs.collection("users").document(uid)
                    .collection("chapter_mastery").document(entity.chapterId)
                    .set(data, SetOptions.merge())
                    .await()

                updateStudyStreak("mastery", 0L)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to push chapter mastery: ${e.message}")
            }
        }
    }

    fun syncQuizAttempt(entity: QuizAttemptEntity) {
        val uid = auth?.currentUser?.uid ?: return
        val fs = firestore ?: return
        scope.launch {
            try {
                val docId = if (entity.id > 0) "attempt_${entity.id}" else "attempt_${System.currentTimeMillis()}"
                val data = hashMapOf(
                    "quizId" to entity.quizId,
                    "score" to entity.score,
                    "totalQuestions" to entity.totalQuestions,
                    "timeTakenSeconds" to entity.timeTakenSeconds,
                    "attemptedAt" to FieldValue.serverTimestamp()
                )
                fs.collection("users").document(uid)
                    .collection("quiz_attempts").document(docId)
                    .set(data, SetOptions.merge())
                    .await()

                updateStudyStreak("quiz", entity.timeTakenSeconds * 1000L)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to push quiz attempt: ${e.message}")
            }
        }
    }

    fun syncMasteredItem(entity: MasteredItemEntity, isMastered: Boolean) {
        val uid = auth?.currentUser?.uid ?: return
        val fs = firestore ?: return
        scope.launch {
            try {
                val data = hashMapOf(
                    "itemId" to entity.itemId,
                    "category" to entity.category,
                    "isMastered" to isMastered,
                    "lastUpdated" to FieldValue.serverTimestamp()
                )
                fs.collection("users").document(uid)
                    .collection("mastered_items").document(entity.itemId)
                    .set(data, SetOptions.merge())
                    .await()

                if (isMastered) {
                    updateStudyStreak("handbook", 0L)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to push mastered item: ${e.message}")
            }
        }
    }

    fun syncAllLocalToCloud() {
        val uid = auth?.currentUser?.uid ?: return
        val fs = firestore ?: return
        scope.launch {
            try {
                // Upload local video progress
                val vids = db.videoProgressDao().getAllProgressList()
                for (v in vids) {
                    val data = hashMapOf(
                        "videoId" to v.videoId,
                        "chapterId" to v.chapterId,
                        "positionMillis" to v.positionMillis,
                        "durationMillis" to v.durationMillis,
                        "isCompleted" to v.isCompleted,
                        "lastPlayed" to FieldValue.serverTimestamp()
                    )
                    fs.collection("users").document(uid)
                        .collection("video_progress").document(v.videoId)
                        .set(data, SetOptions.merge())
                }

                // Upload chapter mastery
                val masteryList = db.masteryDao().getAllMasteryList()
                for (m in masteryList) {
                    val data = hashMapOf(
                        "chapterId" to m.chapterId,
                        "theoryCompleted" to m.theoryCompleted,
                        "ncertCompleted" to m.ncertCompleted,
                        "exemplarCompleted" to m.exemplarCompleted,
                        "pyqCompleted" to m.pyqCompleted,
                        "lastUpdated" to FieldValue.serverTimestamp()
                    )
                    fs.collection("users").document(uid)
                        .collection("chapter_mastery").document(m.chapterId)
                        .set(data, SetOptions.merge())
                }

                // Upload mastered handbook items
                val handbookItems = db.handbookDao().getAllMasteredList()
                for (h in handbookItems) {
                    val data = hashMapOf(
                        "itemId" to h.itemId,
                        "category" to h.category,
                        "isMastered" to h.isMastered,
                        "lastUpdated" to FieldValue.serverTimestamp()
                    )
                    fs.collection("users").document(uid)
                        .collection("mastered_items").document(h.itemId)
                        .set(data, SetOptions.merge())
                }

                Log.d(TAG, "Full local sync to Firestore completed successfully")
            } catch (e: Exception) {
                Log.w(TAG, "Full sync error: ${e.message}")
            }
        }
    }

    // ─── STREAK COMPUTATION (Matches Web Algorithm) ─────────────────────────

    fun updateStudyStreak(activityType: String, durationMillis: Long) {
        val uid = auth?.currentUser?.uid ?: return
        val fs = firestore ?: return

        scope.launch {
            try {
                val today = getTodayDateStr()
                val streakDocRef = fs.collection("users").document(uid).collection("stats").document("streak")
                val snap = streakDocRef.get().await()

                var current = snap.getLong("currentStreak")?.toInt() ?: 0
                var longest = snap.getLong("longestStreak")?.toInt() ?: current
                val lastDate = snap.getString("lastActiveDate") ?: snap.getString("lastStudyDate") ?: ""

                if (lastDate != today) {
                    val yesterday = getYesterdayDateStr()
                    if (lastDate == yesterday) {
                        current += 1
                    } else if (lastDate.isBlank()) {
                        current = 1
                    } else {
                        // Streak broke
                        current = 1
                    }
                    if (current > longest) longest = current

                    val streakData = hashMapOf(
                        "currentStreak" to current,
                        "longestStreak" to longest,
                        "lastActiveDate" to today,
                        "lastStudyDate" to today,
                        "lastUpdated" to FieldValue.serverTimestamp()
                    )
                    streakDocRef.set(streakData, SetOptions.merge()).await()

                    _streakState.value = StreakInfo(
                        currentStreak = current,
                        longestStreak = longest,
                        isStreakActiveToday = true,
                        lastActiveDate = today
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Streak update error: ${e.message}")
            }
        }
    }

    private fun getTodayDateStr(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        }
        return sdf.format(Date())
    }

    private fun getYesterdayDateStr(): String {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"))
        cal.add(Calendar.DATE, -1)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        }
        return sdf.format(cal.time)
    }

    // ─── AUTHENTICATION & MANUAL SYNC ACTIONS ────────────────────────────────

    fun signInWithEmail(email: String, pass: String, onResult: (Boolean, String?) -> Unit) {
        val a = auth ?: run {
            onResult(false, "Firebase Auth not ready")
            return
        }
        a.signInWithEmailAndPassword(email.trim(), pass)
            .addOnSuccessListener {
                syncAllLocalToCloud()
                onResult(true, null)
            }
            .addOnFailureListener { e ->
                onResult(false, e.localizedMessage ?: "Sign in failed")
            }
    }

    fun registerWithEmail(email: String, pass: String, onResult: (Boolean, String?) -> Unit) {
        val a = auth ?: run {
            onResult(false, "Firebase Auth not ready")
            return
        }
        a.createUserWithEmailAndPassword(email.trim(), pass)
            .addOnSuccessListener {
                syncAllLocalToCloud()
                onResult(true, null)
            }
            .addOnFailureListener { e ->
                onResult(false, e.localizedMessage ?: "Registration failed")
            }
    }

    fun signInWithGoogle(idToken: String, onResult: (Boolean, String?) -> Unit) {
        val a = auth ?: run {
            onResult(false, "Firebase Auth not ready")
            return
        }
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val currentUser = a.currentUser
        if (currentUser != null && currentUser.isAnonymous) {
            currentUser.linkWithCredential(credential)
                .addOnSuccessListener {
                    syncAllLocalToCloud()
                    onResult(true, null)
                }
                .addOnFailureListener { linkError ->
                    // If link fails (e.g. account already exists with this Google credential), sign in directly
                    a.signInWithCredential(credential)
                        .addOnSuccessListener {
                            syncAllLocalToCloud()
                            onResult(true, null)
                        }
                        .addOnFailureListener { signInError ->
                            onResult(false, signInError.localizedMessage ?: "Google sign-in failed")
                        }
                }
        } else {
            a.signInWithCredential(credential)
                .addOnSuccessListener {
                    syncAllLocalToCloud()
                    onResult(true, null)
                }
                .addOnFailureListener { e ->
                    onResult(false, e.localizedMessage ?: "Google sign-in failed")
                }
        }
    }

    fun signOutUser(onComplete: () -> Unit = {}) {
        val a = auth ?: return
        a.signOut()
        a.signInAnonymously()
            .addOnCompleteListener {
                onComplete()
            }
    }

    fun triggerManualSync(onComplete: () -> Unit = {}) {
        scope.launch {
            _accountState.value = _accountState.value.copy(isSyncing = true)
            syncAllLocalToCloud()
            _accountState.value = _accountState.value.copy(
                isSyncing = false,
                lastSyncedTime = System.currentTimeMillis()
            )
            withContext(Dispatchers.Main) {
                onComplete()
            }
        }
    }
}


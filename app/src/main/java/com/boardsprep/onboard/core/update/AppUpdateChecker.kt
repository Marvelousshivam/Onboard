package com.boardsprep.onboard.core.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.boardsprep.onboard.BuildConfig
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Robust GitHub Release in-app update engine for OnBOARD.
 * Handles silent background checks, version comparison, asset resolution,
 * streaming downloads with progress reporting, and FileProvider APK installation.
 */
class AppUpdateChecker(private val context: Context) {

    companion object {
        private const val TAG = "AppUpdateChecker"
        const val REPO_OWNER = "Marvelousshivam"
        const val REPO_NAME = "Onboard"
        private const val GITHUB_API_RELEASES = "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases"
        private const val PREFS_NAME = "onboard_update_prefs"
        private const val KEY_LAST_CHECK = "last_check_timestamp_ms"
        private const val CHECK_INTERVAL_MS = 12 * 60 * 60 * 1000L // 12 Hours
    }

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadJob: Job? = null
    private var currentDownloadCall: Call? = null

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Checks for new GitHub releases.
     * @param isManual True if triggered by user tap in Settings/Library; ignores 12h rate limit.
     */
    fun checkForUpdates(isManual: Boolean = false) {
        val lastCheck = prefs.getLong(KEY_LAST_CHECK, 0L)
        val now = System.currentTimeMillis()

        if (!isManual && (now - lastCheck) < CHECK_INTERVAL_MS) {
            Log.d(TAG, "Skipping update check; checked within last 12 hours.")
            return
        }

        // Avoid re-triggering if already in an active download or check
        val current = _updateState.value
        if (current is UpdateState.Checking || current is UpdateState.Downloading) {
            return
        }

        _updateState.value = UpdateState.Checking(isManual = isManual)

        scope.launch {
            try {
                prefs.edit().putLong(KEY_LAST_CHECK, now).apply()

                val request = Request.Builder()
                    .url(GITHUB_API_RELEASES)
                    .header("User-Agent", "OnBoardApp/${BuildConfig.VERSION_NAME}")
                    .header("Accept", "application/vnd.github.v3+json")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val code = response.code
                        val errorMsg = when (code) {
                            404 -> "No published releases found on GitHub."
                            403 -> "GitHub API rate limit exceeded. Please try again later."
                            else -> "GitHub check failed with HTTP $code"
                        }
                        _updateState.value = UpdateState.Error(errorMsg, isManual = isManual)
                        return@launch
                    }

                    val bodyString = response.body?.string().orEmpty()
                    if (bodyString.isBlank()) {
                        _updateState.value = UpdateState.Error("Empty response received from GitHub.", isManual = isManual)
                        return@launch
                    }

                    val listType = object : com.google.gson.reflect.TypeToken<List<GitHubReleaseDto>>() {}.type
                    val releases: List<GitHubReleaseDto> = try {
                        gson.fromJson(bodyString, listType)
                    } catch (e: Exception) {
                        listOfNotNull(gson.fromJson(bodyString, GitHubReleaseDto::class.java))
                    }

                    // Select the latest release that is not a draft and contains an APK asset
                    val releaseDto = releases.firstOrNull { r ->
                        !r.isDraft && r.assets.any { it.name.endsWith(".apk", ignoreCase = true) }
                    } ?: releases.firstOrNull()

                    if (releaseDto == null || releaseDto.tagName.isBlank()) {
                        _updateState.value = UpdateState.Error("No valid releases found.", isManual = isManual)
                        return@launch
                    }

                    val currentVersion = BuildConfig.VERSION_NAME
                    val isNewer = isNewerVersion(releaseDto.tagName, currentVersion)


                    if (!isNewer) {
                        _updateState.value = UpdateState.UpToDate(currentVersion, isManual = isManual)
                        return@launch
                    }

                    // Scan assets for an APK file
                    val apkAsset = releaseDto.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) && it.name.contains("release", ignoreCase = true) }
                        ?: releaseDto.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }

                    if (apkAsset == null || apkAsset.browserDownloadUrl.isBlank()) {
                        _updateState.value = UpdateState.Error(
                            "New release ${releaseDto.tagName} found, but no installable APK file was attached.",
                            isManual = isManual,
                            canRetry = false
                        )
                        return@launch
                    }

                    val releaseInfo = AppReleaseInfo(
                        tagName = releaseDto.tagName,
                        versionName = releaseDto.tagName.removePrefix("v").removePrefix("V").trim(),
                        title = releaseDto.name?.ifBlank { "OnBOARD ${releaseDto.tagName}" } ?: "OnBOARD ${releaseDto.tagName}",
                        changelog = releaseDto.body.orEmpty().trim(),
                        downloadUrl = apkAsset.browserDownloadUrl,
                        apkFileName = apkAsset.name.ifBlank { "OnBOARD-${releaseDto.tagName}.apk" },
                        fileSizeBytes = apkAsset.size,
                        htmlUrl = releaseDto.htmlUrl ?: "https://github.com/$REPO_OWNER/$REPO_NAME/releases",
                        publishedAt = releaseDto.publishedAt.orEmpty()
                    )

                    _updateState.value = UpdateState.UpdateAvailable(releaseInfo, isManual = isManual)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Update check failed", e)
                _updateState.value = UpdateState.Error(
                    message = "Could not check for updates: ${e.localizedMessage ?: "Network error"}",
                    isManual = isManual
                )
            }
        }
    }

    /**
     * Initiates streaming download of the release APK to app cache.
     */
    fun startDownload(release: AppReleaseInfo) {
        downloadJob?.cancel()
        currentDownloadCall?.cancel()

        downloadJob = scope.launch {
            _updateState.value = UpdateState.Downloading(
                release = release,
                progress = 0f,
                downloadedBytes = 0L,
                totalBytes = release.fileSizeBytes
            )

            try {
                val updatesDir = File(context.cacheDir, "updates")
                if (!updatesDir.exists()) {
                    updatesDir.mkdirs()
                }

                // Delete any stale update files before downloading new one
                updatesDir.listFiles()?.forEach { it.delete() }

                val targetFile = File(updatesDir, "OnBOARD_${release.versionName}.apk")

                val request = Request.Builder()
                    .url(release.downloadUrl)
                    .header("User-Agent", "OnBoardApp/${BuildConfig.VERSION_NAME}")
                    .build()

                val call = httpClient.newCall(request)
                currentDownloadCall = call

                val response = call.execute()
                if (!response.isSuccessful) {
                    throw IOException("Download failed with HTTP ${response.code}")
                }

                val responseBody = response.body ?: throw IOException("Empty response body from download server")
                val totalLength = if (responseBody.contentLength() > 0) responseBody.contentLength() else release.fileSizeBytes

                var downloaded = 0L
                val buffer = ByteArray(8192)
                var lastProgressUpdateMs = 0L

                responseBody.byteStream().use { input ->
                    FileOutputStream(targetFile).use { output ->
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            if (!isActive) {
                                targetFile.delete()
                                return@launch
                            }
                            output.write(buffer, 0, read)
                            downloaded += read

                            val now = System.currentTimeMillis()
                            if (now - lastProgressUpdateMs > 100 || downloaded == totalLength) {
                                lastProgressUpdateMs = now
                                val progress = if (totalLength > 0) {
                                    (downloaded.toFloat() / totalLength).coerceIn(0f, 1f)
                                } else 0f

                                _updateState.value = UpdateState.Downloading(
                                    release = release,
                                    progress = progress,
                                    downloadedBytes = downloaded,
                                    totalBytes = totalLength
                                )
                            }
                        }
                        output.flush()
                    }
                }

                if (targetFile.length() > 0) {
                    _updateState.value = UpdateState.ReadyToInstall(release, targetFile)
                } else {
                    throw IOException("Downloaded file is empty")
                }
            } catch (e: Exception) {
                if (e is CancellationException || currentDownloadCall?.isCanceled() == true) {
                    Log.d(TAG, "Download cancelled by user")
                    _updateState.value = UpdateState.UpdateAvailable(release, isManual = false)
                } else {
                    Log.e(TAG, "Download failed", e)
                    _updateState.value = UpdateState.Error(
                        message = "Download failed: ${e.localizedMessage ?: "Unknown error"}",
                        canRetry = true
                    )
                }
            } finally {
                currentDownloadCall = null
            }
        }
    }

    /**
     * Cancels an active in-flight download.
     */
    fun cancelDownload() {
        val current = _updateState.value
        if (current is UpdateState.Downloading) {
            currentDownloadCall?.cancel()
            downloadJob?.cancel()
            _updateState.value = UpdateState.UpdateAvailable(current.release, isManual = false)
        }
    }

    /**
     * Hands off downloaded APK to Android system package installer.
     */
    fun installUpdate(apkFile: File) {
        if (!apkFile.exists() || apkFile.length() == 0L) {
            _updateState.value = UpdateState.Error("Installer package not found on device.")
            return
        }

        // Check Unknown Apps Install Permission (Android 8.0+ / API 26+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val settingsIntent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(settingsIntent)
                return
            }
        }

        try {
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch package installer", e)
            _updateState.value = UpdateState.Error("Could not launch package installer: ${e.localizedMessage}")
        }
    }

    /**
     * Dismisses the update dialog / banner.
     */
    fun dismiss() {
        _updateState.value = UpdateState.Idle
    }

    /**
     * Robust Semantic Version comparator.
     * Returns true if remoteTag represents a strictly newer version than currentVersion.
     * Examples:
     * - "v1.2.1" vs "1.2.0" -> true
     * - "v1.1.0" vs "1.2.0" -> false
     * - "1.2.0" vs "1.2.0" -> false
     */
    fun isNewerVersion(remoteTag: String, currentVersion: String): Boolean {
        try {
            val cleanRemote = remoteTag.trim().removePrefix("v").removePrefix("V").substringBefore("-")
            val cleanCurrent = currentVersion.trim().removePrefix("v").removePrefix("V").substringBefore("-")

            val remoteParts = cleanRemote.split(".").mapNotNull { it.toIntOrNull() }
            val currentParts = cleanCurrent.split(".").mapNotNull { it.toIntOrNull() }

            if (remoteParts.isEmpty() || currentParts.isEmpty()) {
                return false
            }

            val maxLength = maxOf(remoteParts.size, currentParts.size)
            for (i in 0 until maxLength) {
                val r = remoteParts.getOrElse(i) { 0 }
                val c = currentParts.getOrElse(i) { 0 }
                if (r > c) return true
                if (r < c) return false
            }
            return false
        } catch (e: Exception) {
            Log.w(TAG, "Error comparing versions: remote=$remoteTag, current=$currentVersion", e)
            return false
        }
    }
}

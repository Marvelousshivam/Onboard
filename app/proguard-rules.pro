# ============================================================================
# OnBOARD Production ProGuard & R8 Optimization Rules
# ============================================================================

# --- Jetpack Compose ---
-keepclassmembers class * extends androidx.compose.runtime.State { *; }
-keepclassmembers class * extends androidx.compose.runtime.snapshots.SnapshotMutableState { *; }
-dontwarn androidx.compose.**

# --- AndroidX Annotation Keep ---
-keep @androidx.annotation.Keep class * { *; }
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# --- Room Database ---
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep class * extends androidx.room.RoomDatabase {
    <init>();
}
-dontwarn androidx.room.paging.**

# --- All OnBOARD Domain Data, Manifests, DTOs & Entities ---
-keep class com.boardsprep.onboard.data.models.** { *; }
-keepclassmembers class com.boardsprep.onboard.data.models.** { *; }

-keep class com.boardsprep.onboard.data.repository.** { *; }
-keepclassmembers class com.boardsprep.onboard.data.repository.** { *; }

-keep class com.boardsprep.onboard.data.local.entities.** { *; }
-keepclassmembers class com.boardsprep.onboard.data.local.entities.** { *; }

-keep class com.boardsprep.onboard.data.local.dao.** { *; }
-keepclassmembers class com.boardsprep.onboard.data.local.dao.** { *; }

-keep class com.boardsprep.onboard.core.sync.** { *; }
-keepclassmembers class com.boardsprep.onboard.core.sync.** { *; }

-keep class com.boardsprep.onboard.core.pdf.** { *; }
-keepclassmembers class com.boardsprep.onboard.core.pdf.** { *; }

-keep class com.boardsprep.onboard.core.playback.** { *; }
-keepclassmembers class com.boardsprep.onboard.core.playback.** { *; }

# --- Gson Reflection & Serialization ---
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keepclassmembers enum * { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# --- PdfBox-Android (Pure Java PDF Engine) ---
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**
-dontwarn org.bouncycastle.**

# --- Media3 ExoPlayer ---
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# --- OkHttp & Coil ---
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class coil.** { *; }
-dontwarn coil.**

# --- Firebase & Google Play Services ---
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# --- WebView & KaTeX Engine ---
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keepclassmembers class * extends android.webkit.WebViewClient {
    public void *(android.webkit.WebView, java.lang.String);
    public void *(android.webkit.WebView, java.lang.String, android.graphics.Bitmap);
}
-keepclassmembers class * extends android.webkit.WebChromeClient {
    public void *(android.webkit.WebView, ...);
}

# --- General Android & Kotlin ---
-keepattributes SourceFile,LineNumberTable
-dontwarn kotlin.reflect.**

# ==============================================================================
# Luma (com.lord67.luma) — R8 / ProGuard Release Optimization Rules
# ==============================================================================

# Preserve stack trace readability & Kotlin generics / annotations metadata
-keepattributes SourceFile,LineNumberTable,Signature,InnerClasses,EnclosingMethod,*Annotation*
-renamesourcefileattribute SourceFile

# Preserve Kotlin metadata & Coroutines fast-service-loader hooks
-keep class kotlin.Metadata { *; }
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ------------------------------------------------------------------------------
# 1. Android Components & NotificationListenerService
# ------------------------------------------------------------------------------
-keep class com.example.MainActivity { *; }
-keep class com.example.engine.LumaMediaListenerService { *; }
-keep class * extends android.service.notification.NotificationListenerService {
    public <init>();
    public void onNotificationPosted(...);
    public void onNotificationRemoved(...);
    public void onListenerConnected();
    public void onListenerDisconnected();
}
-keep class androidx.profileinstaller.** { *; }

# ------------------------------------------------------------------------------
# 2. Room Database, Generated _Impl, DAOs, and Entities
# ------------------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class com.example.data.local.FolioDatabase* { *; }
-keep class com.example.data.local.FolioDao* { *; }
-keep class com.example.notebook.data.NotebookDao* { *; }
-keep @androidx.room.Entity class com.example.data.local.** { *; }
-keep @androidx.room.Dao interface com.example.data.local.** { *; }
-keep @androidx.room.Entity class com.example.notebook.data.** { *; }
-keep @androidx.room.Dao interface com.example.notebook.data.** { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <init>(...);
}
-dontwarn androidx.room.paging.**

# ------------------------------------------------------------------------------
# 3. JSONObject / JsonConverters / Data Models & Serialized Enums
#    (PreferencesRepository, PeerSyncEngine, JsonConverters, DocumentEntity, Notebook)
# ------------------------------------------------------------------------------
-keep class com.example.data.local.JsonConverters { *; }
-keep class com.example.data.model.** { *; }
-keep class com.example.notebook.model.** { *; }
-keep class com.example.notebook.data.NotebookJsonCodec { *; }
-keepclassmembers enum com.example.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ------------------------------------------------------------------------------
# 4. PDFBox-Android (com.tom_roush) + BouncyCastle (used internally by PDFBox)
# ------------------------------------------------------------------------------
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-keep class com.tom_roush.harmony.** { *; }
-keep class org.bouncycastle.jce.provider.** { *; }
-keep class org.bouncycastle.crypto.** { *; }
-dontwarn com.tom_roush.pdfbox.**
-dontwarn com.tom_roush.fontbox.**
-dontwarn com.tom_roush.harmony.**
-dontwarn org.bouncycastle.**
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn javax.naming.**
-dontwarn sun.misc.**

# ------------------------------------------------------------------------------
# 5. Google ML Kit (OCR Text Recognition + On-Demand Translate)
# ------------------------------------------------------------------------------
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_** { *; }
-keep class com.google.android.libraries.vision.** { *; }
-keepclasseswithmembers class * {
    native <methods>;
}
-keep @com.google.android.gms.common.annotation.KeepName class *
-keepclassmembers class * {
    @com.google.android.gms.common.annotation.KeepName *;
}
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.**

# ------------------------------------------------------------------------------
# 6. Lottie Compose & Core (dynamic properties & Bodymovin JSON parsers)
# ------------------------------------------------------------------------------
-keep class com.airbnb.lottie.** { *; }
-keepclassmembers class com.airbnb.lottie.** { *; }
-dontwarn com.airbnb.lottie.**

# ------------------------------------------------------------------------------
# 7. Jsoup (PPTX / XML / HTML parsing)
# ------------------------------------------------------------------------------
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**
-dontwarn javax.annotation.**

# ------------------------------------------------------------------------------
# 8. OkHttp & Okio
# ------------------------------------------------------------------------------
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase
-keepclassmembers class okhttp3.internal.platform.** { *; }

# ------------------------------------------------------------------------------
# 9. Strip noisy verbose/debug logs in release builds
# ------------------------------------------------------------------------------
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}

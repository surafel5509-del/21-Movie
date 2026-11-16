# TorrentClaw Movies — release rules

# --- Kotlin / coroutines ---
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.**

# --- OkHttp / Retrofit ---
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-dontwarn javax.annotation.**
-keepattributes Signature, InnerClasses, EnclosingMethod, Exceptions
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# --- kotlinx.serialization ---
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# Serializers are generated next to the DTOs.
-keep,includedescriptorclasses class com.torrentclaw.movies.**$$serializer { *; }
-keepclassmembers class com.torrentclaw.movies.** {
    *** Companion;
}
-keepclasseswithmembers class com.torrentclaw.movies.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- Hilt / Dagger ---
-keep,allowobfuscation @interface dagger.hilt.android.AndroidEntryPoint
-keep class dagger.hilt.** { *; }
-dontwarn dagger.hilt.**

# --- Room ---
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn androidx.room.paging.**

# --- Coil ---
-dontwarn coil.**

# Required for Crashlytics-style stack traces (kept even without the SDK).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

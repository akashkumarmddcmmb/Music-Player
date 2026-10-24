# Add project specific ProGuard rules here.
# Preserve source file and line numbers for actionable crash diagnostics
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep Room database, DAOs, and entity models
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep class com.example.data.local.** { *; }

# Keep Media3 session service and constructors called via reflection/manifest
-keep class com.example.service.PulsePlaybackService { *; }
-keepclassmembers class * implements androidx.media3.exoplayer.mediacodec.MediaCodecAdapter$Factory {
    public <init>(...);
}

# Don't warn on optional Robolectric / test annotations referenced in shared metadata
-dontwarn org.robolectric.**
-dontwarn com.google.errorprone.annotations.**


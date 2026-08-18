# ProGuard / R8 rules for Vivid release builds.
# Hilt, Firebase, Room, Coil, Media3 and OkHttp already ship consumer rules;
# this file only covers project-specific reflection and crash-report hygiene.

-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod
-renamesourcefileattribute SourceFile

# Crash reports stay useful after obfuscation.
-keep public class * extends java.lang.Exception

# BuildConfig is read from Java / Compose.
-keep class com.vivid.app.BuildConfig { *; }

# Hilt / Dagger generated components (consumer rules cover most of this).
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-keepnames @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }

# Room entities / DAOs used via generated impls.
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Firebase Auth / Firestore / Messaging / Google Sign-In.
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Media3 / ExoPlayer: keep reflection-touched renderers.
-dontwarn androidx.media3.**
-keep class androidx.media3.** { *; }

# Coil (image pipeline).
-dontwarn coil.**

# OkHttp / Okio platform bits.
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Kotlin coroutines / serialization leftovers.
-dontwarn kotlinx.coroutines.**
-dontwarn kotlinx.serialization.**

# CameraX optional extensions.
-dontwarn androidx.camera.**

# Compose compiler metadata must survive shrinking.
-keep class androidx.compose.runtime.** { *; }

# Keep native methods.
-keepclasseswithmembernames class * {
    native <methods>;
}

# Enums used from Firestore string maps / when() branches.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

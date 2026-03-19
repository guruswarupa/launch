# Add project specific ProGuard rules here.
# Optimized for smaller APK size

# Remove line number information for release to save more space
#-keepattributes SourceFile,LineNumberTable
#-renamesourcefileattribute SourceFile

# Keep essential attributes for Compose and Kotlin
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,Exceptions

# Keep Parcelable implementations
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

# R8 automatically handles Activity/ViewModel keeps via the Manifest.
# We only need explicit keeps for classes accessed via reflection or specific dynamic needs.

# Keep data classes used for serialization
-keep class com.guruswarupa.launch.models.** { *; }

# Keep security crypto classes (required for encryption)
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**

# Keep biometric classes
-keep class androidx.biometric.** { *; }

# Remove warnings for common libraries
-dontwarn javax.naming.**
-dontwarn org.ietf.jgss.**
-dontwarn org.apache.http.**
-dontwarn javax.annotation.**

# Aggressive optimization - remove all debug logs
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
}

# Glide specific optimizations
-keep public class * extends com.github.bumptech.glide.module.AppGlideModule
-keep public class * extends com.github.bumptech.glide.module.LibraryGlideModule
-keep class com.github.bumptech.glide.GeneratedAppGlideModuleImpl
-dontwarn com.github.bumptech.glide.load.resource.bitmap.VideoDecoder

# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Keep Apache HTTP Client classes (not available on Android)
-dontwarn javax.naming.**
-dontwarn org.ietf.jgss.**
-dontwarn org.apache.http.**

# Keep Joda Time annotations
-dontwarn org.joda.convert.**
-keep class org.joda.convert.** { *; }

# Keep Google HTTP Client classes
-keep class com.google.api.client.** { *; }
-dontwarn com.google.api.client.**

# Keep all app classes
-keep class com.guruswarupa.launch.** { *; }

# Keep Jetpack Compose classes
-keep class androidx.compose.** { *; }

# Keep Parcelable implementations
-keep class * implements android.os.Parcelable { *; }

# Keep Activity, ViewModel, and AppCompat classes
-keep class * extends android.app.Activity { *; }
-keep class * extends androidx.lifecycle.ViewModel { *; }

# Keep R class (prevents missing resources)
-keep class **.R$* { *; }

# Keep annotation classes used by external libraries (e.g., Tink, Guava, Firebase)
-keep class javax.annotation.** { *; }
-keep class javax.annotation.concurrent.** { *; }
# Keep attributes related to annotations
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
# If you're using Google Tink or similar:
-keep class com.google.crypto.tink.** { *; }
-keep class com.google.protobuf.** { *; }
# Keep Tink KeysDownloader and required Google HTTP classes
-keep class com.google.crypto.tink.util.** { *; }
-keep class com.google.api.client.http.** { *; }
-keep class com.google.api.client.http.javanet.** { *; }
-keep class org.joda.time.** { *; }

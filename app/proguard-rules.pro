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

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
-ignorewarnings

#必要-> CastException: java.lang.Class cannot be cast to java.lang.reflect.ParameterizedType
-keepattributes * #Signature

#必要-> デバッグが困難になる
-keepnames class ** { *; }

-keep class org.videolan.libvlc.** { *; }
-keepclassmembers class org.videolan.libvlc.** { *; }
-keep class com.github.t_yoshi.vlcext.** { *; }
-keepclassmembers class com.github.t_yoshi.vlcext.** { *; }

# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep JNI native methods and caller classes
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep ImageScramble and inner classes accessed by JNI
-keep class com.example.picencrypt.utils.ImageScramble** { *; }
-keep class com.example.picencrypt.utils.NativeImageScramble { *; }
-keep class com.example.picencrypt.utils.NativeShuffleBridge { *; }
-keep class com.example.picencrypt.utils.MirageTankBridge { *; }

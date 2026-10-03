-dontwarn reactor.blockhound.**
-dontwarn io.netty.**
-keep class androidx.activity.ComponentActivity { *; }

# Strip verbose and debug logs in release builds; preserve info, warning, and error logs
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}

# Protect JNI and Native Bindings (libtermux.so)
-keepclasseswithmembernames class * {
    native <methods>;
}

# Protect libraries that rely on reflection
-keep class io.netty.** { *; }
-keep class org.jose4j.** { *; }
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# Protect kotlinx.serialization models and enum names against R8 obfuscation
-keepattributes *Annotation*,InnerClasses,Signature
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}
-keepclassmembers enum * {
    **[] $VALUES;
    public *;
}
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# Preserve domain and backup data models, fields, and companion serializers
-keep class com.adamoutler.ssh.data.** { *; }
-keep class com.adamoutler.ssh.backup.** { *; }


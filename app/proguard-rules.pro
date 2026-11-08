-dontwarn reactor.blockhound.**
-keep class androidx.activity.ComponentActivity { *; }

# Strip verbose and debug logs in release builds; preserve info, warning, and error logs
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}

-assumenosideeffects class com.adamoutler.ssh.util.AppLog {
    public static void v(...);
    public static void d(...);
}

# Protect JNI and Native Bindings (libtermux.so)
-keepclasseswithmembernames class * {
    native <methods>;
}

# Protect libraries that rely on reflection
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# SSHJ SSH Client Library
-keep class net.schmizz.sshj.** { *; }
-dontwarn net.schmizz.sshj.**

# Google Tink Cryptography
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**

# Termux Terminal and View
-keep class com.termux.terminal.** { *; }
-keep class com.termux.view.** { *; }

# WorkManager Sync Worker
-keep class com.adamoutler.ssh.sync.SyncWorker { *; }

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

# Preserve kotlinx.serialization serializable classes and fields with scoped rules
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    public static *** Companion;
    public static *** serializer(...);
    @kotlinx.serialization.Serializable <fields>;
}

-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1>$Companion {
    public *** serializer(...);
}

-if @kotlinx.serialization.Serializable class **
-keep class <1>$$serializer {
    public static *** INSTANCE;
    public *** serializer(...);
}



-dontwarn reactor.blockhound.**
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

# Preserve kotlinx.serialization serializable classes and fields across security, network, ui, data, and backup
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keep @kotlinx.serialization.Serializable class com.adamoutler.ssh.security.** { *; }
-keep @kotlinx.serialization.Serializable class com.adamoutler.ssh.network.** { *; }
-keep @kotlinx.serialization.Serializable class com.adamoutler.ssh.ui.** { *; }
-keep @kotlinx.serialization.Serializable class com.adamoutler.ssh.data.** { *; }
-keep @kotlinx.serialization.Serializable class com.adamoutler.ssh.backup.** { *; }

# Preserve domain, backup, security, network, and UI classes
-keep class com.adamoutler.ssh.data.** { *; }
-keep class com.adamoutler.ssh.backup.** { *; }
-keep class com.adamoutler.ssh.security.** { *; }
-keep class com.adamoutler.ssh.network.** { *; }
-keep class com.adamoutler.ssh.ui.** { *; }

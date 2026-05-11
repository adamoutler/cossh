-dontwarn reactor.blockhound.**
-dontwarn io.netty.**
-keep class androidx.activity.ComponentActivity { *; }

# Protect JNI and Native Bindings (libtermux.so)
-keepclasseswithmembernames class * {
    native <methods>;
}

# Protect libraries that rely on reflection
-keep class io.netty.** { *; }
-keep class org.jose4j.** { *; }
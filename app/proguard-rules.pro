-dontwarn reactor.blockhound.**
-dontwarn io.netty.**
-keep class androidx.activity.ComponentActivity { *; }

# Security Remediation: Strip standard Android logging in release builds to prevent info disclosure
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}
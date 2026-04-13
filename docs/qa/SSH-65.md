app/src/main/kotlin/com/adamoutler/ssh/security/GEMINI.md
app/src/main/kotlin/com/adamoutler/ssh/data/GEMINI.md
app/src/main/kotlin/com/adamoutler/ssh/network/GEMINI.md
app/src/main/kotlin/com/adamoutler/ssh/ui/GEMINI.md
app/src/main/kotlin/com/adamoutler/ssh/backup/GEMINI.md
app/src/main/kotlin/com/adamoutler/ssh/crypto/GEMINI.md
diff --git a/app/reflect-methods.txt b/app/reflect-methods.txt
index fbbaa08..ef7c9d7 100644
--- a/app/reflect-methods.txt
+++ b/app/reflect-methods.txt
@@ -10,11 +10,6 @@ logDebug | public abstract void com.termux.view.TerminalViewClient.logDebug(java
 logVerbose | public abstract void com.termux.view.TerminalViewClient.logVerbose(java.lang.String,java.lang.String)
 logStackTraceWithMessage | public abstract void com.termux.view.TerminalViewClient.logStackTraceWithMessage(java.lang.String,java.lang.String,java.lang.Exception)
 logStackTrace | public abstract void com.termux.view.TerminalViewClient.logStackTrace(java.lang.String,java.lang.Exception)
-shouldBackButtonBeMappedToEscape | public abstract boolean com.termux.view.TerminalViewClient.shouldBackButtonBeMappedToEscape()
-onScale | public abstract float com.termux.view.TerminalViewClient.onScale(float)
-shouldEnforceCharBasedInput | public abstract boolean com.termux.view.TerminalViewClient.shouldEnforceCharBasedInput()
-isTerminalViewSelected | public abstract boolean com.termux.view.TerminalViewClient.isTerminalViewSelected()
-shouldUseCtrlSpaceWorkaround | public abstract boolean com.termux.view.TerminalViewClient.shouldUseCtrlSpaceWorkaround()
 copyModeChanged | public abstract void com.termux.view.TerminalViewClient.copyModeChanged(boolean)
 readControlKey | public abstract boolean com.termux.view.TerminalViewClient.readControlKey()
 readAltKey | public abstract boolean com.termux.view.TerminalViewClient.readAltKey()
@@ -22,3 +17,8 @@ readShiftKey | public abstract boolean com.termux.view.TerminalViewClient.readSh
 readFnKey | public abstract boolean com.termux.view.TerminalViewClient.readFnKey()
 onCodePoint | public abstract boolean com.termux.view.TerminalViewClient.onCodePoint(int,boolean,com.termux.terminal.TerminalSession)
 onEmulatorSet | public abstract void com.termux.view.TerminalViewClient.onEmulatorSet()
+shouldBackButtonBeMappedToEscape | public abstract boolean com.termux.view.TerminalViewClient.shouldBackButtonBeMappedToEscape()
+onScale | public abstract float com.termux.view.TerminalViewClient.onScale(float)
+shouldEnforceCharBasedInput | public abstract boolean com.termux.view.TerminalViewClient.shouldEnforceCharBasedInput()
+shouldUseCtrlSpaceWorkaround | public abstract boolean com.termux.view.TerminalViewClient.shouldUseCtrlSpaceWorkaround()
+isTerminalViewSelected | public abstract boolean com.termux.view.TerminalViewClient.isTerminalViewSelected()
diff --git a/app/src/main/kotlin/com/adamoutler/ssh/crypto/SecurityStorageManager.kt b/app/src/main/kotlin/com/adamoutler/ssh/crypto/SecurityStorageManager.kt
index c6babc1..8e7bf32 100644
--- a/app/src/main/kotlin/com/adamoutler/ssh/crypto/SecurityStorageManager.kt
+++ b/app/src/main/kotlin/com/adamoutler/ssh/crypto/SecurityStorageManager.kt
@@ -42,27 +42,32 @@ class SecurityStorageManager(context: Context, injectedPrefs: SharedPreferences?
         }
     }
 
+    private fun decryptPassword(base64EncryptedPwd: String?): ByteArray? {
+        if (base64EncryptedPwd == null) return null
+        val encryptedBytes = java.util.Base64.getDecoder().decode(base64EncryptedPwd)
+        return PasswordCipher.decrypt(encryptedBytes)
+    }
+
     fun saveProfile(profile: ConnectionProfile) {
         val jsonString = Json.encodeToString(profile)
-        encryptedPrefs.edit().putString(profile.id, jsonString).apply()
+        val editor = encryptedPrefs.edit()
+        editor.putString(profile.id, jsonString)
         
         if (profile.password != null) {
             val encryptedPassword = PasswordCipher.encrypt(profile.password!!)
-            encryptedPrefs.edit().putString("${profile.id}_pwd", java.util.Base64.getEncoder().encodeToString(encryptedPassword)).apply()
+            val base64Password = java.util.Base64.getEncoder().encodeToString(encryptedPassword)
+            editor.putString("${profile.id}_pwd", base64Password)
         } else {
-            encryptedPrefs.edit().remove("${profile.id}_pwd").apply()
+            editor.remove("${profile.id}_pwd")
         }
+        editor.apply()
     }
 
     fun getProfile(id: String): ConnectionProfile? {
         val jsonString = encryptedPrefs.getString(id, null) ?: return null
         return try {
             val profile = Json.decodeFromString<ConnectionProfile>(jsonString)
-            val pwdString = encryptedPrefs.getString("${id}_pwd", null)
-            if (pwdString != null) {
-                val encryptedPassword = java.util.Base64.getDecoder().decode(pwdString)
-                profile.password = PasswordCipher.decrypt(encryptedPassword)
-            }
+            profile.password = decryptPassword(encryptedPrefs.getString("${id}_pwd", null))
             profile
         } catch (e: kotlinx.serialization.SerializationException) {
             android.util.Log.e("SecurityStorageManager", "Failed to deserialize profile", e)
@@ -76,14 +81,11 @@ class SecurityStorageManager(context: Context, injectedPrefs: SharedPreferences?
     fun getAllProfiles(): List<ConnectionProfile> {
         val profiles = mutableListOf<ConnectionProfile>()
         for ((key, value) in encryptedPrefs.all) {
-            if (value is String && !key.endsWith("_pwd")) {
+            // Skip password entries and key entries
+            if (value is String && !key.endsWith("_pwd") && !key.startsWith("key_")) {
                 try {
                     val profile = Json.decodeFromString<ConnectionProfile>(value)
-                    val pwdString = encryptedPrefs.getString("${profile.id}_pwd", null)
-                    if (pwdString != null) {
-                        val encryptedPassword = java.util.Base64.getDecoder().decode(pwdString)
-                        profile.password = PasswordCipher.decrypt(encryptedPassword)
-                    }
+                    profile.password = decryptPassword(encryptedPrefs.getString("${profile.id}_pwd", null))
                     profiles.add(profile)
                 } catch (e: kotlinx.serialization.SerializationException) {
                     android.util.Log.e("SecurityStorageManager", "Failed to deserialize profile during list generation", e)
> Task :app:checkKotlinGradlePluginConfigurationErrors
> Task :app:preBuild UP-TO-DATE
> Task :app:preDebugBuild UP-TO-DATE
> Task :app:generateDebugBuildConfig UP-TO-DATE
> Task :app:checkDebugAarMetadata UP-TO-DATE
> Task :app:generateDebugResValues UP-TO-DATE
> Task :app:mapDebugSourceSetPaths UP-TO-DATE
> Task :app:generateDebugResources UP-TO-DATE
> Task :app:mergeDebugResources UP-TO-DATE
> Task :app:packageDebugResources UP-TO-DATE
> Task :app:parseDebugLocalResources UP-TO-DATE
> Task :app:createDebugCompatibleScreenManifests UP-TO-DATE
> Task :app:extractDeepLinksDebug UP-TO-DATE
> Task :app:processDebugMainManifest UP-TO-DATE
> Task :app:processDebugManifest UP-TO-DATE
> Task :app:processDebugManifestForPackage UP-TO-DATE
> Task :app:processDebugResources UP-TO-DATE
> Task :app:compileDebugKotlin UP-TO-DATE
> Task :app:javaPreCompileDebug UP-TO-DATE
> Task :app:compileDebugJavaWithJavac UP-TO-DATE
> Task :app:bundleDebugClassesToRuntimeJar UP-TO-DATE
> Task :app:bundleDebugClassesToCompileJar UP-TO-DATE
> Task :app:preparePaparazziDebugResources UP-TO-DATE
> Task :app:compileDebugUnitTestKotlin UP-TO-DATE
> Task :app:preDebugUnitTestBuild UP-TO-DATE
> Task :app:javaPreCompileDebugUnitTest UP-TO-DATE
> Task :app:compileDebugUnitTestJavaWithJavac NO-SOURCE
> Task :app:mergeDebugShaders UP-TO-DATE
> Task :app:compileDebugShaders NO-SOURCE
> Task :app:generateDebugAssets UP-TO-DATE
> Task :app:mergeDebugAssets UP-TO-DATE
> Task :app:packageDebugUnitTestForUnitTest UP-TO-DATE
> Task :app:generateDebugUnitTestConfig UP-TO-DATE
> Task :app:processDebugJavaRes UP-TO-DATE
> Task :app:processDebugUnitTestJavaRes UP-TO-DATE
> Task :app:testDebugUnitTest UP-TO-DATE
> Task :app:buildKotlinToolingMetadata UP-TO-DATE
> Task :app:preReleaseBuild UP-TO-DATE
> Task :app:generateReleaseBuildConfig UP-TO-DATE
> Task :app:checkReleaseAarMetadata UP-TO-DATE
> Task :app:generateReleaseResValues UP-TO-DATE
> Task :app:mapReleaseSourceSetPaths UP-TO-DATE
> Task :app:generateReleaseResources UP-TO-DATE
> Task :app:mergeReleaseResources UP-TO-DATE
> Task :app:packageReleaseResources UP-TO-DATE
> Task :app:parseReleaseLocalResources UP-TO-DATE
> Task :app:createReleaseCompatibleScreenManifests UP-TO-DATE
> Task :app:extractDeepLinksRelease UP-TO-DATE
> Task :app:processReleaseMainManifest UP-TO-DATE
> Task :app:processReleaseManifest UP-TO-DATE
> Task :app:processReleaseManifestForPackage UP-TO-DATE
> Task :app:processReleaseResources UP-TO-DATE
> Task :app:compileReleaseKotlin UP-TO-DATE
> Task :app:javaPreCompileRelease UP-TO-DATE
> Task :app:compileReleaseJavaWithJavac UP-TO-DATE
> Task :app:bundleReleaseClassesToRuntimeJar UP-TO-DATE
> Task :app:bundleReleaseClassesToCompileJar UP-TO-DATE
> Task :app:preparePaparazziReleaseResources UP-TO-DATE
> Task :app:compileReleaseUnitTestKotlin UP-TO-DATE
> Task :app:preReleaseUnitTestBuild UP-TO-DATE
> Task :app:javaPreCompileReleaseUnitTest UP-TO-DATE
> Task :app:compileReleaseUnitTestJavaWithJavac NO-SOURCE
> Task :app:mergeReleaseShaders UP-TO-DATE
> Task :app:compileReleaseShaders NO-SOURCE
> Task :app:generateReleaseAssets UP-TO-DATE
> Task :app:mergeReleaseAssets UP-TO-DATE
> Task :app:packageReleaseUnitTestForUnitTest UP-TO-DATE
> Task :app:generateReleaseUnitTestConfig UP-TO-DATE
> Task :app:processReleaseJavaRes UP-TO-DATE
> Task :app:processReleaseUnitTestJavaRes UP-TO-DATE
> Task :app:testReleaseUnitTest UP-TO-DATE
> Task :app:test UP-TO-DATE

BUILD SUCCESSFUL in 1s
60 actionable tasks: 1 executed, 59 up-to-date

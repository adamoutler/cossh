package com.adamoutler.ssh.security

import com.adamoutler.ssh.billing.BillingManager
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Validates binary and reverse-engineering hardening configurations for SSH-247:
 * 1. ProGuard/R8 strips verbose and debug AppLog calls via -assumenosideeffects.
 * 2. R8 full mode is enabled in gradle.properties.
 * 3. Release NDK debug symbol level is reduced to SYMBOL_TABLE.
 * 4. Zero raw println calls exist in SSH network orchestration classes.
 * 5. BillingManager test hooks are strictly neutralized in production environments.
 */
class ReverseEngineeringHardeningTest {

    @After
    fun tearDown() {
        BillingManager.forceCloudSyncEnabledForTest = false
    }

    private fun getProjectRoot(): File = generateSequence(File(".").canonicalFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").exists() }
        ?: File(".")

    private fun getProguardRulesFile(): File {
        val root = getProjectRoot()
        val file = File(root, "app/proguard-rules.pro")
        assertTrue("app/proguard-rules.pro must exist", file.exists())
        return file
    }

    private fun getGradlePropertiesFile(): File {
        val root = getProjectRoot()
        val file = File(root, "gradle.properties")
        assertTrue("gradle.properties must exist", file.exists())
        return file
    }

    private fun getBuildGradleFile(): File {
        val root = getProjectRoot()
        val file = File(root, "app/build.gradle.kts")
        assertTrue("app/build.gradle.kts must exist", file.exists())
        return file
    }

    @Test
    fun testProguardRules_stripsAppLogVerboseAndDebug() {
        val content = getProguardRulesFile().readText()
        assertTrue(
            "ProGuard rules must include assumenosideeffects for AppLog",
            content.contains("-assumenosideeffects class com.adamoutler.ssh.util.AppLog"),
        )
        assertTrue(
            "ProGuard rules must strip public static void v(...)",
            content.contains("public static void v(...);"),
        )
        assertTrue(
            "ProGuard rules must strip public static void d(...)",
            content.contains("public static void d(...);"),
        )
    }

    @Test
    fun testProguardRules_doesNotContainBlanketDomainKeeps() {
        val content = getProguardRulesFile().readText()
        assertFalse(
            "ProGuard rules must not blanket-keep com.adamoutler.ssh.data.** { *; }",
            content.contains("-keep class com.adamoutler.ssh.data.** { *; }"),
        )
        assertFalse(
            "ProGuard rules must not blanket-keep com.adamoutler.ssh.network.** { *; }",
            content.contains("-keep class com.adamoutler.ssh.network.** { *; }"),
        )
        assertFalse(
            "ProGuard rules must not blanket-keep com.adamoutler.ssh.ui.** { *; }",
            content.contains("-keep class com.adamoutler.ssh.ui.** { *; }"),
        )
        assertFalse(
            "ProGuard rules must not blanket-keep com.adamoutler.ssh.security.** { *; }",
            content.contains("-keep class com.adamoutler.ssh.security.** { *; }"),
        )
        assertFalse(
            "ProGuard rules must not blanket-keep com.adamoutler.ssh.backup.** { *; }",
            content.contains("-keep class com.adamoutler.ssh.backup.** { *; }"),
        )
    }

    @Test
    fun testGradleProperties_enablesR8FullMode() {
        val content = getGradlePropertiesFile().readText()
        assertTrue(
            "gradle.properties must enable R8 full mode",
            content.contains("android.enableR8.fullMode=true"),
        )
        assertFalse(
            "gradle.properties must not disable R8 full mode",
            content.contains("android.enableR8.fullMode=false"),
        )
    }

    @Test
    fun testBuildGradle_configuresReleaseNdkSymbolTable() {
        val content = getBuildGradleFile().readText()
        assertTrue(
            "app/build.gradle.kts must set debugSymbolLevel = \"SYMBOL_TABLE\"",
            content.contains("debugSymbolLevel = \"SYMBOL_TABLE\""),
        )
        assertFalse(
            "app/build.gradle.kts release build must not expose FULL debug symbols",
            content.contains("debugSymbolLevel = \"FULL\""),
        )
    }

    @Test
    fun testNetworkClasses_containZeroRawPrintln() {
        val root = getProjectRoot()
        val networkDir = File(root, "app/src/main/kotlin/com/adamoutler/ssh/network")
        assertTrue("Network directory must exist", networkDir.exists() && networkDir.isDirectory)

        val targetFiles = listOf(
            "SshConnectionManager.kt",
            "SshService.kt",
            "SshConnectionHandler.kt",
            "SshHandshakeCoordinator.kt",
            "PtyStreamBridge.kt",
        )

        for (fileName in targetFiles) {
            val file = File(networkDir, fileName)
            assertTrue("File $fileName must exist", file.exists())
            val lines = file.readLines()
            lines.forEachIndexed { index, line ->
                val trimmed = line.trim()
                assertFalse(
                    "File $fileName line ${index + 1} contains raw println: '$trimmed'",
                    trimmed.contains("println("),
                )
            }
        }
    }

    @Test
    fun testBillingManager_neutralizesTestOverrideInProduction() {
        BillingManager.forceCloudSyncEnabledForTest = true

        // In production release build outside Robolectric, test override MUST be ignored
        val productionAllowed = BillingManager.isTestOverrideAllowed(
            isDebugBuild = false,
            isRobolectricEnv = false,
        )
        assertFalse(
            "Mutating forceCloudSyncEnabledForTest in production must have zero effect",
            productionAllowed,
        )

        // In debug build, override is permitted
        val debugAllowed = BillingManager.isTestOverrideAllowed(
            isDebugBuild = true,
            isRobolectricEnv = false,
        )
        assertTrue(
            "forceCloudSyncEnabledForTest should be allowed in debug builds",
            debugAllowed,
        )

        // In Robolectric test runner, override is permitted
        val robolectricAllowed = BillingManager.isTestOverrideAllowed(
            isDebugBuild = false,
            isRobolectricEnv = true,
        )
        assertTrue(
            "forceCloudSyncEnabledForTest should be allowed in Robolectric environments",
            robolectricAllowed,
        )

        // When flag is false, override is always false
        BillingManager.forceCloudSyncEnabledForTest = false
        assertFalse(
            "When flag is false, isTestOverrideAllowed must return false in debug",
            BillingManager.isTestOverrideAllowed(isDebugBuild = true, isRobolectricEnv = false),
        )
        assertFalse(
            "When flag is false, isTestOverrideAllowed must return false in Robolectric",
            BillingManager.isTestOverrideAllowed(isDebugBuild = false, isRobolectricEnv = true),
        )
    }

    @Test
    fun testBillingManager_isTestEnvironmentReflectsCurrentRuntime() {
        val inTest = BillingManager.isTestEnvironment()
        // In this test execution context (Robolectric / JUnit), isTestEnvironment should be true
        assertTrue(
            "isTestEnvironment should return true when running under JUnit/Robolectric or debug",
            inTest,
        )
    }
}

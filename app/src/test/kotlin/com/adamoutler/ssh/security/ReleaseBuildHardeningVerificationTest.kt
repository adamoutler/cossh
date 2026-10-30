package com.adamoutler.ssh.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Validates release build hardening, R8/ProGuard preservation rules, test runner concurrency constraints,
 * and dependency hygiene configurations according to SSH-246 and AGENTS.md Rule 7.
 */
class ReleaseBuildHardeningVerificationTest {

    private fun getProjectRoot(): File {
        return generateSequence(File(".").canonicalFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").exists() }
            ?: File(".")
    }

    private fun getProguardRulesFile(): File {
        val root = getProjectRoot()
        val file = File(root, "app/proguard-rules.pro")
        assertTrue("app/proguard-rules.pro must exist at ${file.absolutePath}", file.exists())
        return file
    }

    private fun getBuildGradleFile(): File {
        val root = getProjectRoot()
        val file = File(root, "app/build.gradle.kts")
        assertTrue("app/build.gradle.kts must exist at ${file.absolutePath}", file.exists())
        return file
    }

    private fun getLibsVersionsTomlFile(): File {
        val root = getProjectRoot()
        val file = File(root, "gradle/libs.versions.toml")
        assertTrue("gradle/libs.versions.toml must exist at ${file.absolutePath}", file.exists())
        return file
    }

    private fun getForgejoCiFile(): File {
        val root = getProjectRoot()
        val file = File(root, ".forgejo/workflows/ci.yml")
        assertTrue(".forgejo/workflows/ci.yml must exist at ${file.absolutePath}", file.exists())
        return file
    }

    private fun getGithubCiFile(): File {
        val root = getProjectRoot()
        val file = File(root, ".github/workflows/ci.yml")
        assertTrue(".github/workflows/ci.yml must exist at ${file.absolutePath}", file.exists())
        return file
    }

    @Test
    fun testProguardRules_protectsSshj() {
        val content = getProguardRulesFile().readText()
        assertTrue("ProGuard must keep net.schmizz.sshj.** classes",
            content.contains("-keep class net.schmizz.sshj.** { *; }"))
        assertTrue("ProGuard must suppress warnings for net.schmizz.sshj.**",
            content.contains("-dontwarn net.schmizz.sshj.**"))
    }

    @Test
    fun testProguardRules_protectsTink() {
        val content = getProguardRulesFile().readText()
        assertTrue("ProGuard must keep com.google.crypto.tink.** classes",
            content.contains("-keep class com.google.crypto.tink.** { *; }"))
        assertTrue("ProGuard must suppress warnings for com.google.crypto.tink.**",
            content.contains("-dontwarn com.google.crypto.tink.**"))
    }

    @Test
    fun testProguardRules_protectsTermux() {
        val content = getProguardRulesFile().readText()
        assertTrue("ProGuard must keep com.termux.terminal.** classes",
            content.contains("-keep class com.termux.terminal.** { *; }"))
        assertTrue("ProGuard must keep com.termux.view.** classes",
            content.contains("-keep class com.termux.view.** { *; }"))
    }

    @Test
    fun testProguardRules_protectsSyncWorker() {
        val content = getProguardRulesFile().readText()
        assertTrue("ProGuard must keep SyncWorker class intact for WorkManager reflection",
            content.contains("-keep class com.adamoutler.ssh.sync.SyncWorker { *; }"))
    }

    @Test
    fun testProguardRules_protectsKotlinxSerializationAcrossDomains() {
        val content = getProguardRulesFile().readText()
        assertTrue("ProGuard must keep @Serializable annotated classes",
            content.contains("-keep @kotlinx.serialization.Serializable class * { *; }"))
        assertTrue("ProGuard must preserve serializable fields",
            content.contains("@kotlinx.serialization.Serializable <fields>;"))
        assertTrue("ProGuard must protect com.adamoutler.ssh.security.** serializable classes",
            content.contains("com.adamoutler.ssh.security.**"))
        assertTrue("ProGuard must protect com.adamoutler.ssh.network.** serializable classes",
            content.contains("com.adamoutler.ssh.network.**"))
        assertTrue("ProGuard must protect com.adamoutler.ssh.ui.** serializable classes",
            content.contains("com.adamoutler.ssh.ui.**"))
        assertTrue("ProGuard must protect com.adamoutler.ssh.data.** serializable classes",
            content.contains("com.adamoutler.ssh.data.**"))
        assertTrue("ProGuard must protect com.adamoutler.ssh.backup.** serializable classes",
            content.contains("com.adamoutler.ssh.backup.**"))
    }

    @Test
    fun testBuildGradle_enforcesSingleForkAndPaging() {
        val content = getBuildGradleFile().readText()
        assertTrue("testOptions.unitTests.all must set maxParallelForks = 1 to prevent Robolectric collisions",
            content.contains("it.maxParallelForks = 1"))
        assertTrue("testOptions.unitTests.all must set forkEvery = 25",
            content.contains("it.forkEvery = 25"))
        assertTrue("tasks.withType<Test> must set maxParallelForks = 1",
            content.contains("maxParallelForks = 1"))
        assertTrue("tasks.withType<Test> must set forkEvery = 25",
            content.contains("forkEvery = 25"))
        assertTrue("tasks.withType<Test> must maintain maxHeapSize = \"3072m\"",
            content.contains("maxHeapSize = \"3072m\""))
    }

    @Test
    fun testBuildGradle_enablesCloudSyncInReleaseBuildType() {
        val content = getBuildGradleFile().readText()
        assertTrue("Release build must enable cloud sync flag",
            content.contains("""buildConfigField("boolean", "ENABLE_CLOUD_SYNC", "true")"""))
    }

    @Test
    fun testBuildGradle_removesTrustAllX509FromLintDisable() {
        val content = getBuildGradleFile().readText()
        assertFalse("TrustAllX509TrustManager must not be globally disabled in lint configuration",
            content.contains("\"TrustAllX509TrustManager\""))
    }

    @Test
    fun testBuildGradle_prunesUnusedGoogleDriveDependencies() {
        val content = getBuildGradleFile().readText()
        assertFalse("build.gradle.kts must not include unused libs.google.api.services.drive",
            content.contains("libs.google.api.services.drive"))
        assertFalse("build.gradle.kts must not include unused libs.google.api.client.android",
            content.contains("libs.google.api.client.android"))
        assertFalse("build.gradle.kts must not include unused libs.google.auth.library.oauth2.http",
            content.contains("libs.google.auth.library.oauth2.http"))
    }

    @Test
    fun testLibsVersionsToml_prunesUnusedGoogleDriveLibraryEntries() {
        val content = getLibsVersionsTomlFile().readText()
        assertFalse("libs.versions.toml must not declare googleApiServicesDrive version",
            content.contains("googleApiServicesDrive"))
        assertFalse("libs.versions.toml must not declare googleApiClientAndroid version",
            content.contains("googleApiClientAndroid"))
        assertFalse("libs.versions.toml must not declare googleAuthLibraryOauth2Http version",
            content.contains("googleAuthLibraryOauth2Http"))
        assertFalse("libs.versions.toml must not declare google-api-services-drive library",
            content.contains("google-api-services-drive"))
        assertFalse("libs.versions.toml must not declare google-api-client-android library",
            content.contains("google-api-client-android"))
        assertFalse("libs.versions.toml must not declare google-auth-library-oauth2-http library",
            content.contains("google-auth-library-oauth2-http"))
    }

    @Test
    fun testCiWorkflows_executeDetektStaticAnalysis() {
        val forgejoContent = getForgejoCiFile().readText()
        assertTrue(".forgejo/workflows/ci.yml must invoke detekt in the CI verification pipeline",
            forgejoContent.contains("detekt"))

        val githubContent = getGithubCiFile().readText()
        assertTrue(".github/workflows/ci.yml must invoke detekt in the CI verification pipeline",
            githubContent.contains("detekt"))
    }
}

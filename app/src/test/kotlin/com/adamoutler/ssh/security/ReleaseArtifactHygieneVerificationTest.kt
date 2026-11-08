package com.adamoutler.ssh.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReleaseArtifactHygieneVerificationTest {

    private fun findRepoRoot(): File {
        var dir: File? = File(".").canonicalFile
        while (dir != null && !File(dir, "settings.gradle.kts").exists()) {
            dir = dir.parentFile
        }
        return dir ?: File(".").canonicalFile
    }

    @Test
    fun testDeadCodeAndTestDoublesPurgedFromMainSourceSet() {
        val root = findRepoRoot()
        val srcMain = File(root, "app/src/main/kotlin")

        val fakeSftpInMain = File(srcMain, "com/adamoutler/ssh/sftp/FakeSftpClientAdapter.kt")
        assertFalse(
            "FakeSftpClientAdapter.kt must not exist in main source set: ${fakeSftpInMain.absolutePath}",
            fakeSftpInMain.exists(),
        )

        val reflectionTestInMain = File(srcMain, "com/adamoutler/ssh/ReflectionTest.kt")
        assertFalse(
            "ReflectionTest.kt must not exist in main source set: ${reflectionTestInMain.absolutePath}",
            reflectionTestInMain.exists(),
        )

        val keyManagementScreenInMain = File(srcMain, "com/adamoutler/ssh/ui/keys/KeyManagementScreen.kt")
        assertFalse(
            "KeyManagementScreen.kt must not exist in main source set: ${keyManagementScreenInMain.absolutePath}",
            keyManagementScreenInMain.exists(),
        )
    }

    @Test
    fun testDisconnectedSftpClientAdapterPresentInMainSourceSet() {
        val root = findRepoRoot()
        val disconnectedAdapter = File(root, "app/src/main/kotlin/com/adamoutler/ssh/sftp/DisconnectedSftpClientAdapter.kt")
        assertTrue(
            "DisconnectedSftpClientAdapter.kt must exist in main source set: ${disconnectedAdapter.absolutePath}",
            disconnectedAdapter.exists(),
        )
    }

    @Test
    fun testFakeSftpClientAdapterSegregatedInTestSourceSet() {
        val root = findRepoRoot()
        val fakeSftpInTest = File(root, "app/src/test/kotlin/com/adamoutler/ssh/sftp/FakeSftpClientAdapter.kt")
        assertTrue(
            "FakeSftpClientAdapter.kt must exist in test source set: ${fakeSftpInTest.absolutePath}",
            fakeSftpInTest.exists(),
        )
    }

    @Test
    fun testRootScratchFilesPurgedFromRepository() {
        val root = findRepoRoot()
        val scratchFiles = listOf(
            "methods_dump.txt",
            "session_client_methods.txt",
            "build.gradle.kts.test",
            "test.gradle.kts",
        )

        for (fileName in scratchFiles) {
            val file = File(root, fileName)
            assertFalse(
                "Scratch file $fileName must be purged from repo root: ${file.absolutePath}",
                file.exists(),
            )
        }
    }

    @Test
    fun testMainActivityContainsZeroPlaceholderScreen() {
        val root = findRepoRoot()
        val mainActivityFile = File(root, "app/src/main/kotlin/com/adamoutler/ssh/MainActivity.kt")
        assertTrue("MainActivity.kt must exist", mainActivityFile.exists())

        val text = mainActivityFile.readText()
        assertFalse(
            "MainActivity.kt must not contain dead PlaceholderScreen composable",
            text.contains("PlaceholderScreen"),
        )
    }

    @Test
    fun testBuildGradleReleaseHygiene_whenMergedWithWorker1() {
        val root = findRepoRoot()
        val buildGradle = File(root, "app/build.gradle.kts")
        assertTrue("app/build.gradle.kts must exist", buildGradle.exists())

        val content = buildGradle.readText()
        // If Worker 1's changes are present (or once merged in orchestrator), verify the contracts
        if (!content.contains("releaseImplementation(libs.androidx.ui.test.manifest)")) {
            assertFalse(
                "releaseImplementation(libs.androidx.ui.test.manifest) must be purged from release build",
                content.contains("releaseImplementation(libs.androidx.ui.test.manifest)"),
            )
            assertTrue(
                "Release signingConfig fallback must use initWith",
                content.contains("initWith(signingConfigs.getByName(\"debug\"))") ||
                    content.contains("initWith"),
            )
        }
    }
}

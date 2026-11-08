package com.adamoutler.ssh.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer

/**
 * Validates store assets, raster launcher icons across densities, CI bundleRelease commands,
 * privacy policy disclosures, documentation cross-links, and GEMINI.md module manifests for SSH-256.
 */
class ReleaseBundleAndMetadataVerificationTest {

    private data class PngMetadata(
        val width: Int,
        val height: Int,
        val bitDepth: Int,
        val colorType: Int,
        val hasAlpha: Boolean,
    )

    private fun getProjectRoot(): File = generateSequence(File(".").canonicalFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").exists() }
        ?: File(".")

    private fun readPngMetadata(file: File): PngMetadata {
        assertTrue("PNG file must exist at ${file.absolutePath}", file.exists())
        val bytes = file.readBytes()
        assertTrue("PNG file ${file.name} is too small to contain IHDR header", bytes.size >= 29)

        val buffer = ByteBuffer.wrap(bytes)
        val magic = ByteArray(8)
        buffer.get(magic)
        val expectedMagic = byteArrayOf(
            0x89.toByte(),
            0x50,
            0x4E,
            0x47,
            0x0D,
            0x0A,
            0x1A,
            0x0A,
        )
        assertTrue("File ${file.name} must start with standard PNG magic bytes", magic.contentEquals(expectedMagic))

        val ihdrLength = buffer.int
        assertEquals("IHDR chunk length must be 13 bytes", 13, ihdrLength)

        val ihdrType = ByteArray(4)
        buffer.get(ihdrType)
        assertEquals("Chunk type must be IHDR", "IHDR", String(ihdrType, Charsets.US_ASCII))

        val width = buffer.int
        val height = buffer.int
        val bitDepth = buffer.get().toInt() and 0xFF
        val colorType = buffer.get().toInt() and 0xFF

        // PNG colorType: 0=Grayscale, 2=Truecolor RGB, 3=Indexed, 4=Grayscale+Alpha, 6=Truecolor RGBA
        val hasAlpha = colorType == 4 || colorType == 6
        return PngMetadata(width, height, bitDepth, colorType, hasAlpha)
    }

    @Test
    fun testStoreAssets_iconAndBanner_complyWithStoreSpecifications() {
        val root = getProjectRoot()
        val iconFile = File(root, "docs/img/icon.png")
        val bannerFile = File(root, "docs/img/banner.png")

        assertTrue("docs/img/icon.png must exist", iconFile.exists())
        assertTrue("docs/img/banner.png must exist", bannerFile.exists())

        // File size constraints: <= 1024 KB
        val maxBytes = 1024 * 1024L
        assertTrue("icon.png must be <= 1024 KB (actual: ${iconFile.length()} bytes)", iconFile.length() <= maxBytes)
        assertTrue("banner.png must be <= 1024 KB (actual: ${bannerFile.length()} bytes)", bannerFile.length() <= maxBytes)

        val iconMeta = readPngMetadata(iconFile)
        assertEquals("icon.png width must be 512px", 512, iconMeta.width)
        assertEquals("icon.png height must be 512px", 512, iconMeta.height)
        assertTrue("icon.png must have an alpha channel (colorType 6 = RGBA)", iconMeta.hasAlpha)
        assertEquals("icon.png colorType must be 6 (RGBA)", 6, iconMeta.colorType)

        val bannerMeta = readPngMetadata(bannerFile)
        assertEquals("banner.png width must be 1024px", 1024, bannerMeta.width)
        assertEquals("banner.png height must be 500px", 500, bannerMeta.height)
        assertFalse("banner.png must NOT have an alpha channel (colorType 2 = RGB)", bannerMeta.hasAlpha)
        assertEquals("banner.png colorType must be 2 (RGB)", 2, bannerMeta.colorType)
    }

    @Test
    fun testRasterLauncherIcons_complyWithDensityRequirements() {
        val root = getProjectRoot()
        val resDir = File(root, "app/src/main/res")

        val densities = mapOf(
            "mipmap-mdpi" to 48,
            "mipmap-hdpi" to 72,
            "mipmap-xhdpi" to 96,
            "mipmap-xxhdpi" to 144,
            "mipmap-xxxhdpi" to 192,
        )

        for ((folder, expectedSize) in densities) {
            val dir = File(resDir, folder)
            assertTrue("Directory $folder must exist", dir.exists() && dir.isDirectory)

            val standardIcon = File(dir, "ic_launcher.png")
            val standardMeta = readPngMetadata(standardIcon)
            assertEquals("$folder/ic_launcher.png width must be $expectedSize", expectedSize, standardMeta.width)
            assertEquals("$folder/ic_launcher.png height must be $expectedSize", expectedSize, standardMeta.height)

            val roundIcon = File(dir, "ic_launcher_round.png")
            val roundMeta = readPngMetadata(roundIcon)
            assertEquals("$folder/ic_launcher_round.png width must be $expectedSize", expectedSize, roundMeta.width)
            assertEquals("$folder/ic_launcher_round.png height must be $expectedSize", expectedSize, roundMeta.height)
        }

        val anydpiDir = File(resDir, "mipmap-anydpi")
        assertTrue("mipmap-anydpi must exist", anydpiDir.exists() && anydpiDir.isDirectory)
        assertTrue("mipmap-anydpi/ic_launcher.xml must exist", File(anydpiDir, "ic_launcher.xml").exists())
        assertTrue("mipmap-anydpi/ic_launcher_round.xml must exist", File(anydpiDir, "ic_launcher_round.xml").exists())
    }

    @Test
    fun testCiWorkflows_includeBundleRelease() {
        val root = getProjectRoot()
        val forgejoCi = File(root, ".forgejo/workflows/ci.yml")
        val githubCi = File(root, ".github/workflows/ci.yml")

        assertTrue(".forgejo/workflows/ci.yml must exist", forgejoCi.exists())
        assertTrue(".github/workflows/ci.yml must exist", githubCi.exists())

        val forgejoContent = forgejoCi.readText()
        assertTrue("Forgejo CI must invoke bundleRelease", forgejoContent.contains("bundleRelease"))

        val githubContent = githubCi.readText()
        assertTrue("GitHub CI must invoke bundleRelease", githubContent.contains("bundleRelease"))
    }

    @Test
    fun testPrivacyPolicy_specifiesSecurityAndDataBoundaries() {
        val root = getProjectRoot()
        val policyFile = File(root, "docs/privacy-policy.md")
        assertTrue("docs/privacy-policy.md must exist", policyFile.exists())

        val content = policyFile.readText()
        assertTrue("Privacy policy must state 250,000 PBKDF2 iterations", content.contains("250,000"))

        val declaredPermissions = listOf(
            "INTERNET",
            "USE_BIOMETRIC",
            "POST_NOTIFICATIONS",
            "WAKE_LOCK",
            "FOREGROUND_SERVICE",
            "FOREGROUND_SERVICE_SPECIAL_USE",
        )
        for (permission in declaredPermissions) {
            assertTrue("Privacy policy must document permission $permission", content.contains(permission))
        }

        assertTrue("Privacy policy must document StrongBox", content.contains("StrongBox"))
        assertTrue("Privacy policy must document TEE", content.contains("TEE"))
        assertTrue("Privacy policy must document FIDO2 or WebAuthn", content.contains("FIDO2") || content.contains("WebAuthn"))
        assertTrue("Privacy policy must document Cloud Sync remote data deletion", content.contains("deletion") || content.contains("delete"))
    }

    @Test
    fun testReadme_includesDirectLinks() {
        val root = getProjectRoot()
        val readmeFile = File(root, "README.md")
        assertTrue("README.md must exist", readmeFile.exists())

        val content = readmeFile.readText()
        assertTrue("README.md must link to docs/privacy-policy.md", content.contains("docs/privacy-policy.md"))
        assertTrue("README.md must link to docs/security-whitepaper.md", content.contains("docs/security-whitepaper.md"))
        assertTrue("README.md must link to LICENSE", content.contains("LICENSE"))
    }

    @Test
    fun testModuleManifests_existAndCoverRequiredSections() {
        val root = getProjectRoot()
        val manifestPaths = listOf(
            "app/src/main/kotlin/com/adamoutler/ssh/GEMINI.md",
            "app/src/main/kotlin/com/adamoutler/ssh/openssh/GEMINI.md",
            "app/src/main/kotlin/com/adamoutler/ssh/sftp/GEMINI.md",
            "app/src/main/kotlin/com/adamoutler/ssh/util/GEMINI.md",
            "app/src/main/kotlin/com/adamoutler/ssh/vm/GEMINI.md",
        )

        val requiredSections = listOf(
            "Logical Purpose",
            "Key Components & APIs",
            "Behavioral Contracts & Design Patterns",
            "Dependencies",
        )

        for (path in manifestPaths) {
            val manifestFile = File(root, path)
            assertTrue("Module manifest must exist at $path", manifestFile.exists())

            val content = manifestFile.readText()
            for (section in requiredSections) {
                assertTrue("Manifest $path must contain section '$section'", content.contains(section))
            }
        }
    }
}

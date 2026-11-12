package com.adamoutler.ssh.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.util.zip.InflaterOutputStream
import kotlin.math.abs

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

    private fun decodeRgbaPngPixels(file: File): Array<IntArray> {
        val bytes = file.readBytes()
        val buffer = ByteBuffer.wrap(bytes)
        buffer.position(8) // Skip magic bytes

        val idatBaos = ByteArrayOutputStream()
        var width = 0
        var height = 0
        var colorType = 0

        while (buffer.hasRemaining()) {
            val length = buffer.int
            val chunkType = ByteArray(4)
            buffer.get(chunkType)
            val typeStr = String(chunkType, Charsets.US_ASCII)

            if (typeStr == "IHDR") {
                width = buffer.int
                height = buffer.int
                buffer.get() // bitDepth
                colorType = buffer.get().toInt() and 0xFF
                buffer.position(buffer.position() + 3) // compression, filter, interlace
            } else if (typeStr == "IDAT") {
                val data = ByteArray(length)
                buffer.get(data)
                idatBaos.write(data)
            } else {
                buffer.position(buffer.position() + length)
            }
            buffer.int // Skip CRC
        }

        assertEquals("Color type must be RGBA (6)", 6, colorType)
        val decompressedBaos = ByteArrayOutputStream()
        val inflaterStream = InflaterOutputStream(decompressedBaos)
        inflaterStream.write(idatBaos.toByteArray())
        inflaterStream.close()

        val raw = decompressedBaos.toByteArray()
        val bpp = 4
        val scanlineLength = 1 + width * bpp
        assertEquals("Decompressed byte length must match height * scanlineLength", height * scanlineLength, raw.size)

        var prev: IntArray? = null
        val result = Array(height) { IntArray(width * bpp) }

        for (y in 0 until height) {
            val rowOffset = y * scanlineLength
            val filterType = raw[rowOffset].toInt() and 0xFF
            val current = IntArray(width * bpp)

            for (i in 0 until width * bpp) {
                val filtByte = raw[rowOffset + 1 + i].toInt() and 0xFF
                val a = if (i >= bpp) current[i - bpp] else 0
                val b = if (prev != null) prev[i] else 0
                val c = if (prev != null && i >= bpp) prev[i - bpp] else 0

                val v = when (filterType) {
                    0 -> filtByte
                    1 -> (filtByte + a) and 0xFF
                    2 -> (filtByte + b) and 0xFF
                    3 -> (filtByte + (a + b) / 2) and 0xFF
                    4 -> {
                        val p = a + b - c
                        val pa = abs(p - a)
                        val pb = abs(p - b)
                        val pc = abs(p - c)
                        val pr = if (pa <= pb && pa <= pc) a else if (pb <= pc) b else c
                        (filtByte + pr) and 0xFF
                    }
                    else -> filtByte
                }
                current[i] = v
            }
            prev = current
            result[y] = current
        }
        return result
    }

    @Test
    fun testStoreIcon_hasCleanAlphaCorners_andNoBlackBoxArtifact() {
        val root = getProjectRoot()
        val iconFile = File(root, "docs/img/icon.png")
        assertTrue("docs/img/icon.png must exist", iconFile.exists())

        val pixels = decodeRgbaPngPixels(iconFile)
        assertEquals("Icon height must be 512px", 512, pixels.size)

        // 1. Assert clean transparency on 4 outer corners
        val cornerCoords = listOf(
            Pair(0, 0),
            Pair(511, 0),
            Pair(0, 511),
            Pair(511, 511),
        )
        for ((x, y) in cornerCoords) {
            val alpha = pixels[y][x * 4 + 3]
            assertEquals("Outer corner ($x, $y) must have clean alpha == 0", 0, alpha)
        }

        // 2. Assert no unexpected black box in bounding box [x: 312..490, y: 385..424]
        var blackPixelCount = 0
        for (y in 385..424) {
            for (x in 312..490) {
                val r = pixels[y][x * 4]
                val g = pixels[y][x * 4 + 1]
                val b = pixels[y][x * 4 + 2]
                val alpha = pixels[y][x * 4 + 3]

                if (alpha >= 200 && r < 20 && g < 20 && b < 20) {
                    blackPixelCount++
                }
            }
        }

        assertEquals(
            "Region [312..490, 385..424] must NOT contain unexpected black box artifact (found $blackPixelCount black pixels)",
            0,
            blackPixelCount,
        )
    }

    @Test
    fun testFastlaneMetadata_compliesWithStoreSpecifications() {
        val root = getProjectRoot()
        val fastlaneDir = File(root, "fastlane/metadata/android/en-US")
        assertTrue("fastlane/metadata/android/en-US must exist", fastlaneDir.exists())

        val titleFile = File(fastlaneDir, "title.txt")
        assertTrue("title.txt must exist", titleFile.exists())
        val title = titleFile.readText().trim()
        assertTrue("Title must not be empty", title.isNotEmpty())
        assertTrue("Title must not exceed 30 characters (actual: ${title.length})", title.length <= 30)

        val shortDescFile = File(fastlaneDir, "short_description.txt")
        assertTrue("short_description.txt must exist", shortDescFile.exists())
        val shortDesc = shortDescFile.readText().trim()
        assertTrue("Short description must not be empty", shortDesc.isNotEmpty())
        assertTrue("Short description must not exceed 80 characters (actual: ${shortDesc.length})", shortDesc.length <= 80)

        val fullDescFile = File(fastlaneDir, "full_description.txt")
        assertTrue("full_description.txt must exist", fullDescFile.exists())
        val fullDesc = fullDescFile.readText().trim()
        assertTrue("Full description must not be empty", fullDesc.isNotEmpty())
        assertTrue("Full description must not exceed 4000 characters (actual: ${fullDesc.length})", fullDesc.length <= 4000)

        val changelogFile = File(fastlaneDir, "changelogs/default.txt")
        assertTrue("changelogs/default.txt must exist", changelogFile.exists())
        val changelog = changelogFile.readText().trim()
        assertTrue("Changelog must not be empty", changelog.isNotEmpty())
        assertTrue("Changelog must not exceed 500 characters (actual: ${changelog.length})", changelog.length <= 500)

        val imagesDir = File(fastlaneDir, "images")
        assertTrue("images directory must exist", imagesDir.exists())

        val storeIcon = File(imagesDir, "icon.png")
        assertTrue("fastlane icon.png must exist", storeIcon.exists())
        val storeIconMeta = readPngMetadata(storeIcon)
        assertEquals("fastlane icon.png width must be 512px", 512, storeIconMeta.width)
        assertEquals("fastlane icon.png height must be 512px", 512, storeIconMeta.height)

        val featureGraphic = File(imagesDir, "featureGraphic.png")
        assertTrue("fastlane featureGraphic.png must exist", featureGraphic.exists())
        val featureMeta = readPngMetadata(featureGraphic)
        assertEquals("fastlane featureGraphic.png width must be 1024px", 1024, featureMeta.width)
        assertEquals("fastlane featureGraphic.png height must be 500px", 500, featureMeta.height)

        val screenshotsDir = File(imagesDir, "phoneScreenshots")
        assertTrue("phoneScreenshots directory must exist", screenshotsDir.exists())
        val screenshots = screenshotsDir.listFiles { _, name -> name.endsWith(".png") } ?: emptyArray()
        assertTrue("Must have at least 2 phone screenshots for Google Play", screenshots.size >= 2)

        for (screenshot in screenshots) {
            val meta = readPngMetadata(screenshot)
            assertEquals("Screenshot ${screenshot.name} width must be 1080px", 1080, meta.width)
            assertEquals("Screenshot ${screenshot.name} height must be 1920px", 1920, meta.height)
        }
    }

    @Test
    fun testFDroidRecipe_isValidAndSynchronized() {
        val root = getProjectRoot()
        val recipeFile = File(root, "metadata/com.adamoutler.cobaltssh.yml")
        assertTrue("metadata/com.adamoutler.cobaltssh.yml must exist", recipeFile.exists())

        val content = recipeFile.readText()
        assertTrue("F-Droid recipe must define GPL-3.0-or-later", content.contains("GPL-3.0-or-later"))
        assertTrue("F-Droid recipe must declare Summary", content.contains("Summary:"))
        assertTrue("F-Droid recipe must declare Description", content.contains("Description:"))
        assertTrue("F-Droid recipe must declare Builds", content.contains("Builds:"))
        assertTrue("F-Droid recipe must declare AntiFeatures", content.contains("AntiFeatures:"))
        assertFalse("F-Droid recipe must not contain broken subdir: app", content.contains("subdir: app"))
    }
}

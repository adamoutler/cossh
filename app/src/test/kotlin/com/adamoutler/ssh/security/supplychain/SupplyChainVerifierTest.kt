package com.adamoutler.ssh.security.supplychain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SupplyChainVerifierTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val sampleNormalizedDosTime = 315532800000L // 1980-01-01 00:00:00 UTC

    @Test
    fun computeSha256_bytes_producesAccurate64Hex() {
        val input = "CobaltSSH-Reproducible-Build-Validation".toByteArray(Charsets.UTF_8)
        val expected = MessageDigest.getInstance("SHA-256").digest(input)
            .joinToString("") { "%02x".format(it) }

        val actual = SupplyChainVerifier.computeSha256(input)
        assertEquals(64, actual.length)
        assertEquals(expected, actual)
    }

    @Test
    fun computeSha256_file_producesAccurate64Hex() {
        val file = tempFolder.newFile("artifact.apk")
        val content = ByteArray(16384) { (it % 256).toByte() }
        file.writeBytes(content)

        val expected = MessageDigest.getInstance("SHA-256").digest(content)
            .joinToString("") { "%02x".format(it) }

        val actual = SupplyChainVerifier.computeSha256(file)
        assertEquals(expected, actual)
    }

    @Test
    fun generateProvenance_createsValidInTotoAndSlsaStructure() {
        val artifactSha = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        val statement = SupplyChainVerifier.generateProvenance(
            artifactName = "app-release-unsigned.apk",
            artifactDigestSha256 = artifactSha,
            builderId = "https://git.adamoutler.com/act_runner@v1",
            buildType = "https://git.adamoutler.com/aoutler/CoSSH/builds@v1",
            invocationId = "build-run-10492",
            startedOn = "2026-10-26T10:00:00Z",
            finishedOn = "2026-10-26T10:04:30Z",
            externalParameters = mapOf("branch" to "main", "commit" to "abcdef123456"),
            dependencies = listOf(
                SlsaDependency(uri = "pkg:maven/org.bouncycastle/bcprov-jdk18on@1.78.1"),
            ),
        )

        assertEquals(SupplyChainVerifier.IN_TOTO_STATEMENT_TYPE_V1, statement.type)
        assertEquals(SupplyChainVerifier.SLSA_PREDICATE_TYPE_V1, statement.predicateType)
        assertEquals(1, statement.subject.size)
        assertEquals("app-release-unsigned.apk", statement.subject[0].name)
        assertEquals(artifactSha, statement.subject[0].digest["sha256"])
        assertEquals("https://git.adamoutler.com/act_runner@v1", statement.predicate.runDetails.builder.id)
        assertEquals("build-run-10492", statement.predicate.runDetails.metadata.invocationId)
        assertEquals("main", statement.predicate.buildDefinition.externalParameters["branch"])
        assertEquals(1, statement.predicate.buildDefinition.resolvedDependencies.size)
    }

    @Test
    fun generateProvenanceJson_roundtripsCorrectly() {
        val artifactSha = "a" * 64
        val jsonStr = SupplyChainVerifier.generateProvenanceJson(
            artifactName = "cossh-release.apk",
            artifactDigestSha256 = artifactSha,
            builderId = "https://git.adamoutler.com/act_runner",
            buildType = "https://git.adamoutler.com/aoutler/CoSSH/build@v1",
            invocationId = "inv-42",
            startedOn = "2026-10-26T00:00:00Z",
            finishedOn = "2026-10-26T00:05:00Z",
        )

        val result = SupplyChainVerifier.validateProvenance(
            jsonString = jsonStr,
            expectedArtifactSha256 = artifactSha,
            expectedBuilderId = "https://git.adamoutler.com/act_runner",
        )

        assertTrue("Validation should pass for valid generated JSON", result is ProvenanceValidationResult.Valid)
        val valid = result as ProvenanceValidationResult.Valid
        assertEquals("cossh-release.apk", valid.statement.subject[0].name)
    }

    @Test
    fun validateProvenance_detectsDigestMismatch() {
        val statement = SupplyChainVerifier.generateProvenance(
            artifactName = "app.apk",
            artifactDigestSha256 = "0" * 64,
            builderId = "builder-1",
            buildType = "build-type-1",
            invocationId = "inv-1",
            startedOn = "T1",
            finishedOn = "T2",
        )

        val result = SupplyChainVerifier.validateProvenance(
            statement = statement,
            expectedArtifactSha256 = "1" * 64,
        )

        assertTrue(result is ProvenanceValidationResult.Invalid)
        assertTrue((result as ProvenanceValidationResult.Invalid).reason.contains("No subject artifact matched"))
    }

    @Test
    fun validateProvenance_detectsBuilderIdMismatch() {
        val statement = SupplyChainVerifier.generateProvenance(
            artifactName = "app.apk",
            artifactDigestSha256 = "0" * 64,
            builderId = "builder-1",
            buildType = "build-type-1",
            invocationId = "inv-1",
            startedOn = "T1",
            finishedOn = "T2",
        )

        val result = SupplyChainVerifier.validateProvenance(
            statement = statement,
            expectedBuilderId = "expected-trusted-builder",
        )

        assertTrue(result is ProvenanceValidationResult.Invalid)
        assertTrue((result as ProvenanceValidationResult.Invalid).reason.contains("does not match expected"))
    }

    @Test
    fun validateProvenance_detectsInvalidTypesAndMalformedJson() {
        val badTypeJson = """
            {
                "_type": "https://invalid.io/Statement/v1",
                "subject": [{"name": "app.apk", "digest": {"sha256": "${"0" * 64}"}}],
                "predicateType": "${SupplyChainVerifier.SLSA_PREDICATE_TYPE_V1}",
                "predicate": {
                    "buildDefinition": {"buildType": "bt"},
                    "runDetails": {"builder": {"id": "b"}, "metadata": {"invocationId": "i", "startedOn": "s", "finishedOn": "f"}}
                }
            }
        """.trimIndent()

        val badTypeResult = SupplyChainVerifier.validateProvenance(badTypeJson)
        assertTrue(badTypeResult is ProvenanceValidationResult.Invalid)
        assertTrue((badTypeResult as ProvenanceValidationResult.Invalid).reason.contains("Invalid _type"))

        val malformedResult = SupplyChainVerifier.validateProvenance("not-a-json-payload")
        assertTrue(malformedResult is ProvenanceValidationResult.Invalid)
    }

    @Test
    fun verifyZipReproducibility_returnsTrueForOrderedNormalizedEntries() {
        val zipBytes = createSampleZip(
            entries = listOf(
                "AndroidManifest.xml" to sampleNormalizedDosTime,
                "classes.dex" to sampleNormalizedDosTime,
                "resources.arsc" to sampleNormalizedDosTime,
            ),
        )

        val report = SupplyChainVerifier.verifyZipReproducibility(
            zipBytes = zipBytes,
            expectedNormalizedTimeMillis = sampleNormalizedDosTime,
        )

        assertTrue("Report must be reproducible", report.isReproducible)
        assertTrue(report.isLexicographicallyOrdered)
        assertTrue(report.isTimestampsNormalized)
        assertEquals(3, report.entryCount)
        assertTrue(report.issues.isEmpty())
    }

    @Test
    fun verifyZipReproducibility_detectsOutOfOrderEntries() {
        val zipBytes = createSampleZip(
            entries = listOf(
                "resources.arsc" to sampleNormalizedDosTime,
                "classes.dex" to sampleNormalizedDosTime, // out of order!
            ),
        )

        val report = SupplyChainVerifier.verifyZipReproducibility(zipBytes)
        assertFalse("Out of order entries must fail reproducibility", report.isReproducible)
        assertFalse(report.isLexicographicallyOrdered)
        assertTrue(report.issues.any { it.contains("out of order") })
    }

    @Test
    fun verifyZipReproducibility_detectsNonNormalizedTimestamps() {
        val zipBytes = createSampleZip(
            entries = listOf(
                "a.txt" to sampleNormalizedDosTime,
                "b.txt" to sampleNormalizedDosTime + 100_000L, // Different timestamp!
            ),
        )

        val report = SupplyChainVerifier.verifyZipReproducibility(zipBytes)
        assertFalse("Non-normalized timestamps must fail reproducibility", report.isReproducible)
        assertFalse(report.isTimestampsNormalized)
        assertTrue(report.issues.any { it.contains("differs from initial entry timestamp") })
    }

    @Test
    fun verifyZipReproducibility_verifiesAgainstExpectedTimestamp() {
        val zipBytes = createSampleZip(
            entries = listOf(
                "a.txt" to sampleNormalizedDosTime,
            ),
        )

        val unexpectedTime = sampleNormalizedDosTime + 3_600_000L
        val report = SupplyChainVerifier.verifyZipReproducibility(
            zipBytes = zipBytes,
            expectedNormalizedTimeMillis = unexpectedTime,
        )

        assertFalse(report.isReproducible)
        assertFalse(report.isTimestampsNormalized)
        assertTrue(report.issues.any { it.contains("deviates from expected normalized timestamp") })
    }

    @Test
    fun verifyZipReproducibility_handlesCorruptedBytesGracefully() {
        val corruptedBytes = byteArrayOf(0x00, 0x11, 0x22, 0x33, 0x44)
        val report = SupplyChainVerifier.verifyZipReproducibility(corruptedBytes)

        assertFalse(report.isReproducible)
        assertEquals(0, report.entryCount)
    }

    @Test
    fun verifyZipReproducibility_handlesEmptyZip() {
        val emptyZip = ByteArrayOutputStream().apply {
            ZipOutputStream(this).use { /* write nothing */ }
        }.toByteArray()

        val report = SupplyChainVerifier.verifyZipReproducibility(emptyZip)
        assertTrue(report.isReproducible)
        assertEquals(0, report.entryCount)
    }

    private fun createSampleZip(entries: List<Pair<String, Long>>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            for ((name, timeMillis) in entries) {
                val entry = ZipEntry(name).apply {
                    time = timeMillis
                }
                zos.putNextEntry(entry)
                zos.write("content-$name".toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private operator fun String.times(n: Int): String = this.repeat(n)
}

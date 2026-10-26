package com.adamoutler.ssh.security.supplychain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * SLSA v1.0 and in-toto v1 Supply Chain and Reproducible Build Verifier.
 *
 * Implements:
 * 1. SLSA Level 3 Provenance generation and cryptographic digest verification.
 * 2. In-toto Statement v1 serialization and deserialization.
 * 3. Byte-for-byte SHA-256 digest computations for raw bytes and release artifacts.
 * 4. Deterministic zip archive reproducibility analysis (lexicographical entry ordering
 *    and DOS epoch normalized timestamp verification).
 */
object SupplyChainVerifier {

    const val IN_TOTO_STATEMENT_TYPE_V1 = "https://in-toto.io/Statement/v1"
    const val SLSA_PREDICATE_TYPE_V1 = "https://slsa.dev/provenance/v1"
    private const val SHA256_HEX_LENGTH = 64
    private const val DOS_TIMESTAMP_TOLERANCE_MS = 2000L
    private const val BUFFER_SIZE = 8192

    private const val MIN_ZIP_EOCD_SIZE = 22
    private const val MAX_EOCD_SEARCH_LENGTH = 65557

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Computes the hexadecimal SHA-256 digest of the given byte array.
     */
    fun computeSha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Computes the hexadecimal SHA-256 digest of the given file.
     */
    fun computeSha256(file: File): String {
        require(file.exists() && file.isFile) { "File must exist: ${file.absolutePath}" }
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                md.update(buffer, 0, bytesRead)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Generates an in-toto v1 Statement with SLSA v1.0 Provenance predicate.
     */
    @Suppress("LongParameterList")
    fun generateProvenance(
        artifactName: String,
        artifactDigestSha256: String,
        builderId: String,
        buildType: String,
        invocationId: String,
        startedOn: String,
        finishedOn: String,
        externalParameters: Map<String, String> = emptyMap(),
        dependencies: List<SlsaDependency> = emptyList(),
    ): InTotoStatement {
        require(artifactName.isNotBlank()) { "Artifact name must not be blank" }
        require(artifactDigestSha256.isNotBlank()) { "Artifact SHA-256 digest must not be blank" }
        require(builderId.isNotBlank()) { "Builder ID must not be blank" }
        require(buildType.isNotBlank()) { "Build type must not be blank" }
        require(invocationId.isNotBlank()) { "Invocation ID must not be blank" }

        return InTotoStatement(
            type = IN_TOTO_STATEMENT_TYPE_V1,
            subject = listOf(
                SubjectArtifact(
                    name = artifactName,
                    digest = mapOf("sha256" to artifactDigestSha256.lowercase()),
                ),
            ),
            predicateType = SLSA_PREDICATE_TYPE_V1,
            predicate = SlsaProvenancePredicate(
                buildDefinition = SlsaBuildDefinition(
                    buildType = buildType,
                    externalParameters = externalParameters,
                    resolvedDependencies = dependencies,
                ),
                runDetails = SlsaRunDetails(
                    builder = SlsaBuilder(id = builderId),
                    metadata = SlsaMetadata(
                        invocationId = invocationId,
                        startedOn = startedOn,
                        finishedOn = finishedOn,
                    ),
                ),
            ),
        )
    }

    /**
     * Serializes an in-toto Statement to JSON string.
     */
    fun generateProvenanceJson(statement: InTotoStatement): String {
        return json.encodeToString(statement)
    }

    /**
     * Generates and serializes an in-toto Statement with SLSA v1.0 Provenance to JSON string.
     */
    @Suppress("LongParameterList")
    fun generateProvenanceJson(
        artifactName: String,
        artifactDigestSha256: String,
        builderId: String,
        buildType: String,
        invocationId: String,
        startedOn: String,
        finishedOn: String,
        externalParameters: Map<String, String> = emptyMap(),
        dependencies: List<SlsaDependency> = emptyList(),
    ): String {
        val statement = generateProvenance(
            artifactName = artifactName,
            artifactDigestSha256 = artifactDigestSha256,
            builderId = builderId,
            buildType = buildType,
            invocationId = invocationId,
            startedOn = startedOn,
            finishedOn = finishedOn,
            externalParameters = externalParameters,
            dependencies = dependencies,
        )
        return generateProvenanceJson(statement)
    }

    /**
     * Validates an in-toto SLSA v1.0 Provenance JSON string.
     */
    fun validateProvenance(
        jsonString: String,
        expectedArtifactSha256: String? = null,
        expectedBuilderId: String? = null,
    ): ProvenanceValidationResult {
        return try {
            val statement = json.decodeFromString<InTotoStatement>(jsonString)
            validateProvenance(statement, expectedArtifactSha256, expectedBuilderId)
        } catch (e: Exception) {
            ProvenanceValidationResult.Invalid("Malformed JSON or schema: ${e.message}")
        }
    }

    /**
     * Validates an in-toto SLSA v1.0 Provenance statement.
     */
    fun validateProvenance(
        statement: InTotoStatement,
        expectedArtifactSha256: String? = null,
        expectedBuilderId: String? = null,
    ): ProvenanceValidationResult {
        if (statement.type != IN_TOTO_STATEMENT_TYPE_V1) {
            return ProvenanceValidationResult.Invalid(
                "Invalid _type: expected $IN_TOTO_STATEMENT_TYPE_V1, found ${statement.type}",
            )
        }
        if (statement.predicateType != SLSA_PREDICATE_TYPE_V1) {
            return ProvenanceValidationResult.Invalid(
                "Invalid predicateType: expected $SLSA_PREDICATE_TYPE_V1, found ${statement.predicateType}",
            )
        }
        if (statement.subject.isEmpty()) {
            return ProvenanceValidationResult.Invalid("Subject list must not be empty")
        }
        for (subj in statement.subject) {
            if (subj.name.isBlank()) {
                return ProvenanceValidationResult.Invalid("Subject artifact name must not be blank")
            }
            val sha256 = subj.digest["sha256"]
            if (sha256.isNullOrBlank() || sha256.length != SHA256_HEX_LENGTH) {
                return ProvenanceValidationResult.Invalid(
                    "Subject artifact '${subj.name}' must have a valid 64-char sha256 digest",
                )
            }
        }
        if (statement.predicate.runDetails.builder.id.isBlank()) {
            return ProvenanceValidationResult.Invalid("Builder ID must not be blank")
        }
        if (statement.predicate.runDetails.metadata.invocationId.isBlank()) {
            return ProvenanceValidationResult.Invalid("Invocation ID must not be blank")
        }
        if (expectedArtifactSha256 != null) {
            val matched = statement.subject.any {
                it.digest["sha256"].equals(expectedArtifactSha256, ignoreCase = true)
            }
            if (!matched) {
                return ProvenanceValidationResult.Invalid(
                    "No subject artifact matched expected SHA-256 digest: $expectedArtifactSha256",
                )
            }
        }
        if (expectedBuilderId != null && statement.predicate.runDetails.builder.id != expectedBuilderId) {
            return ProvenanceValidationResult.Invalid(
                "Builder ID '${statement.predicate.runDetails.builder.id}' does not match expected '$expectedBuilderId'",
            )
        }
        return ProvenanceValidationResult.Valid(statement)
    }

    /**
     * Inspects a ZIP/APK/AAB binary stream to verify deterministic reproducibility characteristics:
     * 1. Lexicographical entry order.
     * 2. Normalized DOS timestamps.
     */
    fun verifyZipReproducibility(
        zipBytes: ByteArray,
        expectedNormalizedTimeMillis: Long? = null,
    ): ReproducibilityReport {
        if (!containsEocdSignature(zipBytes)) {
            return ReproducibilityReport(
                isReproducible = false,
                entryCount = 0,
                isLexicographicallyOrdered = false,
                isTimestampsNormalized = false,
                entries = emptyList(),
                issues = listOf("Invalid ZIP archive: missing End of Central Directory signature"),
            )
        }

        val issues = mutableListOf<String>()
        val entries = mutableListOf<ZipEntryReport>()
        var isLexicographicallyOrdered = true
        var previousName: String? = null
        var firstTimestamp: Long? = null
        var isTimestampsNormalized = true

        try {
            ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val entryReport = ZipEntryReport(
                        name = entry.name,
                        timeMillis = entry.time,
                        crc = entry.crc,
                        size = entry.size,
                    )
                    entries.add(entryReport)

                    // Lexicographical ordering validation
                    val prevName = previousName
                    if (prevName != null && entry.name < prevName) {
                        isLexicographicallyOrdered = false
                        issues.add("Entry '${entry.name}' is out of order (preceded by '$prevName')")
                    }
                    previousName = entry.name

                    // Timestamp normalization validation
                    if (expectedNormalizedTimeMillis != null) {
                        if (Math.abs(entry.time - expectedNormalizedTimeMillis) > DOS_TIMESTAMP_TOLERANCE_MS) {
                            isTimestampsNormalized = false
                            issues.add(
                                "Entry '${entry.name}' timestamp (${entry.time}) deviates from expected normalized timestamp ($expectedNormalizedTimeMillis)",
                            )
                        }
                    } else {
                        val firstTs = firstTimestamp
                        if (firstTs == null) {
                            firstTimestamp = entry.time
                        } else if (Math.abs(entry.time - firstTs) > DOS_TIMESTAMP_TOLERANCE_MS) {
                            isTimestampsNormalized = false
                            issues.add(
                                "Entry '${entry.name}' timestamp (${entry.time}) differs from initial entry timestamp ($firstTs)",
                            )
                        }
                    }

                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } catch (e: Exception) {
            return ReproducibilityReport(
                isReproducible = false,
                entryCount = 0,
                isLexicographicallyOrdered = false,
                isTimestampsNormalized = false,
                entries = emptyList(),
                issues = listOf("Failed to parse zip archive: ${e.message}"),
            )
        }

        val isReproducible = isLexicographicallyOrdered && isTimestampsNormalized && issues.isEmpty()
        return ReproducibilityReport(
            isReproducible = isReproducible,
            entryCount = entries.size,
            isLexicographicallyOrdered = isLexicographicallyOrdered,
            isTimestampsNormalized = isTimestampsNormalized,
            entries = entries,
            issues = issues,
        )
    }

    private fun containsEocdSignature(bytes: ByteArray): Boolean {
        if (bytes.size < MIN_ZIP_EOCD_SIZE) return false
        val searchLength = minOf(bytes.size, MAX_EOCD_SEARCH_LENGTH)
        val start = bytes.size - searchLength
        for (i in bytes.size - MIN_ZIP_EOCD_SIZE downTo start) {
            if (bytes[i] == 0x50.toByte() &&
                bytes[i + 1] == 0x4B.toByte() &&
                bytes[i + 2] == 0x05.toByte() &&
                bytes[i + 3] == 0x06.toByte()
            ) {
                return true
            }
        }
        return false
    }
}

@Serializable
data class InTotoStatement(
    @SerialName("_type")
    val type: String = SupplyChainVerifier.IN_TOTO_STATEMENT_TYPE_V1,
    val subject: List<SubjectArtifact>,
    val predicateType: String = SupplyChainVerifier.SLSA_PREDICATE_TYPE_V1,
    val predicate: SlsaProvenancePredicate,
)

@Serializable
data class SubjectArtifact(
    val name: String,
    val digest: Map<String, String>,
)

@Serializable
data class SlsaProvenancePredicate(
    val buildDefinition: SlsaBuildDefinition,
    val runDetails: SlsaRunDetails,
)

@Serializable
data class SlsaBuildDefinition(
    val buildType: String,
    val externalParameters: Map<String, String> = emptyMap(),
    val internalParameters: Map<String, String> = emptyMap(),
    val resolvedDependencies: List<SlsaDependency> = emptyList(),
)

@Serializable
data class SlsaDependency(
    val uri: String,
    val digest: Map<String, String> = emptyMap(),
)

@Serializable
data class SlsaRunDetails(
    val builder: SlsaBuilder,
    val metadata: SlsaMetadata,
    val byproducts: List<SlsaByproduct> = emptyList(),
)

@Serializable
data class SlsaBuilder(
    val id: String,
    val version: Map<String, String> = emptyMap(),
)

@Serializable
data class SlsaMetadata(
    val invocationId: String,
    val startedOn: String,
    val finishedOn: String,
)

@Serializable
data class SlsaByproduct(
    val name: String,
    val uri: String? = null,
    val digest: Map<String, String> = emptyMap(),
)

sealed class ProvenanceValidationResult {
    data class Valid(val statement: InTotoStatement) : ProvenanceValidationResult()
    data class Invalid(val reason: String) : ProvenanceValidationResult()
}

data class ReproducibilityReport(
    val isReproducible: Boolean,
    val entryCount: Int,
    val isLexicographicallyOrdered: Boolean,
    val isTimestampsNormalized: Boolean,
    val entries: List<ZipEntryReport>,
    val issues: List<String>,
)

data class ZipEntryReport(
    val name: String,
    val timeMillis: Long,
    val crc: Long,
    val size: Long,
)

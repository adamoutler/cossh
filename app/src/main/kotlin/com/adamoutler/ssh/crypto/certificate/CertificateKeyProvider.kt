package com.adamoutler.ssh.crypto.certificate

import net.schmizz.sshj.common.KeyType
import net.schmizz.sshj.userauth.keyprovider.KeyProvider
import java.math.BigInteger
import java.security.PrivateKey
import java.security.PublicKey
import java.util.Date
import com.hierynomus.sshj.userauth.certificate.Certificate as SshjCertificate

/**
 * Bridges OpenSSH user certificates with SSHJ KeyProvider interface for public key authentication.
 */
class CertificateKeyProvider(
    val certificate: OpenSshCertificate,
    private val privateKey: PrivateKey,
) : KeyProvider {

    private val sshjCert: PublicKey by lazy {
        toSshjCertificate(certificate)
    }

    override fun getPrivate(): PrivateKey = privateKey

    override fun getPublic(): PublicKey = sshjCert

    override fun getType(): KeyType = resolveKeyType(certificate)

    companion object {
        fun resolveKeyType(cert: OpenSshCertificate): KeyType = when {
            cert.magic.startsWith("ssh-ed25519-cert") -> KeyType.ED25519_CERT
            cert.magic.startsWith("ssh-rsa-cert") || cert.magic.startsWith("rsa-sha2-") -> KeyType.RSA_CERT
            cert.magic.startsWith("ecdsa-sha2-nistp256-cert") -> KeyType.ECDSA256_CERT
            cert.magic.startsWith("ecdsa-sha2-nistp384-cert") -> KeyType.ECDSA384_CERT
            cert.magic.startsWith("ecdsa-sha2-nistp521-cert") -> KeyType.ECDSA521_CERT
            else -> KeyType.ED25519_CERT
        }

        fun toSshjCertificate(cert: OpenSshCertificate): PublicKey {
            val builder = SshjCertificate.getBuilder<PublicKey>()
            builder.nonce(cert.nonce)
            builder.publicKey(cert.subjectPublicKey)
            builder.serial(BigInteger.valueOf(cert.serial))
            builder.type(cert.certType.code.toLong())
            builder.id(cert.keyId)
            builder.validPrincipals(cert.validPrincipals)
            builder.validAfter(Date(cert.validAfterSeconds * 1000L))
            builder.validBefore(
                if (cert.validBeforeSeconds == -1L || cert.validBeforeSeconds == Long.MAX_VALUE) {
                    Date(Long.MAX_VALUE)
                } else {
                    Date(cert.validBeforeSeconds * 1000L)
                },
            )
            builder.critOptions(cert.criticalOptions)
            builder.extensions(cert.extensions)
            builder.signatureKey(OpenSshCertificateParser.encodePublicKeyBlob(cert.caPublicKey))

            val sigWriter = WireBufferWriter()
            sigWriter.writeString(cert.signatureAlgorithm)
            sigWriter.writeStringBytes(cert.rawSignatureBlob)
            builder.signature(sigWriter.toByteArray())

            return builder.build()
        }
    }
}

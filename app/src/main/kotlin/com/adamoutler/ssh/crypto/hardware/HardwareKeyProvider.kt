package com.adamoutler.ssh.crypto.hardware

import net.schmizz.sshj.common.KeyType
import net.schmizz.sshj.userauth.keyprovider.KeyProvider
import java.security.PrivateKey
import java.security.PublicKey

/**
 * Bridges hardware-backed Keystore private keys (such as AndroidKeyStorePrivateKey)
 * with the SSHJ KeyProvider interface.
 *
 * Hardware-backed keys strictly protect raw key material and return null for
 * [PrivateKey.getEncoded]. This provider guarantees safe operation without
 * attempting any unauthorized raw key extraction.
 */
class HardwareKeyProvider(
    private val pubKey: PublicKey,
    private val privKey: PrivateKey,
    private val keyType: KeyType = KeyType.fromKey(pubKey),
) : KeyProvider {

    override fun getPrivate(): PrivateKey = privKey

    override fun getPublic(): PublicKey = pubKey

    override fun getType(): KeyType = keyType

    /**
     * Indicates whether this key is hardware-backed (i.e. private key material is non-exportable).
     */
    val isHardwareBacked: Boolean
        get() = try {
            privKey.encoded == null
        } catch (_: Throwable) {
            true
        }
}

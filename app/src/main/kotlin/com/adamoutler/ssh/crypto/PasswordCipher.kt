package com.adamoutler.ssh.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureStorageUnavailableException(message: String, cause: Throwable? = null) : Exception(message, cause)

object PasswordCipher {
    private const val KEY_ALIAS = "cossh_volatile_password_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    private var fallbackKey: SecretKey? = null

    private fun getOrGenerateKey(): SecretKey {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(KEY_ALIAS)) {
                val entry = keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry
                entry.secretKey
            } else {
                val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                val spec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
                keyGenerator.init(spec)
                keyGenerator.generateKey()
            }
        } catch (e: Exception) {
            val isRobolectric = System.getProperty("robolectric.logging") != null || android.os.Build.FINGERPRINT.contains("robolectric")
            if (isRobolectric) {
                android.util.Log.w("PasswordCipher", "AndroidKeyStore unavailable, falling back to standard AES for Robolectric testing only.", e)
                if (fallbackKey == null) {
                    val keyGenerator = KeyGenerator.getInstance("AES")
                    keyGenerator.init(256)
                    fallbackKey = keyGenerator.generateKey()
                }
                return fallbackKey!!
            }
            throw SecureStorageUnavailableException("AndroidKeyStore unavailable. Refusing to fallback to insecure storage.", e)
        }
    }

    fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrGenerateKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)
        return iv + ciphertext
    }

    fun decrypt(encryptedData: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = encryptedData.copyOfRange(0, 12)
        val ciphertext = encryptedData.copyOfRange(12, encryptedData.size)
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, getOrGenerateKey(), spec)
        return cipher.doFinal(ciphertext)
    }
}

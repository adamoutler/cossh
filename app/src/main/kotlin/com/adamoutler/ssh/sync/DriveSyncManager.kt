package com.adamoutler.ssh.sync

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import java.net.SocketTimeoutException
import java.io.IOException

class DriveSyncManager(private val context: Context) {
    private val credentialManager = CredentialManager.create(context)
    private val webClientId = "255929341577-6e1405jlnio601o2em8mr7n7dins7ni9.apps.googleusercontent.com"
    private var oauthToken: String? = null

    suspend fun authenticate(activity: Activity) {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(webClientId)
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        try {
            val result: GetCredentialResponse = credentialManager.getCredential(
                request = request,
                context = activity
            )
            val credential = result.credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                // In a real implementation, exchange ID token for an OAuth token
                // with scope "https://www.googleapis.com/auth/drive.appdata".
                oauthToken = googleIdTokenCredential.idToken
            }
        } catch (e: GetCredentialException) {
            oauthToken = null
        }
    }

    suspend fun uploadBackup(payload: ByteArray, pass: CharArray) {
        withContext(Dispatchers.IO) {
            try {
                val encryptedPayload = encrypt(payload, pass)
                // TODO: Upload `encryptedPayload` to Google Drive AppData folder using Google Drive REST API.
                // Enforce 10s timeout.
            } catch (e: Exception) {
                oauthToken = null
                if (e !is IOException && e !is SocketTimeoutException) {
                    throw e
                }
            } finally {
                // Scrub the OAuth token after operation per volatile state instructions.
                oauthToken = null
            }
        }
    }

    suspend fun downloadBackup(pass: CharArray): ByteArray? {
        return withContext(Dispatchers.IO) {
            try {
                // TODO: Download from Google Drive AppData folder using REST API.
                // Enforce 10s timeout.
                val encryptedPayload = ByteArray(0) // Replace with downloaded bytes
                if (encryptedPayload.isEmpty()) return@withContext null
                decrypt(encryptedPayload, pass)
            } catch (e: AEADBadTagException) {
                oauthToken = null
                null
            } catch (e: Exception) {
                oauthToken = null
                null
            } finally {
                oauthToken = null
            }
        }
    }

    private fun encrypt(payload: ByteArray, pass: CharArray): ByteArray {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)

        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(pass, salt, 65536, 256)
        val tmp = factory.generateSecret(spec)
        val secretKey = SecretKeySpec(tmp.encoded, "AES")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(12)
        SecureRandom().nextBytes(iv)
        val gcmSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)

        val encrypted = cipher.doFinal(payload)

        // Prepend salt and iv for decryption: [salt(16)] [iv(12)] [encrypted payload]
        val result = ByteArray(salt.size + iv.size + encrypted.size)
        System.arraycopy(salt, 0, result, 0, salt.size)
        System.arraycopy(iv, 0, result, salt.size, iv.size)
        System.arraycopy(encrypted, 0, result, salt.size + iv.size, encrypted.size)
        
        return result
    }

    private fun decrypt(encryptedPayload: ByteArray, pass: CharArray): ByteArray {
        if (encryptedPayload.size < 28) throw AEADBadTagException("Invalid payload size")

        val salt = ByteArray(16)
        val iv = ByteArray(12)
        val encrypted = ByteArray(encryptedPayload.size - 28)

        System.arraycopy(encryptedPayload, 0, salt, 0, 16)
        System.arraycopy(encryptedPayload, 16, iv, 0, 12)
        System.arraycopy(encryptedPayload, 28, encrypted, 0, encrypted.size)

        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(pass, salt, 65536, 256)
        val tmp = factory.generateSecret(spec)
        val secretKey = SecretKeySpec(tmp.encoded, "AES")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

        return cipher.doFinal(encrypted)
    }
}

package com.adamoutler.ssh.sync

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONObject
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import android.util.Log

class DriveSyncManager(private val context: Context) {
    private val credentialManager = CredentialManager.create(context)
    private val webClientId = "255929341577-6e1405jlnio601o2em8mr7n7dins7ni9.apps.googleusercontent.com"
    private var oauthToken: String? = null
    
    // We request the strictly restricted appdata scope
    private val scopes = listOf("https://www.googleapis.com/auth/drive.appdata")

    companion object {
        var authorizationContinuation: kotlin.coroutines.Continuation<Unit>? = null
        var currentInstance: DriveSyncManager? = null

        fun handleAuthorizationResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
            if (requestCode == 1001) {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    try {
                        val authResult = Identity.getAuthorizationClient(currentInstance?.context!!).getAuthorizationResultFromIntent(data)
                        currentInstance?.oauthToken = authResult.accessToken
                        Log.d("DriveSyncManager", "OAuth Token received securely from intent")
                        authorizationContinuation?.resume(Unit)
                    } catch (e: Exception) {
                        Log.e("DriveSyncManager", "Failed to get auth result from intent", e)
                        authorizationContinuation?.resumeWithException(e)
                    }
                } else {
                    authorizationContinuation?.resumeWithException(IllegalStateException("Authorization denied or cancelled by user"))
                }
                authorizationContinuation = null
            }
        }
    }

    init {
        currentInstance = this
    }

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
                // Identity asserted. Now request OAuth scope for Drive.
                authorizeScope(activity)
            }
        } catch (e: GetCredentialException) {
            oauthToken = null
            Log.e("DriveSyncManager", "Auth failed", e)
        }
    }

    private suspend fun authorizeScope(activity: Activity) = suspendCancellableCoroutine<Unit> { continuation ->
        val authorizationRequest = AuthorizationRequest.builder()
            .setRequestedScopes(scopes.map { com.google.android.gms.common.api.Scope(it) })
            .build()

        Identity.getAuthorizationClient(activity)
            .authorize(authorizationRequest)
            .addOnSuccessListener { authorizationResult ->
                if (authorizationResult.hasResolution()) {
                    try {
                        authorizationContinuation = continuation
                        activity.startIntentSenderForResult(
                            authorizationResult.pendingIntent?.intentSender,
                            1001, null, 0, 0, 0, null
                        )
                    } catch (e: Exception) {
                        authorizationContinuation = null
                        continuation.resumeWithException(e)
                    }
                } else {
                    oauthToken = authorizationResult.accessToken
                    Log.d("DriveSyncManager", "OAuth Token received securely")
                    continuation.resume(Unit)
                }
            }
            .addOnFailureListener { e ->
                oauthToken = null
                Log.e("DriveSyncManager", "Scope authorization failed", e)
                continuation.resumeWithException(e)
            }
    }
    
    // We must handle the result of the intent sender in the Activity and pass the token here.
    fun setOAuthToken(token: String?) {
        this.oauthToken = token
    }

    suspend fun uploadBackup(payload: ByteArray, pass: CharArray) {
        withContext(Dispatchers.IO) {
            try {
                val encryptedPayload = encrypt(payload, pass)
                if (oauthToken == null) throw IllegalStateException("Not authenticated")

                // 1. Check if file exists to update, else create
                var fileId = findBackupFileId()
                val urlString = if (fileId != null) {
                    "https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media"
                } else {
                    "https://www.googleapis.com/upload/drive/v3/files?uploadType=media"
                }

                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = if (fileId != null) "PATCH" else "POST"
                connection.setRequestProperty("Authorization", "Bearer $oauthToken")
                connection.setRequestProperty("Content-Type", "application/octet-stream")
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                connection.doOutput = true

                connection.outputStream.use { os ->
                    os.write(encryptedPayload)
                }

                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    val error = connection.errorStream?.bufferedReader()?.readText()
                    throw IOException("Failed to upload: $responseCode $error")
                } else {
                    Log.d("DriveSyncManager", "Backup uploaded successfully: HTTP 200")
                }
                
                if (fileId == null) {
                    // Update metadata to put it in appDataFolder
                    val responseStr = connection.inputStream.bufferedReader().readText()
                    fileId = JSONObject(responseStr).getString("id")
                    updateFileMetadata(fileId)
                }

            } finally {
                // Scrub volatile state
                oauthToken = null
                pass.fill('\u0000')
            }
        }
    }

    private fun updateFileMetadata(fileId: String) {
        val url = URL("https://www.googleapis.com/drive/v3/files/$fileId")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "PATCH"
        connection.setRequestProperty("Authorization", "Bearer $oauthToken")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        connection.doOutput = true
        
        val metadata = JSONObject()
        metadata.put("name", "cossh_backup.enc")
        metadata.put("parents", org.json.JSONArray().put("appDataFolder"))
        
        connection.outputStream.use { it.write(metadata.toString().toByteArray()) }
        if (connection.responseCode !in 200..299) {
            throw IOException("Failed to update metadata: ${connection.responseCode}")
        }
    }

    private fun findBackupFileId(): String? {
        val url = URL("https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=name='cossh_backup.enc'")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Authorization", "Bearer $oauthToken")
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        
        if (connection.responseCode in 200..299) {
            val responseStr = connection.inputStream.bufferedReader().readText()
            val files = JSONObject(responseStr).optJSONArray("files")
            if (files != null && files.length() > 0) {
                return files.getJSONObject(0).getString("id")
            }
        }
        return null
    }

    suspend fun downloadBackup(pass: CharArray): ByteArray? {
        return withContext(Dispatchers.IO) {
            try {
                if (oauthToken == null) throw IllegalStateException("Not authenticated")
                val fileId = findBackupFileId() ?: return@withContext null

                val url = URL("https://www.googleapis.com/drive/v3/files/$fileId?alt=media")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Authorization", "Bearer $oauthToken")
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                if (connection.responseCode in 200..299) {
                    val encryptedPayload = readFully(connection.inputStream)
                    val result = decrypt(encryptedPayload, pass)
                    Log.d("DriveSyncManager", "Backup downloaded successfully: HTTP 200")
                    return@withContext result
                } else {
                    throw IOException("Failed to download: ${connection.responseCode}")
                }
            } catch (e: Exception) {
                Log.e("DriveSyncManager", "Download failed", e)
                null
            } finally {
                oauthToken = null
                pass.fill('\u0000')
            }
        }
    }
    
    private fun readFully(inputStream: InputStream): ByteArray {
        val baos = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        var length: Int
        while (inputStream.read(buffer).also { length = it } != -1) {
            baos.write(buffer, 0, length)
        }
        return baos.toByteArray()
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

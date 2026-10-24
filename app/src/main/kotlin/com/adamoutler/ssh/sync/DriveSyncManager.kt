package com.adamoutler.ssh.sync

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import com.adamoutler.ssh.util.AppLog
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

open class DriveSyncManager(context: Context) {
    private val appContext = context.applicationContext
    private val credentialManager = CredentialManager.create(appContext)
    private val webClientId = "255929341577-pcpa8cvp5gj2c4i4jaesq6iifk5n3a7q.apps.googleusercontent.com"

    // We request the strictly restricted appdata scope
    private val scopes = listOf("https://www.googleapis.com/auth/drive.appdata")

    companion object {
        private const val TAG = "DriveSyncManager"
        const val DEFAULT_PBKDF2_ITERATIONS = 65536

        @Volatile
        var pbkdf2Iterations: Int = DEFAULT_PBKDF2_ITERATIONS
            private set

        fun setIterationsForTesting(iterations: Int) {
            pbkdf2Iterations = iterations
        }

        fun resetIterationsForTesting() {
            pbkdf2Iterations = DEFAULT_PBKDF2_ITERATIONS
        }

        var authorizationContinuation: kotlin.coroutines.Continuation<Unit>? = null
        var currentInstance: DriveSyncManager? = null
        var cachedOAuthToken: String? = null

        fun handleAuthorizationResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
            if (requestCode == 1001) {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    try {
                        val authResult = Identity.getAuthorizationClient(currentInstance?.appContext!!).getAuthorizationResultFromIntent(data)
                        currentInstance?.oauthToken = authResult.accessToken
                        AppLog.i(TAG, "OAuth Token received securely from intent")
                        authorizationContinuation?.resume(Unit)
                    } catch (e: Exception) {
                        AppLog.e(TAG, "Failed to get auth result from intent: ${e.message}", e)
                        authorizationContinuation?.resumeWithException(e)
                    }
                } else {
                    authorizationContinuation?.resumeWithException(IllegalStateException("Authorization denied or cancelled by user"))
                }
                authorizationContinuation = null
            }
        }
    }

    var oauthToken: String? = null
        get() = field ?: cachedOAuthToken
        set(value) {
            field = value
            cachedOAuthToken = value
        }

    init {
        currentInstance = this
    }

    open suspend fun authenticate(activity: Activity) {
        val googleIdOption = GetSignInWithGoogleOption.Builder(webClientId)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        try {
            val result: GetCredentialResponse = credentialManager.getCredential(
                request = request,
                context = activity,
            )
            val credential = result.credential
            if (credential is CustomCredential) {
                if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL ||
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_SIWG_CREDENTIAL
                ) {
                    try {
                        GoogleIdTokenCredential.createFrom(credential.data)
                        AppLog.i(TAG, "DriveSyncManager: Auth successful")
                        authorizeScope(activity)
                    } catch (e: Exception) {
                        AppLog.e(TAG, "Received an invalid google id token response: ${e.message}", e)
                    }
                } else {
                    AppLog.w(TAG, "Unexpected type of credential")
                }
            } else {
                AppLog.w(TAG, "Unexpected type of credential")
            }
        } catch (e: GetCredentialException) {
            oauthToken = null
            AppLog.w(TAG, "Auth failed: ${e.message}", e)
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
                        if (activity is com.adamoutler.ssh.MainActivity) {
                            activity.startAuthFlow(authorizationResult.pendingIntent?.intentSender)
                        } else {
                            val intentSender = authorizationResult.pendingIntent?.intentSender
                                ?: throw IllegalStateException("No intent sender available")
                            activity.startIntentSenderForResult(
                                intentSender,
                                1001,
                                null,
                                0,
                                0,
                                0,
                            )
                        }
                    } catch (e: Exception) {
                        authorizationContinuation = null
                        continuation.resumeWithException(e)
                    }
                } else {
                    oauthToken = authorizationResult.accessToken
                    AppLog.i(TAG, "OAuth Token received securely")
                    continuation.resume(Unit)
                }
            }
            .addOnFailureListener { e ->
                oauthToken = null
                AppLog.w(TAG, "Scope authorization failed: ${e.message}", e)
                continuation.resumeWithException(e)
            }
    }

    suspend fun getOrRefreshAccessToken(): String? = withContext(Dispatchers.IO) {
        if (!oauthToken.isNullOrBlank()) return@withContext oauthToken
        val isRobolectric = try {
            Class.forName("org.robolectric.Robolectric")
            true
        } catch (_: ClassNotFoundException) {
            false
        }
        if (isRobolectric) return@withContext oauthToken
        return@withContext kotlinx.coroutines.withTimeoutOrNull(5000L) {
            suspendCancellableCoroutine<String?> { continuation ->
                try {
                    val authorizationRequest = AuthorizationRequest.builder()
                        .setRequestedScopes(scopes.map { com.google.android.gms.common.api.Scope(it) })
                        .build()
                    Identity.getAuthorizationClient(appContext)
                        .authorize(authorizationRequest)
                        .addOnSuccessListener { result ->
                            if (!result.hasResolution() && result.accessToken != null) {
                                oauthToken = result.accessToken
                                AppLog.i(TAG, "Silent token refresh successful")
                                continuation.resume(oauthToken)
                            } else {
                                AppLog.w(TAG, "Authorization requires user resolution; silent refresh impossible")
                                continuation.resume(null)
                            }
                        }
                        .addOnFailureListener { e ->
                            AppLog.w(TAG, "Silent authorization failed: ${e.message}", e)
                            continuation.resume(null)
                        }
                } catch (e: Exception) {
                    continuation.resume(null)
                }
            }
        }
    }

    // We must handle the result of the intent sender in the Activity and pass the token here.
    fun setOAuthToken(token: String?) {
        this.oauthToken = token
    }

    open suspend fun uploadBackup(payload: ByteArray, pass: CharArray) {
        withContext(Dispatchers.IO) {
            try {
                val token = oauthToken ?: getOrRefreshAccessToken() ?: throw IllegalStateException("Not authenticated")

                // 1. Check if file exists to update, else create via multipart POST
                val fileId = findBackupFileId()
                if (fileId == null) {
                    val boundary = "======CoSshSyncBoundary" + System.currentTimeMillis() + "======"
                    val url = URL("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
                    val connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "POST"
                    connection.setRequestProperty("Authorization", "Bearer $token")
                    connection.setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
                    connection.connectTimeout = 10000
                    connection.readTimeout = 10000
                    connection.doOutput = true

                    val metadataJson = "{\"name\": \"cossh_backup.enc\", \"parents\": [\"appDataFolder\"]}"
                    val lineEnd = "\r\n"
                    val twoHyphens = "--"

                    val part1Header = "$twoHyphens$boundary$lineEnd" +
                        "Content-Type: application/json; charset=UTF-8$lineEnd$lineEnd" +
                        metadataJson + lineEnd

                    val part2Header = "$twoHyphens$boundary$lineEnd" +
                        "Content-Type: application/octet-stream$lineEnd$lineEnd"

                    val closingBoundary = "$lineEnd$twoHyphens$boundary$twoHyphens$lineEnd"

                    connection.outputStream.use { os ->
                        os.write(part1Header.toByteArray(Charsets.UTF_8))
                        os.write(part2Header.toByteArray(Charsets.UTF_8))
                        os.write(payload)
                        os.write(closingBoundary.toByteArray(Charsets.UTF_8))
                    }

                    val responseCode = connection.responseCode
                    if (responseCode !in 200..299) {
                        val error = connection.errorStream?.bufferedReader()?.readText()
                        throw IOException("Failed to upload: $responseCode $error")
                    } else {
                        AppLog.i(TAG, "Backup uploaded successfully: HTTP $responseCode")
                    }
                } else {
                    val url = URL("https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media")
                    val connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "PATCH"
                    connection.setRequestProperty("Authorization", "Bearer $token")
                    connection.setRequestProperty("Content-Type", "application/octet-stream")
                    connection.connectTimeout = 10000
                    connection.readTimeout = 10000
                    connection.doOutput = true

                    connection.outputStream.use { os ->
                        os.write(payload)
                    }

                    val responseCode = connection.responseCode
                    if (responseCode !in 200..299) {
                        val error = connection.errorStream?.bufferedReader()?.readText()
                        throw IOException("Failed to upload: $responseCode $error")
                    } else {
                        AppLog.i(TAG, "Backup uploaded successfully: HTTP $responseCode")
                    }
                }
            } finally {
                pass.fill('\u0000')
            }
        }
    }

    open suspend fun deleteBackup(): Boolean = withContext(Dispatchers.IO) {
        try {
            val token = oauthToken ?: getOrRefreshAccessToken() ?: throw IllegalStateException("Not authenticated")
            val fileId = findBackupFileId() ?: return@withContext true
            val url = URL("https://www.googleapis.com/drive/v3/files/$fileId")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "DELETE"
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            val code = connection.responseCode
            code in 200..299 || code == 404
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to delete remote backup: ${e.message}", e)
            false
        }
    }

    private fun findBackupFileId(): String? {
        val token = oauthToken ?: return null
        val url = URL("https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=name='cossh_backup.enc'")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Authorization", "Bearer $token")
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

    open suspend fun downloadBackup(pass: CharArray): ByteArray? {
        return withContext(Dispatchers.IO) {
            try {
                val token = oauthToken ?: getOrRefreshAccessToken() ?: throw IllegalStateException("Not authenticated")
                val fileId = findBackupFileId() ?: return@withContext null

                val url = URL("https://www.googleapis.com/drive/v3/files/$fileId?alt=media")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                if (connection.responseCode in 200..299) {
                    val downloadedPayload = readFully(connection.inputStream)
                    AppLog.i(TAG, "Backup downloaded successfully: HTTP ${connection.responseCode}")
                    if (downloadedPayload.size >= 4 &&
                        downloadedPayload[0] == 0x50.toByte() &&
                        downloadedPayload[1] == 0x4B.toByte() &&
                        downloadedPayload[2] == 0x03.toByte() &&
                        downloadedPayload[3] == 0x04.toByte()
                    ) {
                        return@withContext downloadedPayload
                    }
                    return@withContext decrypt(downloadedPayload, pass)
                } else {
                    throw IOException("Failed to download: ${connection.responseCode}")
                }
            } catch (e: Exception) {
                AppLog.e(TAG, "Download failed: ${e.message}", e)
                null
            } finally {
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
        val spec = PBEKeySpec(pass, salt, pbkdf2Iterations, 256)
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

        val salt = encryptedPayload.copyOfRange(0, 16)
        val iv = encryptedPayload.copyOfRange(16, 28)
        val encrypted = encryptedPayload.copyOfRange(28, encryptedPayload.size)

        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(pass, salt, pbkdf2Iterations, 256)
        val tmp = factory.generateSecret(spec)
        val secretKey = SecretKeySpec(tmp.encoded, "AES")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

        return cipher.doFinal(encrypted)
    }
}

package com.adamoutler.ssh.ui.screens

import android.app.Application
import com.adamoutler.ssh.crypto.IdentityStorageManager
import com.adamoutler.ssh.crypto.InvalidPassphraseException
import com.adamoutler.ssh.crypto.PemUtils
import com.adamoutler.ssh.crypto.SSHKeyGenerator
import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.data.IdentityProfile
import com.adamoutler.ssh.network.SshConnectionManager
import com.adamoutler.ssh.ui.base.BaseAndroidViewModel
import com.adamoutler.ssh.ui.events.UiEvent
import com.adamoutler.ssh.ui.events.UiEventBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class IdentityFormState(
    val name: String = "",
    val username: String = "",
    val authType: AuthType = AuthType.PASSWORD,
    val originalPassword: ByteArray? = null,
    val isPasswordLocked: Boolean = false,
    val password: String = "",
    val publicKey: String = "",
    val privateKey: ByteArray? = null,
    val manualKeyEntry: Boolean = false,
    val manualPrivKey: String = "",
    val isLoaded: Boolean = false,
    val pendingEncryptedKeyBytes: ByteArray? = null,
    val pendingCipherSummary: String? = null,
    val passphraseError: String? = null,
    val securityTier: String? = null,
    val attestationRecord: com.adamoutler.ssh.crypto.strongbox.KeyAttestationRecord? = null,
    val keystoreAlias: String? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as IdentityFormState

        if (name != other.name) return false
        if (username != other.username) return false
        if (authType != other.authType) return false
        if (originalPassword != null) {
            if (other.originalPassword == null) return false
            if (!originalPassword.contentEquals(other.originalPassword)) return false
        } else if (other.originalPassword != null) {
            return false
        }
        if (isPasswordLocked != other.isPasswordLocked) return false
        if (password != other.password) return false
        if (publicKey != other.publicKey) return false
        if (privateKey != null) {
            if (other.privateKey == null) return false
            if (!privateKey.contentEquals(other.privateKey)) return false
        } else if (other.privateKey != null) {
            return false
        }
        if (manualKeyEntry != other.manualKeyEntry) return false
        if (manualPrivKey != other.manualPrivKey) return false
        if (isLoaded != other.isLoaded) return false
        if (pendingEncryptedKeyBytes != null) {
            if (other.pendingEncryptedKeyBytes == null) return false
            if (!pendingEncryptedKeyBytes.contentEquals(other.pendingEncryptedKeyBytes)) return false
        } else if (other.pendingEncryptedKeyBytes != null) {
            return false
        }
        if (pendingCipherSummary != other.pendingCipherSummary) return false
        if (passphraseError != other.passphraseError) return false
        if (securityTier != other.securityTier) return false
        if (attestationRecord != other.attestationRecord) return false
        if (keystoreAlias != other.keystoreAlias) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + username.hashCode()
        result = 31 * result + authType.hashCode()
        result = 31 * result + (originalPassword?.contentHashCode() ?: 0)
        result = 31 * result + isPasswordLocked.hashCode()
        result = 31 * result + password.hashCode()
        result = 31 * result + publicKey.hashCode()
        result = 31 * result + (privateKey?.contentHashCode() ?: 0)
        result = 31 * result + manualKeyEntry.hashCode()
        result = 31 * result + manualPrivKey.hashCode()
        result = 31 * result + isLoaded.hashCode()
        result = 31 * result + (pendingEncryptedKeyBytes?.contentHashCode() ?: 0)
        result = 31 * result + (pendingCipherSummary?.hashCode() ?: 0)
        result = 31 * result + (passphraseError?.hashCode() ?: 0)
        result = 31 * result + (securityTier?.hashCode() ?: 0)
        result = 31 * result + (attestationRecord?.hashCode() ?: 0)
        result = 31 * result + (keystoreAlias?.hashCode() ?: 0)
        return result
    }
}

class IdentityViewModel(
    application: Application,
    private val storageManager: IdentityStorageManager,
) : BaseAndroidViewModel(application) {

    constructor(application: Application) : this(
        application,
        IdentityStorageManager(application),
    )

    private val _identities = MutableStateFlow<List<IdentityProfile>>(emptyList())
    val identities: StateFlow<List<IdentityProfile>> = _identities.asStateFlow()

    private val _uiState = MutableStateFlow(IdentityFormState())
    val uiState: StateFlow<IdentityFormState> = _uiState.asStateFlow()

    init {
        loadIdentities()
    }

    fun updateState(updater: (IdentityFormState) -> IdentityFormState) {
        _uiState.update(updater)
    }

    fun loadIdentityIfNeeded(identityId: String?) {
        if (_uiState.value.isLoaded) return
        if (identityId != null) {
            val identity = getIdentity(identityId)
            if (identity != null) {
                _uiState.update {
                    it.copy(
                        name = identity.name,
                        username = identity.username,
                        authType = identity.authType,
                        originalPassword = identity.password,
                        isPasswordLocked = identity.password != null,
                        publicKey = identity.publicKey ?: "",
                        privateKey = identity.privateKey,
                        securityTier = identity.securityTier,
                        keystoreAlias = identity.keystoreAlias,
                        isLoaded = true,
                    )
                }
            }
        } else {
            _uiState.update { it.copy(isLoaded = true) }
        }
    }

    fun resetState() {
        val currentState = _uiState.value
        currentState.originalPassword?.fill(0)
        currentState.privateKey?.fill(0)
        currentState.pendingEncryptedKeyBytes?.fill(0)
        _uiState.value = IdentityFormState()
    }

    override fun onCleared() {
        resetState()
        super.onCleared()
    }

    fun loadIdentities() {
        launchWithHandler {
            _identities.value = storageManager.getAllIdentities()
        }
    }

    fun saveIdentity(identity: IdentityProfile) {
        launchWithHandler {
            val identityToSave = if (identity.keystoreAlias == null && _uiState.value.keystoreAlias != null) {
                identity.copy(keystoreAlias = _uiState.value.keystoreAlias)
            } else {
                identity
            }
            storageManager.saveIdentity(identityToSave)
            loadIdentities()
        }
    }

    fun deleteIdentity(id: String) {
        launchWithHandler {
            storageManager.deleteIdentity(id)
            loadIdentities()
        }
    }

    fun getIdentity(id: String): IdentityProfile? = storageManager.getIdentity(id)

    fun generateEd25519KeyPair() {
        launchWithHandler(Dispatchers.Default) {
            val keyPair = SSHKeyGenerator.generateEd25519KeyPair()
            _uiState.update {
                it.copy(
                    publicKey = SSHKeyGenerator.encodePublicKey(keyPair),
                    privateKey = SSHKeyGenerator.encodePrivateKey(keyPair),
                    authType = AuthType.KEY,
                    securityTier = "TEE",
                )
            }
        }
    }

    fun generateRSAKeyPair() {
        launchWithHandler(Dispatchers.Default) {
            val keyPair = SSHKeyGenerator.generateRSAKeyPair()
            _uiState.update {
                it.copy(
                    publicKey = SSHKeyGenerator.encodePublicKey(keyPair),
                    privateKey = SSHKeyGenerator.encodePrivateKey(keyPair),
                    authType = AuthType.KEY,
                    securityTier = "TEE",
                )
            }
        }
    }

    fun generateECDSAKeyPair(curveName: String = "secp256r1") {
        launchWithHandler(Dispatchers.Default) {
            val keyPair = SSHKeyGenerator.generateECDSAKeyPair(curveName)
            _uiState.update {
                it.copy(
                    publicKey = SSHKeyGenerator.encodePublicKey(keyPair),
                    privateKey = SSHKeyGenerator.encodePrivateKey(keyPair),
                    authType = AuthType.KEY,
                    securityTier = "TEE",
                )
            }
        }
    }

    fun generateStrongBoxKeyPair() {
        launchWithHandler(Dispatchers.Default) {
            val manager = com.adamoutler.ssh.crypto.strongbox.StrongBoxKeyManager(getApplication())
            val alias = "strongbox_ssh_${java.util.UUID.randomUUID()}"
            val challenge = java.security.SecureRandom().generateSeed(32)
            val result = manager.generateStrongBoxKeyPair(alias = alias, customChallenge = challenge)
            _uiState.update {
                it.copy(
                    publicKey = result.publicKeyOpenSsh,
                    privateKey = try {
                        result.keyPair.private.encoded
                    } catch (_: Throwable) {
                        null
                    },
                    authType = AuthType.KEY,
                    securityTier = if (result.isStrongBoxBacked) "STRONGBOX" else "TEE",
                    attestationRecord = result.attestationRecord,
                    keystoreAlias = result.alias,
                )
            }
        }
    }

    fun importKeyFromBytes(bytes: ByteArray) {
        launchWithHandler(Dispatchers.Default) {
            if (bytes.size > 65536) {
                UiEventBus.publish(UiEvent.ShowSnackbar("Key file exceeds 64KB limit"))
                return@launchWithHandler
            }

            val encSummary = PemUtils.detectKeyEncryptionSummary(bytes)
            if (encSummary != null) {
                _uiState.update {
                    it.copy(
                        pendingEncryptedKeyBytes = bytes.clone(),
                        pendingCipherSummary = encSummary,
                        passphraseError = null,
                    )
                }
            } else {
                try {
                    val keyPair = PemUtils.parsePemToKeyPair(bytes)
                    _uiState.update {
                        it.copy(
                            publicKey = SSHKeyGenerator.encodePublicKey(keyPair),
                            privateKey = SSHKeyGenerator.encodePrivateKey(keyPair),
                            authType = AuthType.KEY,
                            securityTier = "SOFTWARE",
                            pendingEncryptedKeyBytes = null,
                            pendingCipherSummary = null,
                            passphraseError = null,
                        )
                    }
                    UiEventBus.publish(UiEvent.ShowSnackbar("SSH Key imported successfully"))
                } catch (e: Exception) {
                    UiEventBus.publish(UiEvent.ShowSnackbar("Failed to import key: ${e.message}"))
                }
            }
        }
    }

    fun unlockPendingKey(passphrase: CharArray) {
        launchWithHandler(Dispatchers.Default) {
            val pendingBytes = _uiState.value.pendingEncryptedKeyBytes ?: return@launchWithHandler
            try {
                val keyPair = PemUtils.parsePemToKeyPair(pendingBytes, null, passphrase)
                pendingBytes.fill(0)
                _uiState.update {
                    it.copy(
                        publicKey = SSHKeyGenerator.encodePublicKey(keyPair),
                        privateKey = SSHKeyGenerator.encodePrivateKey(keyPair),
                        authType = AuthType.KEY,
                        securityTier = "SOFTWARE",
                        pendingEncryptedKeyBytes = null,
                        pendingCipherSummary = null,
                        passphraseError = null,
                    )
                }
                UiEventBus.publish(UiEvent.ShowSnackbar("SSH Key unlocked and imported"))
            } catch (_: InvalidPassphraseException) {
                _uiState.update { it.copy(passphraseError = "Incorrect passphrase for private key") }
            } catch (e: Exception) {
                _uiState.update { it.copy(passphraseError = "Decryption failed: ${e.message}") }
            } finally {
                passphrase.fill('\u0000')
            }
        }
    }

    fun cancelPendingKeyUnlock() {
        _uiState.value.pendingEncryptedKeyBytes?.fill(0)
        _uiState.update {
            it.copy(
                pendingEncryptedKeyBytes = null,
                pendingCipherSummary = null,
                passphraseError = null,
            )
        }
    }

    fun injectPublicKey(host: String, port: Int, tempPassword: String) {
        launchWithHandler(Dispatchers.IO) {
            UiEventBus.publish(UiEvent.ShowSnackbar("Injecting key..."))
            val manager = SshConnectionManager(context = getApplication())
            val tempProfile = ConnectionProfile(
                id = "temp",
                nickname = "Temp",
                host = host,
                port = port,
                username = _uiState.value.username,
                authType = AuthType.PASSWORD,
                password = tempPassword.toByteArray(),
            )
            try {
                val success = manager.injectPublicKey(tempProfile, _uiState.value.publicKey)
                if (success) {
                    UiEventBus.publish(UiEvent.ShowSnackbar("Key injected successfully!"))
                } else {
                    UiEventBus.publish(UiEvent.ShowSnackbar("Failed to inject key. Check logs."))
                }
            } finally {
                tempProfile.clearSensitiveData()
            }
        }
    }
}

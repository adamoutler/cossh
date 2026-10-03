package com.adamoutler.ssh.backup

import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.data.IdentityProfile
import com.adamoutler.ssh.util.AppLog
import java.util.UUID

/**
 * Sanitizes and validates imported ConnectionProfiles and IdentityProfiles.
 * Handles trailing/leading whitespace, empty ghost profiles, out-of-range ports,
 * duplicate or blank IDs, and orphaned identity references.
 */
object BackupSanitizer {
    private const val TAG = "CoSSH:BackupSanitizer"

    fun sanitize(
        profiles: List<ConnectionProfile>,
        identities: List<IdentityProfile>,
    ): Pair<List<ConnectionProfile>, List<IdentityProfile>> {
        val sanitizedIdentities = mutableListOf<IdentityProfile>()
        val seenIdentityIds = mutableSetOf<String>()

        for (identity in identities) {
            val rawName = identity.name.trim()
            val rawUsername = identity.username.trim()
            val rawPubKey = identity.publicKey?.trim()?.ifBlank { null }

            // Drop completely empty identities with no credentials
            if (rawName.isBlank() && rawUsername.isBlank() && rawPubKey.isNullOrBlank() && identity.password == null && identity.privateKey == null) {
                AppLog.w(TAG, "Dropping empty identity with id: '${identity.id}'")
                continue
            }

            var cleanId = identity.id.trim()
            if (cleanId.isBlank() || seenIdentityIds.contains(cleanId)) {
                val newId = UUID.randomUUID().toString()
                AppLog.w(TAG, "Replaced duplicate/blank identity ID '$cleanId' with '$newId' for '${identity.name}'")
                cleanId = newId
            }
            seenIdentityIds.add(cleanId)

            val cleanName = rawName.ifBlank { rawUsername.ifBlank { "Identity" } }
            val cleanIdentity = identity.copy(
                id = cleanId,
                name = cleanName,
                username = rawUsername,
                publicKey = rawPubKey,
            )
            // Retain transient credentials in memory
            cleanIdentity.password = identity.password
            cleanIdentity.privateKey = identity.privateKey
            sanitizedIdentities.add(cleanIdentity)
        }

        val sanitizedProfiles = mutableListOf<ConnectionProfile>()
        val seenProfileIds = mutableSetOf<String>()
        val validIdentityIds = sanitizedIdentities.map { it.id }.toSet()

        for (profile in profiles) {
            val rawHost = profile.host.trim()
            val rawNickname = profile.nickname.trim()

            // Drop ghost profiles where both host and nickname are blank
            if (rawHost.isBlank() && rawNickname.isBlank()) {
                AppLog.w(TAG, "Dropping empty ghost profile with id '${profile.id}' (both host and nickname are blank)")
                continue
            }

            var cleanId = profile.id.trim()
            if (cleanId.isBlank() || seenProfileIds.contains(cleanId)) {
                val newId = UUID.randomUUID().toString()
                AppLog.w(TAG, "Replaced duplicate/blank profile ID '$cleanId' with '$newId' for '$rawNickname'")
                cleanId = newId
            }
            seenProfileIds.add(cleanId)

            val cleanHost = rawHost
            val cleanNickname = rawNickname.ifBlank {
                if (cleanHost.isNotBlank()) cleanHost else "Unnamed Profile"
            }
            val cleanUsername = profile.username.trim()
            val cleanPort = if (profile.port in 1..65535) profile.port else 22
            val cleanFolderId = profile.folderId?.trim()?.ifBlank { null }
            val cleanInitialDir = profile.initialDirectory?.trim()?.ifBlank { null }
            val cleanFontSize = if (profile.fontSize != null && profile.fontSize in 6..48) profile.fontSize else null

            val cleanIdentityId = if (!profile.identityId.isNullOrBlank()) {
                val targetId = profile.identityId.trim()
                if (!validIdentityIds.contains(targetId)) {
                    AppLog.w(TAG, "Profile '$cleanNickname' references identityId '$targetId' which was not found in the backup.")
                }
                targetId
            } else null

            val cleanProfile = profile.copy(
                id = cleanId,
                nickname = cleanNickname,
                host = cleanHost,
                port = cleanPort,
                username = cleanUsername,
                folderId = cleanFolderId,
                initialDirectory = cleanInitialDir,
                fontSize = cleanFontSize,
                identityId = cleanIdentityId,
            )
            cleanProfile.password = profile.password
            sanitizedProfiles.add(cleanProfile)
        }

        AppLog.i(TAG, "Sanitization complete: ${sanitizedProfiles.size}/${profiles.size} profiles retained, ${sanitizedIdentities.size}/${identities.size} identities retained.")
        return Pair(sanitizedProfiles, sanitizedIdentities)
    }
}

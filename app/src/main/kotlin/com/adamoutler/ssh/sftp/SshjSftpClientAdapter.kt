package com.adamoutler.ssh.sftp

import com.adamoutler.ssh.sftp.model.SftpFileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.sftp.FileMode
import net.schmizz.sshj.sftp.OpenMode
import net.schmizz.sshj.sftp.SFTPClient
import java.io.InputStream
import java.io.OutputStream
import java.util.EnumSet

/**
 * Production implementation of [SftpClientAdapter] backed by SSHJ's [SFTPClient].
 * Executes blocking network IO on [Dispatchers.IO].
 */
class SshjSftpClientAdapter(
    private val sftpClient: SFTPClient,
) : SftpClientAdapter {

    override suspend fun list(remotePath: String): List<SftpFileItem> = withContext(Dispatchers.IO) {
        val canonical = SftpPathValidator.canonicalize(remotePath)
        val entries = sftpClient.ls(canonical)
        entries.map { info ->
            val attrs = info.attributes
            val mode = attrs.mode
            val isDir = info.isDirectory
            val isSymlink = mode.type == FileMode.Type.SYMLINK
            val isRegular = mode.type == FileMode.Type.REGULAR

            val permsStr = buildPermissionsString(mode)
            val octal = mode.mask and 0xFFF
            val mtime = attrs.mtime * 1000L

            SftpFileItem(
                name = info.name,
                fullPath = SftpPathValidator.join(canonical, info.name),
                size = attrs.size,
                isDirectory = isDir,
                isRegularFile = isRegular,
                isSymlink = isSymlink,
                permissions = permsStr,
                octalPermissions = octal,
                modifiedTimeEpochMs = mtime,
            )
        }
    }

    override suspend fun makeDirectory(remotePath: String): Unit = withContext(Dispatchers.IO) {
        val canonical = SftpPathValidator.canonicalize(remotePath)
        sftpClient.mkdir(canonical)
    }

    override suspend fun remove(remotePath: String, isDirectory: Boolean): Unit = withContext(Dispatchers.IO) {
        val canonical = SftpPathValidator.canonicalize(remotePath)
        if (isDirectory) {
            sftpClient.rmdir(canonical)
        } else {
            sftpClient.rm(canonical)
        }
    }

    override suspend fun rename(oldPath: String, newPath: String): Unit = withContext(Dispatchers.IO) {
        val canonicalOld = SftpPathValidator.canonicalize(oldPath)
        val canonicalNew = SftpPathValidator.canonicalize(newPath)
        sftpClient.rename(canonicalOld, canonicalNew)
    }

    override suspend fun setPermissions(remotePath: String, permissionsOctal: Int): Unit = withContext(Dispatchers.IO) {
        val canonical = SftpPathValidator.canonicalize(remotePath)
        sftpClient.chmod(canonical, permissionsOctal)
    }

    override suspend fun openInputStream(remotePath: String): InputStream = withContext(Dispatchers.IO) {
        val canonical = SftpPathValidator.canonicalize(remotePath)
        val file = sftpClient.open(canonical, EnumSet.of(OpenMode.READ))
        file.ReadAheadRemoteFileInputStream(16)
    }

    override suspend fun openOutputStream(remotePath: String, append: Boolean): OutputStream = withContext(Dispatchers.IO) {
        val canonical = SftpPathValidator.canonicalize(remotePath)
        val modes = if (append) {
            EnumSet.of(OpenMode.WRITE, OpenMode.CREAT, OpenMode.APPEND)
        } else {
            EnumSet.of(OpenMode.WRITE, OpenMode.CREAT, OpenMode.TRUNC)
        }
        val file = sftpClient.open(canonical, modes)
        file.RemoteFileOutputStream()
    }

    override suspend fun stat(remotePath: String): SftpFileItem? = withContext(Dispatchers.IO) {
        val canonical = SftpPathValidator.canonicalize(remotePath)
        try {
            val attrs = sftpClient.stat(canonical)
            val mode = attrs.mode
            val isDir = mode.type == FileMode.Type.DIRECTORY
            val isSymlink = mode.type == FileMode.Type.SYMLINK
            val isRegular = mode.type == FileMode.Type.REGULAR
            val name = SftpPathValidator.getFilename(canonical).ifEmpty { "/" }

            SftpFileItem(
                name = name,
                fullPath = canonical,
                size = attrs.size,
                isDirectory = isDir,
                isRegularFile = isRegular,
                isSymlink = isSymlink,
                permissions = buildPermissionsString(mode),
                octalPermissions = mode.mask and 0xFFF,
                modifiedTimeEpochMs = attrs.mtime * 1000L,
            )
        } catch (_: Exception) {
            null
        }
    }

    override fun close() {
        try {
            sftpClient.close()
        } catch (_: Exception) {}
    }

    private fun buildPermissionsString(mode: FileMode): String {
        val typeChar = when (mode.type) {
            FileMode.Type.DIRECTORY -> 'd'
            FileMode.Type.SYMLINK -> 'l'
            else -> '-'
        }
        val mask = mode.mask
        val sb = StringBuilder(10)
        sb.append(typeChar)
        sb.append(if ((mask and 0x100) != 0) 'r' else '-')
        sb.append(if ((mask and 0x080) != 0) 'w' else '-')
        sb.append(if ((mask and 0x040) != 0) 'x' else '-')
        sb.append(if ((mask and 0x020) != 0) 'r' else '-')
        sb.append(if ((mask and 0x010) != 0) 'w' else '-')
        sb.append(if ((mask and 0x008) != 0) 'x' else '-')
        sb.append(if ((mask and 0x004) != 0) 'r' else '-')
        sb.append(if ((mask and 0x002) != 0) 'w' else '-')
        sb.append(if ((mask and 0x001) != 0) 'x' else '-')
        return sb.toString()
    }
}

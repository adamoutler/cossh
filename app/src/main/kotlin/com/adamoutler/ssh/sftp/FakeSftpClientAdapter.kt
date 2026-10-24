package com.adamoutler.ssh.sftp

import com.adamoutler.ssh.sftp.model.SftpFileItem
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory test double of [SftpClientAdapter] for millisecond unit testing of SFTP operations
 * without network socket overhead.
 */
class FakeSftpClientAdapter : SftpClientAdapter {

    private val files = ConcurrentHashMap<String, ByteArray>()
    private val directories = ConcurrentHashMap.newKeySet<String>()
    private val permissions = ConcurrentHashMap<String, Int>()

    init {
        directories.add("/")
    }

    fun addVirtualFile(path: String, content: ByteArray, permsOctal: Int = 0x1A4) { // 0644
        val canonical = SftpPathValidator.canonicalize(path)
        val parent = SftpPathValidator.getParent(canonical)
        directories.add(parent)
        files[canonical] = content
        permissions[canonical] = permsOctal
    }

    fun addVirtualDirectory(path: String, permsOctal: Int = 0x1ED) { // 0755
        val canonical = SftpPathValidator.canonicalize(path)
        directories.add(canonical)
        permissions[canonical] = permsOctal
    }

    override suspend fun list(remotePath: String): List<SftpFileItem> {
        val canonical = SftpPathValidator.canonicalize(remotePath)
        if (!directories.contains(canonical)) {
            throw FileNotFoundException("Directory does not exist: $canonical")
        }

        val results = mutableListOf<SftpFileItem>()

        // Find child directories
        for (dir in directories) {
            if (dir != canonical && SftpPathValidator.getParent(dir) == canonical) {
                val name = SftpPathValidator.getFilename(dir)
                val octal = permissions[dir] ?: 0x1ED
                results.add(
                    SftpFileItem(
                        name = name,
                        fullPath = dir,
                        size = 4096L,
                        isDirectory = true,
                        isRegularFile = false,
                        isSymlink = false,
                        permissions = "drwxr-xr-x",
                        octalPermissions = octal,
                        modifiedTimeEpochMs = System.currentTimeMillis(),
                    ),
                )
            }
        }

        // Find child files
        for ((path, data) in files) {
            if (SftpPathValidator.getParent(path) == canonical) {
                val name = SftpPathValidator.getFilename(path)
                val octal = permissions[path] ?: 0x1A4
                results.add(
                    SftpFileItem(
                        name = name,
                        fullPath = path,
                        size = data.size.toLong(),
                        isDirectory = false,
                        isRegularFile = true,
                        isSymlink = false,
                        permissions = "-rw-r--r--",
                        octalPermissions = octal,
                        modifiedTimeEpochMs = System.currentTimeMillis(),
                    ),
                )
            }
        }

        return results.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    override suspend fun makeDirectory(remotePath: String) {
        val canonical = SftpPathValidator.canonicalize(remotePath)
        directories.add(canonical)
        permissions[canonical] = 0x1ED
    }

    override suspend fun remove(remotePath: String, isDirectory: Boolean) {
        val canonical = SftpPathValidator.canonicalize(remotePath)
        if (isDirectory) {
            directories.remove(canonical)
        } else {
            files.remove(canonical)
        }
        permissions.remove(canonical)
    }

    override suspend fun rename(oldPath: String, newPath: String) {
        val oldCanonical = SftpPathValidator.canonicalize(oldPath)
        val newCanonical = SftpPathValidator.canonicalize(newPath)
        if (directories.contains(oldCanonical)) {
            directories.remove(oldCanonical)
            directories.add(newCanonical)
        }
        val data = files.remove(oldCanonical)
        if (data != null) {
            files[newCanonical] = data
        }
        val perms = permissions.remove(oldCanonical)
        if (perms != null) {
            permissions[newCanonical] = perms
        }
    }

    override suspend fun setPermissions(remotePath: String, permissionsOctal: Int) {
        val canonical = SftpPathValidator.canonicalize(remotePath)
        permissions[canonical] = permissionsOctal
    }

    override suspend fun openInputStream(remotePath: String): InputStream {
        val canonical = SftpPathValidator.canonicalize(remotePath)
        val data = files[canonical] ?: throw FileNotFoundException("File not found: $canonical")
        return ByteArrayInputStream(data)
    }

    override suspend fun openOutputStream(remotePath: String, append: Boolean): OutputStream {
        val canonical = SftpPathValidator.canonicalize(remotePath)
        return object : ByteArrayOutputStream() {
            override fun close() {
                super.close()
                val newBytes = toByteArray()
                if (append) {
                    val existing = files[canonical] ?: ByteArray(0)
                    files[canonical] = existing + newBytes
                } else {
                    files[canonical] = newBytes
                }
                directories.add(SftpPathValidator.getParent(canonical))
            }
        }
    }

    override suspend fun stat(remotePath: String): SftpFileItem? {
        val canonical = SftpPathValidator.canonicalize(remotePath)
        if (directories.contains(canonical)) {
            return SftpFileItem(
                name = SftpPathValidator.getFilename(canonical).ifEmpty { "/" },
                fullPath = canonical,
                size = 4096L,
                isDirectory = true,
                isRegularFile = false,
                isSymlink = false,
                permissions = "drwxr-xr-x",
                octalPermissions = permissions[canonical] ?: 0x1ED,
                modifiedTimeEpochMs = System.currentTimeMillis(),
            )
        }
        val data = files[canonical] ?: return null
        return SftpFileItem(
            name = SftpPathValidator.getFilename(canonical),
            fullPath = canonical,
            size = data.size.toLong(),
            isDirectory = false,
            isRegularFile = true,
            isSymlink = false,
            permissions = "-rw-r--r--",
            octalPermissions = permissions[canonical] ?: 0x1A4,
            modifiedTimeEpochMs = System.currentTimeMillis(),
        )
    }

    override fun close() {
        files.clear()
        directories.clear()
        permissions.clear()
    }
}

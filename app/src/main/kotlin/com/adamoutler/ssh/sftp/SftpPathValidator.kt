package com.adamoutler.ssh.sftp

/**
 * Hardened POSIX path sanitizer and traversal guard for remote SFTP operations.
 * Neutralizes directory traversal attacks (`../`, `.\`), Unicode null byte injection (`\u0000`),
 * backslash pollution, and escapes outside designated filesystem boundaries.
 */
object SftpPathValidator {

    /**
     * Canonicalizes the given remote POSIX path:
     * - Rejects null bytes (`\u0000`)
     * - Converts Windows backslashes (`\`) to forward slashes (`/`)
     * - Removes duplicate consecutive slashes (`//`)
     * - Resolves relative segments (`.` and `..`)
     * - Ensures an absolute path format (prefixed with `/`)
     */
    fun canonicalize(rawPath: String): String {
        require(!rawPath.contains('\u0000')) { "Path contains forbidden null byte (traversal exploit)" }

        val normalized = rawPath.trim().replace('\\', '/')
        if (normalized.isEmpty() || normalized == "/") return "/"

        val segments = normalized.split('/').filter { it.isNotEmpty() && it != "." }

        val resolved = ArrayDeque<String>()
        for (seg in segments) {
            if (seg == "..") {
                if (resolved.isNotEmpty()) {
                    resolved.removeLast()
                }
            } else {
                resolved.addLast(seg)
            }
        }

        val result = resolved.joinToString("/", prefix = "/")
        return if (result.isEmpty()) "/" else result
    }

    /**
     * Safely joins a directory path and child entry name, returning a canonicalized path.
     * Throws [IllegalArgumentException] if childName attempts traversal or contains null bytes.
     */
    fun join(baseDir: String, childName: String): String {
        require(!childName.contains('\u0000')) { "Child name contains forbidden null byte" }
        require(!childName.contains('/') && !childName.contains('\\')) { "Child name cannot contain path separators: '$childName'" }
        require(childName != ".." && childName != ".") { "Invalid child directory navigation token: '$childName'" }

        val baseCanonical = canonicalize(baseDir)
        return if (baseCanonical == "/") "/$childName" else "$baseCanonical/$childName"
    }

    /**
     * Verifies whether [targetPath] resolves strictly within [baseDir] sandbox boundary.
     */
    fun isWithinBounds(targetPath: String, baseDir: String): Boolean {
        val canonicalTarget = canonicalize(targetPath)
        val canonicalBase = canonicalize(baseDir)
        if (canonicalBase == "/") return true
        return canonicalTarget == canonicalBase || canonicalTarget.startsWith("$canonicalBase/")
    }

    /**
     * Extracts parent directory from a canonical path.
     */
    fun getParent(path: String): String {
        val canonical = canonicalize(path)
        if (canonical == "/") return "/"
        val lastSlash = canonical.lastIndexOf('/')
        return if (lastSlash <= 0) "/" else canonical.substring(0, lastSlash)
    }

    /**
     * Extracts filename from a canonical path.
     */
    fun getFilename(path: String): String {
        val canonical = canonicalize(path)
        if (canonical == "/") return ""
        return canonical.substringAfterLast('/')
    }
}

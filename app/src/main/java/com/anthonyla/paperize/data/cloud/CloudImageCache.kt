package com.anthonyla.paperize.data.cloud

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.OutputStream
import java.security.MessageDigest

/**
 * A private, bounded LRU cache. The caller supplies an app-owned cacheDir/cloud-wallpapers
 * directory and a read-only streaming downloader, never a public album directory.
 *
 * Filenames are SHA-256 digests: cloud file IDs cannot escape the cache directory.
 */
class CloudImageCache(
    private val directory: File,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
    private val maxObjectBytes: Long = DEFAULT_MAX_OBJECT_BYTES
) {
    init {
        require(maxBytes > 0L)
        require(maxObjectBytes > 0L)
    }

    private val mutex = Mutex()

    suspend fun getOrDownload(
        namespace: String,
        fileId: String,
        revision: String?,
        download: suspend (OutputStream) -> Unit
    ): File = withContext(Dispatchers.IO) {
        require(namespace.isNotBlank() && fileId.isNotBlank())
        mutex.withLock {
            ensureDirectory()
            val destination = File(directory, cacheKey(namespace, fileId, revision) + ".img")
            if (destination.isFile && destination.length() > 0L) {
                destination.setLastModified(System.currentTimeMillis())
                return@withLock destination
            }
            val partial = File.createTempFile("download-", ".partial", directory)
            try {
                BufferedOutputStream(FileOutputStream(partial)).use { stream ->
                    val limited = LimitedOutputStream(stream, maxObjectBytes.coerceAtMost(maxBytes))
                    download(limited)
                    limited.flush()
                }
                if (partial.length() == 0L) throw IOException("Empty cloud image")
                if (!partial.renameTo(destination)) throw IOException("Unable to commit cloud cache file")
                destination.setLastModified(System.currentTimeMillis())
                trimToLimit(keep = destination)
                destination
            } finally {
                partial.delete()
            }
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (directory.isDirectory) {
                directory.listFiles()?.filter { it.isFile && (it.name.endsWith(".img") || it.name.endsWith(".partial")) }
                    ?.forEach { it.delete() }
            }
        }
    }

    suspend fun trim() = withContext(Dispatchers.IO) {
        mutex.withLock { trimToLimit() }
    }

    private fun trimToLimit(keep: File? = null) {
        val files = directory.listFiles()?.filter { it.isFile && it.name.endsWith(".img") }
            ?.sortedBy(File::lastModified) ?: return
        var total = files.sumOf(File::length)
        for (file in files) {
            if (total <= maxBytes) break
            if (file == keep) continue
            val size = file.length()
            if (file.delete()) total -= size
        }
    }

    private fun ensureDirectory() {
        if (!directory.isDirectory && !directory.mkdirs()) {
            throw IOException("Unable to create private cloud cache directory")
        }
    }

    private fun cacheKey(namespace: String, fileId: String, revision: String?): String {
        val value = listOf(namespace, fileId, revision.orEmpty()).joinToString("\u0000")
        return MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private class LimitedOutputStream(
        out: OutputStream,
        private val limit: Long
    ) : FilterOutputStream(out) {
        private var written = 0L

        override fun write(value: Int) {
            checkCapacity(1)
            out.write(value)
            written++
        }

        override fun write(bytes: ByteArray, off: Int, len: Int) {
            checkCapacity(len)
            out.write(bytes, off, len)
            written += len
        }

        private fun checkCapacity(length: Int) {
            if (length < 0 || written > limit - length) {
                throw IOException("Cloud image exceeds cache object limit")
            }
        }
    }

    companion object {
        const val DEFAULT_MAX_BYTES = 500L * 1024 * 1024
        const val DEFAULT_MAX_OBJECT_BYTES = 200L * 1024 * 1024
    }
}

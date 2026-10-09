package com.anthonyla.paperize.domain.cloud

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.ArrayDeque
import java.util.Locale

/**
 * Paged, iterative, metadata-only walk. It never downloads images.
 *
 * Each onBatch call must durably save that batch before returning; callers should
 * prune stale index rows ONLY after this whole method completes without errors.
 */
class CloudGalleryCrawler {
    suspend fun scan(
        gateway: CloudFileGateway,
        selectedFolderIds: Collection<String>,
        excludedFolderIds: Set<String> = emptySet(),
        batchSize: Int = 250,
        onBatch: suspend (List<CloudImage>) -> Unit
    ): CloudScanSummary {
        require(batchSize in 1..1000) { "Batch size must be between 1 and 1000" }
        val pending = ArrayDeque<String>()
        selectedFolderIds.filter { it.isNotBlank() && it !in excludedFolderIds }
            .forEach(pending::addLast)
        val visitedFolders = HashSet<String>()
        val visitedImages = HashSet<String>()
        val batch = ArrayList<CloudImage>(batchSize)
        var images = 0
        var batches = 0

        suspend fun flush() {
            if (batch.isNotEmpty()) {
                onBatch(batch.toList())
                batches++
                batch.clear()
            }
        }

        while (!pending.isEmpty()) {
            currentCoroutineContext().ensureActive()
            val folderId = pending.removeFirst()
            if (folderId in excludedFolderIds || !visitedFolders.add(folderId)) continue
            val visitedTokens = HashSet<String>()
            var pageToken: String? = null
            do {
                currentCoroutineContext().ensureActive()
                val page = gateway.listFolder(folderId, pageToken)
                for (entry in page.entries) {
                    if (entry.id.isBlank()) continue
                    if (entry.isFolder) {
                        if (entry.id !in excludedFolderIds && entry.id !in visitedFolders) {
                            pending.addLast(entry.id)
                        }
                    } else if (visitedImages.add(entry.id)) {
                        if (!entry.isSupportedImage()) continue
                        batch.add(
                            CloudImage(
                                fileId = entry.id,
                                folderId = folderId,
                                name = entry.name,
                                mimeType = entry.mimeType ?: "",
                                sizeBytes = entry.sizeBytes.coerceAtLeast(0L),
                                modifiedAtMillis = entry.modifiedAtMillis,
                                revision = entry.revision
                            )
                        )
                        images++
                        if (batch.size >= batchSize) flush()
                    }
                }
                val next = page.nextPageToken?.takeIf { it.isNotBlank() }
                check(next == null || visitedTokens.add(next)) {
                    "Cloud provider returned a repeated pagination token"
                }
                pageToken = next
            } while (pageToken != null)
        }
        flush()
        return CloudScanSummary(visitedFolders.size, images, batches)
    }

    private fun CloudEntry.isSupportedImage(): Boolean {
        if (mimeType?.lowercase(Locale.ROOT)?.startsWith("image/") == true) return true
        val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return extension in setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "avif", "gif", "bmp")
    }
}

package com.anthonyla.paperize.data.cloud

import com.anthonyla.paperize.domain.cloud.CloudFileGateway
import com.anthonyla.paperize.domain.cloud.CloudGalleryCrawler
import com.anthonyla.paperize.domain.cloud.CloudScanSummary
import java.util.UUID

/**
 * Durable metadata indexer. When a scan fails it does NOT prune the previously successful index.
 * New/updated rows from completed pages may be retained to support resumable future scans.
 */
class CloudGallerySync(
    private val database: CloudIndexDatabase,
    private val crawler: CloudGalleryCrawler = CloudGalleryCrawler()
) {
    suspend fun sync(
        collectionId: String,
        gateway: CloudFileGateway,
        folders: Collection<String>,
        excludedFolders: Set<String> = emptySet()
    ): CloudScanSummary {
        require(collectionId.isNotBlank())
        require(folders.isNotEmpty())
        val generation = UUID.randomUUID().toString()
        val dao = database.cloudIndexDao()
        val summary = crawler.scan(
            gateway = gateway,
            selectedFolderIds = folders,
            excludedFolderIds = excludedFolders,
            onBatch = { items ->
                dao.upsertImages(items.map { item ->
                    CloudIndexedImage(
                        storageKey = collectionId + ":" + item.fileId,
                        collectionId = collectionId,
                        fileId = item.fileId,
                        folderId = item.folderId,
                        name = item.name,
                        mimeType = item.mimeType,
                        sizeBytes = item.sizeBytes,
                        modifiedAtMillis = item.modifiedAtMillis,
                        revision = item.revision,
                        seenGeneration = generation
                    )
                })
            }
        )
        // Never execute this on an exception or cancelled crawl.
        dao.deleteNotSeenAfterCompleteScan(collectionId, generation)
        return summary
    }
}

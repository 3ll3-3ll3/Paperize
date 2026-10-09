package com.anthonyla.paperize.domain.cloud

import java.io.OutputStream

/**
 * Provider-neutral read-only cloud boundary.
 *
 * This is not a PikPak login implementation. A provider must supply independently
 * verified, user-authorized list and streamed-download operations.
 */
interface CloudFileGateway {
    suspend fun listFolder(folderId: String, pageToken: String?): CloudPage

    /** Stream just one selected file into a caller-managed private cache. */
    suspend fun downloadFile(fileId: String, destination: OutputStream)
}

data class CloudPage(
    val entries: List<CloudEntry>,
    val nextPageToken: String? = null
)

data class CloudEntry(
    val id: String,
    val folderId: String,
    val name: String,
    val isFolder: Boolean,
    val mimeType: String? = null,
    val sizeBytes: Long = 0L,
    val modifiedAtMillis: Long = 0L,
    val revision: String? = null
)

data class CloudImage(
    val fileId: String,
    val folderId: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val modifiedAtMillis: Long,
    val revision: String?
)

data class CloudScanSummary(
    val foldersVisited: Int,
    val imagesDiscovered: Int,
    val batchesPersisted: Int
)

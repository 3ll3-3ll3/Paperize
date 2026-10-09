package com.anthonyla.paperize.data.cloud

import com.anthonyla.paperize.domain.cloud.CloudFileGateway
import com.anthonyla.paperize.domain.cloud.CloudImage
import java.io.File

/**
 * Fetches only a chosen image, never an album. The cached File is app-private.
 * A future authorized provider adapter will supply the gateway.
 */
class CloudWallpaperMaterializer(private val cache: CloudImageCache) {
    suspend fun materialize(
        accountNamespace: String,
        image: CloudImage,
        gateway: CloudFileGateway
    ): File = cache.getOrDownload(accountNamespace, image.fileId, image.revision) { output ->
        gateway.downloadFile(image.fileId, output)
    }
}

package com.anthonyla.paperize.data.cloud

import com.anthonyla.paperize.domain.cloud.CloudEntry
import com.anthonyla.paperize.domain.cloud.CloudFileGateway
import com.anthonyla.paperize.domain.cloud.CloudImage
import com.anthonyla.paperize.domain.cloud.CloudPage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.OutputStream

class CloudWallpaperMaterializerTest {
    @get:Rule val temp = TemporaryFolder()

    @Test
    fun downloads_only_requested_image_and_uses_private_cache() = runTest {
        val downloads = mutableListOf<String>()
        val fake = object : CloudFileGateway {
            override suspend fun listFolder(folderId: String, pageToken: String?) =
                CloudPage(listOf(CloudEntry("other", folderId, "another.jpg", false)))
            override suspend fun downloadFile(fileId: String, destination: OutputStream) {
                downloads.add(fileId)
                destination.write(byteArrayOf(1, 2, 3))
            }
        }
        val cacheDir = temp.newFolder()
        val materializer = CloudWallpaperMaterializer(CloudImageCache(cacheDir))
        val image = CloudImage("selected", "root", "selected.jpg", "image/jpeg", 3L, 0L, "rev1")
        val first = materializer.materialize("account1", image, fake)
        val second = materializer.materialize("account1", image, fake)
        assertEquals(first, second)
        assertTrue(first.parentFile == cacheDir)
        assertArrayEquals(byteArrayOf(1, 2, 3), first.readBytes())
        assertEquals(listOf("selected"), downloads)
    }
}

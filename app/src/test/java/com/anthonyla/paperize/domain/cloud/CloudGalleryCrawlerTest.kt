package com.anthonyla.paperize.domain.cloud

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.OutputStream

class CloudGalleryCrawlerTest {
    private class FakeGateway(
        private val pages: Map<Pair<String, String?>, CloudPage>
    ) : CloudFileGateway {
        var downloaded = false

        override suspend fun listFolder(folderId: String, pageToken: String?): CloudPage =
            pages[folderId to pageToken] ?: CloudPage(emptyList())

        override suspend fun downloadFile(fileId: String, destination: OutputStream) {
            downloaded = true
            error("Indexing may not download pictures")
        }
    }

    @Test
    fun paginates_recurses_excludes_and_never_downloads() = runTest {
        val gateway = FakeGateway(
            mapOf(
                ("root" to null) to CloudPage(
                    listOf(
                        CloudEntry("sub", "root", "sub", isFolder = true),
                        CloudEntry("excluded", "root", "private", isFolder = true),
                        CloudEntry("one", "root", "one.JPG", isFolder = false)
                    ),
                    nextPageToken = "page2"
                ),
                ("root" to "page2") to CloudPage(
                    listOf(CloudEntry("two", "root", "two.png", isFolder = false))
                ),
                ("sub" to null) to CloudPage(
                    listOf(
                        CloudEntry("three", "sub", "third", isFolder = false, mimeType = "image/webp"),
                        CloudEntry("one", "sub", "duplicate.jpg", isFolder = false),
                        CloudEntry("root", "sub", "cycle", isFolder = true)
                    )
                )
            )
        )
        val images = mutableListOf<CloudImage>()
        val result = CloudGalleryCrawler().scan(
            gateway = gateway,
            selectedFolderIds = listOf("root", "root"),
            excludedFolderIds = setOf("excluded"),
            batchSize = 2,
            onBatch = { images.addAll(it) }
        )
        assertEquals(2, result.foldersVisited)
        assertEquals(3, result.imagesDiscovered)
        assertEquals(2, result.batchesPersisted)
        assertEquals(setOf("one", "two", "three"), images.map { it.fileId }.toSet())
        assertTrue(!gateway.downloaded)
    }

    @Test
    fun rejects_cyclic_pagination_token() = runTest {
        val gateway = FakeGateway(
            mapOf(
                ("root" to null) to CloudPage(emptyList(), "repeat"),
                ("root" to "repeat") to CloudPage(emptyList(), "repeat")
            )
        )
        var failed = false
        try {
            CloudGalleryCrawler().scan(gateway, listOf("root")) { }
        } catch (e: IllegalStateException) {
            failed = true
        }
        assertTrue(failed)
    }
}

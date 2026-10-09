package com.anthonyla.paperize.data.cloud

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.IOException

class CloudImageCacheTest {
    @get:Rule val temp = TemporaryFolder()

    @Test
    fun cache_hit_and_revision_change() = runTest {
        val cache = CloudImageCache(temp.newFolder(), maxBytes = 1024L, maxObjectBytes = 100L)
        var downloads = 0
        val first = cache.getOrDownload("account", "id", "v1") {
            downloads++
            it.write(byteArrayOf(1, 2, 3))
        }
        val hit = cache.getOrDownload("account", "id", "v1") {
            downloads++
            it.write(byteArrayOf(9))
        }
        val revision = cache.getOrDownload("account", "id", "v2") {
            downloads++
            it.write(byteArrayOf(4))
        }
        assertEquals(first, hit)
        assertTrue(revision != first)
        assertEquals(2, downloads)
    }

    @Test
    fun failed_download_does_not_leave_corrupt_cache() = runTest {
        val folder = temp.newFolder()
        val cache = CloudImageCache(folder, maxBytes = 50L, maxObjectBytes = 5L)
        var failed = false
        try {
            cache.getOrDownload("account", "../escape", null) {
                it.write(ByteArray(6))
            }
        } catch (e: IOException) {
            failed = true
        }
        assertTrue(failed)
        assertEquals(0, folder.listFiles()?.size ?: -1)
    }

    @Test
    fun evicts_least_recently_used_and_clear_only_owns_cache_files() = runTest {
        val folder = temp.newFolder()
        val cache = CloudImageCache(folder, maxBytes = 5L, maxObjectBytes = 5L)
        val first = cache.getOrDownload("a", "first", null) { it.write(ByteArray(4)) }
        // Explicitly age the first image so tests are independent of filesystem timestamps.
        first.setLastModified(1L)
        val second = cache.getOrDownload("a", "second", null) { it.write(ByteArray(4)) }
        assertFalse(first.exists())
        assertTrue(second.exists())
        val unrelated = java.io.File(folder, "keep.txt").also { it.writeText("kept") }
        cache.clear()
        assertFalse(second.exists())
        assertTrue(unrelated.exists())
    }
}

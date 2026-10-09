package com.anthonyla.paperize.data.cloud

import kotlin.random.Random

/**
 * Picks from a Room-backed metadata collection without loading every file ID in Kotlin memory.
 * Recently applied image IDs are best-effort excluded by bounded retries.
 *
 * This is intentionally separate from the upstream local wallpaper shuffle queue.
 */
class CloudImagePicker(
    private val dao: CloudIndexDao,
    private val random: Random = Random.Default
) {
    suspend fun choose(
        collectionId: String,
        recentFileIds: Set<String> = emptySet(),
        maxAttempts: Int = 30
    ): CloudIndexedImage? {
        require(collectionId.isNotBlank())
        require(maxAttempts > 0)
        val count = dao.countIncluded(collectionId)
        if (count == 0) return null

        var fallback: CloudIndexedImage? = null
        repeat(maxAttempts.coerceAtMost(count)) {
            val candidate = dao.getByOffset(collectionId, random.nextInt(count))
            if (candidate != null) {
                fallback = candidate
                if (candidate.fileId !in recentFileIds) return candidate
            }
        }
        return fallback
    }
}

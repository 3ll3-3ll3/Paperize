package com.anthonyla.paperize.data.cloud

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert

/**
 * Separate from the upstream PaperizeDatabase; no existing local-library schema migration.
 * Store metadata only, NEVER download URLs or authentication credentials.
 */
@Entity(
    tableName = "cloud_images",
    indices = [Index(value = ["collectionId", "fileId"], unique = true), Index(value = ["collectionId", "folderId"])]
)
data class CloudIndexedImage(
    @PrimaryKey val storageKey: String,
    val collectionId: String,
    val fileId: String,
    val folderId: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val modifiedAtMillis: Long,
    val revision: String?,
    val seenGeneration: String,
    val excluded: Boolean = false
)

@Dao
interface CloudIndexDao {
    @Upsert
    suspend fun upsertImages(images: List<CloudIndexedImage>)

    @Query("SELECT COUNT(*) FROM cloud_images WHERE collectionId = :collectionId AND excluded = 0")
    suspend fun countIncluded(collectionId: String): Int

    @Query("SELECT * FROM cloud_images WHERE collectionId = :collectionId AND excluded = 0 ORDER BY storageKey LIMIT 1 OFFSET :offset")
    suspend fun getByOffset(collectionId: String, offset: Int): CloudIndexedImage?

    @Query("SELECT * FROM cloud_images WHERE collectionId = :collectionId ORDER BY storageKey LIMIT :limit OFFSET :offset")
    suspend fun page(collectionId: String, limit: Int, offset: Int): List<CloudIndexedImage>

    @Query("DELETE FROM cloud_images WHERE collectionId = :collectionId AND seenGeneration != :generation")
    suspend fun deleteNotSeenAfterCompleteScan(collectionId: String, generation: String): Int

    @Query("DELETE FROM cloud_images WHERE collectionId = :collectionId")
    suspend fun clearCollection(collectionId: String)
}

@Database(entities = [CloudIndexedImage::class], version = 1, exportSchema = false)
abstract class CloudIndexDatabase : RoomDatabase() {
    abstract fun cloudIndexDao(): CloudIndexDao
}

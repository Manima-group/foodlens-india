package com.manima.foodlens.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Query("SELECT * FROM cached_products WHERE gtin14 = :gtin14")
    fun observe(gtin14: String): Flow<CachedProductEntity?>

    @Query("SELECT * FROM cached_products WHERE gtin14 = :gtin14")
    suspend fun get(gtin14: String): CachedProductEntity?

    @Query("SELECT * FROM cached_products WHERE productId = :productId")
    suspend fun getByProductId(productId: String): CachedProductEntity?

    @Upsert
    suspend fun upsert(entity: CachedProductEntity)

    @Query("UPDATE cached_products SET lastViewedAt = :now WHERE gtin14 = :gtin14")
    suspend fun touch(gtin14: String, now: Long)

    @Query(
        "DELETE FROM cached_products WHERE gtin14 NOT IN " +
            "(SELECT gtin14 FROM cached_products ORDER BY lastViewedAt DESC LIMIT :limit)"
    )
    suspend fun trimTo(limit: Int)

    @Query("DELETE FROM cached_products WHERE gtin14 = :gtin14")
    suspend fun delete(gtin14: String)

    @Query("SELECT COUNT(*) FROM cached_products")
    suspend fun count(): Int

    @Transaction
    suspend fun upsertAndTrim(entity: CachedProductEntity) {
        upsert(entity)
        trimTo(CACHE_LIMIT)
    }

    companion object {
        const val CACHE_LIMIT = 200
    }
}

@Dao
interface HistoryDao {
    /** Most recent entry per barcode. */
    @Query(
        "SELECT * FROM history WHERE id IN (SELECT MAX(id) FROM history GROUP BY gtin14) " +
            "ORDER BY scannedAt DESC LIMIT :limit"
    )
    fun observeRecent(limit: Int): Flow<List<HistoryEntity>>

    @Insert
    suspend fun insert(entity: HistoryEntity)

    @Query("DELETE FROM history")
    suspend fun clear()
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE gtin14 = :gtin14)")
    fun observeIsFavorite(gtin14: String): Flow<Boolean>

    @Upsert
    suspend fun upsert(entity: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE gtin14 = :gtin14")
    suspend fun delete(gtin14: String)
}

@Dao
interface PendingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addLookup(entity: PendingLookupEntity)

    @Query("SELECT * FROM pending_lookups ORDER BY createdAt")
    suspend fun lookups(): List<PendingLookupEntity>

    @Query("DELETE FROM pending_lookups WHERE gtin14 = :gtin14")
    suspend fun removeLookup(gtin14: String)

    @Insert
    suspend fun addScanEvent(entity: PendingScanEventEntity)

    @Query("SELECT * FROM pending_scan_events ORDER BY id LIMIT :limit")
    suspend fun scanEvents(limit: Int): List<PendingScanEventEntity>

    @Query("DELETE FROM pending_scan_events WHERE id IN (:ids)")
    suspend fun removeScanEvents(ids: List<Long>)

    @Query("DELETE FROM pending_scan_events")
    suspend fun clearScanEvents()
}

@Dao
interface ContributionDao {
    @Query("SELECT * FROM contributions ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ContributionEntity>>

    @Query("SELECT * FROM contributions WHERE localId = :localId")
    suspend fun get(localId: String): ContributionEntity?

    @Query("SELECT * FROM contribution_photos WHERE contributionLocalId = :localId")
    suspend fun photos(localId: String): List<ContributionPhotoEntity>

    @Upsert
    suspend fun upsert(entity: ContributionEntity)

    @Upsert
    suspend fun upsertPhoto(entity: ContributionPhotoEntity)

    @Transaction
    suspend fun insertWithPhotos(entity: ContributionEntity, photos: List<ContributionPhotoEntity>) {
        upsert(entity)
        photos.forEach { upsertPhoto(it) }
    }
}

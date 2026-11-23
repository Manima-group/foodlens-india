package com.manima.foodlens.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Offline product cache, LRU by [lastViewedAt], capped at [ProductDao.CACHE_LIMIT] (SPEC §10.5). */
@Entity(tableName = "cached_products", indices = [Index("productId"), Index("lastViewedAt")])
data class CachedProductEntity(
    @PrimaryKey val gtin14: String,
    val productId: String,
    val name: String,
    val brandName: String,
    val analysisStatus: String,
    /** The full GET /products/{id} body as returned by the API. */
    val detailJson: String,
    val etag: String?,
    val fetchedAt: Long,
    val lastViewedAt: Long
)

/** Local scan history. Guests' history never leaves the device (SPEC §10.5). */
@Entity(tableName = "history", indices = [Index("scannedAt"), Index("gtin14")])
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val gtin14: String,
    val productId: String?,
    val name: String?,
    val brandName: String?,
    val analysisStatus: String?,
    val found: Boolean,
    val scannedAt: Long
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val gtin14: String,
    val productId: String,
    val name: String,
    val brandName: String,
    val analysisStatus: String,
    val addedAt: Long
)

/** Barcodes scanned while offline, resolved by LookupWorker on connectivity (SPEC §10.5). */
@Entity(tableName = "pending_lookups")
data class PendingLookupEntity(@PrimaryKey val gtin14: String, val createdAt: Long)

@Entity(tableName = "pending_scan_events")
data class PendingScanEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val gtin14: String,
    val scannedAt: Long,
    val found: Boolean
)

enum class LocalContributionState { QUEUED, UPLOADING, SUBMITTED, FAILED }

@Entity(tableName = "contributions", indices = [Index("createdAt")])
data class ContributionEntity(
    @PrimaryKey val localId: String,
    val gtin14: String,
    val productName: String?,
    val brandName: String?,
    val notes: String?,
    val remoteId: String?,
    val state: LocalContributionState,
    val remoteState: String?,
    val lastError: String?,
    val createdAt: Long
)

@Entity(
    tableName = "contribution_photos",
    foreignKeys = [
        ForeignKey(
            entity = ContributionEntity::class,
            parentColumns = ["localId"],
            childColumns = ["contributionLocalId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("contributionLocalId")]
)
data class ContributionPhotoEntity(
    @PrimaryKey val id: String,
    val contributionLocalId: String,
    val kind: String,
    /** EXIF-stripped, resized JPEG in app-private storage. */
    val filePath: String,
    val remotePhotoId: String?,
    val uploaded: Boolean,
    val completed: Boolean
)

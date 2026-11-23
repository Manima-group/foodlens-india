package com.manima.foodlens.core.data

import com.manima.foodlens.core.database.FavoriteDao
import com.manima.foodlens.core.database.FavoriteEntity
import com.manima.foodlens.core.database.HistoryDao
import com.manima.foodlens.core.database.HistoryEntity
import com.manima.foodlens.core.database.PendingDao
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** Local recents and favourites. Guests' lists stay on the device (SPEC §10.5). */
@Singleton
class LibraryRepository @Inject constructor(
    private val history: HistoryDao,
    private val favorites: FavoriteDao,
    private val pending: PendingDao,
    private val clock: Clock
) {
    fun recent(limit: Int = RECENT_LIMIT): Flow<List<HistoryEntity>> = history.observeRecent(limit)

    fun favorites(): Flow<List<FavoriteEntity>> = favorites.observeAll()

    fun isFavorite(gtin14: String): Flow<Boolean> = favorites.observeIsFavorite(gtin14)

    suspend fun setFavorite(item: FavoriteItem, favorite: Boolean) {
        if (favorite) {
            favorites.upsert(
                FavoriteEntity(
                    gtin14 = item.gtin14,
                    productId = item.productId,
                    name = item.name,
                    brandName = item.brandName,
                    analysisStatus = item.analysisStatus,
                    addedAt = clock.now()
                )
            )
        } else {
            favorites.delete(item.gtin14)
        }
    }

    suspend fun clearHistory() {
        history.clear()
        pending.clearScanEvents()
    }

    companion object {
        const val RECENT_LIMIT = 50
    }
}

data class FavoriteItem(
    val gtin14: String,
    val productId: String,
    val name: String,
    val brandName: String,
    val analysisStatus: String
)

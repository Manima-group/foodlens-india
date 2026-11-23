package com.manima.foodlens.core.data

import com.manima.foodlens.core.database.CachedProductEntity
import com.manima.foodlens.core.database.HistoryDao
import com.manima.foodlens.core.database.HistoryEntity
import com.manima.foodlens.core.database.PendingDao
import com.manima.foodlens.core.database.PendingLookupEntity
import com.manima.foodlens.core.database.PendingScanEventEntity
import com.manima.foodlens.core.database.ProductDao
import com.manima.foodlens.core.datastore.SettingsStore
import com.manima.foodlens.core.network.ApiErrorCode
import com.manima.foodlens.core.network.ApiResult
import com.manima.foodlens.core.network.FoodLensClient
import com.manima.foodlens.core.network.NetworkJson
import com.manima.foodlens.core.network.model.ProductDetailDto
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException

/** A product as last stored in Room, the single source of truth for the product screen (SPEC §10.3). */
data class CachedProduct(val detail: ProductDetailDto, val fetchedAt: Long)

sealed interface RefreshResult {
    /** Fresh data was written to Room (or the server said the cached copy is current). */
    data object Updated : RefreshResult

    /** No connection. The barcode is queued for LookupWorker if nothing was cached. */
    data class Offline(val queued: Boolean) : RefreshResult

    data class NotFound(val canContribute: Boolean) : RefreshResult

    data class Failed(val error: DataError) : RefreshResult
}

/**
 * Offline-first product access: Room first, then network refresh with ETag (SPEC §10.5, §10.8).
 * Records local history; guest history never leaves the device.
 */
@Singleton
class ProductRepository @Inject constructor(
    private val client: FoodLensClient,
    private val products: ProductDao,
    private val history: HistoryDao,
    private val pending: PendingDao,
    private val settings: SettingsStore,
    private val scheduler: WorkScheduler,
    private val clock: Clock
) {
    fun observe(gtin14: String): Flow<CachedProduct?> = products.observe(gtin14)
        .distinctUntilChanged()
        .map { entity -> entity?.let(::decode) }

    /**
     * Refreshes [gtin14] into Room. [fromScan] records a history entry and (with consent) a scan event.
     */
    suspend fun refresh(gtin14: String, fromScan: Boolean = true): RefreshResult {
        val cached = products.get(gtin14)
        val productId = cached?.productId ?: when (val lookup = client.productByBarcode(gtin14)) {
            is ApiResult.Success -> lookup.value.product.id
            is ApiResult.Offline -> return offline(gtin14, cached, fromScan)
            is ApiResult.Failure -> return failed(gtin14, lookup.error.code, lookup.error.canContribute, fromScan)
            ApiResult.NotModified -> return RefreshResult.Failed(DataError.Api(ApiErrorCode.INTERNAL))
        }
        val result = when (val detail = client.productDetail(productId, cached?.etag)) {
            is ApiResult.Success -> {
                store(detail.value, detail.etag)
                RefreshResult.Updated
            }
            ApiResult.NotModified -> {
                products.touch(gtin14, clock.now())
                RefreshResult.Updated
            }
            is ApiResult.Offline -> return offline(gtin14, cached, fromScan)
            is ApiResult.Failure -> {
                // A cached id that no longer exists (e.g. product merged): drop it and resolve by barcode again.
                if (cached != null && detail.error.code.httpStatus == HTTP_NOT_FOUND) {
                    products.delete(gtin14)
                    return refresh(gtin14, fromScan)
                }
                return failed(gtin14, detail.error.code, detail.error.canContribute, fromScan)
            }
        }
        if (fromScan) record(gtin14, found = true)
        return result
    }

    /** Resolves a barcode scanned offline (LookupWorker). Returns true when it no longer needs retrying. */
    suspend fun resolvePending(gtin14: String): Boolean = when (refresh(gtin14, fromScan = false)) {
        RefreshResult.Updated -> {
            products.get(gtin14)?.let { historyEntry(it) }?.let { history.insert(it) }
            pending.removeLookup(gtin14)
            true
        }
        is RefreshResult.NotFound -> {
            pending.removeLookup(gtin14)
            true
        }
        is RefreshResult.Offline, is RefreshResult.Failed -> false
    }

    private suspend fun offline(gtin14: String, cached: CachedProductEntity?, fromScan: Boolean): RefreshResult {
        if (cached != null) {
            products.touch(gtin14, clock.now())
            if (fromScan) record(gtin14, found = true)
            return RefreshResult.Offline(queued = false)
        }
        pending.addLookup(PendingLookupEntity(gtin14, clock.now()))
        scheduler.scheduleLookups()
        return RefreshResult.Offline(queued = true)
    }

    private suspend fun failed(gtin14: String, code: ApiErrorCode, canContribute: Boolean, fromScan: Boolean) =
        if (code == ApiErrorCode.PRODUCT_NOT_FOUND) {
            if (fromScan) record(gtin14, found = false)
            RefreshResult.NotFound(canContribute)
        } else {
            RefreshResult.Failed(DataError.Api(code, canContribute))
        }

    private suspend fun store(detail: ProductDetailDto, etag: String?) {
        val now = clock.now()
        products.upsertAndTrim(
            CachedProductEntity(
                gtin14 = detail.product.gtin14,
                productId = detail.product.id,
                name = detail.product.name,
                brandName = detail.product.brand.name,
                analysisStatus = detail.analysisSummary.analysisStatus.wire,
                detailJson = NetworkJson.encodeToString(ProductDetailDto.serializer(), detail),
                etag = etag,
                fetchedAt = now,
                lastViewedAt = now
            )
        )
    }

    private suspend fun record(gtin14: String, found: Boolean) {
        val entry = products.get(gtin14)?.takeIf { found }?.let { historyEntry(it) }
            ?: HistoryEntity(
                gtin14 = gtin14,
                productId = null,
                name = null,
                brandName = null,
                analysisStatus = null,
                found = found,
                scannedAt = clock.now()
            )
        history.insert(entry)
        if (settings.current().analyticsConsent) {
            pending.addScanEvent(PendingScanEventEntity(gtin14 = gtin14, scannedAt = clock.now(), found = found))
            scheduler.scheduleScanEvents()
        }
    }

    private fun historyEntry(entity: CachedProductEntity) = HistoryEntity(
        gtin14 = entity.gtin14,
        productId = entity.productId,
        name = entity.name,
        brandName = entity.brandName,
        analysisStatus = entity.analysisStatus,
        found = true,
        scannedAt = clock.now()
    )

    private fun decode(entity: CachedProductEntity): CachedProduct? = try {
        CachedProduct(NetworkJson.decodeFromString(ProductDetailDto.serializer(), entity.detailJson), entity.fetchedAt)
    } catch (_: SerializationException) {
        null // written by an older app version with an incompatible shape: treated as not cached
    } catch (_: IllegalArgumentException) {
        null
    }

    private companion object {
        const val HTTP_NOT_FOUND = 404
    }
}

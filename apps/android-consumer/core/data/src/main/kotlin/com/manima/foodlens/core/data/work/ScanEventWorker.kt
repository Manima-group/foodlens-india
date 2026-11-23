package com.manima.foodlens.core.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.manima.foodlens.core.data.GuestSession
import com.manima.foodlens.core.database.PendingDao
import com.manima.foodlens.core.datastore.SettingsStore
import com.manima.foodlens.core.network.ApiResult
import com.manima.foodlens.core.network.FoodLensClient
import com.manima.foodlens.core.network.model.ScanEventDto
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Instant

/** Sends anonymous scan events in batches of ≤ 50, only with analytics consent (SPEC §10.5). */
@HiltWorker
class ScanEventWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val pending: PendingDao,
    private val settings: SettingsStore,
    private val guest: GuestSession,
    private val client: FoodLensClient
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!settings.current().analyticsConsent) {
            pending.clearScanEvents()
            return Result.success()
        }
        while (true) {
            val batch = pending.scanEvents(BATCH)
            if (batch.isEmpty()) return Result.success()
            val events = batch.map { ScanEventDto(it.gtin14, Instant.ofEpochMilli(it.scannedAt).toString(), it.found) }
            when (guest.authorized { client.sendScanEvents(events) }) {
                is ApiResult.Success -> pending.removeScanEvents(batch.map { it.id })
                is ApiResult.Offline -> return Result.retry()
                // Rejected batches are dropped: analytics must never block or grow without bound.
                else -> {
                    pending.removeScanEvents(batch.map { it.id })
                    return Result.success()
                }
            }
        }
    }

    companion object {
        const val NAME = "scan-events"
        private const val BATCH = 50
    }
}

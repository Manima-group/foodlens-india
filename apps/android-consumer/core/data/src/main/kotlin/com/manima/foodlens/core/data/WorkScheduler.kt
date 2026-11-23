package com.manima.foodlens.core.data

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.manima.foodlens.core.data.work.ContributionUploadWorker
import com.manima.foodlens.core.data.work.LookupWorker
import com.manima.foodlens.core.data.work.ScanEventWorker
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/** All background work is unique, network-constrained and backs off exponentially (SPEC §10.5). */
@Singleton
class WorkScheduler @Inject constructor(private val workManager: Provider<WorkManager>) {
    fun enqueueContribution(localId: String) {
        val request = OneTimeWorkRequestBuilder<ContributionUploadWorker>()
            .setInputData(workDataOf(ContributionUploadWorker.KEY_LOCAL_ID to localId))
            .connected()
            .build()
        workManager.get().enqueueUniqueWork("contribution-$localId", ExistingWorkPolicy.KEEP, request)
    }

    fun scheduleLookups() {
        val request = OneTimeWorkRequestBuilder<LookupWorker>().connected().build()
        workManager.get().enqueueUniqueWork(LookupWorker.NAME, ExistingWorkPolicy.KEEP, request)
    }

    fun scheduleScanEvents() {
        val request = OneTimeWorkRequestBuilder<ScanEventWorker>()
            .connected()
            .setInitialDelay(SCAN_EVENT_BATCH_DELAY_S, TimeUnit.SECONDS)
            .build()
        workManager.get().enqueueUniqueWork(ScanEventWorker.NAME, ExistingWorkPolicy.KEEP, request)
    }

    private fun OneTimeWorkRequest.Builder.connected(): OneTimeWorkRequest.Builder = this
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_S, TimeUnit.SECONDS)

    private companion object {
        const val BACKOFF_S = 30L
        const val SCAN_EVENT_BATCH_DELAY_S = 60L
    }
}

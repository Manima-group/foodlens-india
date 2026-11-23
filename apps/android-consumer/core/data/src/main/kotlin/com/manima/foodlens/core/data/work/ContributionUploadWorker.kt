package com.manima.foodlens.core.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.manima.foodlens.core.data.ContributionRepository
import com.manima.foodlens.core.data.UploadStep
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Uploads one contribution, resumable per photo; Idempotency-Key = local id (SPEC §10.5). */
@HiltWorker
class ContributionUploadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: ContributionRepository
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val localId = inputData.getString(KEY_LOCAL_ID) ?: return Result.failure()
        return when (repository.upload(localId)) {
            UploadStep.DONE -> Result.success()
            UploadStep.FAILED -> Result.failure()
            UploadStep.RETRY -> if (runAttemptCount < MAX_ATTEMPTS) {
                Result.retry()
            } else {
                repository.giveUp(localId)
                Result.failure()
            }
        }
    }

    companion object {
        const val KEY_LOCAL_ID = "local_id"
        private const val MAX_ATTEMPTS = 10
    }
}

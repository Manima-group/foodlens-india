package com.manima.foodlens.core.data

import android.content.Context
import android.net.Uri
import com.manima.foodlens.core.data.image.ImageProcessor
import com.manima.foodlens.core.database.ContributionDao
import com.manima.foodlens.core.database.ContributionEntity
import com.manima.foodlens.core.database.ContributionPhotoEntity
import com.manima.foodlens.core.database.LocalContributionState
import com.manima.foodlens.core.network.ApiErrorCode
import com.manima.foodlens.core.network.ApiResult
import com.manima.foodlens.core.network.FoodLensClient
import com.manima.foodlens.core.network.model.ContributionRequest
import com.manima.foodlens.core.network.model.PhotoRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** SPEC §8.3 photo kinds accepted by POST /contributions/{id}/photos. */
enum class PhotoKind(val wire: String) {
    FRONT("front"),
    BACK("back"),
    INGREDIENTS("ingredients"),
    NUTRITION("nutrition"),
    BARCODE("barcode"),
    OTHER("other")
}

data class DraftPhoto(val uri: Uri, val kind: PhotoKind, val rotationDegrees: Int = 0)

data class ContributionDraft(
    val gtin14: String,
    val productName: String?,
    val brandName: String?,
    val notes: String?,
    val photos: List<DraftPhoto>
)

enum class UploadStep { DONE, RETRY, FAILED }

/**
 * Missing-product contributions (SPEC Tier 2): photos are processed on-device, queued in Room and uploaded by
 * ContributionUploadWorker, so a submission made offline completes when the phone is back online.
 */
@Singleton
class ContributionRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: ContributionDao,
    private val client: FoodLensClient,
    private val guest: GuestSession,
    private val images: ImageProcessor,
    private val scheduler: WorkScheduler,
    private val clock: Clock
) {
    fun observe(): Flow<List<ContributionEntity>> = dao.observeAll()

    /** Processes photos (EXIF stripped, resized), stores the draft and queues the upload. Returns the local id. */
    suspend fun submit(draft: ContributionDraft): String {
        require(draft.photos.isNotEmpty()) { "At least one photo is required" }
        val localId = UUID.randomUUID().toString()
        val dir = File(context.filesDir, "contributions/$localId")
        val photos = draft.photos.map { photo ->
            val id = UUID.randomUUID().toString()
            val processed = images.process(photo.uri, File(dir, "$id.jpg"), photo.rotationDegrees)
            ContributionPhotoEntity(
                id = id,
                contributionLocalId = localId,
                kind = photo.kind.wire,
                filePath = processed.file.absolutePath,
                remotePhotoId = null,
                uploaded = false,
                completed = false
            )
        }
        dao.insertWithPhotos(
            ContributionEntity(
                localId = localId,
                gtin14 = draft.gtin14,
                productName = draft.productName?.trim()?.takeIf { it.isNotEmpty() },
                brandName = draft.brandName?.trim()?.takeIf { it.isNotEmpty() },
                notes = draft.notes?.trim()?.takeIf { it.isNotEmpty() },
                remoteId = null,
                state = LocalContributionState.QUEUED,
                remoteState = null,
                lastError = null,
                createdAt = clock.now()
            ),
            photos
        )
        scheduler.enqueueContribution(localId)
        return localId
    }

    suspend fun retry(localId: String) {
        val c = dao.get(localId) ?: return
        if (c.state == LocalContributionState.FAILED) {
            dao.upsert(c.copy(state = LocalContributionState.QUEUED, lastError = null))
            scheduler.enqueueContribution(localId)
        }
    }

    /** One resumable upload pass; each finished step is persisted so a retry continues where it stopped. */
    suspend fun upload(localId: String): UploadStep {
        var c = dao.get(localId) ?: return UploadStep.FAILED
        if (c.state == LocalContributionState.SUBMITTED) return UploadStep.DONE
        c = c.copy(state = LocalContributionState.UPLOADING).also { dao.upsert(it) }

        val remoteId = c.remoteId ?: when (val created = guest.authorized { client.createContribution(c.request()) }) {
            is ApiResult.Success -> created.value.id.also { c = c.copy(remoteId = it).also { e -> dao.upsert(e) } }
            else -> return stop(c, created)
        }
        for (photo in dao.photos(localId).filterNot { it.completed }) {
            val step = uploadPhoto(c, remoteId, photo)
            if (step != null) return step
        }
        return when (val confirmed = guest.authorized { client.confirmContribution(remoteId, localId) }) {
            is ApiResult.Success -> {
                dao.upsert(
                    c.copy(
                        state = LocalContributionState.SUBMITTED,
                        remoteState = confirmed.value.state,
                        lastError = null
                    )
                )
                File(context.filesDir, "contributions/$localId").deleteRecursively()
                UploadStep.DONE
            }
            else -> stop(c, confirmed)
        }
    }

    /** Returns null when the photo is done, else the step the worker should take. */
    private suspend fun uploadPhoto(
        c: ContributionEntity,
        remoteId: String,
        start: ContributionPhotoEntity
    ): UploadStep? {
        var photo = start
        if (!photo.uploaded) {
            val file = File(photo.filePath)
            if (!file.exists()) return markFailed(c, "photo_missing")
            val bytes = file.readBytes()
            val slot = guest.authorized {
                client.requestPhotoUpload(remoteId, PhotoRequest(photo.kind, JPEG, bytes.size.toLong()))
            }
            if (slot !is ApiResult.Success) return stop(c, slot)
            val put = client.upload(slot.value, bytes)
            if (put !is ApiResult.Success) return stop(c, put)
            photo = photo.copy(remotePhotoId = slot.value.photoId, uploaded = true).also { dao.upsertPhoto(it) }
        }
        val done = guest.authorized { client.completePhoto(remoteId, checkNotNull(photo.remotePhotoId)) }
        if (done !is ApiResult.Success) return stop(c, done)
        dao.upsertPhoto(photo.copy(completed = true))
        return null
    }

    private suspend fun stop(c: ContributionEntity, result: ApiResult<*>): UploadStep = when (result) {
        is ApiResult.Offline -> queued(c)
        is ApiResult.Failure -> when (result.error.code) {
            ApiErrorCode.RATE_LIMITED, ApiErrorCode.UPSTREAM_UNAVAILABLE, ApiErrorCode.MAINTENANCE,
            ApiErrorCode.INTERNAL, ApiErrorCode.UNAUTHENTICATED -> queued(c, result.error.code.name)
            else -> markFailed(c, result.error.code.name)
        }
        else -> queued(c)
    }

    private suspend fun queued(c: ContributionEntity, error: String? = null): UploadStep {
        dao.upsert(c.copy(state = LocalContributionState.QUEUED, lastError = error))
        return UploadStep.RETRY
    }

    suspend fun markFailed(c: ContributionEntity, error: String): UploadStep {
        dao.upsert(c.copy(state = LocalContributionState.FAILED, lastError = error))
        return UploadStep.FAILED
    }

    suspend fun giveUp(localId: String) {
        dao.get(localId)?.let { markFailed(it, "retries_exhausted") }
    }

    private fun ContributionEntity.request() = ContributionRequest(gtin14, productName, brandName, notes)

    private companion object {
        const val JPEG = "image/jpeg"
    }
}

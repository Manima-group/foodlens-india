package com.manima.foodlens.core.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.manima.foodlens.core.database.ContributionEntity
import com.manima.foodlens.core.database.ContributionPhotoEntity
import com.manima.foodlens.core.database.LocalContributionState
import io.mockk.mockk
import java.io.File
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ContributionRepositoryTest {
    private val scope = TestScope()
    private val env = TestEnv(scope.backgroundScope)
    private val repo = ContributionRepository(
        env.context,
        env.db.contributions(),
        env.client,
        GuestSession(env.client, env.tokens, env.settings),
        mockk(relaxed = true),
        env.scheduler,
        env.clock
    )

    @After
    fun tearDown() = env.close()

    private suspend fun queue(): String {
        val photo = File(env.context.filesDir, "contributions/l1/p1.jpg").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 1, 2, 3))
        }
        val contribution = ContributionEntity(
            localId = "l1",
            gtin14 = "04006381333931",
            productName = "Test",
            brandName = null,
            notes = null,
            remoteId = null,
            state = LocalContributionState.QUEUED,
            remoteState = null,
            lastError = null,
            createdAt = 1
        )
        val photoEntity = ContributionPhotoEntity(
            id = "p1",
            contributionLocalId = "l1",
            kind = "front",
            filePath = photo.absolutePath,
            remotePhotoId = null,
            uploaded = false,
            completed = false
        )
        env.db.contributions().insertWithPhotos(contribution, listOf(photoEntity))
        return "l1"
    }

    @Test
    fun uploadRunsFullFlowAsGuestWithIdempotencyKey() = scope.runTest {
        val id = queue()
        env.enqueue("guest-register.json", code = 201)
        env.enqueue("contribution.json", code = 201)
        env.enqueue("photo-upload.json", code = 201)
        env.server.enqueue(MockResponse(code = 204))
        env.enqueue("contribution.json")
        env.enqueue("contribution.json")

        assertEquals(UploadStep.DONE, repo.upload(id))

        val paths = List(6) { env.server.takeRequest() }
        assertEquals("/api/v1/guest/register", paths[0].url.encodedPath)
        assertTrue(paths[1].headers["Authorization"]!!.startsWith("Bearer "))
        assertEquals("/api/v1/uploads/0LaeCa4q6HsS-0h4YUWIRB05aITwllpGr91D0jFWUqU", paths[3].url.encodedPath)
        assertEquals("image/jpeg", paths[3].headers["Content-Type"])
        assertEquals("l1", paths[5].headers["Idempotency-Key"])
        assertEquals(LocalContributionState.SUBMITTED, env.db.contributions().get(id)!!.state)
    }

    @Test
    fun uploadResumesWithoutReuploadingFinishedPhotos() = scope.runTest {
        val id = queue()
        env.enqueue("guest-register.json", code = 201)
        env.enqueue("contribution.json", code = 201)
        env.enqueue("photo-upload.json", code = 201)
        env.server.enqueue(MockResponse(code = 204))
        env.server.enqueue(MockResponse(code = 503, body = error("UPSTREAM_UNAVAILABLE")))

        assertEquals(UploadStep.RETRY, repo.upload(id))
        repeat(5) { env.server.takeRequest() }
        assertTrue(env.db.contributions().photos(id).single().uploaded)

        env.enqueue("contribution.json")
        env.enqueue("contribution.json")
        assertEquals(UploadStep.DONE, repo.upload(id))
        assertTrue(env.server.takeRequest().url.encodedPath.endsWith("/complete"))
        assertTrue(env.server.takeRequest().url.encodedPath.endsWith("/confirm"))
    }

    @Test
    fun validationFailureIsPermanent() = scope.runTest {
        val id = queue()
        env.enqueue("guest-register.json", code = 201)
        env.server.enqueue(MockResponse(code = 422, body = error("INVALID_BARCODE")))

        assertEquals(UploadStep.FAILED, repo.upload(id))
        assertEquals(LocalContributionState.FAILED, env.db.contributions().get(id)!!.state)
    }

    private fun error(code: String) = """{"error":{"code":"$code","message":"x"}}"""
}

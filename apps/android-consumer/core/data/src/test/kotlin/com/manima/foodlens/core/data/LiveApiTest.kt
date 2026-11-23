package com.manima.foodlens.core.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.manima.foodlens.core.database.ContributionEntity
import com.manima.foodlens.core.database.ContributionPhotoEntity
import com.manima.foodlens.core.database.LocalContributionState
import com.manima.foodlens.core.model.AnalysisStatus
import com.manima.foodlens.core.network.ApiResult
import com.manima.foodlens.core.network.ClientHeaders
import com.manima.foodlens.core.network.FoodLensClient
import com.manima.foodlens.core.network.TokenProvider
import io.mockk.mockk
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * End-to-end guest flow against a running API + PostgreSQL with seed data (T10):
 * scan -> product -> alternatives -> compare -> search -> unknown barcode -> contribution upload.
 * Runs only when FOODLENS_LIVE_API is set, e.g. `FOODLENS_LIVE_API=http://127.0.0.1:8000 make android-unit`.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class LiveApiTest {
    private val base: String? = System.getenv("FOODLENS_LIVE_API")?.takeIf { it.isNotBlank() }
    private val scope = TestScope()
    private lateinit var env: TestEnv
    private lateinit var client: FoodLensClient

    @Before
    fun setUp() {
        assumeTrue("FOODLENS_LIVE_API not set; skipping live API test", base != null)
        env = TestEnv(scope.backgroundScope)
        client = FoodLensClient.create(
            baseUrl = base!!.trimEnd('/') + "/",
            headers = ClientHeaders(appVersionCode = 1, languageTag = { "en-IN" }),
            tokens = TokenProvider { env.tokens.current() }
        )
    }

    @After
    fun tearDown() {
        if (::env.isInitialized) env.close()
    }

    @Test
    fun guestCoreLoop() = scope.runTest {
        val products = ProductRepository(
            client,
            env.db.products(),
            env.db.history(),
            env.db.pending(),
            env.settings,
            env.scheduler,
            env.clock
        )
        val catalog = CatalogRepository(client)

        assertEquals(RefreshResult.Updated, products.refresh(SEEDED))
        val detail = products.observe(SEEDED).first()!!.detail
        assertEquals(AnalysisStatus.METHODOLOGY_PENDING, detail.analysisSummary.analysisStatus)
        assertEquals(null, detail.analysisSummary.scoreValue)

        val alternatives = (catalog.alternatives(detail.product.id) as Outcome.Ok).value
        if (alternatives.items.isNotEmpty()) {
            val compare = catalog.compare(listOf(detail.product.id, alternatives.items.first().id))
            assertTrue(compare is Outcome.Ok)
        }

        val search = (catalog.search("namkeen", null, null) as Outcome.Ok).value
        assertTrue(search.items.isNotEmpty())
        assertTrue((catalog.categories() as Outcome.Ok).value.isNotEmpty())

        val ingredientId = detail.ingredients.items.firstNotNullOf { it.ingredientId }
        assertTrue(catalog.ingredient(ingredientId) is Outcome.Ok)
        assertTrue(catalog.sources(detail.product.id) is Outcome.Ok)

        assertEquals(RefreshResult.Updated, products.refresh(INCOMPLETE))
        assertEquals(
            AnalysisStatus.INSUFFICIENT_DATA,
            products.observe(INCOMPLETE).first()!!.detail.analysisSummary.analysisStatus
        )

        assertEquals(RefreshResult.NotFound(canContribute = true), products.refresh(UNKNOWN))
    }

    @Test
    fun contributionUploadsAsGuest() = scope.runTest {
        val repo = ContributionRepository(
            env.context,
            env.db.contributions(),
            client,
            GuestSession(client, env.tokens, env.settings),
            mockk(relaxed = true),
            env.scheduler,
            env.clock
        )
        val photo = File(env.context.filesDir, "contributions/live/p.jpg").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) + ByteArray(256))
        }
        env.db.contributions().insertWithPhotos(
            ContributionEntity(
                localId = "live-${System.nanoTime()}",
                gtin14 = UNKNOWN,
                productName = "Live test (Synthetic)",
                brandName = null,
                notes = "Automated Android integration test",
                remoteId = null,
                state = LocalContributionState.QUEUED,
                remoteState = null,
                lastError = null,
                createdAt = 1
            ).also { env.lastLocalId = it.localId },
            emptyList()
        )
        val id = env.lastLocalId!!
        env.db.contributions().upsertPhoto(
            ContributionPhotoEntity("p", id, "front", photo.absolutePath, null, uploaded = false, completed = false)
        )

        assertEquals(UploadStep.DONE, repo.upload(id))
        assertEquals("submitted", env.db.contributions().get(id)!!.remoteState)
        val mine = client.myContributions() as ApiResult.Success
        assertTrue(mine.value.any { it.state == "submitted" && it.gtin14 == UNKNOWN })
    }

    private companion object {
        const val SEEDED = "08901234567890"
        const val INCOMPLETE = "02900000000025"
        const val UNKNOWN = "04006381333931"
    }
}

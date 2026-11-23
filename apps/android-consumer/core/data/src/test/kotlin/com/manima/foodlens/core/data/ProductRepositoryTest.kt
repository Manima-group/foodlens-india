package com.manima.foodlens.core.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.manima.foodlens.core.model.AnalysisStatus
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ProductRepositoryTest {
    private val scope = TestScope()
    private val env = TestEnv(scope.backgroundScope)
    private val repo = ProductRepository(
        env.client,
        env.db.products(),
        env.db.history(),
        env.db.pending(),
        env.settings,
        env.scheduler,
        env.clock
    )

    @After
    fun tearDown() = env.close()

    @Test
    fun scanOnlineCachesDetailAndRecordsHistory() = scope.runTest {
        env.enqueue("by-barcode.json")
        env.enqueue("product-detail.json", headers = mapOf("ETag" to "\"v1\""))

        assertEquals(RefreshResult.Updated, repo.refresh(GTIN))

        val cached = repo.observe(GTIN).first()
        assertNotNull(cached)
        assertEquals(AnalysisStatus.METHODOLOGY_PENDING, cached!!.detail.analysisSummary.analysisStatus)
        val recent = env.db.history().observeRecent(10).first()
        assertEquals(listOf(GTIN), recent.map { it.gtin14 })
        assertTrue(recent.single().found)
        assertEquals("/api/v1/products/by-barcode/$GTIN", env.server.takeRequest().url.encodedPath)
    }

    @Test
    fun cachedProductRevalidatesWithEtagAndSkipsBarcodeLookup() = scope.runTest {
        env.enqueue("by-barcode.json")
        env.enqueue("product-detail.json", headers = mapOf("ETag" to "\"v1\""))
        repo.refresh(GTIN)
        env.server.takeRequest()
        env.server.takeRequest()

        env.server.enqueue(MockResponse(code = 304))
        assertEquals(RefreshResult.Updated, repo.refresh(GTIN))

        val revalidate = env.server.takeRequest()
        assertTrue(revalidate.url.encodedPath.startsWith("/api/v1/products/01a2083e"))
        assertEquals("\"v1\"", revalidate.headers["If-None-Match"])
    }

    @Test
    fun offlineWithoutCacheQueuesLookup() = scope.runTest {
        env.server.close()

        val result = repo.refresh(GTIN)

        assertEquals(RefreshResult.Offline(queued = true), result)
        assertEquals(listOf(GTIN), env.db.pending().lookups().map { it.gtin14 })
        verify { env.scheduler.scheduleLookups() }
    }

    @Test
    fun offlineReopenOfCachedProductWorks() = scope.runTest {
        env.enqueue("by-barcode.json")
        env.enqueue("product-detail.json")
        repo.refresh(GTIN)
        env.server.close()

        assertEquals(RefreshResult.Offline(queued = false), repo.refresh(GTIN))
        assertNotNull(repo.observe(GTIN).first())
        assertTrue(env.db.pending().lookups().isEmpty())
    }

    @Test
    fun unknownBarcodeOffersContribution() = scope.runTest {
        env.enqueue("not-found.json", code = 404)

        assertEquals(RefreshResult.NotFound(canContribute = true), repo.refresh("04006381333931"))
        assertFalse(env.db.history().observeRecent(10).first().single().found)
        assertNull(repo.observe("04006381333931").first())
    }

    @Test
    fun scanEventsQueuedOnlyWithConsent() = scope.runTest {
        env.enqueue("not-found.json", code = 404)
        repo.refresh("04006381333931")
        assertTrue(env.db.pending().scanEvents(50).isEmpty())

        env.settings.setAnalyticsConsent(true)
        env.enqueue("not-found.json", code = 404)
        repo.refresh("04006381333931")
        assertEquals(1, env.db.pending().scanEvents(50).size)
        verify(exactly = 1) { env.scheduler.scheduleScanEvents() }
    }

    @Test
    fun resolvePendingStoresProductAndClearsQueue() = scope.runTest {
        env.server.close()
        repo.refresh(GTIN)
        val online = TestEnv(scope.backgroundScope)
        val onlineRepo = ProductRepository(
            online.client,
            env.db.products(),
            env.db.history(),
            env.db.pending(),
            env.settings,
            env.scheduler,
            env.clock
        )
        online.enqueue("by-barcode.json")
        online.enqueue("product-detail.json")

        assertTrue(onlineRepo.resolvePending(GTIN))
        assertTrue(env.db.pending().lookups().isEmpty())
        assertNotNull(onlineRepo.observe(GTIN).first())
        online.server.close()
    }

    private companion object {
        const val GTIN = "08901234567890"
    }
}

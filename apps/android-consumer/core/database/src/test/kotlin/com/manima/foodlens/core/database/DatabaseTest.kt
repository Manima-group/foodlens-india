package com.manima.foodlens.core.database

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class DatabaseTest {
    private val db = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        FoodLensDatabase::class.java
    ).allowMainThreadQueries().build()

    @get:Rule
    val migrations = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        FoodLensDatabase::class.java
    )

    @After
    fun close() = db.close()

    private fun product(gtin: String, viewed: Long) = CachedProductEntity(
        gtin14 = gtin, productId = "p$gtin", name = "n", brandName = "b", analysisStatus = "methodology_pending",
        detailJson = "{}", etag = null, fetchedAt = viewed, lastViewedAt = viewed
    )

    @Test
    fun cacheIsLruCappedAt200() = runTest {
        val dao = db.products()
        repeat(205) { i -> dao.upsertAndTrim(product(i.toString().padStart(14, '0'), viewed = i.toLong())) }
        assertEquals(200, dao.count())
        assertNull("oldest evicted", dao.get("00000000000000"))
        assertNotNull(dao.get("00000000000204"))
    }

    @Test
    fun touchingKeepsAProductCached() = runTest {
        val dao = db.products()
        repeat(200) { i -> dao.upsertAndTrim(product(i.toString().padStart(14, '0'), viewed = i.toLong())) }
        dao.touch("00000000000000", now = 10_000)
        dao.upsertAndTrim(product("99999999999999", viewed = 500))
        assertNotNull(dao.get("00000000000000"))
        assertNull(dao.get("00000000000001"))
    }

    @Test
    fun recentHistoryShowsLatestPerBarcode() = runTest {
        val dao = db.history()
        dao.insert(
            HistoryEntity(
                gtin14 = "a",
                productId = null,
                name = null,
                brandName = null,
                analysisStatus = null,
                found = false,
                scannedAt = 1
            )
        )
        dao.insert(
            HistoryEntity(
                gtin14 = "b",
                productId = "p",
                name = "B",
                brandName = "x",
                analysisStatus = "s",
                found = true,
                scannedAt = 2
            )
        )
        dao.insert(
            HistoryEntity(
                gtin14 = "a",
                productId = "p2",
                name = "A",
                brandName = "y",
                analysisStatus = "s",
                found = true,
                scannedAt = 3
            )
        )
        val recent = dao.observeRecent(10).first()
        assertEquals(listOf("a", "b"), recent.map { it.gtin14 })
        assertEquals(true, recent.first().found)
    }

    @Test
    fun contributionPhotosCascade() = runTest {
        val dao = db.contributions()
        val c = ContributionEntity(
            "l1",
            "04006381333931",
            null,
            null,
            null,
            null,
            LocalContributionState.QUEUED,
            null,
            null,
            1
        )
        dao.insertWithPhotos(
            c,
            listOf(ContributionPhotoEntity("ph1", "l1", "front", "/x.jpg", null, uploaded = false, completed = false))
        )
        assertEquals(1, dao.photos("l1").size)
        assertEquals(LocalContributionState.QUEUED, dao.get("l1")?.state)
    }

    @Test
    fun schemaVersion1CreatesFromExportedSchema() {
        migrations.createDatabase("migration-test", 1).close()
    }
}

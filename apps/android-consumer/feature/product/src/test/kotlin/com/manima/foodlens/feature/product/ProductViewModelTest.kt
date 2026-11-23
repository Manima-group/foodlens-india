package com.manima.foodlens.feature.product

import androidx.lifecycle.SavedStateHandle
import com.manima.foodlens.core.data.CatalogRepository
import com.manima.foodlens.core.data.ConnectivityMonitor
import com.manima.foodlens.core.data.LibraryRepository
import com.manima.foodlens.core.data.ProductRepository
import com.manima.foodlens.core.data.RefreshResult
import com.manima.foodlens.core.datastore.SettingsStore
import com.manima.foodlens.core.datastore.UserSettings
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProductViewModelTest {
    private val products = mockk<ProductRepository>()
    private val catalog = mockk<CatalogRepository>(relaxed = true)
    private val library = mockk<LibraryRepository>(relaxed = true)
    private val settings = mockk<SettingsStore>()
    private val connectivity = mockk<ConnectivityMonitor>()
    private val online = MutableStateFlow(true)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { products.observe(GTIN) } returns flowOf(null)
        every { library.isFavorite(GTIN) } returns flowOf(false)
        every { settings.settings } returns flowOf(UserSettings())
        every { connectivity.isOnline } returns online
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm() = ProductViewModel(
        SavedStateHandle(mapOf(ProductViewModel.ARG_GTIN14 to GTIN)),
        products,
        catalog,
        library,
        settings,
        connectivity
    )

    @Test
    fun firstRefreshCountsAsAScan() = runTest {
        coEvery { products.refresh(GTIN, any()) } returns RefreshResult.Updated
        val vm = vm()
        assertFalse(vm.state.value.refreshing)
        coVerify(exactly = 1) { products.refresh(GTIN, fromScan = true) }
        vm.refresh()
        coVerify(exactly = 1) { products.refresh(GTIN, fromScan = false) }
    }

    @Test
    fun notFoundOffersContribution() = runTest {
        coEvery { products.refresh(GTIN, any()) } returns RefreshResult.NotFound(canContribute = true)
        val state = vm().state.value
        assertTrue(state.notFound)
        assertTrue(state.canContribute)
    }

    @Test
    fun offlineWithoutCacheIsQueuedAndRetriesWhenBackOnline() = runTest {
        coEvery { products.refresh(GTIN, any()) } returns RefreshResult.Offline(queued = true)
        val vm = vm()
        assertTrue(vm.state.value.queued)
        coEvery { products.refresh(GTIN, any()) } returns RefreshResult.Updated
        online.value = false
        online.value = true
        assertFalse(vm.state.value.offline)
        assertEquals(false, vm.state.value.queued)
    }

    private companion object {
        const val GTIN = "08901234567890"
    }
}

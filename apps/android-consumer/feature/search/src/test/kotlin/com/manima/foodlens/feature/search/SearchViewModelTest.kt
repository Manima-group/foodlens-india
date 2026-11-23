package com.manima.foodlens.feature.search

import com.manima.foodlens.core.data.CatalogRepository
import com.manima.foodlens.core.data.DataError
import com.manima.foodlens.core.data.Outcome
import com.manima.foodlens.core.network.NetworkJson
import com.manima.foodlens.core.network.model.PageDto
import com.manima.foodlens.core.network.model.ProductSummaryDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)
    private val catalog = mockk<CatalogRepository>()
    private val page = NetworkJson.decodeFromString(
        PageDto.serializer(ProductSummaryDto.serializer()),
        java.io.File(System.getProperty("foodlens.fixtures.dir"), "api-examples/live/search.json").readText()
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { catalog.categories() } returns Outcome.Ok(emptyList())
        coEvery { catalog.search(any(), any(), any()) } returns Outcome.Ok(page)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun typingIsDebouncedIntoOneSearch() = scope.runTest {
        val vm = SearchViewModel(catalog)
        advanceUntilIdle()
        "chips".forEachIndexed { i, _ -> vm.onQueryChange("chips".take(i + 1)) }
        advanceTimeBy(100)
        coVerify(exactly = 0) { catalog.search("chips", null, null) }
        advanceUntilIdle()
        coVerify(exactly = 1) { catalog.search("chips", null, null) }
        assertTrue(vm.state.value.searched)
        assertEquals(page.items.size, vm.state.value.results.size)
    }

    @Test
    fun offlineShowsError() = scope.runTest {
        coEvery { catalog.search(any(), any(), any()) } returns Outcome.Err(DataError.Offline)
        val vm = SearchViewModel(catalog)
        advanceUntilIdle()
        assertEquals(DataError.Offline, vm.state.value.error)
    }

    @Test
    fun selectingACategoryTwiceClearsIt() = scope.runTest {
        val vm = SearchViewModel(catalog)
        vm.onCategory("snacks")
        assertEquals("snacks", vm.state.value.category)
        vm.onCategory("snacks")
        assertEquals(null, vm.state.value.category)
    }
}

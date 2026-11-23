package com.manima.foodlens.feature.history

import com.manima.foodlens.core.data.LibraryRepository
import com.manima.foodlens.core.database.FavoriteEntity
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
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
class LibraryViewModelTest {
    private val library = mockk<LibraryRepository>(relaxed = true)
    private val favorites = (1..5).map {
        FavoriteEntity("g$it", "p$it", "Product $it", "Brand", "methodology_pending", it.toLong())
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { library.recent(any()) } returns flowOf(emptyList())
        every { library.favorites() } returns flowOf(favorites)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun compareNeedsTwoToFourProducts() = runTest {
        val vm = LibraryViewModel(library)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        vm.toggleSelected("p1")
        assertFalse(vm.state.value.canCompare)
        vm.toggleSelected("p2")
        assertTrue(vm.state.value.canCompare)
        listOf("p3", "p4", "p5").forEach(vm::toggleSelected)
        assertEquals(setOf("p1", "p2", "p3", "p4"), vm.state.value.selected)
        vm.toggleSelected("p1")
        assertEquals(3, vm.state.value.selected.size)
    }
}

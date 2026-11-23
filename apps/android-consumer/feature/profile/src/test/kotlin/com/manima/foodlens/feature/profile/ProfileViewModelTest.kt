package com.manima.foodlens.feature.profile

import com.manima.foodlens.core.data.ApiEndpoint
import com.manima.foodlens.core.data.AppConfig
import com.manima.foodlens.core.data.ConfigRepository
import com.manima.foodlens.core.data.LibraryRepository
import com.manima.foodlens.core.datastore.SettingsStore
import com.manima.foodlens.core.datastore.UserSettings
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {
    private val settings = mockk<SettingsStore>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { settings.settings } returns flowOf(UserSettings())
        coEvery { settings.current() } returns UserSettings(allergenPrefs = setOf("milk"))
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm(debug: Boolean) = ProfileViewModel(
        settings,
        mockk<LibraryRepository>(relaxed = true),
        mockk<ConfigRepository>(relaxed = true),
        mockk(relaxed = true),
        mockk<ApiEndpoint>(relaxed = true),
        AppConfig("http://10.0.2.2:8000", 1, "1.0", isDebug = debug)
    )

    @Test
    fun releaseBuildIgnoresApiUrlOverride() {
        vm(debug = false).setBaseUrl("http://192.168.1.5:8000")
        coVerify(exactly = 0) { settings.setApiBaseUrlOverride(any()) }
    }

    @Test
    fun invalidApiUrlIsRejectedWithoutSaving() {
        val vm = vm(debug = true)
        vm.setBaseUrl("not a url")
        assertEquals(ConnectionCheck.Invalid, vm.check.value)
        coVerify(exactly = 0) { settings.setApiBaseUrlOverride(any()) }
    }

    @Test
    fun togglingAllergenRemovesExistingAndAddsNew() {
        val vm = vm(debug = false)
        vm.toggleAllergen("milk")
        coVerify { settings.setAllergenPrefs(emptySet()) }
        vm.toggleAllergen("peanut")
        coVerify { settings.setAllergenPrefs(setOf("milk", "peanut")) }
    }
}

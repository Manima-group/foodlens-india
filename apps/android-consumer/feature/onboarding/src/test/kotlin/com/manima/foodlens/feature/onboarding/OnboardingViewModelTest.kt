package com.manima.foodlens.feature.onboarding

import com.manima.foodlens.core.datastore.SettingsStore
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    private val settings = mockk<SettingsStore>(relaxed = true)

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun consentIsOffByDefaultAndSavedOnFinish() {
        val vm = OnboardingViewModel(settings)
        assertFalse(vm.state.value.analyticsConsent)
        vm.toggleAllergen("milk")
        var done = false
        vm.finish { done = true }
        assertTrue(done)
        coVerify { settings.setAnalyticsConsent(false) }
        coVerify { settings.setAllergenPrefs(setOf("milk")) }
        coVerify { settings.setOnboardingDone(true) }
    }

    @Test
    fun pagesAreBounded() {
        val vm = OnboardingViewModel(settings)
        vm.back()
        assertEquals(0, vm.state.value.page)
        repeat(10) { vm.next() }
        assertEquals(OnboardingViewModel.PAGES - 1, vm.state.value.page)
    }
}

package com.manima.foodlens.feature.contribute

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.manima.foodlens.core.data.ConfigRepository
import com.manima.foodlens.core.data.ContributionDraft
import com.manima.foodlens.core.data.ContributionRepository
import com.manima.foodlens.core.data.PhotoKind
import com.manima.foodlens.core.data.RemoteConfig
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ContributeViewModelTest {
    private val repo = mockk<ContributionRepository>()
    private val config = mockk<ConfigRepository>()
    private val draft = slot<ContributionDraft>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { config.state } returns MutableStateFlow(RemoteConfig())
        coEvery { repo.submit(capture(draft)) } returns "local-1"
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm() = ContributeViewModel(SavedStateHandle(mapOf("gtin14" to GTIN)), repo, config)

    @Test
    fun submitNeedsAPhoto() {
        val vm = vm()
        assertFalse(vm.state.value.canSubmit)
        vm.submit()
        assertEquals(SubmitError.NeedPhoto, vm.state.value.error)
    }

    @Test
    fun submitQueuesDraftWithRotationAndKinds() {
        val vm = vm()
        val uri = Uri.parse("content://test/front.jpg")
        vm.onPhoto(PhotoKind.FRONT, uri)
        vm.rotate(PhotoKind.FRONT)
        vm.rotate(PhotoKind.FRONT)
        vm.onName("Test Namkeen (Synthetic)")
        vm.submit()
        assertTrue(vm.state.value.submitted)
        assertEquals(GTIN, draft.captured.gtin14)
        assertEquals(PhotoKind.FRONT, draft.captured.photos.single().kind)
        assertEquals(180, draft.captured.photos.single().rotationDegrees)
    }

    @Test
    fun unreadableImageIsReported() {
        coEvery { repo.submit(any()) } throws IOException("bad")
        val vm = vm()
        vm.onPhoto(PhotoKind.NUTRITION, Uri.parse("content://test/x.jpg"))
        vm.submit()
        assertEquals(SubmitError.ImageUnreadable, vm.state.value.error)
        assertFalse(vm.state.value.submitted)
    }

    private companion object {
        const val GTIN = "04006381333931"
    }
}

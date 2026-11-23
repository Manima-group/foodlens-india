package com.manima.foodlens.feature.scan

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualEntryViewModelTest {
    private val viewModel = ManualEntryViewModel()

    @Test
    fun `valid EAN-13 emits lookup event with canonical GTIN-14`() = runTest {
        viewModel.events.test {
            viewModel.onCodeChanged("8901234567890")
            viewModel.onSubmit()
            assertEquals(ManualEntryEvent.Lookup("08901234567890"), awaitItem())
        }
        assertEquals(ManualEntryError.None, viewModel.state.value.error)
    }

    @Test
    fun `bad check digit shows check digit error and no event`() = runTest {
        viewModel.onCodeChanged("8901234567891")
        viewModel.onSubmit()
        assertEquals(ManualEntryError.InvalidCheckDigit, viewModel.state.value.error)
    }

    @Test
    fun `non digit input shows invalid barcode error`() = runTest {
        viewModel.onCodeChanged("89012345A")
        viewModel.onSubmit()
        assertEquals(ManualEntryError.InvalidBarcode, viewModel.state.value.error)
    }

    @Test
    fun `ambiguous 8 digit code offers both candidates and choosing one emits lookup`() = runTest {
        viewModel.onCodeChanged("01234565")
        viewModel.onSubmit()
        val candidates = viewModel.state.value.candidates
        assertEquals(listOf("00000001234565", "00012345000065"), candidates.map { it.gtin14 })
        viewModel.events.test {
            viewModel.onCandidateChosen(candidates[1].gtin14)
            assertEquals(ManualEntryEvent.Lookup("00012345000065"), awaitItem())
        }
        assertTrue(viewModel.state.value.candidates.isEmpty())
    }

    @Test
    fun `editing the code clears the previous error`() = runTest {
        viewModel.onCodeChanged("123")
        viewModel.onSubmit()
        assertEquals(ManualEntryError.InvalidBarcode, viewModel.state.value.error)
        viewModel.onCodeChanged("1234")
        assertEquals(ManualEntryError.None, viewModel.state.value.error)
    }
}

package com.manima.foodlens.feature.scan

import com.manima.foodlens.core.model.Symbology
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerViewModelTest {
    private val vm = ScannerViewModel()

    @Test
    fun validScanNavigatesWithGtin14() = runTest {
        assertTrue(vm.onDetected(Detection("8901234567890", Symbology.EAN_13, 0f, 0f)))
        assertEquals(ScannerEvent.Lookup("08901234567890"), vm.events.first())
    }

    @Test
    fun scannerSymbologyIsTrusted() {
        // Valid as UPC-E but the scanner said EAN-8 and the EAN-8 check digit fails (fixture ean8_symbology_bad_check).
        assertFalse(vm.onDetected(Detection("04252614", Symbology.EAN_8, 0f, 0f)))
        assertEquals(ScanMessage.InvalidBarcode, vm.state.value.message)
    }

    @Test
    fun photoWithoutValidBarcodeShowsMessage() {
        vm.onPhotoScanned(emptyList())
        assertEquals(ScanMessage.NoBarcodeInPhoto, vm.state.value.message)
    }

    @Test
    fun photoPicksFirstValidBarcode() = runTest {
        vm.onPhotoScanned(
            listOf(
                Detection("8901234567891", Symbology.EAN_13, 0f, 0f),
                Detection("036000291452", Symbology.UPC_A, 0f, 0f)
            )
        )
        assertEquals(ScannerEvent.Lookup("00036000291452"), vm.events.first())
    }
}

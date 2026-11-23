package com.manima.foodlens.feature.scan

import androidx.lifecycle.ViewModel
import com.manima.foodlens.core.gtin.Gtin
import com.manima.foodlens.core.gtin.GtinResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

enum class ScanMessage { None, InvalidBarcode, NoBarcodeInPhoto }

data class ScannerUiState(val torchOn: Boolean = false, val message: ScanMessage = ScanMessage.None)

sealed interface ScannerEvent {
    data class Lookup(val gtin14: String) : ScannerEvent
}

/** Validates every accepted barcode with :core:gtin, trusting the scanner's symbology (SPEC §7). */
@HiltViewModel
class ScannerViewModel @Inject constructor() : ViewModel() {
    private val _state = MutableStateFlow(ScannerUiState())
    val state: StateFlow<ScannerUiState> = _state.asStateFlow()

    private val _events = Channel<ScannerEvent>(Channel.BUFFERED)
    val events: Flow<ScannerEvent> = _events.receiveAsFlow()

    fun onDetected(detection: Detection): Boolean =
        when (val result = Gtin.normalize(detection.rawValue, detection.symbology)) {
            is GtinResult.Valid -> {
                _state.update { it.copy(message = ScanMessage.None) }
                _events.trySend(ScannerEvent.Lookup(result.gtin14))
                true
            }
            else -> {
                _state.update { it.copy(message = ScanMessage.InvalidBarcode) }
                false
            }
        }

    fun onPhotoScanned(detections: List<Detection>) {
        val accepted = detections.firstOrNull { Gtin.normalize(it.rawValue, it.symbology) is GtinResult.Valid }
        if (accepted == null) {
            _state.update { it.copy(message = ScanMessage.NoBarcodeInPhoto) }
        } else {
            onDetected(accepted)
        }
    }

    fun toggleTorch() = _state.update { it.copy(torchOn = !it.torchOn) }

    fun dismissMessage() = _state.update { it.copy(message = ScanMessage.None) }
}

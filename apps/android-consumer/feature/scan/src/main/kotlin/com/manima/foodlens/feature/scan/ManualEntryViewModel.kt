package com.manima.foodlens.feature.scan

import androidx.lifecycle.ViewModel
import com.manima.foodlens.core.gtin.Gtin
import com.manima.foodlens.core.gtin.GtinErrorCode
import com.manima.foodlens.core.gtin.GtinResult
import com.manima.foodlens.core.model.Symbology
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

enum class ManualEntryError { None, InvalidBarcode, InvalidCheckDigit }

data class BarcodeCandidate(val gtin14: String, val symbology: Symbology)

data class ManualEntryUiState(
    val code: String = "",
    val error: ManualEntryError = ManualEntryError.None,
    /** Non-empty when the code is a valid EAN-8 and a valid UPC-E (SPEC §7 step 2). */
    val candidates: List<BarcodeCandidate> = emptyList()
)

sealed interface ManualEntryEvent {
    data class Lookup(val gtin14: String) : ManualEntryEvent
}

/** Manual barcode entry: validates locally with :core:gtin before any network call. */
@HiltViewModel
class ManualEntryViewModel @Inject constructor() : ViewModel() {
    private val _state = MutableStateFlow(ManualEntryUiState())
    val state: StateFlow<ManualEntryUiState> = _state.asStateFlow()

    private val _events = Channel<ManualEntryEvent>(Channel.BUFFERED)
    val events: Flow<ManualEntryEvent> = _events.receiveAsFlow()

    fun onCodeChanged(code: String) {
        _state.update { ManualEntryUiState(code = code.take(MAX_INPUT_LENGTH)) }
    }

    fun onSubmit() {
        when (val result = Gtin.normalize(_state.value.code, Symbology.MANUAL)) {
            is GtinResult.Valid -> lookup(result.gtin14)
            is GtinResult.Ambiguous -> _state.update { state ->
                state.copy(
                    error = ManualEntryError.None,
                    candidates = result.candidates.map { BarcodeCandidate(it.gtin14, it.symbology) }
                )
            }
            is GtinResult.Invalid -> _state.update {
                it.copy(error = result.code.toUiError(), candidates = emptyList())
            }
        }
    }

    fun onCandidateChosen(gtin14: String) {
        if (_state.value.candidates.any { it.gtin14 == gtin14 }) lookup(gtin14)
    }

    private fun lookup(gtin14: String) {
        _state.update { it.copy(error = ManualEntryError.None, candidates = emptyList()) }
        _events.trySend(ManualEntryEvent.Lookup(gtin14))
    }

    private fun GtinErrorCode.toUiError() = when (this) {
        GtinErrorCode.INVALID_CHECK_DIGIT -> ManualEntryError.InvalidCheckDigit
        GtinErrorCode.INVALID_BARCODE, GtinErrorCode.AMBIGUOUS_BARCODE -> ManualEntryError.InvalidBarcode
    }

    private companion object {
        /** Room for 14 digits plus spaces/hyphens a user may type. */
        const val MAX_INPUT_LENGTH = 24
    }
}

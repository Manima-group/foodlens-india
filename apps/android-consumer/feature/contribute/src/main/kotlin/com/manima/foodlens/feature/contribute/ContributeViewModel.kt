package com.manima.foodlens.feature.contribute

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manima.foodlens.core.data.ConfigRepository
import com.manima.foodlens.core.data.ContributionDraft
import com.manima.foodlens.core.data.ContributionRepository
import com.manima.foodlens.core.data.DraftPhoto
import com.manima.foodlens.core.data.PhotoKind
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PhotoSlot(val uri: Uri, val rotation: Int = 0)

enum class SubmitError { None, NeedPhoto, ImageUnreadable }

data class ContributeUiState(
    val gtin14: String,
    val correction: Boolean,
    val productName: String = "",
    val brandName: String = "",
    val notes: String = "",
    val photos: Map<PhotoKind, PhotoSlot> = emptyMap(),
    val submitting: Boolean = false,
    val submitted: Boolean = false,
    val error: SubmitError = SubmitError.None,
    val enabled: Boolean = true
) {
    val canSubmit: Boolean get() = photos.isNotEmpty() && !submitting
}

/** Missing-product / correction submission (SPEC Tier 2): photos are queued and uploaded in the background. */
@HiltViewModel
class ContributeViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val contributions: ContributionRepository,
    config: ConfigRepository
) : ViewModel() {
    private val _state = MutableStateFlow(
        ContributeUiState(
            gtin14 = checkNotNull(savedState[ARG_GTIN14]),
            correction = savedState[ARG_CORRECTION] ?: false,
            enabled = config.state.value.contributionsEnabled
        )
    )
    val state: StateFlow<ContributeUiState> = _state.asStateFlow()

    fun onName(value: String) = _state.update { it.copy(productName = value.take(MAX_NAME)) }

    fun onBrand(value: String) = _state.update { it.copy(brandName = value.take(MAX_BRAND)) }

    fun onNotes(value: String) = _state.update { it.copy(notes = value.take(MAX_NOTES)) }

    fun onPhoto(kind: PhotoKind, uri: Uri) =
        _state.update { it.copy(photos = it.photos + (kind to PhotoSlot(uri)), error = SubmitError.None) }

    fun rotate(kind: PhotoKind) = _state.update { s ->
        val slot = s.photos[kind] ?: return@update s
        s.copy(photos = s.photos + (kind to slot.copy(rotation = (slot.rotation + QUARTER_TURN) % FULL_TURN)))
    }

    fun remove(kind: PhotoKind) = _state.update { it.copy(photos = it.photos - kind) }

    fun submit() {
        val s = _state.value
        if (s.photos.isEmpty()) {
            _state.update { it.copy(error = SubmitError.NeedPhoto) }
            return
        }
        if (s.submitting) return
        _state.update { it.copy(submitting = true, error = SubmitError.None) }
        viewModelScope.launch {
            val draft = ContributionDraft(
                gtin14 = s.gtin14,
                productName = s.productName,
                brandName = s.brandName,
                notes = s.notes,
                photos = s.photos.map { (kind, slot) -> DraftPhoto(slot.uri, kind, slot.rotation) }
            )
            try {
                contributions.submit(draft)
                _state.update { it.copy(submitting = false, submitted = true) }
            } catch (_: IOException) {
                _state.update { it.copy(submitting = false, error = SubmitError.ImageUnreadable) }
            }
        }
    }

    companion object {
        const val ARG_GTIN14 = "gtin14"
        const val ARG_CORRECTION = "correction"
        private const val MAX_NAME = 300
        private const val MAX_BRAND = 200
        private const val MAX_NOTES = 2000
        private const val QUARTER_TURN = 90
        private const val FULL_TURN = 360
    }
}

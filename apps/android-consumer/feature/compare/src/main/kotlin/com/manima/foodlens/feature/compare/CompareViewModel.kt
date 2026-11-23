package com.manima.foodlens.feature.compare

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manima.foodlens.core.data.CatalogRepository
import com.manima.foodlens.core.data.DataError
import com.manima.foodlens.core.data.Outcome
import com.manima.foodlens.core.network.model.CompareDto
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface CompareUiState {
    data object Loading : CompareUiState

    data class Content(val compare: CompareDto) : CompareUiState

    data class Error(val error: DataError) : CompareUiState

    data object NeedMore : CompareUiState
}

@HiltViewModel
class CompareViewModel @Inject constructor(savedState: SavedStateHandle, private val catalog: CatalogRepository) :
    ViewModel() {
    /** Route contract: CompareRoute(ids = "id1,id2"). */
    private val ids: List<String> = checkNotNull(savedState.get<String>(ARG_IDS)).split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .take(CatalogRepository.COMPARE_MAX)

    private val _state = MutableStateFlow<CompareUiState>(CompareUiState.Loading)
    val state: StateFlow<CompareUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        if (ids.size < CatalogRepository.COMPARE_MIN) {
            _state.value = CompareUiState.NeedMore
            return
        }
        viewModelScope.launch {
            _state.value = CompareUiState.Loading
            _state.value = when (val result = catalog.compare(ids)) {
                is Outcome.Ok -> CompareUiState.Content(result.value)
                is Outcome.Err -> CompareUiState.Error(result.error)
            }
        }
    }

    companion object {
        const val ARG_IDS = "ids"
    }
}

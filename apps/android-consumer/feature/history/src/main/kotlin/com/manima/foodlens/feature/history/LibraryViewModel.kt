package com.manima.foodlens.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manima.foodlens.core.data.CatalogRepository
import com.manima.foodlens.core.data.LibraryRepository
import com.manima.foodlens.core.database.FavoriteEntity
import com.manima.foodlens.core.database.HistoryEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LibraryUiState(
    val loading: Boolean = true,
    val recent: List<HistoryEntity> = emptyList(),
    val favorites: List<FavoriteEntity> = emptyList(),
    /** Product ids picked for comparison (2–4). */
    val selected: Set<String> = emptySet()
) {
    val canCompare: Boolean get() = selected.size in CatalogRepository.COMPARE_MIN..CatalogRepository.COMPARE_MAX
}

@HiltViewModel
class LibraryViewModel @Inject constructor(private val library: LibraryRepository) : ViewModel() {
    private val selected = MutableStateFlow<Set<String>>(emptySet())

    val state: StateFlow<LibraryUiState> = combine(library.recent(), library.favorites(), selected) { r, f, s ->
        LibraryUiState(
            loading = false,
            recent = r,
            favorites = f,
            selected = s.intersect(
                f.map {
                    it.productId
                }.toSet()
            )
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LibraryUiState())

    fun toggleSelected(productId: String) = selected.update {
        when {
            productId in it -> it - productId
            it.size >= CatalogRepository.COMPARE_MAX -> it
            else -> it + productId
        }
    }

    fun clearSelection() = selected.update { emptySet() }

    fun removeFavorite(item: FavoriteEntity) {
        viewModelScope.launch {
            library.setFavorite(
                com.manima.foodlens.core.data.FavoriteItem(
                    item.gtin14,
                    item.productId,
                    item.name,
                    item.brandName,
                    item.analysisStatus
                ),
                favorite = false
            )
        }
    }

    fun clearHistory() {
        viewModelScope.launch { library.clearHistory() }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

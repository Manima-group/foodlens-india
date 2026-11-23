package com.manima.foodlens.feature.product

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manima.foodlens.core.data.CatalogRepository
import com.manima.foodlens.core.data.ConnectivityMonitor
import com.manima.foodlens.core.data.DataError
import com.manima.foodlens.core.data.FavoriteItem
import com.manima.foodlens.core.data.LibraryRepository
import com.manima.foodlens.core.data.Outcome
import com.manima.foodlens.core.data.ProductRepository
import com.manima.foodlens.core.data.RefreshResult
import com.manima.foodlens.core.datastore.SettingsStore
import com.manima.foodlens.core.network.model.AlternativesDto
import com.manima.foodlens.core.network.model.ProductDetailDto
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface AlternativesState {
    data object Loading : AlternativesState

    data class Loaded(val value: AlternativesDto) : AlternativesState

    data class Failed(val error: DataError) : AlternativesState
}

/** What the product screen shows; [detail] comes from Room so a cached product works offline (SPEC §10.5). */
data class ProductUiState(
    val gtin14: String,
    val detail: ProductDetailDto? = null,
    val fetchedAt: Long? = null,
    val refreshing: Boolean = true,
    /** The last refresh could not reach the server; cached data is shown with the offline banner. */
    val offline: Boolean = false,
    /** Offline and nothing cached: the barcode was queued for lookup. */
    val queued: Boolean = false,
    val notFound: Boolean = false,
    val canContribute: Boolean = false,
    val error: DataError? = null,
    val perServing: Boolean = false,
    val isFavorite: Boolean = false,
    val alternatives: AlternativesState = AlternativesState.Loading,
    val highlightAllergens: Set<String> = emptySet()
)

@HiltViewModel
class ProductViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val products: ProductRepository,
    private val catalog: CatalogRepository,
    private val library: LibraryRepository,
    settings: SettingsStore,
    connectivity: ConnectivityMonitor
) : ViewModel() {
    /** Route contract: ProductRoute(gtin14) in :app. */
    private val gtin14: String = checkNotNull(savedState[ARG_GTIN14])

    private val _state = MutableStateFlow(ProductUiState(gtin14))
    val state: StateFlow<ProductUiState> = _state.asStateFlow()

    private var alternativesFor: String? = null

    /** The first refresh of a screen counts as a scan/view for local history. */
    private var refreshedOnce = false

    init {
        viewModelScope.launch {
            combine(products.observe(gtin14), library.isFavorite(gtin14), settings.settings) { cached, fav, prefs ->
                Triple(cached, fav, prefs.allergenPrefs)
            }.collect { (cached, fav, allergens) ->
                _state.update {
                    it.copy(
                        detail = cached?.detail ?: it.detail,
                        fetchedAt = cached?.fetchedAt ?: it.fetchedAt,
                        isFavorite = fav,
                        highlightAllergens = allergens
                    )
                }
                cached?.detail?.product?.id?.let(::loadAlternatives)
            }
        }
        viewModelScope.launch {
            // Back online after a failed refresh: refresh automatically.
            connectivity.isOnline.collect { online -> if (online && _state.value.offline) refresh() }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true, error = null) }
            val result = products.refresh(gtin14, fromScan = !refreshedOnce)
            refreshedOnce = true
            _state.update {
                when (result) {
                    RefreshResult.Updated -> it.copy(refreshing = false, offline = false, queued = false)
                    is RefreshResult.Offline -> it.copy(refreshing = false, offline = true, queued = result.queued)
                    is RefreshResult.NotFound -> it.copy(
                        refreshing = false,
                        offline = false,
                        notFound = true,
                        canContribute = result.canContribute
                    )
                    is RefreshResult.Failed -> it.copy(refreshing = false, offline = false, error = result.error)
                }
            }
            if (result == RefreshResult.Updated && _state.value.alternatives is AlternativesState.Failed) {
                alternativesFor = null
                _state.value.detail?.product?.id?.let(::loadAlternatives)
            }
        }
    }

    fun setPerServing(perServing: Boolean) = _state.update { it.copy(perServing = perServing) }

    fun toggleFavorite() {
        val detail = _state.value.detail ?: return
        val favorite = !_state.value.isFavorite
        viewModelScope.launch {
            library.setFavorite(
                FavoriteItem(
                    gtin14 = detail.product.gtin14,
                    productId = detail.product.id,
                    name = detail.product.name,
                    brandName = detail.product.brand.name,
                    analysisStatus = detail.analysisSummary.analysisStatus.wire
                ),
                favorite
            )
        }
    }

    private fun loadAlternatives(productId: String) {
        if (alternativesFor == productId) return
        alternativesFor = productId
        viewModelScope.launch {
            _state.update { it.copy(alternatives = AlternativesState.Loading) }
            val next = when (val result = catalog.alternatives(productId)) {
                is Outcome.Ok -> AlternativesState.Loaded(result.value)
                is Outcome.Err -> AlternativesState.Failed(result.error)
            }
            _state.update { it.copy(alternatives = next) }
        }
    }

    companion object {
        const val ARG_GTIN14 = "gtin14"
    }
}

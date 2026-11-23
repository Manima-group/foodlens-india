package com.manima.foodlens.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manima.foodlens.core.data.CatalogRepository
import com.manima.foodlens.core.data.DataError
import com.manima.foodlens.core.data.Outcome
import com.manima.foodlens.core.network.model.CategoryNodeDto
import com.manima.foodlens.core.network.model.ProductSummaryDto
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val category: String? = null,
    val categories: List<CategoryNodeDto> = emptyList(),
    val results: List<ProductSummaryDto> = emptyList(),
    val nextCursor: String? = null,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: DataError? = null,
    /** True once a search has returned, so an empty list means "no results" rather than "not searched". */
    val searched: Boolean = false
)

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(private val catalog: CatalogRepository) : ViewModel() {
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private val queries = MutableStateFlow("")
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            queries.drop(1).debounce(DEBOUNCE_MS).distinctUntilChanged().collect { search() }
        }
        loadCategories()
        search()
    }

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query.take(MAX_QUERY)) }
        queries.value = query.trim()
    }

    fun onCategory(slug: String?) {
        _state.update { it.copy(category = if (it.category == slug) null else slug) }
        search()
    }

    fun retry() {
        if (_state.value.categories.isEmpty()) loadCategories()
        search()
    }

    fun search() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val s = _state.value
            _state.update {
                when (val result = catalog.search(s.query.trim(), s.category, cursor = null)) {
                    is Outcome.Ok -> it.copy(
                        results = result.value.items,
                        nextCursor = result.value.nextCursor,
                        loading = false,
                        searched = true
                    )
                    is Outcome.Err -> it.copy(loading = false, error = result.error, results = emptyList())
                }
            }
        }
    }

    fun loadMore() {
        val s = _state.value
        val cursor = s.nextCursor ?: return
        if (s.loading || s.loadingMore) return
        viewModelScope.launch {
            _state.update { it.copy(loadingMore = true) }
            val result = catalog.search(s.query.trim(), s.category, cursor)
            _state.update {
                when (result) {
                    is Outcome.Ok -> it.copy(
                        results = it.results + result.value.items,
                        nextCursor = result.value.nextCursor,
                        loadingMore = false
                    )
                    is Outcome.Err -> it.copy(loadingMore = false)
                }
            }
        }
    }

    private fun loadCategories() {
        viewModelScope.launch {
            val result = catalog.categories()
            if (result is Outcome.Ok) {
                _state.update {
                    it.copy(categories = result.value.filter { c -> c.productCount > 0 })
                }
            }
        }
    }

    private companion object {
        const val DEBOUNCE_MS = 350L
        const val MAX_QUERY = 100
    }
}

package com.manima.foodlens.feature.product

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manima.foodlens.core.data.CatalogRepository
import com.manima.foodlens.core.data.DataError
import com.manima.foodlens.core.data.Outcome
import com.manima.foodlens.core.network.model.IngredientCardDto
import com.manima.foodlens.core.network.model.SourcesDto
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** SPEC §10.3 screen state. */
sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>

    data class Content<T>(val value: T) : LoadState<T>

    data class Error(val error: DataError) : LoadState<Nothing>
}

abstract class LoadViewModel<T> : ViewModel() {
    private val _state = MutableStateFlow<LoadState<T>>(LoadState.Loading)
    val state: StateFlow<LoadState<T>> = _state.asStateFlow()

    protected abstract suspend fun fetch(): Outcome<T>

    fun load() {
        viewModelScope.launch {
            _state.value = LoadState.Loading
            _state.value = when (val result = fetch()) {
                is Outcome.Ok -> LoadState.Content(result.value)
                is Outcome.Err -> LoadState.Error(result.error)
            }
        }
    }
}

/** Ingredient card: name, aliases, function, INS code, reviewed references (SPEC §10.9 item 4). */
@HiltViewModel
class IngredientViewModel @Inject constructor(savedState: SavedStateHandle, private val catalog: CatalogRepository) :
    LoadViewModel<IngredientCardDto>() {
    private val id: String = checkNotNull(savedState[ARG_ID])

    init {
        load()
    }

    override suspend fun fetch() = catalog.ingredient(id)

    companion object {
        const val ARG_ID = "ingredientId"
    }
}

/** Sources & attribution (T9). */
@HiltViewModel
class SourcesViewModel @Inject constructor(savedState: SavedStateHandle, private val catalog: CatalogRepository) :
    LoadViewModel<SourcesDto>() {
    private val productId: String = checkNotNull(savedState[ARG_PRODUCT_ID])

    init {
        load()
    }

    override suspend fun fetch() = catalog.sources(productId)

    companion object {
        const val ARG_PRODUCT_ID = "productId"
    }
}

package com.manima.foodlens.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.manima.foodlens.core.ui.EmptyState
import com.manima.foodlens.core.ui.ErrorState
import com.manima.foodlens.core.ui.LoadingState
import com.manima.foodlens.core.ui.ProductRow
import com.manima.foodlens.core.ui.isHindi

@Composable
fun SearchRoute(onProduct: (gtin14: String) -> Unit, viewModel: SearchViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SearchScreen(
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onCategory = viewModel::onCategory,
        onRetry = viewModel::retry,
        onLoadMore = viewModel::loadMore,
        onProduct = onProduct
    )
}

@Composable
fun SearchScreen(
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onCategory: (String?) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onProduct: (String) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.search_clear))
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("search_field")
        )
        if (state.categories.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.categories, key = { it.id }) { c ->
                    val name = if (isHindi()) c.nameHi ?: c.name else c.name
                    FilterChip(
                        selected = state.category == c.slug,
                        onClick = { onCategory(c.slug) },
                        label = { Text("$name (${c.productCount})") }
                    )
                }
            }
        }
        if (state.loading && state.results.isNotEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth())
        when {
            state.loading && state.results.isEmpty() -> LoadingState()
            state.error != null -> ErrorState(state.error, onRetry)
            state.searched && state.results.isEmpty() -> EmptyState(
                title = stringResource(R.string.search_empty_title),
                body = stringResource(R.string.search_empty_body)
            )
            else -> Results(state, onLoadMore, onProduct)
        }
    }
}

@Composable
private fun Results(state: SearchUiState, onLoadMore: () -> Unit, onProduct: (String) -> Unit) {
    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - LOAD_MORE_AHEAD
        }
    }
    LaunchedEffect(nearEnd, state.nextCursor) { if (nearEnd && state.nextCursor != null) onLoadMore() }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize().testTag("search_results")
    ) {
        items(state.results, key = { it.id }) { p ->
            ProductRow(
                name = p.name,
                brand = p.brand.name,
                status = p.analysisStatus,
                supporting = p.category?.name,
                onClick = { onProduct(p.gtin14) }
            )
        }
        if (state.loadingMore) {
            item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                }
            }
        }
        if (state.results.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.search_order_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private const val LOAD_MORE_AHEAD = 4

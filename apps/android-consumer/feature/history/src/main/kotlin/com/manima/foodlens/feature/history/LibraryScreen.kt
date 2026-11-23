package com.manima.foodlens.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.manima.foodlens.core.model.AnalysisStatus
import com.manima.foodlens.core.ui.EmptyState
import com.manima.foodlens.core.ui.LoadingState
import com.manima.foodlens.core.ui.ProductRow
import java.text.DateFormat
import java.util.Date

@Composable
fun LibraryRoute(
    onProduct: (gtin14: String) -> Unit,
    onCompare: (productIds: List<String>) -> Unit,
    onScan: () -> Unit,
    initialTab: Int = 0,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.library_recent)) })
            Tab(
                selected = tab == 1,
                onClick = { tab = 1 },
                text = { Text(stringResource(R.string.library_favorites)) },
                modifier = Modifier.testTag("tab_favorites")
            )
        }
        when {
            state.loading -> LoadingState()
            tab == 0 -> RecentList(state, onProduct, onScan, onClear = { confirmClear = true })
            else -> FavoriteList(
                state = state,
                onProduct = onProduct,
                onToggle = viewModel::toggleSelected,
                onRemove = viewModel::removeFavorite,
                onCompare = {
                    onCompare(state.selected.toList())
                    viewModel.clearSelection()
                }
            )
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.library_clear_title)) },
            text = { Text(stringResource(R.string.library_clear_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearHistory()
                    confirmClear = false
                }) { Text(stringResource(R.string.library_clear_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun RecentList(state: LibraryUiState, onProduct: (String) -> Unit, onScan: () -> Unit, onClear: () -> Unit) {
    if (state.recent.isEmpty()) {
        EmptyState(
            title = stringResource(R.string.library_recent_empty_title),
            body = stringResource(R.string.library_recent_empty_body),
            action = stringResource(R.string.library_scan) to onScan
        )
        return
    }
    val format = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize().testTag("recent_list")
    ) {
        item {
            TextButton(onClick = onClear) { Text(stringResource(R.string.library_clear_history)) }
        }
        items(state.recent, key = { it.id }) { h ->
            ProductRow(
                name =
                h.name ?: stringResource(
                    if (h.found) R.string.library_unknown_name else R.string.library_not_found
                ),
                brand = h.brandName ?: h.gtin14,
                status = h.analysisStatus?.let { AnalysisStatus.fromWire(it) },
                supporting = format.format(Date(h.scannedAt)),
                onClick = { onProduct(h.gtin14) }
            )
        }
        item {
            Text(
                stringResource(R.string.library_local_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FavoriteList(
    state: LibraryUiState,
    onProduct: (String) -> Unit,
    onToggle: (String) -> Unit,
    onRemove: (com.manima.foodlens.core.database.FavoriteEntity) -> Unit,
    onCompare: () -> Unit
) {
    if (state.favorites.isEmpty()) {
        EmptyState(
            title = stringResource(R.string.library_favorites_empty_title),
            body = stringResource(R.string.library_favorites_empty_body)
        )
        return
    }
    Column(Modifier.fillMaxSize()) {
        Text(
            stringResource(R.string.library_compare_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f).testTag("favorite_list")
        ) {
            items(state.favorites, key = { it.gtin14 }) { f ->
                ProductRow(
                    name = f.name,
                    brand = f.brandName,
                    status = AnalysisStatus.fromWire(f.analysisStatus),
                    onClick = { onProduct(f.gtin14) },
                    trailing = {
                        Checkbox(checked = f.productId in state.selected, onCheckedChange = { onToggle(f.productId) })
                        IconButton(onClick = { onRemove(f) }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.library_remove_favorite, f.name)
                            )
                        }
                    }
                )
            }
        }
        Button(
            onClick = onCompare,
            enabled = state.canCompare,
            modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(min = 48.dp).testTag("compare_selected")
        ) {
            Text(stringResource(R.string.library_compare_selected, state.selected.size))
        }
    }
}

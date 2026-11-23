package com.manima.foodlens.feature.product

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.manima.foodlens.core.ui.AaharTopBar
import com.manima.foodlens.core.ui.ErrorState
import com.manima.foodlens.core.ui.LoadingState
import com.manima.foodlens.core.ui.MessageState
import com.manima.foodlens.core.ui.OfflineBanner

@Composable
fun ProductRoute(actions: ProductActions, viewModel: ProductViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ProductScreen(
        state = state,
        actions = actions,
        onRetry = viewModel::refresh,
        onPerServing = viewModel::setPerServing,
        onToggleFavorite = viewModel::toggleFavorite
    )
}

@Composable
fun ProductScreen(
    state: ProductUiState,
    actions: ProductActions,
    onRetry: () -> Unit,
    onPerServing: (Boolean) -> Unit,
    onToggleFavorite: () -> Unit
) {
    val detail = state.detail
    Column(Modifier.fillMaxSize()) {
        AaharTopBar(
            title = detail?.product?.name ?: stringResource(R.string.product_title),
            onBack = actions.onBack,
            actions = {
                if (detail != null) {
                    IconButton(onClick = onRetry) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.product_refresh))
                    }
                    IconButton(onClick = onToggleFavorite, modifier = Modifier.testTag("favorite_toggle")) {
                        Icon(
                            if (state.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = stringResource(
                                if (state.isFavorite) R.string.product_unfavorite else R.string.product_favorite
                            )
                        )
                    }
                }
            }
        )
        if (state.offline && detail != null) OfflineBanner()
        if (state.refreshing && detail != null) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("refreshing"))
        when {
            detail != null -> ProductContent(state, detail, actions, onPerServing)
            state.refreshing -> LoadingState(label = stringResource(R.string.product_loading))
            state.notFound -> MessageState(
                title = stringResource(R.string.product_not_found_title),
                body = stringResource(
                    if (state.canContribute) R.string.product_not_found_body else R.string.product_not_found_body_closed
                ),
                primaryAction = if (state.canContribute) {
                    stringResource(R.string.product_add) to { actions.onContribute(state.gtin14, false) }
                } else {
                    null
                },
                secondaryAction = stringResource(R.string.product_scan_another) to actions.onScanAnother,
                modifier = Modifier.testTag("not_found_state")
            )
            state.queued -> MessageState(
                title = stringResource(R.string.product_queued_title),
                body = stringResource(R.string.product_queued_body, state.gtin14),
                primaryAction = stringResource(R.string.product_scan_another) to actions.onScanAnother,
                modifier = Modifier.testTag("queued_state")
            )
            state.error != null -> ErrorState(state.error, onRetry = onRetry)
        }
    }
}

@Composable
private fun ProductContent(
    state: ProductUiState,
    detail: com.manima.foodlens.core.network.model.ProductDetailDto,
    actions: ProductActions,
    onPerServing: (Boolean) -> Unit
) {
    // Order is the SPEC §10.9 trust hierarchy; do not reorder.
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize().testTag("product_content")
    ) {
        item(key = "identity") { IdentitySection(detail, state.gtin14) }
        item(key = "status") { StatusSection(detail) }
        item(key = "nutrition") { NutritionSection(detail, state.perServing, onPerServing) }
        item(key = "ingredients") { IngredientsSection(detail.ingredients, actions.onIngredient) }
        item(key = "allergens") { AllergenSection(detail, state.highlightAllergens) }
        item(key = "lab") { LabSection(detail.labInformation) }
        item(key = "alternatives") {
            AlternativesSection(state.alternatives, detail.product.id, actions.onProduct, actions.onCompare)
        }
        item(key = "sponsored") { SponsoredSection(detail, state.alternatives) }
        item(key = "sources") {
            SourcesSection(
                onSources = { actions.onSources(detail.product.id) },
                onReport = { actions.onContribute(state.gtin14, true) },
                fetchedAt = state.fetchedAt
            )
        }
    }
}

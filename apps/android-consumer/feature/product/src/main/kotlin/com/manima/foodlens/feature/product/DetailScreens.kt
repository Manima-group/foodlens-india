package com.manima.foodlens.feature.product

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.manima.foodlens.core.designsystem.EvidenceChip
import com.manima.foodlens.core.model.EvidenceLevel
import com.manima.foodlens.core.network.model.IngredientCardDto
import com.manima.foodlens.core.network.model.SourcesDto
import com.manima.foodlens.core.ui.AaharTopBar
import com.manima.foodlens.core.ui.ErrorState
import com.manima.foodlens.core.ui.LoadingState

@Composable
private fun <T> LoadScaffold(
    title: String,
    state: LoadState<T>,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    content: @Composable (T) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        AaharTopBar(title = title, onBack = onBack)
        when (state) {
            LoadState.Loading -> LoadingState()
            is LoadState.Error -> ErrorState(state.error, onRetry)
            is LoadState.Content -> content(state.value)
        }
    }
}

@Composable
fun IngredientRoute(onBack: () -> Unit, viewModel: IngredientViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LoadScaffold(stringResource(R.string.ingredient_title), state, onBack, viewModel::load) { IngredientCard(it) }
}

@Composable
fun IngredientCard(card: IngredientCardDto) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize().testTag("ingredient_card")
    ) {
        item { Text(card.name, style = MaterialTheme.typography.headlineSmall) }
        if (card.isAdditive) {
            item {
                Text(
                    listOfNotNull(
                        stringResource(R.string.ingredient_additive),
                        card.insCode?.let { "INS $it" },
                        card.function
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
        card.description?.let { item { Text(it, style = MaterialTheme.typography.bodyLarge) } }
        val aliases = card.aliases.mapNotNull { it["alias"] }.distinct()
        if (aliases.isNotEmpty()) {
            item { SectionCard(stringResource(R.string.ingredient_aliases)) { Text(aliases.joinToString(", ")) } }
        }
        item {
            SectionCard(stringResource(R.string.ingredient_references)) {
                if (card.references.isEmpty()) {
                    Text(card.referencesNote, style = MaterialTheme.typography.bodyMedium)
                }
                card.references.forEach { ref ->
                    Text(
                        listOfNotNull(ref["title"], ref["publisher"], ref["year"], ref["url"]).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
fun SourcesRoute(onBack: () -> Unit, viewModel: SourcesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LoadScaffold(stringResource(R.string.sources_title), state, onBack, viewModel::load) { SourcesList(it) }
}

@Composable
fun SourcesList(sources: SourcesDto) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize().testTag("sources_list")
    ) {
        item { Text(stringResource(R.string.sources_intro), style = MaterialTheme.typography.bodyLarge) }
        sources.sources.groupBy { it.source to it.licenceCode }.forEach { (key, fields) ->
            item {
                SectionCard(stringResource(R.string.sources_source, key.first, key.second)) {
                    fields.forEach { f ->
                        Text(
                            stringResource(R.string.sources_field, f.fieldName, f.observedAt.take(OBSERVED_DATE_CHARS)),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    fields.firstNotNullOfOrNull { EvidenceLevel.fromWire(it.evidenceLevel) }?.let { EvidenceChip(it) }
                }
            }
        }
        if (sources.attributions.isNotEmpty()) {
            item {
                SectionCard(stringResource(R.string.sources_attribution)) {
                    sources.attributions.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
    }
}

private const val OBSERVED_DATE_CHARS = 10

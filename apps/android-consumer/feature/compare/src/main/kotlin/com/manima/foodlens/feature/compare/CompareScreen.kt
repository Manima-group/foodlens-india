package com.manima.foodlens.feature.compare

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.manima.foodlens.core.designsystem.glyph
import com.manima.foodlens.core.designsystem.labelRes
import com.manima.foodlens.core.network.model.CompareDto
import com.manima.foodlens.core.ui.AaharTopBar
import com.manima.foodlens.core.ui.EmptyState
import com.manima.foodlens.core.ui.ErrorState
import com.manima.foodlens.core.ui.LoadingState
import com.manima.foodlens.core.ui.Nutrients
import com.manima.foodlens.core.ui.nutrientName

@Composable
fun CompareRoute(onBack: () -> Unit, onProduct: (String) -> Unit, viewModel: CompareViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        AaharTopBar(stringResource(R.string.compare_title), onBack = onBack)
        when (val s = state) {
            CompareUiState.Loading -> LoadingState()
            CompareUiState.NeedMore -> EmptyState(
                title = stringResource(R.string.compare_need_more_title),
                body = stringResource(R.string.compare_need_more_body)
            )
            is CompareUiState.Error -> ErrorState(s.error, viewModel::load)
            is CompareUiState.Content -> CompareTable(s.compare, onProduct)
        }
    }
}

/** Side-by-side per 100 g/ml values. Missing values say so; they are never shown as 0 (T4). */
@Composable
fun CompareTable(compare: CompareDto, onProduct: (String) -> Unit) {
    val columnWidth = 132.dp
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("compare_table"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            Column {
                Row {
                    Text("", modifier = Modifier.width(columnWidth))
                    compare.items.forEach { item ->
                        Column(Modifier.width(columnWidth).padding(end = 8.dp)) {
                            TextButton(onClick = {
                                onProduct(item.product.gtin14)
                            }, modifier = Modifier.heightIn(min = 48.dp)) {
                                Text(
                                    item.product.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(item.product.brand.name, style = MaterialTheme.typography.bodySmall)
                            Text(
                                "${item.product.analysisStatus.glyph()} ${stringResource(
                                    item.product.analysisStatus.labelRes()
                                )}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                stringResource(basisRes(item.basis)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                compare.nutrients.forEach { key ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 40.dp)) {
                        Text(
                            nutrientName(key),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.width(columnWidth)
                        )
                        compare.items.forEach { item ->
                            val v = item.per100.firstOrNull { it.nutrient == key }
                            Text(
                                v?.let { "${Nutrients.format(it.value)} ${it.unit}" }
                                    ?: stringResource(R.string.compare_not_declared),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (v != null) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (v !=
                                    null
                                ) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.width(columnWidth)
                            )
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
        Text(
            compare.note,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            stringResource(R.string.compare_footer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun basisRes(basis: String) = when (basis) {
    "per_100ml" -> R.string.compare_per_100ml
    else -> R.string.compare_per_100g
}

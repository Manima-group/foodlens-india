package com.manima.foodlens.feature.product

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.manima.foodlens.core.designsystem.FoodLensTokens
import com.manima.foodlens.core.designsystem.SponsoredCard
import com.manima.foodlens.core.network.model.ProductDetailDto
import com.manima.foodlens.core.ui.ProductRow
import com.manima.foodlens.core.ui.messageRes
import java.text.DateFormat
import java.util.Date
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

// 7. Alternatives (organic only) ------------------------------------------------------------------

@Composable
internal fun AlternativesSection(
    state: AlternativesState,
    productId: String,
    onProduct: (String) -> Unit,
    onCompare: (List<String>) -> Unit
) {
    SectionCard(stringResource(R.string.section_alternatives), Modifier.testTag("section_alternatives")) {
        when (state) {
            AlternativesState.Loading -> Muted(stringResource(R.string.alternatives_loading))
            is AlternativesState.Failed -> Muted(stringResource(state.error.messageRes()))
            is AlternativesState.Loaded -> {
                val dto = state.value
                if (dto.items.isEmpty()) {
                    Muted(
                        stringResource(
                            if (dto.suppressedReason == "insufficient_data") {
                                R.string.alternatives_insufficient
                            } else {
                                R.string.alternatives_none
                            }
                        )
                    )
                }
                dto.items.forEach { alt ->
                    ProductRow(
                        name = alt.name,
                        brand = alt.brand.name,
                        status = alt.analysisStatus,
                        onClick = { onProduct(alt.gtin14) },
                        trailing = {
                            OutlinedButton(onClick = { onCompare(listOf(productId, alt.id)) }) {
                                Text(stringResource(R.string.alternatives_compare))
                            }
                        }
                    )
                }
                var showBasis by rememberSaveable { mutableStateOf(false) }
                TextButton(onClick = { showBasis = !showBasis }) { Text(stringResource(R.string.alternatives_how)) }
                if (showBasis) Muted(dto.rankingBasis)
            }
        }
    }
}

// 8. Sponsored (separate; SponsoredCard only, T2) ------------------------------------------------

@Composable
internal fun SponsoredSection(detail: ProductDetailDto, alternatives: AlternativesState) {
    val placements = detail.sponsored + ((alternatives as? AlternativesState.Loaded)?.value?.sponsored ?: emptyList())
    if (placements.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("section_sponsored")) {
        placements.forEach { p ->
            SponsoredCard(title = p.text("title"), reason = p.text("reason"), onClick = {})
        }
    }
}

private fun JsonObject.text(key: String): String = this[key]?.jsonPrimitive?.content.orEmpty()

// 9. Sources, report a problem --------------------------------------------------------------------

@Composable
internal fun SourcesSection(onSources: () -> Unit, onReport: () -> Unit, fetchedAt: Long?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("section_sources")) {
        OutlinedButton(onClick = onSources, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.sources_button))
        }
        OutlinedButton(onClick = onReport, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.report_problem))
        }
        fetchedAt?.let {
            Muted(stringResource(R.string.product_updated_at, DateFormat.getDateTimeInstance().format(Date(it))))
        }
        Text(
            stringResource(R.string.product_trust_footer),
            style = MaterialTheme.typography.bodySmall,
            color = FoodLensTokens.Grey,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

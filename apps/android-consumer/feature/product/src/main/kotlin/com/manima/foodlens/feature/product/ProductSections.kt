package com.manima.foodlens.feature.product

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.manima.foodlens.core.designsystem.AnalysisStatusBanner
import com.manima.foodlens.core.designsystem.EvidenceChip
import com.manima.foodlens.core.designsystem.IndicatorChip
import com.manima.foodlens.core.designsystem.Level
import com.manima.foodlens.core.model.DataFreshness
import com.manima.foodlens.core.model.EvidenceLevel
import com.manima.foodlens.core.network.model.AllergenDeclarationDto
import com.manima.foodlens.core.network.model.IndicatorLevel
import com.manima.foodlens.core.network.model.IngredientsDto
import com.manima.foodlens.core.network.model.LabInformationDto
import com.manima.foodlens.core.network.model.NutrientValueDto
import com.manima.foodlens.core.network.model.ProductDetailDto
import com.manima.foodlens.core.ui.Nutrients
import com.manima.foodlens.core.ui.isHindi
import com.manima.foodlens.core.ui.nutrientName

@Composable
internal fun SectionCard(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            content()
        }
    }
}

@Composable
internal fun Muted(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

private fun String.toEvidence(): EvidenceLevel? = EvidenceLevel.fromWire(this)

// 1. Identity --------------------------------------------------------------------------------------

@Composable
internal fun IdentitySection(detail: ProductDetailDto, gtin14: String) {
    val product = detail.product
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth().testTag("section_identity")) {
        val image = product.images.firstOrNull { it.kind == "front" } ?: product.images.firstOrNull()
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.size(88.dp)
        ) {
            if (image != null) {
                AsyncImage(
                    model = image.url,
                    contentDescription = stringResource(R.string.product_image, product.name),
                    contentScale = ContentScale.Crop
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(
                        product.name.take(1).uppercase(),
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(top = 18.dp)
                    )
                }
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(product.name, style = MaterialTheme.typography.headlineSmall)
            val brand = if (product.brand.isVerified) {
                stringResource(R.string.product_brand_verified, product.brand.name)
            } else {
                product.brand.name
            }
            Text(
                brand,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            product.variant?.let { v ->
                val qty =
                    listOfNotNull(
                        v.netQuantity?.let {
                            Nutrients.format(it) + (v.unit?.let { u -> " $u" } ?: "")
                        },
                        v.packFormat
                    )
                if (qty.isNotEmpty()) Muted(qty.joinToString(" · "))
            }
            product.category?.let { Muted(it.name) }
            Muted(stringResource(R.string.product_gtin, gtin14), Modifier.testTag("product_gtin"))
            Muted(stringResource(freshnessRes(product.dataFreshness)))
            product.labelVersion?.let { label ->
                EvidenceChip(label.evidenceLevel)
                Muted(stringResource(R.string.product_label_observed, label.observedAt.take(DATE_CHARS)))
            }
        }
    }
}

private const val DATE_CHARS = 10

private fun freshnessRes(f: DataFreshness) = when (f) {
    DataFreshness.FRESH -> R.string.freshness_fresh
    DataFreshness.STALE -> R.string.freshness_stale
    DataFreshness.UNKNOWN -> R.string.freshness_unknown
}

// 2. Analysis status -----------------------------------------------------------------------------

@Composable
internal fun StatusSection(detail: ProductDetailDto) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("section_status")) {
        AnalysisStatusBanner(detail.analysisSummary.analysisStatus)
        // Scores are never computed on the device; we only show what the API returns (T4, T8).
        detail.limitations.forEach { Muted("• $it") }
        val missing = detail.product.completeness.missingFields
        if (missing.isNotEmpty()) {
            Muted(
                stringResource(
                    R.string.product_missing_fields,
                    missing.map {
                        nutrientNameOrKey(it)
                    }.joinToString(", ")
                )
            )
        }
    }
}

@Composable
private fun nutrientNameOrKey(key: String): String = nutrientName(key)

// 3. Nutrition --------------------------------------------------------------------------------------

@Composable
internal fun NutritionSection(detail: ProductDetailDto, perServing: Boolean, onPerServing: (Boolean) -> Unit) {
    val nutrition = detail.nutrition
    SectionCard(stringResource(R.string.section_nutrition), Modifier.testTag("section_nutrition")) {
        if (nutrition.per100.isEmpty()) {
            Muted(stringResource(R.string.nutrition_not_available))
            return@SectionCard
        }
        val hasServing = nutrition.perServing.isNotEmpty()
        val per100Label = stringResource(
            if (nutrition.basis == "per_100ml") R.string.nutrition_per_100ml else R.string.nutrition_per_100g
        )
        if (hasServing) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !perServing,
                    onClick = { onPerServing(false) },
                    shape = SegmentedButtonDefaults.itemShape(0, 2)
                ) { Text(per100Label) }
                SegmentedButton(
                    selected = perServing,
                    onClick = { onPerServing(true) },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                    modifier = Modifier.testTag("per_serving_toggle")
                ) { Text(stringResource(R.string.nutrition_per_serving)) }
            }
        } else {
            Muted(per100Label)
        }
        nutrition.serving?.let { s ->
            val size = Nutrients.format(s.size) + " " + s.unit
            val text =
                s.servingsPerPack?.let { stringResource(R.string.nutrition_serving_pack, size, Nutrients.format(it)) }
                    ?: stringResource(R.string.nutrition_serving, size)
            Muted(text)
        }
        val rows = if (perServing && hasServing) nutrition.perServing else nutrition.per100
        rows.forEach { NutrientRow(it) }
        if (nutrition.indicators.isNotEmpty()) {
            HorizontalDivider()
            Text(stringResource(R.string.nutrition_indicators), style = MaterialTheme.typography.titleMedium)
            IndicatorRow(nutrition.indicators.map { nutrientName(it.nutrient) to it.level.toUi() })
            if (nutrition.thresholdsArePlaceholder || nutrition.indicators.any { it.isPlaceholderThreshold }) {
                Muted(
                    stringResource(com.manima.foodlens.core.designsystem.R.string.placeholder_thresholds_note),
                    Modifier.testTag("placeholder_note")
                )
            }
        }
        nutrition.warnings.forEach { Muted("⚠ $it") }
        nutrition.evidenceLevel?.toEvidence()?.let { EvidenceChip(it) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IndicatorRow(items: List<Pair<String, Level>>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (name, level) -> IndicatorChip(name, level) }
    }
}

internal fun IndicatorLevel.toUi(): Level = when (this) {
    IndicatorLevel.LOW -> Level.LOW
    IndicatorLevel.MEDIUM -> Level.MEDIUM
    IndicatorLevel.HIGH -> Level.HIGH
}

@Composable
private fun NutrientRow(value: NutrientValueDto) {
    Row(Modifier.fillMaxWidth().heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(nutrientName(value.nutrient), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            "${Nutrients.format(value.value)} ${value.unit}",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// 4. Ingredients -----------------------------------------------------------------------------------

@Composable
internal fun IngredientsSection(ingredients: IngredientsDto, onIngredient: (String) -> Unit) {
    SectionCard(stringResource(R.string.section_ingredients), Modifier.testTag("section_ingredients")) {
        if (ingredients.items.isEmpty()) {
            Muted(stringResource(R.string.ingredients_not_available))
            return@SectionCard
        }
        if (!ingredients.isComplete) Muted(stringResource(R.string.ingredients_incomplete))
        ingredients.items.sortedBy { it.position }.forEachIndexed { index, item ->
            val label = buildString {
                append("${index + 1}. ")
                append(item.ingredientName ?: item.rawText)
                if (item.isAdditive && item.insCode != null) append(" (INS ${item.insCode})")
            }
            val ingredientId = item.ingredientId
            if (ingredientId != null) {
                TextButton(
                    onClick = { onIngredient(ingredientId) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Text("›", style = MaterialTheme.typography.titleLarge)
                }
            } else {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                    Muted(stringResource(R.string.ingredient_unmatched))
                }
            }
        }
        val processing = ingredients.processing
        if (processing != null) {
            val ingredientCount = processing["ingredient_count"] ?: ingredients.items.size
            val additiveCount = processing["additive_count"] ?: 0
            Muted(
                pluralStringResource(R.plurals.ingredients_count, ingredientCount, ingredientCount) + " · " +
                    pluralStringResource(R.plurals.additives_count, additiveCount, additiveCount)
            )
        } else if (ingredients.processingReason != null) {
            Muted(stringResource(R.string.ingredients_processing_unavailable))
        }
        ingredients.evidenceLevel?.toEvidence()?.let { EvidenceChip(it) }
    }
}

// 5. Allergens and dietary declarations (T7: declared on label, never guaranteed) ----------------

@Composable
internal fun AllergenSection(detail: ProductDetailDto, highlight: Set<String>) {
    SectionCard(stringResource(R.string.section_allergens), Modifier.testTag("section_allergens")) {
        if (detail.allergens.isEmpty()) {
            Muted(stringResource(R.string.allergens_none_declared))
        }
        detail.allergens.forEach { AllergenRow(it, it.code in highlight) }
        detail.dietaryDeclarations.forEach { code ->
            Text(
                stringResource(R.string.dietary_declared, dietaryName(code)),
                style = MaterialTheme.typography.bodyLarge
            )
        }
        Muted(stringResource(R.string.allergens_disclaimer))
    }
}

@Composable
private fun AllergenRow(allergen: AllergenDeclarationDto, highlighted: Boolean) {
    val name = if (isHindi()) allergen.nameHi ?: allergen.name else allergen.name
    val text = stringResource(
        if (allergen.kind == "may_contain") R.string.allergen_may_contain else R.string.allergen_contains,
        name
    )
    Surface(
        color = if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(8.dp)) {
            Text(text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            if (highlighted) {
                Text(
                    stringResource(R.string.allergen_highlighted),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            allergen.evidenceLevel.toEvidence()?.let { EvidenceChip(it) }
        }
    }
}

@Composable
private fun dietaryName(code: String): String = when (code) {
    "veg_mark" -> stringResource(R.string.dietary_veg_mark)
    "non_veg_mark" -> stringResource(R.string.dietary_non_veg_mark)
    else -> code.replace('_', ' ')
}

// 6. Lab information (T5) -------------------------------------------------------------------------

@Composable
internal fun LabSection(lab: LabInformationDto) {
    SectionCard(stringResource(R.string.section_lab), Modifier.testTag("section_lab")) {
        // Only a verified, dated, batch-linked report may be shown; anything else gets the fixed text.
        val text = if (lab.status == "verified") {
            lab.message
        } else {
            stringResource(com.manima.foodlens.core.designsystem.R.string.no_verified_lab_info)
        }
        Muted(text)
    }
}

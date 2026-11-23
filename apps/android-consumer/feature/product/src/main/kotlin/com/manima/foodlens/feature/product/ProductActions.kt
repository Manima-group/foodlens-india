package com.manima.foodlens.feature.product

/** Navigation callbacks; route contracts live in :app (SPEC §10.2). */
data class ProductActions(
    val onBack: () -> Unit,
    val onIngredient: (ingredientId: String) -> Unit,
    val onProduct: (gtin14: String) -> Unit,
    val onCompare: (productIds: List<String>) -> Unit,
    val onSources: (productId: String) -> Unit,
    val onContribute: (gtin14: String, correction: Boolean) -> Unit,
    val onScanAnother: () -> Unit
)

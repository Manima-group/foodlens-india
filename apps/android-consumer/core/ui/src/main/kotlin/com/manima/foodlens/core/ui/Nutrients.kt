package com.manima.foodlens.core.ui

import androidx.annotation.StringRes
import java.text.NumberFormat
import java.util.Locale

/** API nutrient keys (SPEC §9.1) to display names; unknown keys fall back to a readable form of the key. */
object Nutrients {
    @StringRes
    fun labelRes(key: String): Int? = when (key) {
        "energy_kcal" -> R.string.nutrient_energy
        "energy_kj" -> R.string.nutrient_energy_kj
        "fat_g" -> R.string.nutrient_fat
        "saturated_fat_g" -> R.string.nutrient_saturated_fat
        "trans_fat_g" -> R.string.nutrient_trans_fat
        "carbohydrate_g" -> R.string.nutrient_carbohydrate
        "sugars_g" -> R.string.nutrient_sugars
        "added_sugars_g" -> R.string.nutrient_added_sugars
        "fibre_g" -> R.string.nutrient_fibre
        "protein_g" -> R.string.nutrient_protein
        "sodium_mg" -> R.string.nutrient_sodium
        "salt_g" -> R.string.nutrient_salt
        "cholesterol_mg" -> R.string.nutrient_cholesterol
        else -> null
    }

    fun fallbackLabel(key: String): String = key.substringBeforeLast('_').replace('_', ' ')
        .replaceFirstChar { it.titlecase(Locale.ROOT) }

    /** Up to 1 decimal place, no trailing ".0", locale digits grouping. */
    fun format(value: Double, locale: Locale = Locale.getDefault()): String =
        NumberFormat.getNumberInstance(locale).apply {
            maximumFractionDigits = 1
            minimumFractionDigits = 0
        }.format(value)
}

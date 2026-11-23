package com.manima.foodlens.core.designsystem

import androidx.compose.ui.graphics.Color

/**
 * Colour tokens. Will be generated from packages/ui-tokens/tokens.json (SPEC §4) once that package exists;
 * until then these literals are the single source for Android.
 */
object FoodLensTokens {
    val Primary = Color(0xFF1B5E20)
    val OnPrimary = Color(0xFFFFFFFF)
    val PrimaryDark = Color(0xFF81C784)
    val OnPrimaryDark = Color(0xFF003300)
    val Sponsored = Color(0xFF6A4C00)
    val SponsoredContainer = Color(0xFFFFF3C4)
}

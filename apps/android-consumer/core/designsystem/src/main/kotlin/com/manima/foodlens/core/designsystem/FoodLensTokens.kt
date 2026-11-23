package com.manima.foodlens.core.designsystem

import androidx.compose.ui.graphics.Color

/**
 * AAHAR colour tokens (ADR 0006): primary orange #F97316, white #FFFFFF, grey #6B7280.
 * Orange is used for fills with white bold text; body text stays dark or grey for contrast.
 */
object FoodLensTokens {
    val Orange = Color(0xFFF97316)
    val White = Color(0xFFFFFFFF)
    val Grey = Color(0xFF6B7280)

    // Supporting shades derived from the brand colours (not new brand colours).
    val OrangeLight = Color(0xFFFFEDD5)
    val OrangeDeep = Color(0xFF9A3412)
    val OrangeSoft = Color(0xFFFB923C)
    val Ink = Color(0xFF1F2937)
    val GreyLight = Color(0xFFF3F4F6)
    val GreyLine = Color(0xFFD1D5DB)
    val Night = Color(0xFF111827)
    val NightSurface = Color(0xFF1F2937)
    val NightLine = Color(0xFF374151)
    val NightText = Color(0xFFF9FAFB)
    val NightMuted = Color(0xFF9CA3AF)
    val Error = Color(0xFFB91C1C)

    val Primary = Orange
    val OnPrimary = White
    val PrimaryDark = OrangeSoft
    val OnPrimaryDark = Night

    // Indicator levels: always paired with a text label, never colour alone (SPEC §10.9).
    val LevelHigh = Color(0xFFB91C1C)
    val LevelMedium = Color(0xFFB45309)
    val LevelLow = Color(0xFF15803D)

    val Sponsored = Color(0xFF6A4C00)
    val SponsoredContainer = Color(0xFFFFF3C4)
}

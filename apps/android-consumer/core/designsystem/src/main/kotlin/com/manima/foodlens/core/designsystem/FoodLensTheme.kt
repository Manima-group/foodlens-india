package com.manima.foodlens.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = FoodLensTokens.Orange,
    onPrimary = FoodLensTokens.White,
    primaryContainer = FoodLensTokens.OrangeLight,
    onPrimaryContainer = FoodLensTokens.OrangeDeep,
    secondary = FoodLensTokens.Grey,
    onSecondary = FoodLensTokens.White,
    secondaryContainer = FoodLensTokens.GreyLight,
    onSecondaryContainer = FoodLensTokens.Ink,
    tertiary = FoodLensTokens.OrangeDeep,
    background = FoodLensTokens.White,
    onBackground = FoodLensTokens.Ink,
    surface = FoodLensTokens.White,
    onSurface = FoodLensTokens.Ink,
    surfaceVariant = FoodLensTokens.GreyLight,
    onSurfaceVariant = FoodLensTokens.Grey,
    surfaceContainerLowest = FoodLensTokens.White,
    surfaceContainerLow = FoodLensTokens.White,
    surfaceContainer = FoodLensTokens.GreyLight,
    surfaceContainerHigh = FoodLensTokens.GreyLight,
    outline = FoodLensTokens.GreyLine,
    outlineVariant = FoodLensTokens.GreyLine,
    error = FoodLensTokens.Error
)

private val DarkColors = darkColorScheme(
    primary = FoodLensTokens.OrangeSoft,
    onPrimary = FoodLensTokens.Night,
    primaryContainer = FoodLensTokens.OrangeDeep,
    onPrimaryContainer = FoodLensTokens.OrangeLight,
    secondary = FoodLensTokens.NightMuted,
    onSecondary = FoodLensTokens.Night,
    secondaryContainer = FoodLensTokens.NightLine,
    onSecondaryContainer = FoodLensTokens.NightText,
    background = FoodLensTokens.Night,
    onBackground = FoodLensTokens.NightText,
    surface = FoodLensTokens.Night,
    onSurface = FoodLensTokens.NightText,
    surfaceVariant = FoodLensTokens.NightSurface,
    onSurfaceVariant = FoodLensTokens.NightMuted,
    surfaceContainer = FoodLensTokens.NightSurface,
    surfaceContainerHigh = FoodLensTokens.NightSurface,
    outline = FoodLensTokens.NightLine,
    outlineVariant = FoodLensTokens.NightLine
)

private val BaseTypography = Typography()

/** Labels on orange fills are bold so white text stays legible (ADR 0006). */
private val AaharTypography = BaseTypography.copy(
    headlineLarge = BaseTypography.headlineLarge.copy(fontWeight = FontWeight.Bold),
    headlineMedium = BaseTypography.headlineMedium.copy(fontWeight = FontWeight.Bold),
    headlineSmall = BaseTypography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = BaseTypography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = BaseTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = BaseTypography.labelLarge.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp)
)

private val AaharShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(20.dp)
)

@Composable
fun FoodLensTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AaharTypography,
        shapes = AaharShapes,
        content = content
    )
}

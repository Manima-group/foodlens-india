package com.manima.foodlens.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(primary = FoodLensTokens.Primary, onPrimary = FoodLensTokens.OnPrimary)
private val DarkColors = darkColorScheme(primary = FoodLensTokens.PrimaryDark, onPrimary = FoodLensTokens.OnPrimaryDark)

@Composable
fun FoodLensTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}

package com.manima.foodlens.core.designsystem

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp

fun Level.color(): Color = when (this) {
    Level.LOW -> FoodLensTokens.LevelLow
    Level.MEDIUM -> FoodLensTokens.LevelMedium
    Level.HIGH -> FoodLensTokens.LevelHigh
}

private fun Level.glyph(): String = when (this) {
    Level.LOW -> "▼"
    Level.MEDIUM -> "●"
    Level.HIGH -> "▲"
}

/** "High fat ▲": a glyph and words, so meaning never depends on colour (SPEC §10.9). */
@Composable
fun IndicatorChip(nutrient: String, level: Level, modifier: Modifier = Modifier) {
    val levelText = stringResource(
        when (level) {
            Level.LOW -> R.string.level_low
            Level.MEDIUM -> R.string.level_medium
            Level.HIGH -> R.string.level_high
        }
    )
    val text = stringResource(R.string.indicator_chip, levelText, nutrient)
    Surface(
        color = level.color().copy(alpha = 0.12f),
        contentColor = level.color(),
        shape = MaterialTheme.shapes.small,
        modifier = modifier.clearAndSetSemantics { contentDescription = text }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(level.glyph() + " " + text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

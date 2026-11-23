@file:Suppress("MagicNumber") // Logo geometry as fractions of the tile size.

package com.manima.foodlens.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Original AAHAR mark: an orange tile with a white scan frame around barcode bars. Decorative only. */
@Composable
fun AaharLogo(modifier: Modifier = Modifier, size: Dp = 72.dp) {
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        drawRoundRect(FoodLensTokens.Orange, cornerRadius = CornerRadius(s * 0.24f), size = Size(s, s))
        val stroke = s * 0.06f
        val inset = s * 0.2f
        val arm = s * 0.16f
        val far = s - inset
        val white = FoodLensTokens.White
        // Scan-frame corners.
        listOf(
            Triple(Offset(inset, inset), Offset(inset + arm, inset), Offset(inset, inset + arm)),
            Triple(Offset(far, inset), Offset(far - arm, inset), Offset(far, inset + arm)),
            Triple(Offset(inset, far), Offset(inset + arm, far), Offset(inset, far - arm)),
            Triple(Offset(far, far), Offset(far - arm, far), Offset(far, far - arm))
        ).forEach { (corner, h, v) ->
            drawLine(white, corner, h, strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(white, corner, v, strokeWidth = stroke, cap = StrokeCap.Round)
        }
        // Barcode bars of varying width.
        val widths = listOf(0.035f, 0.02f, 0.05f, 0.02f, 0.035f, 0.05f, 0.02f)
        var x = s * 0.31f
        val top = s * 0.34f
        val bottom = s * 0.66f
        widths.forEach { w ->
            drawRect(white, topLeft = Offset(x, top), size = Size(s * w, bottom - top))
            x += s * w + s * 0.025f
        }
    }
}

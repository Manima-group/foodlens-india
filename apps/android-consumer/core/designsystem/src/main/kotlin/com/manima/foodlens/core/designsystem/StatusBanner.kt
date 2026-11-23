package com.manima.foodlens.core.designsystem

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.manima.foodlens.core.model.AnalysisStatus

/** Glyph + text for each status: meaning never relies on colour alone (SPEC §10.9 item 2). */
@StringRes
fun AnalysisStatus.labelRes(): Int = when (this) {
    AnalysisStatus.SCORED -> R.string.status_scored
    AnalysisStatus.INSUFFICIENT_DATA -> R.string.status_insufficient_data
    AnalysisStatus.METHODOLOGY_PENDING -> R.string.status_methodology_pending
    AnalysisStatus.UNVERIFIED_PROVISIONAL -> R.string.status_unverified_provisional
    AnalysisStatus.NOT_APPLICABLE -> R.string.status_not_applicable
}

fun AnalysisStatus.glyph(): String = when (this) {
    AnalysisStatus.SCORED -> "✓"
    AnalysisStatus.INSUFFICIENT_DATA -> "!"
    AnalysisStatus.METHODOLOGY_PENDING -> "⏳"
    AnalysisStatus.UNVERIFIED_PROVISIONAL -> "?"
    AnalysisStatus.NOT_APPLICABLE -> "–"
}

@Composable
fun AnalysisStatusBanner(status: AnalysisStatus, modifier: Modifier = Modifier) {
    val label = stringResource(status.labelRes())
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag("analysis_status_banner")
            .semantics(mergeDescendants = true) { contentDescription = label }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
            Text(
                status.glyph(),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.clearAndSetSemantics {
                }
            )
            Spacer(Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

package com.manima.foodlens.core.designsystem

import androidx.annotation.StringRes
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.manima.foodlens.core.model.EvidenceLevel

@StringRes
fun EvidenceLevel.labelRes(): Int = when (this) {
    EvidenceLevel.LABEL_DECLARED -> R.string.evidence_label_declared
    EvidenceLevel.COMMUNITY_SUBMITTED -> R.string.evidence_community_submitted
    EvidenceLevel.MANUFACTURER_SUBMITTED -> R.string.evidence_manufacturer_submitted
    EvidenceLevel.REVIEWER_VERIFIED -> R.string.evidence_reviewer_verified
    EvidenceLevel.LABORATORY_TESTED -> R.string.evidence_laboratory_tested
}

/** Every material fact shows its evidence level (trust rule T6). */
@Composable
fun EvidenceChip(level: EvidenceLevel, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    AssistChip(onClick = onClick, label = { Text(stringResource(level.labelRes())) }, modifier = modifier)
}

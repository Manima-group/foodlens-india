package com.manima.foodlens.core.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * The ONLY component allowed to render campaign content (trust rule T2).
 * Always shows visible "Sponsored" text and the placement reason; the accessibility label starts with "Sponsored".
 */
@Composable
fun SponsoredCard(title: String, reason: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val sponsored = stringResource(R.string.sponsored_label)
    val description = stringResource(R.string.sponsored_content_description, sponsored, title, reason)
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = FoodLensTokens.SponsoredContainer),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag("sponsored_card")
            .semantics(mergeDescendants = true) { contentDescription = description }
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(sponsored, color = FoodLensTokens.Sponsored, style = MaterialTheme.typography.labelLarge)
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(reason, style = MaterialTheme.typography.bodySmall)
        }
    }
}

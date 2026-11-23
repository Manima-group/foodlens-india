package com.manima.foodlens.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.manima.foodlens.core.data.DataError

@Composable
fun LoadingState(modifier: Modifier = Modifier, label: String = stringResource(R.string.loading)) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.fillMaxSize().padding(24.dp).testTag("loading_state")
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.heightIn(min = 12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun MessageState(
    title: String,
    body: String?,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Filled.Info,
    primaryAction: Pair<String, () -> Unit>? = null,
    secondaryAction: Pair<String, () -> Unit>? = null
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        modifier = modifier.fillMaxSize().padding(24.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        if (body != null) {
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        primaryAction?.let { (label, action) ->
            Button(onClick = action, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(label) }
        }
        secondaryAction?.let { (label, action) ->
            OutlinedButton(onClick = action, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(label) }
        }
    }
}

@Composable
fun ErrorState(error: DataError, onRetry: (() -> Unit)?, modifier: Modifier = Modifier) {
    MessageState(
        title = stringResource(if (error == DataError.Offline) R.string.offline_title else R.string.error_title),
        body = stringResource(error.messageRes()),
        icon = Icons.Filled.Warning,
        primaryAction = onRetry?.let { stringResource(R.string.retry) to it },
        modifier = modifier.testTag("error_state")
    )
}

@Composable
fun EmptyState(title: String, body: String?, modifier: Modifier = Modifier, action: Pair<String, () -> Unit>? = null) {
    MessageState(title = title, body = body, primaryAction = action, modifier = modifier.testTag("empty_state"))
}

/** Shown above content while the phone is offline; cached data stays usable (SPEC §10.3, §10.5). */
@Composable
fun OfflineBanner(modifier: Modifier = Modifier, text: String = stringResource(R.string.offline_banner)) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier
            .fillMaxWidth()
            .testTag("offline_banner")
            .semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

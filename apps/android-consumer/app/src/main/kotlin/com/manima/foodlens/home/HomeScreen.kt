package com.manima.foodlens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.manima.foodlens.R
import com.manima.foodlens.core.designsystem.AaharLogo
import com.manima.foodlens.core.model.AnalysisStatus
import com.manima.foodlens.core.ui.OfflineBanner
import com.manima.foodlens.core.ui.ProductRow

@Composable
fun HomeRoute(
    onScan: () -> Unit,
    onManualEntry: () -> Unit,
    onSearch: () -> Unit,
    onLibrary: () -> Unit,
    onProduct: (String) -> Unit,
    onMyContributions: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeScreen(state, onScan, onManualEntry, onSearch, onLibrary, onProduct, onMyContributions, viewModel::retryServer)
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    onScan: () -> Unit,
    onManualEntry: () -> Unit,
    onSearch: () -> Unit,
    onLibrary: () -> Unit,
    onProduct: (String) -> Unit,
    onMyContributions: () -> Unit,
    onRetryServer: () -> Unit
) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        if (!state.online) OfflineBanner(text = stringResource(R.string.home_offline))
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("home")
        ) {
            Header()
            when {
                state.remote.maintenance -> Notice(stringResource(R.string.home_maintenance), null)
                state.online && !state.remote.reachable -> Notice(
                    stringResource(R.string.home_server_unreachable),
                    stringResource(R.string.retry_short) to onRetryServer
                )
            }
            ScanCard(onScan)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                QuickAction(
                    Icons.Filled.Edit,
                    stringResource(R.string.home_enter_barcode),
                    onManualEntry,
                    Modifier.weight(1f)
                )
                QuickAction(Icons.Filled.Search, stringResource(R.string.home_search), onSearch, Modifier.weight(1f))
            }
            if (state.pendingContributions > 0) {
                Notice(
                    pluralStringResource(
                        R.plurals.home_pending_contributions,
                        state.pendingContributions,
                        state.pendingContributions
                    ),
                    stringResource(R.string.home_view) to onMyContributions
                )
            }
            RecentSection(state, onProduct, onLibrary)
            QuickAction(
                Icons.Filled.Favorite,
                stringResource(R.string.home_favorites, state.favoriteCount),
                onLibrary,
                Modifier.fillMaxWidth()
            )
            TrustCard()
        }
    }
}

@Composable
private fun Header() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AaharLogo(size = 52.dp)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                stringResource(R.string.tagline),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ScanCard(onScan: () -> Unit) {
    Card(
        onClick = onScan,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp).testTag("home_scan")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(20.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.home_scan_title), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.home_scan_body), style = MaterialTheme.typography.titleSmall)
            }
            AaharLogo(size = 72.dp)
        }
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedCard(onClick = onClick, modifier = modifier.heightIn(min = 64.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun Notice(text: String, action: Pair<String, () -> Unit>?) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
            Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            action?.let { (label, onClick) -> TextButton(onClick = onClick) { Text(label) } }
        }
    }
}

@Composable
private fun RecentSection(state: HomeUiState, onProduct: (String) -> Unit, onLibrary: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.home_recent),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f).semantics { heading() }
        )
        if (state.recent.isNotEmpty()) TextButton(onClick = onLibrary) { Text(stringResource(R.string.home_see_all)) }
    }
    if (state.recent.isEmpty()) {
        Text(
            stringResource(R.string.home_recent_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    state.recent.forEach { h ->
        ProductRow(
            name = h.name ?: stringResource(if (h.found) R.string.home_product else R.string.home_not_found),
            brand = h.brandName ?: h.gtin14,
            status = h.analysisStatus?.let { AnalysisStatus.fromWire(it) },
            onClick = { onProduct(h.gtin14) }
        )
    }
}

@Composable
private fun TrustCard() {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp)) {
            Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.home_trust_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.home_trust_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

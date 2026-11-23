package com.manima.foodlens.feature.contribute

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.manima.foodlens.core.database.ContributionEntity
import com.manima.foodlens.core.database.LocalContributionState
import com.manima.foodlens.core.ui.AaharTopBar
import com.manima.foodlens.core.ui.EmptyState
import com.manima.foodlens.core.ui.LoadingState
import com.manima.foodlens.core.ui.ProductRow

@Composable
fun MyContributionsRoute(onBack: () -> Unit, viewModel: MyContributionsViewModel = hiltViewModel()) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        AaharTopBar(stringResource(R.string.mine_title), onBack = onBack)
        val list = items
        when {
            list == null -> LoadingState()
            list.isEmpty() -> EmptyState(
                title = stringResource(R.string.mine_empty_title),
                body = stringResource(R.string.mine_empty_body)
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize().testTag("my_contributions")
            ) {
                items(list, key = { it.localId }) { c -> ContributionRow(c, onRetry = { viewModel.retry(c.localId) }) }
            }
        }
    }
}

@Composable
private fun ContributionRow(c: ContributionEntity, onRetry: () -> Unit) {
    val status = stringResource(
        when (c.state) {
            LocalContributionState.QUEUED -> R.string.mine_state_queued
            LocalContributionState.UPLOADING -> R.string.mine_state_uploading
            LocalContributionState.SUBMITTED -> R.string.mine_state_submitted
            LocalContributionState.FAILED -> R.string.mine_state_failed
        }
    )
    ProductRow(
        name = c.productName ?: stringResource(R.string.mine_unnamed),
        brand = c.brandName ?: c.gtin14,
        status = null,
        supporting = status,
        onClick = {},
        trailing = if (c.state == LocalContributionState.FAILED) {
            { TextButton(onClick = onRetry) { Text(stringResource(R.string.mine_retry)) } }
        } else {
            null
        }
    )
}

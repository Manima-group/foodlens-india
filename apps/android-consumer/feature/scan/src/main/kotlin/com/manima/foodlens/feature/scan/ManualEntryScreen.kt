package com.manima.foodlens.feature.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.manima.foodlens.core.model.Symbology

@Composable
fun ManualEntryRoute(onLookup: (gtin14: String) -> Unit, viewModel: ManualEntryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ManualEntryEvent.Lookup -> onLookup(event.gtin14)
            }
        }
    }
    ManualEntryScreen(
        state = state,
        onCodeChanged = viewModel::onCodeChanged,
        onSubmit = viewModel::onSubmit,
        onCandidateChosen = viewModel::onCandidateChosen
    )
}

@Composable
fun ManualEntryScreen(
    state: ManualEntryUiState,
    onCodeChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onCandidateChosen: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.fillMaxSize().padding(16.dp)
    ) {
        Text(stringResource(R.string.manual_entry_title), style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = state.code,
            onValueChange = onCodeChanged,
            label = { Text(stringResource(R.string.manual_entry_label)) },
            isError = state.error != ManualEntryError.None,
            supportingText = errorText(state.error)?.let { message -> { Text(message) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("manual_entry_field")
        )
        Button(onClick = onSubmit, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.manual_entry_submit))
        }
        if (state.candidates.isNotEmpty()) {
            Text(stringResource(R.string.manual_entry_ambiguous), style = MaterialTheme.typography.bodyLarge)
            state.candidates.forEach { candidate ->
                OutlinedButton(
                    onClick = { onCandidateChosen(candidate.gtin14) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Text(stringResource(R.string.manual_entry_candidate, candidate.symbology.label(), candidate.gtin14))
                }
            }
        }
    }
}

@Composable
private fun errorText(error: ManualEntryError): String? = when (error) {
    ManualEntryError.None -> null
    ManualEntryError.InvalidBarcode -> stringResource(R.string.error_invalid_barcode)
    ManualEntryError.InvalidCheckDigit -> stringResource(R.string.error_invalid_check_digit)
}

private fun Symbology.label(): String = wire.replace('_', '-')

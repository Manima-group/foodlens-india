package com.manima.foodlens.feature.profile

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ProfileRoute(
    onMyContributions: () -> Unit,
    onOnboarding: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    val remote by viewModel.remote.collectAsStateWithLifecycle()
    val check by viewModel.check.collectAsStateWithLifecycle()
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.setNotify(granted)
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("profile")
    ) {
        Group(stringResource(R.string.profile_account)) {
            Text(stringResource(R.string.profile_guest), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.profile_signin_later),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = onMyContributions, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.profile_my_contributions))
            }
        }
        Group(stringResource(R.string.profile_language)) { LanguageChips() }
        Group(stringResource(R.string.profile_allergens)) {
            Text(
                stringResource(R.string.profile_allergens_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            AllergenChips(settings.allergenPrefs, viewModel::toggleAllergen)
        }
        Group(stringResource(R.string.profile_privacy)) {
            ToggleRow(
                title = stringResource(R.string.profile_consent),
                body = stringResource(R.string.profile_consent_body),
                checked = settings.analyticsConsent,
                onChange = viewModel::setConsent,
                tag = "consent_switch"
            )
            ToggleRow(
                title = stringResource(R.string.profile_notify),
                body = stringResource(R.string.profile_notify_body),
                checked = settings.notifyLookups,
                onChange = { on ->
                    if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        viewModel.setNotify(on)
                    }
                },
                tag = "notify_switch"
            )
            OutlinedButton(onClick = {
                confirmClear = true
            }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.profile_clear_history))
            }
        }
        Group(stringResource(R.string.profile_about)) {
            Text(
                stringResource(
                    R.string.profile_version,
                    viewModel.appConfig.versionName,
                    viewModel.appConfig.versionCode
                )
            )
            val legal = remote.config?.legal
            if (legal != null) {
                Text(
                    stringResource(
                        R.string.profile_versions,
                        legal.methodologyVersion,
                        legal.privacyVersion,
                        legal.termsVersion
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(stringResource(R.string.profile_methodology), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.profile_privacy_summary), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.profile_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onOnboarding) { Text(stringResource(R.string.profile_show_intro)) }
        }
        if (viewModel.appConfig.isDebug) {
            DeveloperGroup(
                current = viewModel.currentBaseUrl(),
                default = viewModel.defaultBaseUrl,
                override = settings.apiBaseUrlOverride,
                check = check,
                onSave = viewModel::setBaseUrl,
                onTest = viewModel::testConnection
            )
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.profile_clear_history)) },
            text = { Text(stringResource(R.string.profile_clear_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearHistory()
                    confirmClear = false
                }) { Text(stringResource(R.string.profile_clear_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.profile_cancel)) }
            }
        )
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            content()
        }
    }
}

@Composable
private fun ToggleRow(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit, tag: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .testTag(tag)
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun LanguageChips() {
    val current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("" to R.string.language_system, "en" to R.string.language_english, "hi" to R.string.language_hindi)
            .forEach { (tag, label) ->
                FilterChip(
                    selected = current == tag,
                    onClick = { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag)) },
                    label = { Text(stringResource(label)) }
                )
            }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AllergenChips(selected: Set<String>, onToggle: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        PROFILE_ALLERGENS.forEach { (code, label) ->
            FilterChip(selected = code in selected, onClick = {
                onToggle(code)
            }, label = { Text(stringResource(label)) })
        }
    }
}

@Composable
private fun DeveloperGroup(
    current: String,
    default: String,
    override: String?,
    check: ConnectionCheck,
    onSave: (String?) -> Unit,
    onTest: () -> Unit
) {
    var text by rememberSaveable(override) { mutableStateOf(override ?: "") }
    Group(stringResource(R.string.dev_title)) {
        Text(
            stringResource(R.string.dev_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(stringResource(R.string.dev_current, current), style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(stringResource(R.string.dev_url_label)) },
            placeholder = { Text("http://192.168.1.20:8000") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth().testTag("dev_url")
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(default, USB_REVERSE, EMULATOR).distinct().forEach { preset ->
                FilterChip(selected = text == preset, onClick = {
                    text = preset
                }, label = { Text(preset.removePrefix("http://")) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onSave(text.ifBlank { null }) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Text(stringResource(R.string.dev_save))
            }
            OutlinedButton(onClick = onTest, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Text(stringResource(R.string.dev_test))
            }
        }
        val status = when (check) {
            ConnectionCheck.Idle -> null
            ConnectionCheck.Checking -> R.string.dev_checking
            ConnectionCheck.Ok -> R.string.dev_ok
            ConnectionCheck.Failed -> R.string.dev_failed
            ConnectionCheck.Invalid -> R.string.dev_invalid
        }
        status?.let {
            Text(
                stringResource(it),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("dev_status")
            )
        }
    }
}

private const val USB_REVERSE = "http://127.0.0.1:8000"
private const val EMULATOR = "http://10.0.2.2:8000"

private val PROFILE_ALLERGENS = listOf(
    "milk" to R.string.allergen_milk,
    "peanut" to R.string.allergen_peanut,
    "tree_nuts" to R.string.allergen_tree_nuts,
    "cereals_gluten" to R.string.allergen_gluten,
    "soy" to R.string.allergen_soy,
    "egg" to R.string.allergen_egg,
    "fish" to R.string.allergen_fish,
    "crustacean" to R.string.allergen_crustacean,
    "sesame" to R.string.allergen_sesame,
    "mustard" to R.string.allergen_mustard,
    "sulphite" to R.string.allergen_sulphite
)

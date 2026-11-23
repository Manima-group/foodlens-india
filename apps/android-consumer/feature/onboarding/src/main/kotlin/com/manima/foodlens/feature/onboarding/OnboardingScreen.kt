package com.manima.foodlens.feature.onboarding

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.manima.foodlens.core.designsystem.AaharLogo

@Composable
fun OnboardingRoute(onDone: () -> Unit, viewModel: OnboardingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    OnboardingScreen(
        state = state,
        onNext = viewModel::next,
        onBack = viewModel::back,
        onConsent = viewModel::setConsent,
        onAllergen = viewModel::toggleAllergen,
        onFinish = { viewModel.finish(onDone) }
    )
}

@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onConsent: (Boolean) -> Unit,
    onAllergen: (String) -> Unit,
    onFinish: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
        ) {
            when (state.page) {
                0 -> WelcomePage()
                1 -> HowItWorksPage()
                else -> ChoicesPage(state, onConsent, onAllergen)
            }
        }
        PageDots(state.page)
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        ) {
            if (state.page > 0) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                    Text(stringResource(R.string.onboarding_back))
                }
            }
            val last = state.page == OnboardingViewModel.PAGES - 1
            Button(
                onClick = if (last) onFinish else onNext,
                modifier = Modifier.weight(
                    1f
                ).heightIn(min = 52.dp).testTag(if (last) "onboarding_finish" else "onboarding_next")
            ) {
                Text(stringResource(if (last) R.string.onboarding_start else R.string.onboarding_next))
            }
        }
    }
}

@Composable
private fun WelcomePage() {
    Spacer(Modifier.heightIn(min = 32.dp))
    AaharLogo(size = 112.dp)
    Text(
        stringResource(R.string.brand_name),
        style = MaterialTheme.typography.displaySmall,
        color = MaterialTheme.colorScheme.primary
    )
    Text(stringResource(R.string.brand_tagline), style = MaterialTheme.typography.headlineSmall)
    Text(stringResource(R.string.onboarding_welcome_body), style = MaterialTheme.typography.bodyLarge)
    LanguagePicker()
}

@Composable
private fun LanguagePicker() {
    val current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
    Text(stringResource(R.string.onboarding_language), style = MaterialTheme.typography.titleMedium)
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

@Composable
private fun HowItWorksPage() {
    Text(stringResource(R.string.onboarding_how_title), style = MaterialTheme.typography.headlineSmall)
    listOf(
        R.string.onboarding_how_scan,
        R.string.onboarding_how_label,
        R.string.onboarding_how_evidence,
        R.string.onboarding_how_score,
        R.string.onboarding_how_free
    ).forEachIndexed { i, res ->
        Row(verticalAlignment = Alignment.Top) {
            Surface(color = MaterialTheme.colorScheme.primary, shape = CircleShape, modifier = Modifier.size(28.dp)) {
                Text(
                    "${i + 1}",
                    color = MaterialTheme.colorScheme.onPrimary,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
            Text(
                stringResource(res),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoicesPage(state: OnboardingUiState, onConsent: (Boolean) -> Unit, onAllergen: (String) -> Unit) {
    Text(stringResource(R.string.onboarding_choices_title), style = MaterialTheme.typography.headlineSmall)
    Text(stringResource(R.string.onboarding_allergens), style = MaterialTheme.typography.titleMedium)
    Text(
        stringResource(R.string.onboarding_allergens_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ALLERGENS.forEach { (code, label) ->
            FilterChip(selected = code in state.allergens, onClick = {
                onAllergen(code)
            }, label = { Text(stringResource(label)) })
        }
    }
    val consentLabel = stringResource(R.string.onboarding_consent)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = state.analyticsConsent, role = Role.Switch, onValueChange = onConsent)
            .semantics { contentDescription = consentLabel }
            .testTag("consent_toggle")
    ) {
        Column(Modifier.weight(1f)) {
            Text(consentLabel, style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.onboarding_consent_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = state.analyticsConsent, onCheckedChange = null)
    }
    Text(
        stringResource(R.string.onboarding_guest_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun PageDots(page: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        repeat(OnboardingViewModel.PAGES) { i ->
            Surface(
                color = if (i == page) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = CircleShape,
                modifier = Modifier.size(if (i == page) 10.dp else 8.dp)
            ) {}
        }
    }
}

/** Codes match the API's allergen list (SPEC §6, seed). Highlighting uses label declarations only (T7). */
val ALLERGENS = listOf(
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

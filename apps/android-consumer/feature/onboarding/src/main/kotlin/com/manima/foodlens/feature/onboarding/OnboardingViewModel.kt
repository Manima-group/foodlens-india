package com.manima.foodlens.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manima.foodlens.core.datastore.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val page: Int = 0,
    /** Off until the user opts in (SPEC §10.5). */
    val analyticsConsent: Boolean = false,
    val allergens: Set<String> = emptySet()
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(private val settings: SettingsStore) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    fun next() = _state.update { it.copy(page = (it.page + 1).coerceAtMost(PAGES - 1)) }

    fun back() = _state.update { it.copy(page = (it.page - 1).coerceAtLeast(0)) }

    fun setConsent(consent: Boolean) = _state.update { it.copy(analyticsConsent = consent) }

    fun toggleAllergen(code: String) = _state.update {
        it.copy(allergens = if (code in it.allergens) it.allergens - code else it.allergens + code)
    }

    fun finish(onDone: () -> Unit) {
        val s = _state.value
        viewModelScope.launch {
            settings.setAnalyticsConsent(s.analyticsConsent)
            settings.setAllergenPrefs(s.allergens)
            settings.setOnboardingDone(true)
            onDone()
        }
    }

    companion object {
        const val PAGES = 3
    }
}

package com.manima.foodlens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manima.foodlens.core.data.AppStatus
import com.manima.foodlens.core.data.ConfigRepository
import com.manima.foodlens.core.data.GuestSession
import com.manima.foodlens.core.data.WorkScheduler
import com.manima.foodlens.core.datastore.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface LaunchState {
    data object Loading : LaunchState

    data class Ready(val onboardingDone: Boolean) : LaunchState
}

@HiltViewModel
class MainViewModel @Inject constructor(
    settings: SettingsStore,
    status: AppStatus,
    private val config: ConfigRepository,
    private val guest: GuestSession,
    private val scheduler: WorkScheduler
) : ViewModel() {
    val launch: StateFlow<LaunchState> = settings.settings
        .map { LaunchState.Ready(it.onboardingDone) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, LaunchState.Loading)

    val updateRequired: StateFlow<Boolean> = status.updateRequired

    init {
        viewModelScope.launch {
            // SPEC §8.4 config on launch; §10.7 guest registration on first launch. Failures are retried later.
            config.refresh()
            guest.ensureToken()
            scheduler.scheduleLookups()
        }
    }
}

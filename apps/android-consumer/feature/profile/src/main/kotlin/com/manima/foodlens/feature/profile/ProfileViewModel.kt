package com.manima.foodlens.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manima.foodlens.core.data.ApiEndpoint
import com.manima.foodlens.core.data.AppConfig
import com.manima.foodlens.core.data.ConfigRepository
import com.manima.foodlens.core.data.LibraryRepository
import com.manima.foodlens.core.data.RemoteConfig
import com.manima.foodlens.core.datastore.SettingsStore
import com.manima.foodlens.core.datastore.UserSettings
import com.manima.foodlens.core.network.ApiResult
import com.manima.foodlens.core.network.FoodLensClient
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ConnectionCheck { Idle, Checking, Ok, Failed, Invalid }

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val settings: SettingsStore,
    private val library: LibraryRepository,
    private val config: ConfigRepository,
    private val client: FoodLensClient,
    private val endpoint: ApiEndpoint,
    val appConfig: AppConfig
) : ViewModel() {
    val settingsState: StateFlow<UserSettings> =
        settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), UserSettings())

    val remote: StateFlow<RemoteConfig> = config.state

    private val _check = MutableStateFlow(ConnectionCheck.Idle)
    val check: StateFlow<ConnectionCheck> = _check.asStateFlow()

    val defaultBaseUrl: String get() = endpoint.default

    fun currentBaseUrl(): String = endpoint.baseUrl()

    fun setConsent(value: Boolean) = launch { settings.setAnalyticsConsent(value) }

    fun setNotify(value: Boolean) = launch { settings.setNotifyLookups(value) }

    fun toggleAllergen(code: String) = launch {
        val current = settings.current().allergenPrefs
        settings.setAllergenPrefs(if (code in current) current - code else current + code)
    }

    fun clearHistory() = launch { library.clearHistory() }

    /** Debug only: lets a physical phone reach the API on a LAN address (README "Physical phone"). */
    fun setBaseUrl(url: String?) {
        if (!appConfig.isDebug) return
        if (url != null && url.isNotBlank() && !ApiEndpoint.isValid(url)) {
            _check.value = ConnectionCheck.Invalid
            return
        }
        launch {
            settings.setApiBaseUrlOverride(url?.trim()?.trimEnd('/'))
            testConnection()
        }
    }

    fun testConnection() = launch {
        _check.value = ConnectionCheck.Checking
        // Give the endpoint collector a moment to pick up a just-saved override.
        kotlinx.coroutines.delay(SETTLE_MS)
        _check.value = if (client.metaConfig() is ApiResult.Success) ConnectionCheck.Ok else ConnectionCheck.Failed
        config.refresh(force = true)
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private companion object {
        const val STOP_MS = 5_000L
        const val SETTLE_MS = 150L
    }
}

package com.manima.foodlens.core.data

import com.manima.foodlens.core.network.ApiErrorCode
import com.manima.foodlens.core.network.ApiResult
import com.manima.foodlens.core.network.FoodLensClient
import com.manima.foodlens.core.network.model.MetaConfigDto
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RemoteConfig(
    val config: MetaConfigDto? = null,
    val maintenance: Boolean = false,
    val reachable: Boolean = true
) {
    val contributionsEnabled: Boolean get() = config?.features?.contributions ?: true
}

/** GET /meta/config on launch, cached for 6 h (SPEC §8.4). */
@Singleton
class ConfigRepository @Inject constructor(
    private val client: FoodLensClient,
    private val config: AppConfig,
    private val status: AppStatus,
    private val clock: Clock
) {
    private val _state = MutableStateFlow(RemoteConfig())
    val state: StateFlow<RemoteConfig> = _state.asStateFlow()

    @Volatile
    private var fetchedAt = 0L

    suspend fun refresh(force: Boolean = false) {
        if (!force && _state.value.config != null && clock.now() - fetchedAt < CACHE_MS) return
        when (val result = client.metaConfig()) {
            is ApiResult.Success -> {
                val cfg = result.value
                fetchedAt = clock.now()
                if (config.versionCode < cfg.minSupportedAppVersionCode) status.markUpdateRequired()
                _state.value = RemoteConfig(cfg, maintenance = cfg.maintenance, reachable = true)
            }
            is ApiResult.Failure -> _state.value = _state.value.copy(
                maintenance = result.error.code == ApiErrorCode.MAINTENANCE,
                reachable = true
            )
            is ApiResult.Offline -> _state.value = _state.value.copy(reachable = false)
            ApiResult.NotModified -> Unit
        }
    }

    private companion object {
        const val CACHE_MS = 6L * 60 * 60 * 1000
    }
}

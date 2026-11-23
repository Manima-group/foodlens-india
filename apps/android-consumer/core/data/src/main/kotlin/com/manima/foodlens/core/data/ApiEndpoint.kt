package com.manima.foodlens.core.data

import com.manima.foodlens.core.datastore.SettingsStore
import com.manima.foodlens.core.network.BaseUrlProvider
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * The API origin used for every request. Release builds always use [AppConfig.apiBaseUrl]; debug builds
 * may override it in Profile → Developer so a phone on Wi-Fi can reach a laptop's LAN IP.
 */
@Singleton
class ApiEndpoint @Inject constructor(
    private val config: AppConfig,
    settings: SettingsStore,
    @ApplicationScope scope: CoroutineScope
) : BaseUrlProvider {
    @Volatile
    private var override: String? = null

    init {
        if (config.isDebug) {
            scope.launch {
                settings.settings.map { it.apiBaseUrlOverride }.distinctUntilChanged().collect { override = it }
            }
        }
    }

    override fun baseUrl(): String = override?.takeIf { isValid(it) } ?: config.apiBaseUrl

    val default: String get() = config.apiBaseUrl

    companion object {
        fun isValid(url: String): Boolean {
            val parsed = url.trim().toHttpUrlOrNull() ?: return false
            return parsed.encodedPath == "/" || parsed.encodedPath.isEmpty()
        }
    }
}

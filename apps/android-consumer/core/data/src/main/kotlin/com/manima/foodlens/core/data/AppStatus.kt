package com.manima.foodlens.core.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Interceptor
import okhttp3.Response

/** App-wide server signals: 426 shows the blocking update screen (SPEC §10.8). */
@Singleton
class AppStatus @Inject constructor() {
    private val _updateRequired = MutableStateFlow(false)
    val updateRequired: StateFlow<Boolean> = _updateRequired.asStateFlow()

    fun markUpdateRequired() {
        _updateRequired.value = true
    }
}

internal class AppStatusInterceptor(private val status: AppStatus) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (response.code == HTTP_UPGRADE_REQUIRED) status.markUpdateRequired()
        return response
    }

    private companion object {
        const val HTTP_UPGRADE_REQUIRED = 426
    }
}

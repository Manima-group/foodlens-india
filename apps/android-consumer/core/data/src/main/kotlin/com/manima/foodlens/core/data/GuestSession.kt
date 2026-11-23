package com.manima.foodlens.core.data

import com.manima.foodlens.core.datastore.SettingsStore
import com.manima.foodlens.core.datastore.TokenStore
import com.manima.foodlens.core.network.ApiErrorCode
import com.manima.foodlens.core.network.ApiResult
import com.manima.foodlens.core.network.FoodLensClient
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Guest mode (SPEC §10.7, T10): no login, no personal data. The guest token is only needed for
 * contributions and consented scan events; browsing works without it.
 */
@Singleton
class GuestSession @Inject constructor(
    private val client: FoodLensClient,
    private val tokens: TokenStore,
    private val settings: SettingsStore
) {
    private val mutex = Mutex()

    /** Single-flight: concurrent callers share one registration. */
    suspend fun ensureToken(): Outcome<String> = mutex.withLock {
        tokens.load()?.let { return@withLock Outcome.Ok(it) }
        when (val result = client.registerGuest(settings.installId())) {
            is ApiResult.Success -> {
                tokens.save(result.value.guestToken)
                Outcome.Ok(result.value.guestToken)
            }
            else -> result.toOutcome() as Outcome.Err
        }
    }

    /** Runs [block] with a guest token; on 401 re-registers once and retries (token expired or revoked). */
    suspend fun <T> authorized(block: suspend () -> ApiResult<T>): ApiResult<T> {
        val token = ensureToken()
        if (token is Outcome.Err) return token.toApiResult()
        val first = block()
        if (first !is ApiResult.Failure || first.error.code != ApiErrorCode.UNAUTHENTICATED) return first
        tokens.clear()
        val again = ensureToken()
        if (again is Outcome.Err) return again.toApiResult()
        return block()
    }

    private fun Outcome.Err.toApiResult(): ApiResult<Nothing> = when (val e = error) {
        DataError.Offline -> ApiResult.Offline(java.io.IOException("offline"))
        is DataError.Api -> ApiResult.Failure(
            com.manima.foodlens.core.network.ApiError(e.code, e.code.httpStatus, requestId = null, details = null)
        )
    }
}

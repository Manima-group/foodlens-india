package com.manima.foodlens.core.data

import com.manima.foodlens.core.network.ApiError
import com.manima.foodlens.core.network.ApiErrorCode
import com.manima.foodlens.core.network.ApiResult

/** Repository result. UI maps [DataError] to string resources by code, never by server message (SPEC §8.2). */
sealed interface Outcome<out T> {
    data class Ok<T>(val value: T) : Outcome<T>

    data class Err(val error: DataError) : Outcome<Nothing>
}

sealed interface DataError {
    data object Offline : DataError

    data class Api(val code: ApiErrorCode, val canContribute: Boolean = false) : DataError

    companion object {
        fun from(error: ApiError): DataError = Api(error.code, error.canContribute)
    }
}

internal fun <T> ApiResult<T>.toOutcome(): Outcome<T> = when (this) {
    is ApiResult.Success -> Outcome.Ok(value)
    is ApiResult.Failure -> Outcome.Err(DataError.from(error))
    is ApiResult.Offline -> Outcome.Err(DataError.Offline)
    // Only conditional requests can return 304, and those are handled where they are made.
    ApiResult.NotModified -> Outcome.Err(DataError.Api(ApiErrorCode.INTERNAL))
}

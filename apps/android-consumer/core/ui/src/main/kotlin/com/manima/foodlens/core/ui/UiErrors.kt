package com.manima.foodlens.core.ui

import androidx.annotation.StringRes
import com.manima.foodlens.core.data.DataError
import com.manima.foodlens.core.network.ApiErrorCode

/** Maps error codes (never server messages) to strings (SPEC §8.2, §10.3). */
@StringRes
fun DataError.messageRes(): Int = when (this) {
    DataError.Offline -> R.string.error_offline
    is DataError.Api -> when (code) {
        ApiErrorCode.PRODUCT_NOT_FOUND, ApiErrorCode.NOT_FOUND -> R.string.error_not_found
        ApiErrorCode.INVALID_BARCODE, ApiErrorCode.VALIDATION_ERROR -> R.string.error_invalid_barcode
        ApiErrorCode.INVALID_CHECK_DIGIT -> R.string.error_invalid_check_digit
        ApiErrorCode.AMBIGUOUS_BARCODE -> R.string.error_ambiguous
        ApiErrorCode.RATE_LIMITED, ApiErrorCode.QUOTA_EXCEEDED -> R.string.error_rate_limited
        ApiErrorCode.MAINTENANCE -> R.string.error_maintenance
        ApiErrorCode.UPSTREAM_UNAVAILABLE -> R.string.error_unavailable
        ApiErrorCode.APP_VERSION_UNSUPPORTED -> R.string.error_update_required
        ApiErrorCode.UNAUTHENTICATED, ApiErrorCode.FORBIDDEN -> R.string.error_session
        ApiErrorCode.CONFLICT, ApiErrorCode.IDEMPOTENCY_CONFLICT, ApiErrorCode.INTERNAL -> R.string.error_generic
    }
}

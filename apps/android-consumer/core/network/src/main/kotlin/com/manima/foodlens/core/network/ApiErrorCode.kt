package com.manima.foodlens.core.network

/** SPEC §8.2 fixed error codes, verbatim. Extend only via ADR. The app maps codes (never messages) to strings. */
@Suppress("MagicNumber")
enum class ApiErrorCode(val httpStatus: Int) {
    VALIDATION_ERROR(422),
    INVALID_BARCODE(422),
    INVALID_CHECK_DIGIT(422),
    AMBIGUOUS_BARCODE(409),
    PRODUCT_NOT_FOUND(404),
    NOT_FOUND(404),
    UNAUTHENTICATED(401),
    FORBIDDEN(403),
    CONFLICT(409),
    IDEMPOTENCY_CONFLICT(409),
    RATE_LIMITED(429),
    QUOTA_EXCEEDED(402),
    UPSTREAM_UNAVAILABLE(503),
    MAINTENANCE(503),
    APP_VERSION_UNSUPPORTED(426),
    INTERNAL(500)
    ;

    companion object {
        fun fromWire(code: String): ApiErrorCode = entries.firstOrNull { it.name == code } ?: INTERNAL

        /** Used only when the body is not a SPEC error envelope (e.g. a proxy page). */
        fun fromStatus(status: Int): ApiErrorCode = when (status) {
            401 -> UNAUTHENTICATED
            403 -> FORBIDDEN
            404 -> NOT_FOUND
            409 -> CONFLICT
            422 -> VALIDATION_ERROR
            426 -> APP_VERSION_UNSUPPORTED
            429 -> RATE_LIMITED
            502, 503, 504 -> UPSTREAM_UNAVAILABLE
            else -> INTERNAL
        }
    }
}

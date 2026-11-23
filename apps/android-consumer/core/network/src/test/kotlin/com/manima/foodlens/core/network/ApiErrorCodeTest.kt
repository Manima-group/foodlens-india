package com.manima.foodlens.core.network

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ApiErrorCodeTest {
    /** SPEC §8.2 verbatim. */
    private val spec = mapOf(
        "VALIDATION_ERROR" to 422, "INVALID_BARCODE" to 422, "INVALID_CHECK_DIGIT" to 422,
        "AMBIGUOUS_BARCODE" to 409, "PRODUCT_NOT_FOUND" to 404, "NOT_FOUND" to 404,
        "UNAUTHENTICATED" to 401, "FORBIDDEN" to 403, "CONFLICT" to 409, "IDEMPOTENCY_CONFLICT" to 409,
        "RATE_LIMITED" to 429, "QUOTA_EXCEEDED" to 402, "UPSTREAM_UNAVAILABLE" to 503, "MAINTENANCE" to 503,
        "APP_VERSION_UNSUPPORTED" to 426, "INTERNAL" to 500
    )

    @Test
    fun `codes and statuses match SPEC`() {
        assertEquals(spec, ApiErrorCode.entries.associate { it.name to it.httpStatus })
    }

    @Test
    fun `unknown code falls back by status, never by message`() {
        assertEquals(ApiErrorCode.INTERNAL, ApiErrorCode.fromWire("SOMETHING_NEW"))
        assertEquals(ApiErrorCode.NOT_FOUND, ApiErrorCode.fromStatus(404))
        assertEquals(ApiErrorCode.RATE_LIMITED, ApiErrorCode.fromStatus(429))
        assertEquals(ApiErrorCode.INTERNAL, ApiErrorCode.fromStatus(418))
    }
}

package com.manima.foodlens.core.gtin

import kotlin.random.Random
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/** SPEC §7: compute_check_digit then normalize round-trips; normalize is idempotent. */
class GtinPropertyTest {
    @ParameterizedTest(name = "body length {0}")
    @ValueSource(ints = [11, 12, 13])
    fun `check digit round-trips and normalize is idempotent`(bodyLength: Int) {
        val random = Random(seed = 20261123 + bodyLength)
        repeat(ITERATIONS) {
            val body = (1..bodyLength).joinToString("") { random.nextInt(10).toString() }
            val code = body + Gtin.computeCheckDigit(body)

            val first = Gtin.normalize(code)
            assertTrue(first is GtinResult.Valid, "$code should be valid, got $first")
            first as GtinResult.Valid
            assertEquals(14, first.gtin14.length)
            assertTrue(first.gtin14.endsWith(code), "leading zeros must only be added, never stripped")

            val second = Gtin.normalize(first.gtin14)
            assertEquals(first.gtin14, (second as GtinResult.Valid).gtin14)
        }
    }

    @ParameterizedTest(name = "body length {0}")
    @ValueSource(ints = [11, 12, 13])
    fun `any single wrong check digit is rejected`(bodyLength: Int) {
        val random = Random(seed = 7 + bodyLength)
        repeat(ITERATIONS) {
            val body = (1..bodyLength).joinToString("") { random.nextInt(10).toString() }
            val wrong = (Gtin.computeCheckDigit(body) + 1 + random.nextInt(9)) % 10
            val result = Gtin.normalize(body + wrong)
            assertEquals(GtinErrorCode.INVALID_CHECK_DIGIT, (result as GtinResult.Invalid).code)
        }
    }

    private companion object {
        const val ITERATIONS = 500
    }
}

package com.manima.foodlens.core.gtin

import com.manima.foodlens.core.model.Symbology
import com.manima.foodlens.core.testing.Fixtures
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

/** Runs every vector in packages/fixtures/gtin.json (SPEC §7, quality gate 1). */
class GtinFixturesTest {
    @Serializable
    private data class FixtureFile(val cases: List<Case>)

    @Serializable
    private data class Case(
        val id: String,
        val input: String,
        val symbology: Symbology? = null,
        val expected: Expected? = null,
        @SerialName("expected_error") val expectedError: String? = null,
        @SerialName("expected_candidates") val expectedCandidates: List<Candidate> = emptyList()
    )

    @Serializable
    private data class Expected(
        val gtin14: String,
        @SerialName("is_restricted_circulation") val isRestrictedCirculation: Boolean
    )

    @Serializable
    private data class Candidate(val gtin14: String, val symbology: Symbology)

    private val cases = Fixtures.json.decodeFromString(FixtureFile.serializer(), Fixtures.read("gtin.json")).cases

    @Test
    fun `fixture file contains every SPEC table row`() {
        assertTrue(cases.size >= 14, "expected at least the 14 SPEC §7 vectors, got ${cases.size}")
    }

    @TestFactory
    fun vectors(): List<DynamicTest> = cases.map { case ->
        DynamicTest.dynamicTest(case.id) {
            val result = Gtin.normalize(case.input, case.symbology)
            when {
                case.expected != null -> {
                    val valid = result as? GtinResult.Valid
                        ?: error("${case.id}: expected valid ${case.expected.gtin14}, got $result")
                    assertEquals(case.expected.gtin14, valid.gtin14, case.id)
                    assertEquals(case.expected.isRestrictedCirculation, valid.isRestrictedCirculation, case.id)
                    assertEquals(
                        case.input.filter(Char::isDigit),
                        valid.originalCode,
                        "${case.id}: original_code keeps entered digits"
                    )
                }
                case.expectedError == GtinErrorCode.AMBIGUOUS_BARCODE.name -> {
                    val ambiguous = result as? GtinResult.Ambiguous
                        ?: error("${case.id}: expected AMBIGUOUS_BARCODE, got $result")
                    assertEquals(
                        case.expectedCandidates.map { it.gtin14 to it.symbology },
                        ambiguous.candidates.map { it.gtin14 to it.symbology }
                    )
                }
                else -> {
                    val invalid = result as? GtinResult.Invalid
                        ?: error("${case.id}: expected ${case.expectedError}, got $result")
                    assertEquals(case.expectedError, invalid.code.name, case.id)
                }
            }
        }
    }
}

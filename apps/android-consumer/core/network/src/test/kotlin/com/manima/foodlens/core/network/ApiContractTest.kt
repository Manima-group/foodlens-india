package com.manima.foodlens.core.network

import com.manima.foodlens.core.model.AnalysisStatus
import com.manima.foodlens.core.model.DataFreshness
import com.manima.foodlens.core.model.EvidenceLevel
import com.manima.foodlens.core.model.NutrientBasis
import com.manima.foodlens.core.network.model.AnalysisExplainDto
import com.manima.foodlens.core.network.model.ErrorEnvelopeDto
import com.manima.foodlens.core.network.model.MetaConfigDto
import com.manima.foodlens.core.network.model.ProductLookupDto
import com.manima.foodlens.core.testing.Fixtures
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * SPEC §10.12 contract: Android parses every API example without error.
 * Strict decoding (unknown keys fail) so DTOs cannot silently drop fields that SPEC defines.
 */
class ApiContractTest {
    private val strict = Json { ignoreUnknownKeys = false }

    private inline fun <reified T> parse(name: String): T =
        strict.decodeFromString<T>(Fixtures.read("api-examples/$name"))

    @Test
    fun `meta config example parses`() {
        val config = parse<MetaConfigDto>("meta-config.json")
        assertEquals(1, config.minSupportedAppVersionCode)
        assertFalse(config.features.publicScore)
        assertEquals(listOf("en", "hi"), config.supportedLocales)
    }

    @Test
    fun `product by barcode example parses with gtin kept as string`() {
        val lookup = parse<ProductLookupDto>("product-by-barcode-200.json")
        assertEquals("08901234567890", lookup.product.gtin14)
        assertEquals(DataFreshness.FRESH, lookup.product.dataFreshness)
        assertEquals(EvidenceLevel.REVIEWER_VERIFIED, lookup.product.labelVersion?.evidenceLevel)
        assertEquals(AnalysisStatus.METHODOLOGY_PENDING, lookup.analysisSummary.analysisStatus)
        assertNull(lookup.analysisSummary.scoreValue)
        val indicator = lookup.analysisSummary.headlineIndicators.single()
        assertEquals(NutrientBasis.PER_100G, indicator.basis)
        assertEquals(true, indicator.isPlaceholderThreshold)
    }

    @Test
    fun `analysis explain example parses`() {
        val explain = parse<AnalysisExplainDto>("analysis-explain.json")
        assertEquals(AnalysisStatus.INSUFFICIENT_DATA, explain.analysisStatus)
        assertEquals(listOf("saturated_fat_g", "sodium_mg"), explain.missingFields)
        assertEquals(EvidenceLevel.LABEL_DECLARED, explain.inputs.single().source)
    }

    @Test
    fun `error envelope example parses`() {
        val envelope = parse<ErrorEnvelopeDto>("error-envelope.json")
        assertEquals("PRODUCT_NOT_FOUND", envelope.error.code)
        assertEquals(ApiErrorCode.PRODUCT_NOT_FOUND, ApiErrorCode.fromWire(envelope.error.code))
    }
}

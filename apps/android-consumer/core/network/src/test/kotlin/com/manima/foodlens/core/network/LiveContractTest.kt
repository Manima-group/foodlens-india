package com.manima.foodlens.core.network

import com.manima.foodlens.core.model.AnalysisStatus
import com.manima.foodlens.core.network.model.AlternativesDto
import com.manima.foodlens.core.network.model.AnalysisExplainDto
import com.manima.foodlens.core.network.model.CategoryNodeDto
import com.manima.foodlens.core.network.model.CompareDto
import com.manima.foodlens.core.network.model.ContributionDto
import com.manima.foodlens.core.network.model.ErrorEnvelopeDto
import com.manima.foodlens.core.network.model.GuestRegisterDto
import com.manima.foodlens.core.network.model.IngredientCardDto
import com.manima.foodlens.core.network.model.PageDto
import com.manima.foodlens.core.network.model.PhotoUploadDto
import com.manima.foodlens.core.network.model.ProductDetailDto
import com.manima.foodlens.core.network.model.ProductLookupDto
import com.manima.foodlens.core.network.model.ProductSummaryDto
import com.manima.foodlens.core.network.model.SourcesDto
import com.manima.foodlens.core.testing.Fixtures
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Real responses from the seeded backend (services/api/scripts/dump_examples.py), decoded strictly:
 * any field the API sends that a DTO lacks fails here.
 */
class LiveContractTest {
    private val strict = Json { ignoreUnknownKeys = false }

    private inline fun <reified T> parse(name: String): T =
        strict.decodeFromString<T>(Fixtures.read("api-examples/live/$name"))

    @Test
    fun `product responses parse`() {
        val lookup = parse<ProductLookupDto>("by-barcode.json")
        assertEquals("08901234567890", lookup.product.gtin14)
        val detail = parse<ProductDetailDto>("product-detail.json")
        assertTrue(detail.nutrition.per100.isNotEmpty())
        assertEquals("No verified laboratory information available", detail.labInformation.message)
        val insufficient = parse<ProductDetailDto>("product-detail-insufficient.json")
        assertEquals(AnalysisStatus.INSUFFICIENT_DATA, insufficient.analysisSummary.analysisStatus)
        assertNull(insufficient.analysisSummary.scoreValue)
        parse<AnalysisExplainDto>("explain.json")
        parse<SourcesDto>("sources.json")
        parse<IngredientCardDto>("ingredient.json")
    }

    @Test
    fun `list and comparison responses parse`() {
        assertTrue(parse<AlternativesDto>("alternatives.json").items.isNotEmpty())
        assertEquals("Not enough data to compare", parse<AlternativesDto>("alternatives-suppressed.json").message)
        assertEquals(2, parse<CompareDto>("compare.json").items.size)
        assertTrue(parse<PageDto<ProductSummaryDto>>("search.json").items.isNotEmpty())
        assertTrue(parse<PageDto<CategoryNodeDto>>("categories.json").items.size >= 8)
    }

    @Test
    fun `guest and contribution responses parse`() {
        parse<GuestRegisterDto>("guest-register.json")
        assertEquals("draft", parse<ContributionDto>("contribution.json").state)
        assertEquals("PUT", parse<PhotoUploadDto>("photo-upload.json").uploadMethod)
    }

    @Test
    fun `error responses parse`() {
        val notFound = parse<ErrorEnvelopeDto>("not-found.json").error
        assertEquals(ApiErrorCode.PRODUCT_NOT_FOUND, ApiErrorCode.fromWire(notFound.code))
        val ambiguous =
            ApiError(ApiErrorCode.AMBIGUOUS_BARCODE, 409, null, parse<ErrorEnvelopeDto>("ambiguous.json").error.details)
        assertEquals(listOf("00000001234565" to "EAN_8", "00012345000065" to "UPC_E"), ambiguous.candidates)
    }
}

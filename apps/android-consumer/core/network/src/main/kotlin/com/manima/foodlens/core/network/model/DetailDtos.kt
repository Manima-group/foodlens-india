package com.manima.foodlens.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// Shapes beyond SPEC §8 examples, mirrored from packages/openapi/openapi.json (ApiContractTest guards them).

@Serializable
data class NutrientValueDto(val nutrient: String, val value: Double, val unit: String)

@Serializable
data class ServingDto(
    val size: Double,
    val unit: String,
    @SerialName("servings_per_pack") val servingsPerPack: Double? = null
)

@Serializable
data class IndicatorDto(
    val nutrient: String,
    val level: IndicatorLevel,
    val value: Double,
    val basis: String,
    @SerialName("is_placeholder_threshold") val isPlaceholderThreshold: Boolean
)

@Serializable
data class NutritionDto(
    @SerialName("declared_basis") val declaredBasis: String? = null,
    val basis: String,
    @SerialName("per_100") val per100: List<NutrientValueDto> = emptyList(),
    @SerialName("per_serving") val perServing: List<NutrientValueDto> = emptyList(),
    val serving: ServingDto? = null,
    val indicators: List<IndicatorDto> = emptyList(),
    @SerialName("thresholds_are_placeholder") val thresholdsArePlaceholder: Boolean = true,
    val warnings: List<String> = emptyList(),
    @SerialName("evidence_level") val evidenceLevel: String? = null
)

@Serializable
data class IngredientItemDto(
    val position: Int,
    @SerialName("raw_text") val rawText: String,
    @SerialName("ingredient_id") val ingredientId: String? = null,
    @SerialName("ingredient_name") val ingredientName: String? = null,
    @SerialName("is_additive") val isAdditive: Boolean = false,
    @SerialName("ins_code") val insCode: String? = null,
    @SerialName("match_confidence") val matchConfidence: Double = 0.0
)

@Serializable
data class IngredientsDto(
    @SerialName("is_complete") val isComplete: Boolean,
    val items: List<IngredientItemDto> = emptyList(),
    val processing: Map<String, Int>? = null,
    @SerialName("processing_reason") val processingReason: String? = null,
    @SerialName("evidence_level") val evidenceLevel: String? = null
)

@Serializable
data class AllergenDeclarationDto(
    val code: String,
    val name: String,
    @SerialName("name_hi") val nameHi: String? = null,
    val kind: String,
    val source: String,
    @SerialName("evidence_level") val evidenceLevel: String
)

@Serializable
data class LabInformationDto(val status: String, val message: String)

@Serializable
data class ProductDetailDto(
    val product: ProductDto,
    @SerialName("analysis_summary") val analysisSummary: AnalysisSummaryDto,
    val sponsored: List<JsonObject> = emptyList(),
    val nutrition: NutritionDto,
    val ingredients: IngredientsDto,
    val allergens: List<AllergenDeclarationDto> = emptyList(),
    @SerialName("dietary_declarations") val dietaryDeclarations: List<String> = emptyList(),
    @SerialName("lab_information") val labInformation: LabInformationDto,
    val limitations: List<String> = emptyList()
)

@Serializable
data class ProductSummaryDto(
    val id: String,
    val gtin14: String,
    val name: String,
    val brand: BrandDto,
    val category: CategoryDto? = null,
    val variant: VariantDto? = null,
    @SerialName("analysis_status") val analysisStatus: com.manima.foodlens.core.model.AnalysisStatus,
    @SerialName("data_freshness") val dataFreshness: String,
    @SerialName("headline_indicators") val headlineIndicators: List<HeadlineIndicatorDto> = emptyList()
)

@Serializable
data class PageDto<T>(val items: List<T>, @SerialName("next_cursor") val nextCursor: String? = null)

@Serializable
data class AlternativesDto(
    val items: List<ProductSummaryDto> = emptyList(),
    /** Always separate from organic items and rendered only by SponsoredCard (T1, T2). */
    val sponsored: List<JsonObject> = emptyList(),
    @SerialName("suppressed_reason") val suppressedReason: String? = null,
    val message: String? = null,
    @SerialName("ranking_basis") val rankingBasis: String
)

@Serializable
data class CompareItemDto(
    val product: ProductSummaryDto,
    val basis: String,
    @SerialName("per_100") val per100: List<NutrientValueDto>,
    @SerialName("missing_fields") val missingFields: List<String>
)

@Serializable
data class CompareDto(val items: List<CompareItemDto>, val nutrients: List<String>, val note: String)

@Serializable
data class CategoryNodeDto(
    val id: String,
    val slug: String,
    val name: String,
    @SerialName("name_hi") val nameHi: String? = null,
    @SerialName("parent_id") val parentId: String? = null,
    @SerialName("product_count") val productCount: Int
)

@Serializable
data class IngredientCardDto(
    val id: String,
    val key: String,
    val name: String,
    val description: String? = null,
    @SerialName("is_additive") val isAdditive: Boolean,
    @SerialName("ins_code") val insCode: String? = null,
    val function: String? = null,
    val aliases: List<Map<String, String>> = emptyList(),
    val references: List<Map<String, String>> = emptyList(),
    @SerialName("references_note") val referencesNote: String
)

@Serializable
data class SourceDto(
    @SerialName("field_name") val fieldName: String,
    val source: String,
    @SerialName("licence_code") val licenceCode: String,
    @SerialName("evidence_level") val evidenceLevel: String,
    @SerialName("observed_at") val observedAt: String
)

@Serializable
data class SourcesDto(val sources: List<SourceDto>, val attributions: List<String>)

@Serializable
data class GuestRegisterRequest(@SerialName("install_id") val installId: String)

@Serializable
data class GuestRegisterDto(
    @SerialName("guest_token") val guestToken: String,
    @SerialName("token_type") val tokenType: String,
    @SerialName("expires_at") val expiresAt: String,
    val scopes: List<String>
)

@Serializable
data class ScanEventDto(val gtin14: String, @SerialName("scanned_at") val scannedAt: String, val found: Boolean)

@Serializable
data class ScanEventsRequest(val events: List<ScanEventDto>)

@Serializable
data class ContributionRequest(
    val gtin14: String,
    @SerialName("product_name") val productName: String? = null,
    @SerialName("brand_name") val brandName: String? = null,
    val notes: String? = null
)

@Serializable
data class ContributionPhotoDto(val id: String, val kind: String, val state: String)

@Serializable
data class ContributionDto(
    val id: String,
    val gtin14: String,
    @SerialName("product_name") val productName: String? = null,
    @SerialName("brand_name") val brandName: String? = null,
    val state: String,
    val photos: List<ContributionPhotoDto> = emptyList(),
    @SerialName("created_at") val createdAt: String
)

@Serializable
data class PhotoRequest(
    val kind: String,
    @SerialName("content_type") val contentType: String,
    @SerialName("size_bytes") val sizeBytes: Long
)

@Serializable
data class PhotoUploadDto(
    @SerialName("photo_id") val photoId: String,
    @SerialName("upload_url") val uploadUrl: String,
    @SerialName("upload_method") val uploadMethod: String,
    @SerialName("upload_headers") val uploadHeaders: Map<String, String>,
    @SerialName("expires_at") val expiresAt: String
)

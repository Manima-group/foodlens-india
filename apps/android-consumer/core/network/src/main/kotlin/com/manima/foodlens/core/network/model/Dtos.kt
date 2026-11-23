package com.manima.foodlens.core.network.model

import com.manima.foodlens.core.model.AnalysisStatus
import com.manima.foodlens.core.model.DataFreshness
import com.manima.foodlens.core.model.EvidenceLevel
import com.manima.foodlens.core.model.NutrientBasis
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// Wire shapes from SPEC §8. GTINs are always strings (SPEC §7 rule 7).

@Serializable
data class ErrorEnvelopeDto(val error: ErrorBodyDto)

@Serializable
data class ErrorBodyDto(
    val code: String,
    val message: String,
    val details: JsonObject? = null,
    @SerialName("request_id") val requestId: String? = null
)

@Serializable
data class MetaConfigDto(
    @SerialName("min_supported_app_version_code") val minSupportedAppVersionCode: Int,
    @SerialName("latest_app_version_code") val latestAppVersionCode: Int,
    val maintenance: Boolean,
    val features: FeaturesDto,
    val legal: LegalDto,
    @SerialName("supported_locales") val supportedLocales: List<String>
)

@Serializable
data class FeaturesDto(
    val contributions: Boolean = false,
    @SerialName("public_score") val publicScore: Boolean = false,
    val sponsored: Boolean = false,
    @SerialName("off_lookup") val offLookup: Boolean = false,
    @SerialName("google_sign_in") val googleSignIn: Boolean = false
)

@Serializable
data class LegalDto(
    @SerialName("privacy_version") val privacyVersion: String,
    @SerialName("terms_version") val termsVersion: String,
    @SerialName("methodology_version") val methodologyVersion: String
)

@Serializable
data class ProductLookupDto(
    val product: ProductDto,
    @SerialName("analysis_summary") val analysisSummary: AnalysisSummaryDto,
    /** Paid placements arrive separately and are never interleaved with organic content (T1, T2). */
    val sponsored: List<JsonObject> = emptyList()
)

@Serializable
data class ProductDto(
    val id: String,
    val gtin14: String,
    @SerialName("original_code") val originalCode: String,
    val name: String,
    val brand: BrandDto,
    val variant: VariantDto? = null,
    val category: CategoryDto? = null,
    val images: List<ImageDto> = emptyList(),
    @SerialName("label_version") val labelVersion: LabelVersionDto,
    @SerialName("data_freshness") val dataFreshness: DataFreshness,
    val completeness: CompletenessDto,
    val badges: List<String> = emptyList()
)

@Serializable
data class BrandDto(val id: String, val name: String, @SerialName("is_verified") val isVerified: Boolean)

@Serializable
data class VariantDto(
    @SerialName("net_quantity") val netQuantity: Double? = null,
    val unit: String? = null,
    @SerialName("pack_format") val packFormat: String? = null
)

@Serializable
data class CategoryDto(val id: String, val slug: String, val name: String)

@Serializable
data class ImageDto(val kind: String, val url: String, val licence: String? = null, val attribution: String? = null)

@Serializable
data class LabelVersionDto(
    val id: String,
    @SerialName("observed_at") val observedAt: String,
    @SerialName("evidence_level") val evidenceLevel: EvidenceLevel
)

@Serializable
data class CompletenessDto(
    val nutrition: Boolean,
    val ingredients: Boolean,
    val allergens: Boolean,
    @SerialName("missing_fields") val missingFields: List<String> = emptyList()
)

@Serializable
data class AnalysisSummaryDto(
    @SerialName("analysis_status") val analysisStatus: AnalysisStatus,
    @SerialName("score_value") val scoreValue: Double? = null,
    val confidence: Double? = null,
    @SerialName("rule_version") val ruleVersion: String? = null,
    @SerialName("headline_indicators") val headlineIndicators: List<HeadlineIndicatorDto> = emptyList()
)

@Serializable
enum class IndicatorLevel {
    @SerialName("low")
    LOW,

    @SerialName("medium")
    MEDIUM,

    @SerialName("high")
    HIGH
}

@Serializable
data class HeadlineIndicatorDto(
    val nutrient: String,
    val level: IndicatorLevel,
    @SerialName("is_placeholder_threshold") val isPlaceholderThreshold: Boolean,
    val basis: NutrientBasis
)

@Serializable
data class AnalysisExplainDto(
    @SerialName("analysis_status") val analysisStatus: AnalysisStatus,
    @SerialName("score_value") val scoreValue: Double? = null,
    val methodology: MethodologyDto,
    @SerialName("rule_version") val ruleVersion: String,
    val inputs: List<ExplainInputDto> = emptyList(),
    @SerialName("missing_fields") val missingFields: List<String> = emptyList(),
    val factors: List<JsonObject> = emptyList(),
    val limitations: List<String> = emptyList()
)

@Serializable
data class MethodologyDto(val id: String, val version: String, val status: String)

@Serializable
data class ExplainInputDto(
    val field: String,
    val value: Double,
    val basis: NutrientBasis,
    val source: EvidenceLevel,
    @SerialName("observed_at") val observedAt: String
)

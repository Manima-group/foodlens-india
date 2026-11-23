// Enum values mirror docs/SPEC.md §2 verbatim; EnumContractTest checks them against packages/fixtures/enums.json.
package com.manima.foodlens.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** SPEC §2 `analysis_status`. */
@Serializable
enum class AnalysisStatus(val wire: String) {
    @SerialName("scored")
    SCORED("scored"),

    @SerialName("insufficient_data")
    INSUFFICIENT_DATA("insufficient_data"),

    @SerialName("methodology_pending")
    METHODOLOGY_PENDING("methodology_pending"),

    @SerialName("unverified_provisional")
    UNVERIFIED_PROVISIONAL("unverified_provisional"),

    @SerialName("not_applicable")
    NOT_APPLICABLE("not_applicable")
    ;

    companion object {
        fun fromWire(value: String): AnalysisStatus? = entries.firstOrNull { it.wire == value }
    }
}

/** SPEC §2 `evidence_level`. */
@Serializable
enum class EvidenceLevel(val wire: String) {
    @SerialName("label_declared")
    LABEL_DECLARED("label_declared"),

    @SerialName("community_submitted")
    COMMUNITY_SUBMITTED("community_submitted"),

    @SerialName("manufacturer_submitted")
    MANUFACTURER_SUBMITTED("manufacturer_submitted"),

    @SerialName("reviewer_verified")
    REVIEWER_VERIFIED("reviewer_verified"),

    @SerialName("laboratory_tested")
    LABORATORY_TESTED("laboratory_tested")
    ;

    companion object {
        fun fromWire(value: String): EvidenceLevel? = entries.firstOrNull { it.wire == value }
    }
}

/** SPEC §2 `review_state`. */
@Serializable
enum class ReviewState(val wire: String) {
    @SerialName("draft")
    DRAFT("draft"),

    @SerialName("submitted")
    SUBMITTED("submitted"),

    @SerialName("needs_changes")
    NEEDS_CHANGES("needs_changes"),

    @SerialName("approved")
    APPROVED("approved"),

    @SerialName("rejected")
    REJECTED("rejected"),

    @SerialName("archived")
    ARCHIVED("archived")
    ;

    companion object {
        fun fromWire(value: String): ReviewState? = entries.firstOrNull { it.wire == value }
    }
}

/** SPEC §2 `contribution_state`. */
@Serializable
enum class ContributionState(val wire: String) {
    @SerialName("draft")
    DRAFT("draft"),

    @SerialName("photos_uploaded")
    PHOTOS_UPLOADED("photos_uploaded"),

    @SerialName("extracting")
    EXTRACTING("extracting"),

    @SerialName("awaiting_confirmation")
    AWAITING_CONFIRMATION("awaiting_confirmation"),

    @SerialName("submitted")
    SUBMITTED("submitted"),

    @SerialName("in_review")
    IN_REVIEW("in_review"),

    @SerialName("accepted")
    ACCEPTED("accepted"),

    @SerialName("rejected")
    REJECTED("rejected"),

    @SerialName("spam")
    SPAM("spam")
    ;

    companion object {
        fun fromWire(value: String): ContributionState? = entries.firstOrNull { it.wire == value }
    }
}

/** SPEC §2 `claim_state`. */
@Serializable
enum class ClaimState(val wire: String) {
    @SerialName("pending")
    PENDING("pending"),

    @SerialName("evidence_requested")
    EVIDENCE_REQUESTED("evidence_requested"),

    @SerialName("verified")
    VERIFIED("verified"),

    @SerialName("disputed")
    DISPUTED("disputed"),

    @SerialName("rejected")
    REJECTED("rejected"),

    @SerialName("revoked")
    REVOKED("revoked")
    ;

    companion object {
        fun fromWire(value: String): ClaimState? = entries.firstOrNull { it.wire == value }
    }
}

/** SPEC §2 `org_verification`. */
@Serializable
enum class OrgVerification(val wire: String) {
    @SerialName("unverified")
    UNVERIFIED("unverified"),

    @SerialName("pending")
    PENDING("pending"),

    @SerialName("verified")
    VERIFIED("verified"),

    @SerialName("suspended")
    SUSPENDED("suspended")
    ;

    companion object {
        fun fromWire(value: String): OrgVerification? = entries.firstOrNull { it.wire == value }
    }
}

/** SPEC §2 `lab_report_status`. */
@Serializable
enum class LabReportStatus(val wire: String) {
    @SerialName("received")
    RECEIVED("received"),

    @SerialName("under_review")
    UNDER_REVIEW("under_review"),

    @SerialName("verified")
    VERIFIED("verified"),

    @SerialName("rejected")
    REJECTED("rejected"),

    @SerialName("expired")
    EXPIRED("expired")
    ;

    companion object {
        fun fromWire(value: String): LabReportStatus? = entries.firstOrNull { it.wire == value }
    }
}

/** SPEC §2 `payment_state`. */
@Serializable
enum class PaymentState(val wire: String) {
    @SerialName("created")
    CREATED("created"),

    @SerialName("attempted")
    ATTEMPTED("attempted"),

    @SerialName("paid")
    PAID("paid"),

    @SerialName("failed")
    FAILED("failed"),

    @SerialName("refunded")
    REFUNDED("refunded"),

    @SerialName("partially_refunded")
    PARTIALLY_REFUNDED("partially_refunded")
    ;

    companion object {
        fun fromWire(value: String): PaymentState? = entries.firstOrNull { it.wire == value }
    }
}

/** SPEC §2 `subscription_state`. */
@Serializable
enum class SubscriptionState(val wire: String) {
    @SerialName("trialing")
    TRIALING("trialing"),

    @SerialName("active")
    ACTIVE("active"),

    @SerialName("past_due")
    PAST_DUE("past_due"),

    @SerialName("grace")
    GRACE("grace"),

    @SerialName("cancelled")
    CANCELLED("cancelled"),

    @SerialName("expired")
    EXPIRED("expired")
    ;

    companion object {
        fun fromWire(value: String): SubscriptionState? = entries.firstOrNull { it.wire == value }
    }
}

/** SPEC §2 `campaign_state`. */
@Serializable
enum class CampaignState(val wire: String) {
    @SerialName("draft")
    DRAFT("draft"),

    @SerialName("pending_approval")
    PENDING_APPROVAL("pending_approval"),

    @SerialName("approved")
    APPROVED("approved"),

    @SerialName("active")
    ACTIVE("active"),

    @SerialName("paused")
    PAUSED("paused"),

    @SerialName("completed")
    COMPLETED("completed"),

    @SerialName("rejected")
    REJECTED("rejected")
    ;

    companion object {
        fun fromWire(value: String): CampaignState? = entries.firstOrNull { it.wire == value }
    }
}

/** SPEC §2 `placement_slot`. */
@Serializable
enum class PlacementSlot(val wire: String) {
    @SerialName("product_detail_sponsored")
    PRODUCT_DETAIL_SPONSORED("product_detail_sponsored"),

    @SerialName("search_sponsored")
    SEARCH_SPONSORED("search_sponsored"),

    @SerialName("discover_sponsored")
    DISCOVER_SPONSORED("discover_sponsored"),

    @SerialName("brand_storefront")
    BRAND_STOREFRONT("brand_storefront")
    ;

    companion object {
        fun fromWire(value: String): PlacementSlot? = entries.firstOrNull { it.wire == value }
    }
}

/** SPEC §2 `nutrient_basis`. */
@Serializable
enum class NutrientBasis(val wire: String) {
    @SerialName("per_100g")
    PER_100G("per_100g"),

    @SerialName("per_100ml")
    PER_100ML("per_100ml"),

    @SerialName("per_serving")
    PER_SERVING("per_serving")
    ;

    companion object {
        fun fromWire(value: String): NutrientBasis? = entries.firstOrNull { it.wire == value }
    }
}

/** SPEC §2 `data_freshness`. */
@Serializable
enum class DataFreshness(val wire: String) {
    @SerialName("fresh")
    FRESH("fresh"),

    @SerialName("stale")
    STALE("stale"),

    @SerialName("unknown")
    UNKNOWN("unknown")
    ;

    companion object {
        fun fromWire(value: String): DataFreshness? = entries.firstOrNull { it.wire == value }
    }
}

/** SPEC §2 `symbology`. */
@Serializable
enum class Symbology(val wire: String) {
    @SerialName("EAN_13")
    EAN_13("EAN_13"),

    @SerialName("EAN_8")
    EAN_8("EAN_8"),

    @SerialName("UPC_A")
    UPC_A("UPC_A"),

    @SerialName("UPC_E")
    UPC_E("UPC_E"),

    @SerialName("GTIN_14")
    GTIN_14("GTIN_14"),

    @SerialName("MANUAL")
    MANUAL("MANUAL")
    ;

    companion object {
        fun fromWire(value: String): Symbology? = entries.firstOrNull { it.wire == value }
    }
}

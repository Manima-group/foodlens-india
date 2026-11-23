package com.manima.foodlens.core.model

import com.manima.foodlens.core.testing.Fixtures
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EnumContractTest {
    private val kotlinEnums: Map<String, List<String>> = mapOf(
        "analysis_status" to AnalysisStatus.entries.map { it.wire },
        "evidence_level" to EvidenceLevel.entries.map { it.wire },
        "review_state" to ReviewState.entries.map { it.wire },
        "contribution_state" to ContributionState.entries.map { it.wire },
        "claim_state" to ClaimState.entries.map { it.wire },
        "org_verification" to OrgVerification.entries.map { it.wire },
        "lab_report_status" to LabReportStatus.entries.map { it.wire },
        "payment_state" to PaymentState.entries.map { it.wire },
        "subscription_state" to SubscriptionState.entries.map { it.wire },
        "campaign_state" to CampaignState.entries.map { it.wire },
        "placement_slot" to PlacementSlot.entries.map { it.wire },
        "nutrient_basis" to NutrientBasis.entries.map { it.wire },
        "data_freshness" to DataFreshness.entries.map { it.wire },
        "symbology" to Symbology.entries.map { it.wire }
    )

    @Test
    fun `every SPEC enum is mirrored verbatim`() {
        val spec = Fixtures.element("enums.json").jsonObject
            .mapValues { (_, v) -> v.jsonArray.map { it.jsonPrimitive.content } }
        assertEquals(spec, kotlinEnums)
    }

    @Test
    fun `serial names equal wire values`() {
        AnalysisStatus.entries.forEach {
            assertEquals("\"${it.wire}\"", Json.encodeToString(AnalysisStatus.serializer(), it))
        }
        assertEquals(Symbology.UPC_E, Json.decodeFromString(Symbology.serializer(), "\"UPC_E\""))
    }
}

package com.manima.foodlens.feature.product

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.manima.foodlens.core.designsystem.FoodLensTheme
import com.manima.foodlens.core.network.NetworkJson
import com.manima.foodlens.core.network.model.AlternativesDto
import com.manima.foodlens.core.network.model.ProductDetailDto
import com.manima.foodlens.core.testing.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ProductScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private fun detail(name: String) =
        NetworkJson.decodeFromString(ProductDetailDto.serializer(), Fixtures.read("api-examples/live/$name"))

    private fun alternatives(name: String) =
        NetworkJson.decodeFromString(AlternativesDto.serializer(), Fixtures.read("api-examples/live/$name"))

    private var contributed: Pair<String, Boolean>? = null
    private var perServing: Boolean? = null

    private val actions = ProductActions(
        onBack = {},
        onIngredient = {},
        onProduct = {},
        onCompare = {},
        onSources = {},
        onContribute = { gtin, correction -> contributed = gtin to correction },
        onScanAnother = {}
    )

    private fun show(state: ProductUiState) = compose.setContent {
        FoodLensTheme {
            ProductScreen(state, actions, onRetry = {}, onPerServing = { perServing = it }, onToggleFavorite = {})
        }
    }

    private fun scrollTo(tag: String) = compose.onNodeWithTag("product_content").performScrollToNode(hasTestTag(tag))

    @Test
    fun showsSectionsInTrustOrderWithoutAScore() {
        show(
            ProductUiState(
                gtin14 = "08901234567890",
                detail = detail("product-detail.json"),
                refreshing = false,
                alternatives = AlternativesState.Loaded(alternatives("alternatives.json"))
            )
        )
        compose.onNodeWithText("No score yet: scoring method is pending expert review").assertIsDisplayed()
        compose.onNodeWithText("Barcode (GTIN): 08901234567890").assertExists()
        scrollTo("section_nutrition")
        compose.onNodeWithText("512 kcal").assertExists()
        compose.onNodeWithTag("placeholder_note").assertExists()
        scrollTo("section_allergens")
        compose.onNodeWithText("Declared on label: contains Peanut").assertExists()
        compose.onNodeWithText("Declared on label: vegetarian mark (green dot)").assertExists()
        scrollTo("section_lab")
        compose.onNodeWithText("No verified laboratory information available").assertExists()
        scrollTo("section_alternatives")
        scrollTo("section_sources")
        compose.onNodeWithTag("section_sponsored").assertDoesNotExist()
    }

    @Test
    fun incompleteProductSaysInsufficientData() {
        show(
            ProductUiState(
                gtin14 = "02900000000025",
                detail = detail("product-detail-insufficient.json"),
                refreshing = false
            )
        )
        compose.onNodeWithText("Insufficient data to score").assertIsDisplayed()
    }

    @Test
    fun perServingToggleReportsChoice() {
        show(ProductUiState(gtin14 = "08901234567890", detail = detail("product-detail.json"), refreshing = false))
        scrollTo("section_nutrition")
        compose.onNodeWithTag("per_serving_toggle").performClick()
        assertEquals(true, perServing)
    }

    @Test
    fun perServingValuesAreShownWhenSelected() {
        show(
            ProductUiState(
                gtin14 = "08901234567890",
                detail = detail("product-detail.json"),
                refreshing = false,
                perServing = true
            )
        )
        scrollTo("section_nutrition")
        compose.onNodeWithText("153.6 kcal").assertExists()
    }

    @Test
    fun highlightedAllergenIsCalledOut() {
        show(
            ProductUiState(
                gtin14 = "08901234567890",
                detail = detail("product-detail.json"),
                refreshing = false,
                highlightAllergens = setOf("peanut")
            )
        )
        scrollTo("section_allergens")
        compose.onNodeWithText("You asked to highlight this allergen.").assertExists()
    }

    @Test
    fun suppressedAlternativesExplainWhy() {
        show(
            ProductUiState(
                gtin14 = "02900000000025",
                detail = detail("product-detail-insufficient.json"),
                refreshing = false,
                alternatives = AlternativesState.Loaded(alternatives("alternatives-suppressed.json"))
            )
        )
        scrollTo("section_alternatives")
        compose.onNodeWithText("Not enough data on this product to suggest alternatives.").assertExists()
    }

    @Test
    fun unknownProductOffersContribution() {
        show(ProductUiState(gtin14 = "04006381333931", refreshing = false, notFound = true, canContribute = true))
        compose.onNodeWithTag("not_found_state").assertIsDisplayed()
        compose.onNodeWithText("Add this product").performClick()
        assertEquals("04006381333931" to false, contributed)
    }

    @Test
    fun offlineUnknownBarcodeIsQueued() {
        show(ProductUiState(gtin14 = "08901234567890", refreshing = false, offline = true, queued = true))
        compose.onNodeWithTag("queued_state").assertIsDisplayed()
    }

    @Test
    fun cachedProductOfflineShowsBanner() {
        show(
            ProductUiState(
                gtin14 = "08901234567890",
                detail = detail("product-detail.json"),
                refreshing = false,
                offline = true
            )
        )
        compose.onNodeWithTag("offline_banner").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithTagExists("product_content"))
    }

    @Test
    @Config(qualifiers = "hi")
    fun hindiShowsHindiAllergenNames() {
        show(ProductUiState(gtin14 = "08901234567890", detail = detail("product-detail.json"), refreshing = false))
        scrollTo("section_allergens")
        compose.onNode(hasText("मूंगफली", substring = true)).assertExists()
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTagExists(tag: String): Boolean =
        runCatching { onNodeWithTag(tag).assertExists() }.isSuccess
}

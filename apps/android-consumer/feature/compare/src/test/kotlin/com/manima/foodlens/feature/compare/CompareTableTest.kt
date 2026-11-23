package com.manima.foodlens.feature.compare

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.manima.foodlens.core.designsystem.FoodLensTheme
import com.manima.foodlens.core.network.NetworkJson
import com.manima.foodlens.core.network.model.CompareDto
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class CompareTableTest {
    @get:Rule
    val compose = createComposeRule()

    private val compare = NetworkJson.decodeFromString(
        CompareDto.serializer(),
        File(System.getProperty("foodlens.fixtures.dir"), "api-examples/live/compare.json").readText()
    )

    @Test
    fun showsEveryProductAndNeverFillsMissingValuesWithZero() {
        compose.setContent { FoodLensTheme { CompareTable(compare) {} } }
        compare.items.forEach { compose.onNodeWithText(it.product.name).assertExists() }
        val anyMissing = compare.items.any { item ->
            compare.nutrients.any { n -> item.per100.none { it.nutrient == n } }
        }
        if (anyMissing) {
            assertTrue(compose.onAllNodesWithText("Not declared").fetchSemanticsNodes().isNotEmpty())
        }
        compose.onNodeWithText(compare.note).assertExists()
    }
}

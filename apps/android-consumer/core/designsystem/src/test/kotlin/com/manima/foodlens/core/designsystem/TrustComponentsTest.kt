package com.manima.foodlens.core.designsystem

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.manima.foodlens.core.model.AnalysisStatus
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class TrustComponentsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun insufficientDataBanner_showsDoDText() {
        compose.setContent { FoodLensTheme { AnalysisStatusBanner(AnalysisStatus.INSUFFICIENT_DATA) } }
        compose.onNodeWithText("Insufficient data to score").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "hi")
    fun insufficientDataBanner_isLocalisedInHindi() {
        compose.setContent { FoodLensTheme { AnalysisStatusBanner(AnalysisStatus.INSUFFICIENT_DATA) } }
        compose.onNodeWithText("स्कोर देने के लिए पर्याप्त डेटा नहीं है").assertIsDisplayed()
    }

    @Test
    fun sponsoredCard_showsSponsoredTextAndReason_andA11yLabelStartsWithSponsored() {
        compose.setContent {
            FoodLensTheme {
                SponsoredCard(title = "Demo Oats (Synthetic)", reason = "Shown for: breakfast cereals", onClick = {})
            }
        }
        compose.onNodeWithText("Sponsored").assertIsDisplayed()
        compose.onNodeWithText("Shown for: breakfast cereals").assertIsDisplayed()
        val description = compose.onNodeWithTag("sponsored_card").fetchSemanticsNode()
            .config[SemanticsProperties.ContentDescription].single()
        assertTrue(description, description.startsWith("Sponsored"))
    }
}

package com.manima.foodlens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.manima.foodlens.core.data.RemoteConfig
import com.manima.foodlens.core.database.HistoryEntity
import com.manima.foodlens.core.designsystem.FoodLensTheme
import com.manima.foodlens.home.HomeScreen
import com.manima.foodlens.home.HomeUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class HomeScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private var scanned = false
    private var opened: String? = null

    private fun show(state: HomeUiState) = compose.setContent {
        FoodLensTheme {
            HomeScreen(
                state = state,
                onScan = { scanned = true },
                onManualEntry = {},
                onSearch = {},
                onLibrary = {},
                onProduct = { opened = it },
                onMyContributions = {},
                onRetryServer = {}
            )
        }
    }

    @Test
    fun showsBrandAndOpensScanner() {
        show(HomeUiState())
        compose.onNodeWithText("AAHAR").assertIsDisplayed()
        compose.onNodeWithText("Know What You Eat.").assertIsDisplayed()
        compose.onNodeWithTag("home_scan").performClick()
        assertTrue(scanned)
    }

    @Test
    fun recentScansOpenTheProduct() {
        val h =
            HistoryEntity(
                1,
                "08901234567890",
                "p",
                "Masala Namkeen (Synthetic)",
                "Demo Foods",
                "methodology_pending",
                true,
                1
            )
        show(HomeUiState(recent = listOf(h)))
        compose.onNodeWithText("Masala Namkeen (Synthetic)").performClick()
        assertEquals("08901234567890", opened)
    }

    @Test
    fun offlineAndUnreachableStatesAreExplained() {
        show(HomeUiState(online = false))
        compose.onNodeWithTag("offline_banner").assertIsDisplayed()
    }

    @Test
    fun serverUnreachableShowsRetry() {
        show(HomeUiState(online = true, remote = RemoteConfig(reachable = false)))
        compose.onNodeWithText("Can't reach the AAHAR server right now.").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "hi")
    fun hindiTagline() {
        show(HomeUiState())
        compose.onNodeWithText("जानिए आप क्या खाते हैं।").assertIsDisplayed()
    }
}

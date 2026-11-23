package com.manima.foodlens.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.manima.foodlens.R
import com.manima.foodlens.feature.scan.ManualEntryRoute
import kotlinx.serialization.Serializable

/** Type-safe route contracts live in :app so features never depend on each other (SPEC §10.2). */
@Serializable data object HomeRoute

@Serializable data object ManualEntryRoute

@Serializable data class ProductRoute(val gtin14: String)

@Composable
fun FoodLensNavHost() {
    val navController = rememberNavController()
    Scaffold { padding ->
        NavHost(navController, startDestination = HomeRoute, modifier = Modifier.padding(padding)) {
            composable<HomeRoute> {
                HomeScreen(onManualEntry = { navController.navigate(ManualEntryRoute) })
            }
            composable<ManualEntryRoute> {
                ManualEntryRoute(onLookup = { gtin14 -> navController.navigate(ProductRoute(gtin14)) })
            }
            composable<ProductRoute> { entry ->
                // Product detail (SPEC §10.9) lands with :feature:product; this shows the resolved GTIN meanwhile.
                Text(entry.toRoute<ProductRoute>().gtin14, modifier = Modifier.padding(16.dp))
            }
        }
    }
}

@Composable
private fun HomeScreen(onManualEntry: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        Button(onClick = onManualEntry, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.home_enter_barcode))
        }
    }
}

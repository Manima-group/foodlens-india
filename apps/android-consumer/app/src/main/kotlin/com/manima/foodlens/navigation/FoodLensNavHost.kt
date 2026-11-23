package com.manima.foodlens.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.manima.foodlens.R
import com.manima.foodlens.core.ui.AaharTopBar
import com.manima.foodlens.feature.compare.CompareRoute
import com.manima.foodlens.feature.contribute.ContributeRoute
import com.manima.foodlens.feature.contribute.MyContributionsRoute
import com.manima.foodlens.feature.history.LibraryRoute
import com.manima.foodlens.feature.onboarding.OnboardingRoute
import com.manima.foodlens.feature.product.IngredientRoute
import com.manima.foodlens.feature.product.ProductActions
import com.manima.foodlens.feature.product.ProductRoute
import com.manima.foodlens.feature.product.SourcesRoute
import com.manima.foodlens.feature.profile.ProfileRoute
import com.manima.foodlens.feature.scan.ManualEntryRoute
import com.manima.foodlens.feature.scan.ScannerRoute
import com.manima.foodlens.feature.search.SearchRoute
import com.manima.foodlens.home.HomeRoute
import kotlin.reflect.KClass
import kotlinx.serialization.Serializable

/*
 * Type-safe route contracts live in :app so features never depend on each other (SPEC §10.2).
 * Property names are the SavedStateHandle keys each feature's ViewModel reads (e.g. ProductViewModel.ARG_GTIN14).
 */
@Serializable data object OnboardingDest

@Serializable data object HomeDest

@Serializable data object SearchDest

@Serializable data object LibraryDest

@Serializable data object ProfileDest

@Serializable data object ScanDest

@Serializable data object ManualEntryDest

@Serializable data class ProductDest(val gtin14: String)

@Serializable data class IngredientDest(val ingredientId: String)

@Serializable data class SourcesDest(val productId: String)

@Serializable data class CompareDest(val ids: String)

@Serializable data class ContributeDest(val gtin14: String, val correction: Boolean = false)

@Serializable data object MyContributionsDest

private data class TopLevel(val route: Any, val routeClass: KClass<*>, val icon: ImageVector, val label: Int)

private val TOP_LEVEL = listOf(
    TopLevel(HomeDest, HomeDest::class, Icons.Filled.Home, R.string.nav_home),
    TopLevel(SearchDest, SearchDest::class, Icons.Filled.Search, R.string.nav_search),
    TopLevel(LibraryDest, LibraryDest::class, Icons.Filled.Favorite, R.string.nav_library),
    TopLevel(ProfileDest, ProfileDest::class, Icons.Filled.Settings, R.string.nav_settings)
)

@Composable
fun AaharApp(onboardingDone: Boolean) {
    val navController = rememberNavController()
    val start = remember { if (onboardingDone) HomeDest else OnboardingDest }
    val entry by navController.currentBackStackEntryAsState()
    val current = entry?.destination
    val topLevel = TOP_LEVEL.firstOrNull { t -> current?.hierarchy?.any { it.hasRoute(t.routeClass) } == true }
    Scaffold(
        bottomBar = {
            if (topLevel != null) {
                NavigationBar(Modifier.testTag("bottom_nav")) {
                    TOP_LEVEL.forEach { t ->
                        NavigationBarItem(
                            selected = t == topLevel,
                            onClick = { navController.navigateTopLevel(t.route) },
                            icon = { Icon(t.icon, contentDescription = null) },
                            label = { Text(stringResource(t.label)) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
            if (topLevel != null && topLevel.route != HomeDest) AaharTopBar(title = stringResource(topLevel.label))
            NavHost(navController, startDestination = start, modifier = Modifier.weight(1f)) {
                graph(navController)
            }
        }
    }
}

private fun NavHostController.navigateTopLevel(route: Any) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

private fun androidx.navigation.NavGraphBuilder.graph(nav: NavHostController) {
    val openProduct: (String) -> Unit = { nav.navigate(ProductDest(it)) }
    val openCompare: (List<String>) -> Unit = { nav.navigate(CompareDest(it.joinToString(","))) }
    composable<OnboardingDest> {
        OnboardingRoute(onDone = {
            nav.navigate(HomeDest) { popUpTo(nav.graph.id) { inclusive = true } }
        })
    }
    composable<HomeDest> {
        HomeRoute(
            onScan = { nav.navigate(ScanDest) },
            onManualEntry = { nav.navigate(ManualEntryDest) },
            onSearch = { nav.navigateTopLevel(SearchDest) },
            onLibrary = { nav.navigateTopLevel(LibraryDest) },
            onProduct = openProduct,
            onMyContributions = { nav.navigate(MyContributionsDest) }
        )
    }
    composable<SearchDest> { SearchRoute(onProduct = openProduct) }
    composable<LibraryDest> {
        LibraryRoute(onProduct = openProduct, onCompare = openCompare, onScan = { nav.navigate(ScanDest) })
    }
    composable<ProfileDest> {
        ProfileRoute(
            onMyContributions = { nav.navigate(MyContributionsDest) },
            onOnboarding = { nav.navigate(OnboardingDest) }
        )
    }
    detailGraph(nav, openProduct, openCompare)
}

private fun androidx.navigation.NavGraphBuilder.detailGraph(
    nav: NavHostController,
    openProduct: (String) -> Unit,
    openCompare: (List<String>) -> Unit
) {
    composable<ScanDest> {
        ScannerRoute(
            onLookup = { gtin -> nav.navigate(ProductDest(gtin)) },
            onManualEntry = { nav.navigate(ManualEntryDest) { popUpTo<ScanDest> { inclusive = true } } },
            onBack = { nav.popBackStack() }
        )
    }
    composable<ManualEntryDest> {
        ManualEntryRoute(
            onLookup = { gtin -> nav.navigate(ProductDest(gtin)) },
            onBack = { nav.popBackStack() }
        )
    }
    composable<ProductDest> {
        ProductRoute(
            ProductActions(
                onBack = { nav.popBackStack() },
                onIngredient = { nav.navigate(IngredientDest(it)) },
                onProduct = openProduct,
                onCompare = openCompare,
                onSources = { nav.navigate(SourcesDest(it)) },
                onContribute = { gtin, correction -> nav.navigate(ContributeDest(gtin, correction)) },
                onScanAnother = {
                    nav.navigate(ScanDest) { popUpTo<HomeDest>() }
                }
            )
        )
    }
    composable<IngredientDest> { IngredientRoute(onBack = { nav.popBackStack() }) }
    composable<SourcesDest> { SourcesRoute(onBack = { nav.popBackStack() }) }
    composable<CompareDest> { CompareRoute(onBack = { nav.popBackStack() }, onProduct = openProduct) }
    composable<ContributeDest> {
        ContributeRoute(
            onBack = { nav.popBackStack() },
            onMyContributions = {
                nav.navigate(MyContributionsDest) { popUpTo<HomeDest>() }
            }
        )
    }
    composable<MyContributionsDest> { MyContributionsRoute(onBack = { nav.popBackStack() }) }
}

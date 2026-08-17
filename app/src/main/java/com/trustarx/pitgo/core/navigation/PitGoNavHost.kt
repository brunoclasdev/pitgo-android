package com.trustarx.pitgo.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.trustarx.pitgo.features.home.model.ServiceCategoryId
import com.trustarx.pitgo.features.home.ui.HomeRoute
import com.trustarx.pitgo.features.provider.ui.ProviderSearchEntryScreen

@Composable
fun PitGoNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = PitGoRoutes.Home,
    ) {
        composable(PitGoRoutes.Home) {
            var pendingCategoryId by remember { mutableStateOf<ServiceCategoryId?>(null) }
            val navigateToProviderSearch = dropUnlessResumed {
                pendingCategoryId?.let { categoryId ->
                    navController.navigate(PitGoRoutes.providerSearch(categoryId)) {
                        launchSingleTop = true
                    }
                }
            }

            HomeRoute(
                onCategorySelected = { categoryId ->
                    pendingCategoryId = categoryId
                    navigateToProviderSearch()
                },
            )
        }

        composable(
            route = PitGoRoutes.ProviderSearch,
            arguments = listOf(
                navArgument(PitGoRoutes.CategoryIdArgument) {
                    type = NavType.StringType
                },
            ),
        ) { backStackEntry ->
            val categoryId = PitGoRoutes.parseCategoryId(
                backStackEntry.arguments?.getString(PitGoRoutes.CategoryIdArgument),
            )
            ProviderSearchEntryScreen(
                categoryId = categoryId,
                onBack = navController::navigateUp,
            )
        }
    }
}

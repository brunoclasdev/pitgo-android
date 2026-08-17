package com.trustarx.pitgo.core.navigation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.trustarx.pitgo.core.common.model.DemoServiceId
import com.trustarx.pitgo.features.home.ui.HomeRoute
import com.trustarx.pitgo.features.requestservice.ui.RequestServiceRoute
import com.trustarx.pitgo.features.welcome.ui.WelcomeRoute

@Composable
fun PitGoNavHost() {
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val usesAppSurface = currentRoute == PitGoRoutes.Home ||
        currentRoute == PitGoRoutes.RequestServicePattern
    val useDarkSystemBarIcons = usesAppSurface && !isSystemInDarkTheme()

    SystemBarIconAppearance(useDarkIcons = useDarkSystemBarIcons)

    NavHost(
        navController = navController,
        startDestination = PitGoRoutes.Welcome,
    ) {
        composable(PitGoRoutes.Welcome) {
            var enterNavigationPending by remember { mutableStateOf(false) }

            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                enterNavigationPending = false
            }

            val navigateToHome = dropUnlessResumed {
                if (!enterNavigationPending) {
                    enterNavigationPending = true
                    navController.navigate(PitGoRoutes.Home) {
                        launchSingleTop = true
                    }
                }
            }

            WelcomeRoute(
                onCreateAccount = {},
                onEnter = navigateToHome,
                onRegisterCompany = {},
            )
        }

        composable(PitGoRoutes.Home) {
            var serviceNavigationPending by remember { mutableStateOf(false) }
            var selectedServiceId by remember { mutableStateOf<DemoServiceId?>(null) }

            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                serviceNavigationPending = false
                selectedServiceId = null
            }

            val performRequestServiceNavigation = dropUnlessResumed {
                val serviceId = selectedServiceId
                if (!serviceNavigationPending && serviceId != null) {
                    serviceNavigationPending = true
                    navController.navigate(PitGoRoutes.requestService(serviceId)) {
                        launchSingleTop = true
                    }
                }
            }
            val navigateToRequestService: (DemoServiceId) -> Unit = { serviceId ->
                selectedServiceId = serviceId
                performRequestServiceNavigation()
            }

            HomeRoute(onServiceClick = navigateToRequestService)
        }

        composable(
            route = PitGoRoutes.RequestServicePattern,
            arguments = listOf(
                navArgument(PitGoRoutes.RequestServiceArg) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            val navigateBack = {
                if (!navController.popBackStack()) {
                    navController.navigate(PitGoRoutes.Home) {
                        popUpTo(PitGoRoutes.RequestServicePattern) {
                            inclusive = true
                        }
                        launchSingleTop = true
                    }
                }
            }

            RequestServiceRoute(
                onBack = navigateBack,
                onSubmit = {},
                onBottomItemClick = {},
            )
        }
    }
}

@Composable
private fun SystemBarIconAppearance(useDarkIcons: Boolean) {
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            view.context.findActivity()?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = useDarkIcons
                    isAppearanceLightNavigationBars = useDarkIcons
                }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

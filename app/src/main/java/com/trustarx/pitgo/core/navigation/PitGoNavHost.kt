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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.trustarx.pitgo.features.home.ui.HomeRoute
import com.trustarx.pitgo.features.welcome.ui.WelcomeRoute

@Composable
fun PitGoNavHost() {
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val useDarkSystemBarIcons = currentRoute == PitGoRoutes.Home && !isSystemInDarkTheme()

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
            HomeRoute()
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

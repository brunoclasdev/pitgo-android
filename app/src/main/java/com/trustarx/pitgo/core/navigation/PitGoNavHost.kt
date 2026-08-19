package com.trustarx.pitgo.core.navigation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.trustarx.pitgo.core.app.PitGoAppContainer
import com.trustarx.pitgo.core.auth.model.AppSessionState
import com.trustarx.pitgo.core.common.model.DemoServiceId
import com.trustarx.pitgo.core.ui.BottomItemId
import com.trustarx.pitgo.core.vehicle.model.VehicleFormOrigin
import com.trustarx.pitgo.features.garage.ui.GarageRoute
import com.trustarx.pitgo.features.garage.ui.VehicleFormRoute
import com.trustarx.pitgo.features.garage.viewmodel.GarageViewModel
import com.trustarx.pitgo.features.garage.viewmodel.VehicleFormViewModel
import com.trustarx.pitgo.features.home.ui.HomeRoute
import com.trustarx.pitgo.features.home.viewmodel.HomeViewModel
import com.trustarx.pitgo.features.login.ui.LoginRoute
import com.trustarx.pitgo.features.login.viewmodel.LoginViewModel
import com.trustarx.pitgo.features.onboarding.ui.VehicleOnboardingRoute
import com.trustarx.pitgo.features.onboarding.viewmodel.OnboardingDestination
import com.trustarx.pitgo.features.onboarding.viewmodel.VehicleOnboardingViewModel
import com.trustarx.pitgo.features.profile.ui.ProfileScreen
import com.trustarx.pitgo.features.requestservice.ui.RequestServiceRoute
import com.trustarx.pitgo.features.session.ui.SessionGateScreen
import com.trustarx.pitgo.features.session.viewmodel.SessionViewModel
import com.trustarx.pitgo.features.signup.ui.SignUpRoute
import com.trustarx.pitgo.features.signup.viewmodel.SignUpViewModel
import com.trustarx.pitgo.features.welcome.ui.WelcomeRoute

@Composable
fun PitGoNavHost(container: PitGoAppContainer) {
    val navController = rememberNavController()
    val sessionViewModel: SessionViewModel = viewModel(
        factory = SessionViewModel.factory(container.sessionRepository),
    )
    val sessionState by sessionViewModel.state.collectAsStateWithLifecycle()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val usesAppSurface = currentRoute in setOf(
        PitGoRoutes.SessionGate,
        PitGoRoutes.Home,
        PitGoRoutes.Garage,
        PitGoRoutes.Login,
        PitGoRoutes.Profile,
        PitGoRoutes.VehicleOnboarding,
        PitGoRoutes.VehicleFormPattern,
        PitGoRoutes.RequestServicePattern,
        PitGoRoutes.SignUp,
    )
    SystemBarIconAppearance(usesAppSurface && !isSystemInDarkTheme())

    LaunchedEffect(sessionState, currentRoute) {
        val publicRoute = currentRoute in setOf(
            PitGoRoutes.Welcome,
            PitGoRoutes.Login,
            PitGoRoutes.SignUp,
        )
        when (val state = sessionState) {
            is AppSessionState.Unauthenticated -> if (!publicRoute) {
                navController.navigateRoot(PitGoRoutes.Welcome)
            }
            is AppSessionState.Authenticated -> {
                if (publicRoute || currentRoute == PitGoRoutes.SessionGate) {
                    val destination = if (
                        container.onboardingRepository.isPending(state.account.userId)
                    ) PitGoRoutes.VehicleOnboarding else PitGoRoutes.Home
                    navController.navigateRoot(destination)
                }
            }
            AppSessionState.Initializing,
            AppSessionState.Verifying,
            -> if (!publicRoute && currentRoute != PitGoRoutes.SessionGate) {
                navController.navigateRoot(PitGoRoutes.SessionGate)
            }
            AppSessionState.OfflineVerification,
            is AppSessionState.ProfileBlocked,
            -> if (currentRoute != PitGoRoutes.SessionGate) {
                navController.navigateRoot(PitGoRoutes.SessionGate)
            }
        }
    }

    NavHost(navController = navController, startDestination = PitGoRoutes.SessionGate) {
        composable(PitGoRoutes.SessionGate) {
            SessionGateScreen(
                state = sessionState,
                onRetry = sessionViewModel::retry,
                onLogout = sessionViewModel::logout,
            )
        }

        composable(PitGoRoutes.Welcome) {
            var navigationPending by remember { mutableStateOf(false) }
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { navigationPending = false }
            val navigateToSignUp = dropUnlessResumed {
                if (!navigationPending) {
                    navigationPending = true
                    navController.navigate(PitGoRoutes.SignUp) { launchSingleTop = true }
                }
            }
            val navigateToLogin = dropUnlessResumed {
                if (!navigationPending) {
                    navigationPending = true
                    navController.navigate(PitGoRoutes.Login) { launchSingleTop = true }
                }
            }
            WelcomeRoute(
                onCreateAccount = navigateToSignUp,
                onEnter = navigateToLogin,
                onRegisterCompany = {},
            )
        }

        composable(PitGoRoutes.Login) {
            val loginViewModel: LoginViewModel = viewModel(
                factory = LoginViewModel.factory(container.sessionRepository),
            )
            LoginRoute(
                onBack = { navController.popBackStack() },
                viewModel = loginViewModel,
            )
        }

        composable(PitGoRoutes.SignUp) {
            val signUpViewModel: SignUpViewModel = viewModel(
                factory = SignUpViewModel.factory(container.signUpRepository),
            )
            SignUpRoute(
                onBack = { navController.popBackStack() },
                onAccountCreated = sessionViewModel::refresh,
                viewModel = signUpViewModel,
            )
        }

        composable(PitGoRoutes.VehicleOnboarding) {
            if (sessionState is AppSessionState.Authenticated) {
                val onboardingViewModel: VehicleOnboardingViewModel = viewModel(
                    factory = VehicleOnboardingViewModel.factory(
                        container.onboardingRepository,
                        container.sessionRepository,
                    ),
                )
                VehicleOnboardingRoute(
                    viewModel = onboardingViewModel,
                    onCompleted = { destination ->
                        when (destination) {
                            OnboardingDestination.HOME ->
                                navController.navigateRoot(PitGoRoutes.Home)
                            OnboardingDestination.VEHICLE_FORM -> navController.navigateRoot(
                                PitGoRoutes.newVehicle(VehicleFormOrigin.ONBOARDING.name),
                            )
                        }
                    },
                )
            }
        }

        composable(
            route = PitGoRoutes.VehicleFormPattern,
            arguments = listOf(navArgument(PitGoRoutes.VehicleFormOriginArg) {
                type = NavType.StringType
                nullable = false
                defaultValue = VehicleFormOrigin.GARAGE.name
            }),
        ) { entry ->
            val origin = runCatching {
                VehicleFormOrigin.valueOf(
                    entry.arguments?.getString(PitGoRoutes.VehicleFormOriginArg).orEmpty(),
                )
            }.getOrDefault(VehicleFormOrigin.GARAGE)
            if (sessionState is AppSessionState.Authenticated) {
                val formViewModel: VehicleFormViewModel = viewModel(
                    factory = VehicleFormViewModel.factory(
                        origin,
                        container.vehicleRepository,
                        container.sessionRepository,
                    ),
                )
                VehicleFormRoute(
                    viewModel = formViewModel,
                    onBack = {
                        if (origin == VehicleFormOrigin.ONBOARDING) {
                            navController.navigateRoot(PitGoRoutes.Home)
                        } else {
                            navController.popBackStack()
                        }
                    },
                    onSaved = {
                        if (origin == VehicleFormOrigin.ONBOARDING) {
                            sessionViewModel.refresh()
                        } else {
                            navController.popBackStack()
                        }
                    },
                    onSessionExpired = sessionViewModel::refresh,
                )
            }
        }

        composable(PitGoRoutes.Home) {
            val account = (sessionState as? AppSessionState.Authenticated)?.account
            if (account != null) {
                val homeViewModel: HomeViewModel = viewModel(
                    factory = HomeViewModel.factory(container.sessionRepository),
                )
                var navigationPending by remember { mutableStateOf(false) }
                var selectedServiceId by remember { mutableStateOf<DemoServiceId?>(null) }
                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                    navigationPending = false
                    selectedServiceId = null
                }
                val performNavigation = dropUnlessResumed {
                    selectedServiceId?.let { serviceId ->
                        if (!navigationPending) {
                            navigationPending = true
                            navController.navigate(PitGoRoutes.requestService(serviceId)) {
                                launchSingleTop = true
                            }
                        }
                    }
                }
                HomeRoute(
                    viewModel = homeViewModel,
                    onServiceClick = { serviceId ->
                        selectedServiceId = serviceId
                        performNavigation()
                    },
                    onOpenGarage = {
                        navController.navigate(PitGoRoutes.Garage) { launchSingleTop = true }
                    },
                    onBottomItemClick = { item ->
                        when (item) {
                            BottomItemId.HOME -> Unit
                            BottomItemId.PROFILE -> navController.navigate(PitGoRoutes.Profile) {
                                launchSingleTop = true
                            }
                            BottomItemId.GARAGE -> navController.navigate(PitGoRoutes.Garage) {
                                launchSingleTop = true
                            }
                            else -> Unit
                        }
                    },
                )
            }
        }

        composable(PitGoRoutes.Garage) {
            if (sessionState is AppSessionState.Authenticated) {
                val garageViewModel: GarageViewModel = viewModel(
                    factory = GarageViewModel.factory(
                        container.vehicleRepository,
                        container.sessionRepository,
                    ),
                )
                GarageRoute(
                    viewModel = garageViewModel,
                    onAddVehicle = {
                        navController.navigate(
                            PitGoRoutes.newVehicle(VehicleFormOrigin.GARAGE.name),
                        ) { launchSingleTop = true }
                    },
                    onBottomItemClick = { item ->
                        when (item) {
                            BottomItemId.HOME -> sessionViewModel.refresh()
                            BottomItemId.GARAGE -> Unit
                            BottomItemId.PROFILE -> navController.navigate(PitGoRoutes.Profile) {
                                launchSingleTop = true
                            }
                            else -> Unit
                        }
                    },
                )
            }
        }

        composable(PitGoRoutes.Profile) {
            val account = (sessionState as? AppSessionState.Authenticated)?.account
            if (account != null) {
                ProfileScreen(
                    account = account,
                    onBottomItemClick = { item ->
                        when (item) {
                            BottomItemId.HOME -> navController.navigate(PitGoRoutes.Home) {
                                popUpTo(PitGoRoutes.Home) { inclusive = false }
                                launchSingleTop = true
                            }
                            BottomItemId.PROFILE -> Unit
                            BottomItemId.GARAGE -> navController.navigate(PitGoRoutes.Garage) {
                                launchSingleTop = true
                            }
                            else -> Unit
                        }
                    },
                    onLogout = sessionViewModel::logout,
                )
            }
        }

        composable(
            route = PitGoRoutes.RequestServicePattern,
            arguments = listOf(navArgument(PitGoRoutes.RequestServiceArg) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            }),
        ) {
            if (sessionState is AppSessionState.Authenticated) {
                RequestServiceRoute(
                    vehicle = (sessionState as AppSessionState.Authenticated).account.primaryVehicle,
                    onBack = { navController.popBackStack() },
                    onSubmit = {},
                    onBottomItemClick = { item ->
                        when (item) {
                            BottomItemId.HOME -> navController.popBackStack(PitGoRoutes.Home, false)
                            BottomItemId.PROFILE -> navController.navigate(PitGoRoutes.Profile) {
                                launchSingleTop = true
                            }
                            BottomItemId.GARAGE -> navController.navigate(PitGoRoutes.Garage) {
                                launchSingleTop = true
                            }
                            else -> Unit
                        }
                    },
                )
            }
        }
    }
}

private fun androidx.navigation.NavHostController.navigateRoot(route: String) {
    if (currentDestination?.route == route) return
    navigate(route) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
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

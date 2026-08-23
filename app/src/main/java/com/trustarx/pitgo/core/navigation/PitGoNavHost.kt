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
import com.trustarx.pitgo.features.providerdashboard.ui.ProviderDashboardRoute
import com.trustarx.pitgo.features.providerdashboard.viewmodel.ProviderDashboardViewModel
import com.trustarx.pitgo.features.providerregistration.ui.ProviderRegistrationRoute
import com.trustarx.pitgo.features.providerregistration.viewmodel.ProviderRegistrationViewModel
import com.trustarx.pitgo.features.providersignup.ui.ProviderSignUpRoute
import com.trustarx.pitgo.features.providersignup.viewmodel.ProviderSignUpViewModel
import com.trustarx.pitgo.features.providermigration.model.ProviderMigrationStage
import com.trustarx.pitgo.features.providermigration.ui.ProviderMigrationRoute
import com.trustarx.pitgo.features.providermigration.viewmodel.ProviderMigrationViewModel
import com.trustarx.pitgo.features.providertracking.ui.ProviderTrackingRoute
import com.trustarx.pitgo.features.providertracking.viewmodel.ProviderTrackingViewModel
import com.trustarx.pitgo.features.appointmenttracking.ui.AppointmentTrackingRoute
import com.trustarx.pitgo.features.appointmenttracking.viewmodel.AppointmentTrackingViewModel
import com.trustarx.pitgo.features.providermap.ui.ServiceMapRoute
import com.trustarx.pitgo.features.providermap.viewmodel.ServiceMapViewModel
import com.trustarx.pitgo.features.requestservice.ui.RequestServiceRoute
import com.trustarx.pitgo.features.requestservice.viewmodel.RequestServiceViewModel
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
    val migrationStage by container.providerMigrationCoordinator.stage.collectAsStateWithLifecycle()
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
        PitGoRoutes.SignUpPattern,
        PitGoRoutes.ProviderSignUp,
        PitGoRoutes.ProviderRegistration,
        PitGoRoutes.ProviderDashboard,
        PitGoRoutes.ProviderMigration,
        PitGoRoutes.ProviderTracking,
        PitGoRoutes.AppointmentTracking,
        PitGoRoutes.ServiceMap,
    )
    SystemBarIconAppearance(usesAppSurface && !isSystemInDarkTheme())

    LaunchedEffect(sessionState, currentRoute) {
        val publicRoute = currentRoute in setOf(
            PitGoRoutes.Welcome,
            PitGoRoutes.Login,
            PitGoRoutes.SignUpPattern,
            PitGoRoutes.ProviderSignUp,
        )
        when (val state = sessionState) {
            is AppSessionState.Unauthenticated -> if (!publicRoute) {
                navController.navigateRoot(PitGoRoutes.Welcome)
            }
            is AppSessionState.Authenticated.Client -> {
                val providerOnly = currentRoute in setOf(
                    PitGoRoutes.ProviderRegistration,
                    PitGoRoutes.ProviderDashboard,
                )
                if (publicRoute || providerOnly || currentRoute == PitGoRoutes.SessionGate) {
                    val destination = if (container.onboardingRepository.isPending(state.account.userId)) {
                        PitGoRoutes.VehicleOnboarding
                    } else PitGoRoutes.Home
                    navController.navigateRoot(destination)
                }
            }
            is AppSessionState.Authenticated.ProviderOnboarding -> {
                val destination = if (migrationStage == ProviderMigrationStage.AWAITING_PROVIDER ||
                    migrationStage == ProviderMigrationStage.CLAIMING
                ) PitGoRoutes.ProviderMigration else PitGoRoutes.ProviderRegistration
                if (currentRoute != destination) {
                    navController.navigateRoot(destination)
                }
            }
            is AppSessionState.Authenticated.ProviderReady -> {
                if (currentRoute !in setOf(
                        PitGoRoutes.ProviderDashboard,
                        PitGoRoutes.ProviderTracking,
                        PitGoRoutes.AppointmentTracking,
                    )
                ) {
                    navController.navigateRoot(PitGoRoutes.ProviderDashboard)
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
                    navController.navigate(PitGoRoutes.SignUp) {
                        launchSingleTop = true
                    }
                }
            }
            val navigateToLogin = dropUnlessResumed {
                if (!navigationPending) {
                    navigationPending = true
                    navController.navigate(PitGoRoutes.Login) { launchSingleTop = true }
                }
            }
            val navigateToProviderSignUp = dropUnlessResumed {
                if (!navigationPending) {
                    navigationPending = true
                    navController.navigate(PitGoRoutes.ProviderSignUp) {
                        launchSingleTop = true
                    }
                }
            }
            WelcomeRoute(
                onCreateAccount = navigateToSignUp,
                onEnter = navigateToLogin,
                onRegisterCompany = navigateToProviderSignUp,
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
                factory = SignUpViewModel.factory(
                    repository = container.signUpRepository,
                    lockedPersonType = null,
                ),
            )
            SignUpRoute(
                onBack = { navController.popBackStack() },
                onAccountCreated = sessionViewModel::refresh,
                viewModel = signUpViewModel,
            )
        }

        composable(PitGoRoutes.ProviderSignUp) {
            val providerSignUpViewModel: ProviderSignUpViewModel = viewModel(
                factory = ProviderSignUpViewModel.factory(container.providerSignUpRepository),
            )
            ProviderSignUpRoute(
                viewModel = providerSignUpViewModel,
                onBack = { navController.popBackStack() },
                onAccountCreated = sessionViewModel::refresh,
            )
        }

        composable(PitGoRoutes.VehicleOnboarding) {
            if (sessionState is AppSessionState.Authenticated.Client) {
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
            if (sessionState is AppSessionState.Authenticated.Client) {
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
            val account = (sessionState as? AppSessionState.Authenticated.Client)?.account
            if (account != null) {
                val homeViewModel: HomeViewModel = viewModel(
                    factory = HomeViewModel.factory(
                        container.sessionRepository,
                        container.serviceCatalogRepository,
                    ),
                )
                var navigationPending by remember { mutableStateOf(false) }
                var selectedServiceId by remember { mutableStateOf<String?>(null) }
                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                    navigationPending = false
                    selectedServiceId = null
                }
                val performNavigation = dropUnlessResumed {
                    selectedServiceId?.let { serviceId ->
                        if (!navigationPending) {
                            navigationPending = true
                            navController.navigate(PitGoRoutes.serviceMap(serviceId)) {
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
            if (sessionState is AppSessionState.Authenticated.Client) {
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
            val clientSession = sessionState as? AppSessionState.Authenticated.Client
            val account = clientSession?.account
            if (account != null) {
                ProfileScreen(
                    account = account,
                    legacyProviderMigrationAvailable = clientSession.legacyProviderMigrationAvailable,
                    onMigrateProvider = { navController.navigate(PitGoRoutes.ProviderMigration) },
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

        composable(PitGoRoutes.ProviderMigration) {
            val providerMigrationViewModel: ProviderMigrationViewModel = viewModel(
                factory = ProviderMigrationViewModel.factory(
                    container.providerMigrationRepository,
                    container.providerMigrationCoordinator,
                ),
            )
            ProviderMigrationRoute(
                viewModel = providerMigrationViewModel,
                providerSession = sessionState is AppSessionState.Authenticated.ProviderOnboarding,
                onSwitchAccount = sessionViewModel::logout,
                onCompleted = sessionViewModel::refresh,
            )
        }

        composable(PitGoRoutes.ProviderRegistration) {
            val account = (sessionState as? AppSessionState.Authenticated.ProviderOnboarding)?.session
            if (account != null) {
                val providerViewModel: ProviderRegistrationViewModel = viewModel(
                    factory = ProviderRegistrationViewModel.factory(
                        account = account,
                        repository = container.providerRegistrationRepository,
                        addressVerificationRepository = container.providerAddressVerificationRepository,
                        sessionRepository = container.sessionRepository,
                    ),
                )
                ProviderRegistrationRoute(
                    viewModel = providerViewModel,
                    onBack = {
                        if (!navController.popBackStack()) {
                            navController.navigateRoot(PitGoRoutes.Welcome)
                        }
                    },
                    onFinished = {
                        sessionViewModel.refresh()
                    },
                    onSessionExpired = sessionViewModel::refresh,
                )
            }
        }

        composable(PitGoRoutes.ProviderDashboard) {
            val account = (sessionState as? AppSessionState.Authenticated.ProviderReady)?.account
            if (account != null) {
                val dashboardViewModel: ProviderDashboardViewModel = viewModel(
                    factory = ProviderDashboardViewModel.factory(
                        account,
                        container.providerDashboardRepository,
                    ),
                )
                ProviderDashboardRoute(
                    viewModel = dashboardViewModel,
                    onLogout = sessionViewModel::logout,
                )
            }
        }

        composable(
            route = PitGoRoutes.ProviderTracking,
            arguments = listOf(navArgument(PitGoRoutes.AppointmentIdArg) { type = NavType.StringType }),
        ) { entry ->
            val appointmentId = entry.arguments?.getString(PitGoRoutes.AppointmentIdArg).orEmpty()
            if (sessionState is AppSessionState.Authenticated.ProviderReady && appointmentId.isNotBlank()) {
                val trackingViewModel: ProviderTrackingViewModel = viewModel(
                    factory = ProviderTrackingViewModel.factory(
                        appointmentId, container.providerTrackingStateRepository,
                    ),
                )
                ProviderTrackingRoute(trackingViewModel)
            }
        }

        composable(
            route = PitGoRoutes.AppointmentTracking,
            arguments = listOf(navArgument(PitGoRoutes.AppointmentIdArg) { type = NavType.StringType }),
        ) { entry ->
            val appointmentId = entry.arguments?.getString(PitGoRoutes.AppointmentIdArg).orEmpty()
            if (sessionState is AppSessionState.Authenticated && appointmentId.isNotBlank()) {
                val trackingViewModel: AppointmentTrackingViewModel = viewModel(
                    factory = AppointmentTrackingViewModel.factory(
                        appointmentId, container.appointmentTrackingRepository,
                    ),
                )
                AppointmentTrackingRoute(
                    viewModel = trackingViewModel,
                    onBack = { navController.popBackStack() },
                    onSessionExpired = sessionViewModel::refresh,
                )
            }
        }

        composable(
            route = PitGoRoutes.ServiceMap,
            arguments = listOf(navArgument(PitGoRoutes.ServiceMapArg) { type = NavType.StringType }),
        ) { entry ->
            val serviceId = entry.arguments?.getString(PitGoRoutes.ServiceMapArg).orEmpty()
            if (sessionState is AppSessionState.Authenticated.Client) {
                val mapViewModel: ServiceMapViewModel = viewModel(
                    factory = ServiceMapViewModel.factory(
                        serviceId,
                        container.serviceMapRepository,
                        container.deviceLocationRepository,
                        container.connectivityObserver,
                    ),
                )
                ServiceMapRoute(
                    viewModel = mapViewModel,
                    onBack = { navController.popBackStack() },
                    onRequestService = { providerId, selectedServiceId ->
                        navController.navigate(
                            PitGoRoutes.requestService(selectedServiceId, providerId),
                        ) { launchSingleTop = true }
                    },
                    onSessionExpired = sessionViewModel::refresh,
                )
            }
        }

        composable(
            route = PitGoRoutes.RequestServicePattern,
            arguments = listOf(
                navArgument(PitGoRoutes.RequestServiceArg) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument(PitGoRoutes.RequestProviderArg) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            val clientSession = sessionState as? AppSessionState.Authenticated.Client
            if (clientSession != null) {
                val requestViewModel: RequestServiceViewModel =
                    viewModel(
                        factory = RequestServiceViewModel.factory(container.serviceCatalogRepository),
                    )
                RequestServiceRoute(
                    viewModel = requestViewModel,
                    vehicle = clientSession.account.primaryVehicle,
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

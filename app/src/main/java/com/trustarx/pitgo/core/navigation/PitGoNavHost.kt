package com.trustarx.pitgo.core.navigation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import com.trustarx.pitgo.core.account.model.ProviderLocationMode
import androidx.compose.foundation.isSystemInDarkTheme
import kotlinx.coroutines.delay
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
import com.trustarx.pitgo.features.clientaddress.ui.ClientAddressRoute
import com.trustarx.pitgo.features.clientaddress.viewmodel.ClientAddressViewModel
import com.trustarx.pitgo.features.garage.ui.GarageRoute
import com.trustarx.pitgo.features.garage.ui.VehicleFormRoute
import com.trustarx.pitgo.features.garage.viewmodel.GarageViewModel
import com.trustarx.pitgo.features.garage.viewmodel.VehicleFormViewModel
import com.trustarx.pitgo.features.home.ui.HomeRoute
import com.trustarx.pitgo.features.home.viewmodel.HomeViewModel
import com.trustarx.pitgo.features.login.ui.LoginRoute
import com.trustarx.pitgo.features.login.viewmodel.LoginViewModel
import com.trustarx.pitgo.features.onboarding.ui.VehicleOnboardingRoute
import com.trustarx.pitgo.features.onboarding.viewmodel.VehicleOnboardingViewModel
import com.trustarx.pitgo.features.profile.ui.ProfileRoute
import com.trustarx.pitgo.features.profile.viewmodel.ProfileViewModel
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
import com.trustarx.pitgo.core.catalog.model.HomeService
import com.trustarx.pitgo.core.catalog.model.ServiceDispatchKind
import com.trustarx.pitgo.features.mobiledispatch.ui.ClientMobileDispatchRoute
import com.trustarx.pitgo.features.mobiledispatch.ui.ProviderIncomingRequestRoute
import com.trustarx.pitgo.features.mobiledispatch.viewmodel.ClientMobileDispatchViewModel
import com.trustarx.pitgo.features.mobiledispatch.viewmodel.ProviderIncomingRequestViewModel
import com.trustarx.pitgo.features.mobiledispatch.viewmodel.DispatchResumeViewModel
import com.trustarx.pitgo.features.mobiledispatch.viewmodel.DispatchResumeState
import com.trustarx.pitgo.features.mobiledispatch.model.MobileAppointmentStatus
import com.trustarx.pitgo.features.pittec.ui.PitTecProviderDashboardRoute
import com.trustarx.pitgo.features.pittec.ui.PitTecRoute
import com.trustarx.pitgo.features.pittec.viewmodel.PitTecClientMockViewModel
import com.trustarx.pitgo.features.pittec.viewmodel.PitTecProviderMockViewModel

private const val TRACKING_WATCHDOG_INTERVAL_MS = 30_000L

@Composable
fun PitGoNavHost(container: PitGoAppContainer) {
    val navController = rememberNavController()
    val sessionViewModel: SessionViewModel = viewModel(
        factory = SessionViewModel.factory(container.sessionRepository),
    )
    val sessionState by sessionViewModel.state.collectAsStateWithLifecycle()
    val dispatchResumeViewModel: DispatchResumeViewModel = viewModel(
        factory = DispatchResumeViewModel.factory(
            container.sessionRepository, container.mobileDispatchRepository,
        ),
    )
    val dispatchResumeState by dispatchResumeViewModel.state.collectAsStateWithLifecycle()
    val migrationStage by container.providerMigrationCoordinator.stage.collectAsStateWithLifecycle()

    // PitTec's entirely mocked prototype (specs/017-pittec-mocked-ui): two independent, in-memory
    // state machines scoped to this NavHost's owner and keyed by session so they survive internal
    // navigation but never outlive the authenticated account. See research.md #2.
    val clientMockSessionKey = (sessionState as? AppSessionState.Authenticated.Client)?.account?.userId ?: "none"
    val pitTecClientMockViewModel: PitTecClientMockViewModel = viewModel(
        key = "pittec-client-mock-$clientMockSessionKey",
        factory = PitTecClientMockViewModel.factory("pittec", container.deviceLocationRepository),
    )
    val providerReadySession = sessionState as? AppSessionState.Authenticated.ProviderReady
    val providerMockSessionKey = providerReadySession?.account?.userId ?: "none"
    val pitTecProviderMockViewModel: PitTecProviderMockViewModel = viewModel(
        key = "pittec-provider-mock-$providerMockSessionKey",
        factory = PitTecProviderMockViewModel.factory(
            providerReadySession?.account?.name ?: "PitTec",
            container.deviceLocationRepository,
        ),
    )
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val usesAppSurface = currentRoute in setOf(
        PitGoRoutes.SessionGate,
        PitGoRoutes.Home,
        PitGoRoutes.Garage,
        PitGoRoutes.Login,
        PitGoRoutes.Profile,
        PitGoRoutes.ClientAddress,
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
        PitGoRoutes.MobileDispatchNew,
        PitGoRoutes.MobileDispatch,
        PitGoRoutes.ProviderRequest,
        PitGoRoutes.PitTec,
    )
    SystemBarIconAppearance(usesAppSurface && !isSystemInDarkTheme())

    // Resuming provider location tracking used to live only in ProviderDashboardViewModel.init(),
    // which never runs when a ProviderReady session with an active appointment gets auto-navigated
    // straight to ProviderRequest/AppointmentTracking (the very next effect below) without ever
    // composing the Dashboard route. That left the foreground tracking service dead — server-side
    // still shows available/MOBILE, but nothing publishes — until the provider happened to visit
    // the Dashboard tab manually. Hoisted here so it fires regardless of which screen the provider
    // lands on.
    //
    // A one-shot check on session start isn't enough either: reproduced live with two devices
    // fully patched (deviceidle whitelist granted, ProviderLocationTrackingService.onDestroy() now
    // reflects Stopped) — the OS (Samsung's own "sleeping apps" layer, on top of the standard
    // Android battery API) can still kill just the Service component mid-session while the rest of
    // the app process, including this composition, keeps running untouched. Nothing else ever
    // notices that death. Looping with a periodic re-check turns this into a real watchdog:
    // start() is idempotent when the service is genuinely still alive (no-ops), and now that
    // onDestroy() always reports Stopped, a real death is reliably picked up within one interval.
    LaunchedEffect(sessionState) {
        while (true) {
            val ready = sessionState as? AppSessionState.Authenticated.ProviderReady ?: return@LaunchedEffect
            if (ready.account.available && ready.account.locationMode == ProviderLocationMode.MOBILE) {
                container.providerTrackingStateRepository.start()
            }
            delay(TRACKING_WATCHDOG_INTERVAL_MS)
        }
    }

    // PitTec's Home is now the fully mocked prototype (specs/017-pittec-mocked-ui): it must never
    // resume real dispatch, Realtime or provider location publication. A PitTec session stops any
    // legacy tracking/alert job left over from a previous MOBILE/real-PitTec run without an RPC or
    // an availability mutation — see contracts/provider-pittec-ui.md "Route selection and isolation".
    LaunchedEffect(sessionState) {
        val ready = sessionState as? AppSessionState.Authenticated.ProviderReady ?: return@LaunchedEffect
        if (ready.account.locationMode == ProviderLocationMode.PITTEC) {
            container.providerTrackingStateRepository.stopLocally()
        }
    }

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
                    PitGoRoutes.ProviderRequest,
                )
                if (publicRoute || providerOnly || currentRoute == PitGoRoutes.SessionGate) {
                    val destination = if (
                        state.account.primaryVehicle == null &&
                        container.onboardingRepository.isPending(state.account.userId)
                    ) {
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
                        PitGoRoutes.ProviderRequest,
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

    // Tracks the (id, revision) of the last active search/appointment this effect already
    // auto-navigated for. Without it, this effect re-fires on every recomposition where
    // dispatchResumeState is still Active — including right after a manual "voltar" — snapping
    // the user straight back and making the back button look stuck. Only a genuinely new
    // search/appointment, or a real revision bump on the same one, should navigate again; a
    // cold start naturally starts from null, so the "resume on session start" behavior is kept.
    // See contracts/appointment-map-and-navigation.md.
    var lastAutoNavigated by remember { mutableStateOf<Pair<String, Long>?>(null) }
    LaunchedEffect(dispatchResumeState, sessionState, currentRoute) {
        val active = dispatchResumeState as? DispatchResumeState.Active ?: return@LaunchedEffect
        val related = currentRoute in setOf(
            PitGoRoutes.MobileDispatchNew, PitGoRoutes.MobileDispatch,
            PitGoRoutes.ProviderRequest, PitGoRoutes.AppointmentTracking,
        )
        if (related) return@LaunchedEffect
        val target = when (sessionState) {
            is AppSessionState.Authenticated.Client -> active.snapshot.appointment?.let { it.id to it.revision }
                ?: (active.snapshot.searchId to active.snapshot.revision)
            is AppSessionState.Authenticated.ProviderReady ->
                active.snapshot.appointment?.let { it.id to it.revision }
            else -> null
        } ?: return@LaunchedEffect
        if (target == lastAutoNavigated) return@LaunchedEffect
        lastAutoNavigated = target
        when (sessionState) {
            is AppSessionState.Authenticated.Client -> {
                val appointment = active.snapshot.appointment
                if (appointment != null && appointment.status in setOf(MobileAppointmentStatus.ACCEPTED, MobileAppointmentStatus.IN_PROGRESS)) {
                    navController.navigate(PitGoRoutes.appointmentTracking(appointment.id)) { launchSingleTop = true }
                } else navController.navigate(PitGoRoutes.mobileDispatch(active.snapshot.searchId)) { launchSingleTop = true }
            }
            is AppSessionState.Authenticated.ProviderReady -> active.snapshot.appointment?.let {
                navController.navigate(PitGoRoutes.providerRequest(it.id)) { launchSingleTop = true }
            }
            else -> Unit
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
                    onRegisterVehicle = {
                        navController.navigate(
                            PitGoRoutes.newVehicle(VehicleFormOrigin.ONBOARDING.name),
                        ) {
                            launchSingleTop = true
                        }
                    },
                    onCompleted = { navController.navigateRoot(PitGoRoutes.Home) },
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
                        container.onboardingRepository,
                    ),
                )
                VehicleFormRoute(
                    viewModel = formViewModel,
                    onBack = {
                        if (origin == VehicleFormOrigin.ONBOARDING) {
                            if (!navController.popBackStack()) {
                                navController.navigateRoot(PitGoRoutes.VehicleOnboarding)
                            }
                        } else {
                            navController.popBackStack()
                        }
                    },
                    onSaved = {
                        if (origin == VehicleFormOrigin.ONBOARDING) {
                            navController.navigateRoot(PitGoRoutes.Home)
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
                        container.clientAddressRepository,
                    ),
                )
                var navigationPending by remember { mutableStateOf(false) }
                var selectedService by remember { mutableStateOf<HomeService?>(null) }
                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                    navigationPending = false
                    selectedService = null
                    homeViewModel.refreshAddress()
                }
                val performNavigation = dropUnlessResumed {
                    selectedService?.let { service ->
                        if (!navigationPending) {
                            navigationPending = true
                            val destination = when (service.dispatchKind) {
                                ServiceDispatchKind.MOBILE_SEQUENTIAL -> PitGoRoutes.mobileDispatchNew(service.id)
                                ServiceDispatchKind.PITTEC_AUTO_SEQUENTIAL -> PitGoRoutes.pitTec(service.id)
                                ServiceDispatchKind.BROWSE -> PitGoRoutes.serviceMap(service.id)
                            }
                            navController.navigate(destination) {
                                launchSingleTop = true
                            }
                        }
                    }
                }
                HomeRoute(
                    viewModel = homeViewModel,
                    onServiceClick = { service ->
                        selectedService = service
                        performNavigation()
                    },
                    onOpenGarage = {
                        navController.navigate(PitGoRoutes.Garage) { launchSingleTop = true }
                    },
                    onOpenAddress = {
                        navController.navigate(PitGoRoutes.ClientAddress) { launchSingleTop = true }
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
                val profileViewModel: ProfileViewModel = viewModel(
                    factory = ProfileViewModel.factory(
                        container.clientAddressRepository, container.appearancePreferences,
                    ),
                )
                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { profileViewModel.refreshAddress() }
                ProfileRoute(
                    viewModel = profileViewModel,
                    account = account,
                    legacyProviderMigrationAvailable = clientSession.legacyProviderMigrationAvailable,
                    onMigrateProvider = { navController.navigate(PitGoRoutes.ProviderMigration) },
                    onOpenAddress = {
                        navController.navigate(PitGoRoutes.ClientAddress) { launchSingleTop = true }
                    },
                    onOpenGarage = {
                        navController.navigate(PitGoRoutes.Garage) { launchSingleTop = true }
                    },
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

        composable(PitGoRoutes.ClientAddress) {
            if (sessionState is AppSessionState.Authenticated.Client) {
                val clientAddressViewModel: ClientAddressViewModel = viewModel(
                    factory = ClientAddressViewModel.factory(
                        container.clientAddressRepository, container.viaCepAddressLookupRepository,
                    ),
                )
                ClientAddressRoute(
                    viewModel = clientAddressViewModel,
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                    onSessionExpired = sessionViewModel::refresh,
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
                        viaCepAddressLookupRepository = container.viaCepAddressLookupRepository,
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
                    onLogout = sessionViewModel::logout,
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
                if (account.locationMode == ProviderLocationMode.PITTEC) {
                    // Mocked prototype: no ProviderDashboardViewModel/repository is created for
                    // PITTEC — see contracts/provider-pittec-ui.md "Route selection and isolation".
                    PitTecProviderDashboardRoute(
                        viewModel = pitTecProviderMockViewModel,
                        onLogout = sessionViewModel::logout,
                    )
                } else {
                    val dashboardViewModel: ProviderDashboardViewModel = viewModel(
                        factory = ProviderDashboardViewModel.factory(
                            account,
                            container.providerDashboardRepository,
                            container.sessionRepository,
                            container.providerTrackingStateRepository,
                            container.mobileDispatchRepository,
                            container.deviceLocationRepository,
                            container.providerIncomingRequestNotifier,
                        ),
                    )
                    ProviderDashboardRoute(
                        viewModel = dashboardViewModel,
                        onLogout = sessionViewModel::logout,
                        onOpenIncomingRequest = { navController.navigate(PitGoRoutes.providerRequest(it)) },
                    )
                }
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
                val isClientSession = sessionState is AppSessionState.Authenticated.Client
                val trackingViewModel: AppointmentTrackingViewModel = viewModel(
                    factory = AppointmentTrackingViewModel.factory(
                        appointmentId, container.appointmentTrackingRepository,
                        container.mobileDispatchRepository, container.pitTecRouteRepository,
                        container.deviceLocationRepository,
                        isClient = isClientSession,
                    ),
                )
                AppointmentTrackingRoute(
                    viewModel = trackingViewModel,
                    isClient = isClientSession,
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
            route = PitGoRoutes.PitTec,
            arguments = listOf(navArgument(PitGoRoutes.PitTecServiceArg) { type = NavType.StringType }),
        ) { entry ->
            val serviceId = entry.arguments?.getString(PitGoRoutes.PitTecServiceArg).orEmpty()
            if (sessionState is AppSessionState.Authenticated.Client && serviceId.isNotBlank()) {
                // Mocked prototype: no PitTecRepository/connectivity dependency and no navigation
                // to the real AppointmentTracking route — see contracts/client-pittec-ui.md.
                PitTecRoute(
                    viewModel = pitTecClientMockViewModel,
                    onBack = { navController.popBackStack() },
                )
            }
        }

        composable(
            route = PitGoRoutes.MobileDispatchNew,
            arguments = listOf(navArgument(PitGoRoutes.MobileDispatchServiceArg) { type = NavType.StringType }),
        ) { entry ->
            val serviceId = entry.arguments?.getString(PitGoRoutes.MobileDispatchServiceArg).orEmpty()
            if (sessionState is AppSessionState.Authenticated.Client && serviceId.isNotBlank()) {
                val dispatchViewModel: ClientMobileDispatchViewModel = viewModel(
                    factory = ClientMobileDispatchViewModel.factory(
                        serviceId, container.mobileDispatchRepository, container.deviceLocationRepository,
                    ),
                )
                ClientMobileDispatchRoute(
                    viewModel = dispatchViewModel,
                    onBack = { navController.popBackStack() },
                    onSessionExpired = sessionViewModel::refresh,
                    onAccepted = { navController.navigate(PitGoRoutes.appointmentTracking(it)) { launchSingleTop = true } },
                )
            }
        }

        composable(
            route = PitGoRoutes.ProviderRequest,
            arguments = listOf(navArgument(PitGoRoutes.AppointmentIdArg) { type = NavType.StringType }),
        ) {
            val account = (sessionState as? AppSessionState.Authenticated.ProviderReady)?.account
            if (account != null) {
                val incomingViewModel: ProviderIncomingRequestViewModel = viewModel(
                    factory = ProviderIncomingRequestViewModel.factory(
                        account.providerId, container.mobileDispatchRepository,
                        container.appointmentTrackingRepository,
                    ),
                )
                ProviderIncomingRequestRoute(
                    viewModel = incomingViewModel,
                    onBack = { navController.popBackStack() },
                    onSessionExpired = sessionViewModel::refresh,
                    onTrack = { navController.navigate(PitGoRoutes.appointmentTracking(it)) },
                )
            }
        }

        composable(
            route = PitGoRoutes.MobileDispatch,
            arguments = listOf(navArgument(PitGoRoutes.MobileDispatchSearchArg) { type = NavType.StringType }),
        ) {
            val active = dispatchResumeState as? DispatchResumeState.Active
            if (sessionState is AppSessionState.Authenticated.Client && active != null) {
                val dispatchViewModel: ClientMobileDispatchViewModel = viewModel(
                    factory = ClientMobileDispatchViewModel.factory(
                        active.snapshot.serviceId,
                        container.mobileDispatchRepository,
                        container.deviceLocationRepository,
                    ),
                )
                ClientMobileDispatchRoute(
                    viewModel = dispatchViewModel,
                    onBack = { navController.popBackStack() },
                    onSessionExpired = sessionViewModel::refresh,
                    onAccepted = { navController.navigate(PitGoRoutes.appointmentTracking(it)) { launchSingleTop = true } },
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

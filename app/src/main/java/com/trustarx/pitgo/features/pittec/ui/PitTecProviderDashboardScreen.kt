package com.trustarx.pitgo.features.pittec.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.MotionDurationScale
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trustarx.pitgo.R
import com.trustarx.pitgo.core.account.model.ProviderLocationMode
import com.trustarx.pitgo.core.design.theme.PitGoSize
import com.trustarx.pitgo.core.design.theme.PitGoSpacing
import com.trustarx.pitgo.core.map.config.PitGoManagedMapStyle
import com.trustarx.pitgo.core.map.model.MapMarkerKind
import com.trustarx.pitgo.core.map.model.MapMarkerSpec
import com.trustarx.pitgo.core.map.model.MapRenderModel
import com.trustarx.pitgo.core.map.model.MapSurfaceState
import com.trustarx.pitgo.core.map.model.MapViewport
import com.trustarx.pitgo.core.map.ui.MapLibreMapHost
import com.trustarx.pitgo.core.map.ui.ParticipantMapStyleController
import com.trustarx.pitgo.core.ui.BottomItemId
import com.trustarx.pitgo.core.ui.PitGoBottomNavigation
import com.trustarx.pitgo.core.ui.PitTecBottomNavigationItems
import com.trustarx.pitgo.features.mobiledispatch.model.DispatchPendingAction
import com.trustarx.pitgo.features.mobiledispatch.model.MobileAppointmentStatus
import com.trustarx.pitgo.features.mobiledispatch.model.MobileDispatchKind
import com.trustarx.pitgo.features.mobiledispatch.model.PendingDispatchAction
import com.trustarx.pitgo.features.mobiledispatch.model.PitTecOfferSummary
import com.trustarx.pitgo.features.mobiledispatch.model.ProviderIncomingUiState
import com.trustarx.pitgo.features.mobiledispatch.viewmodel.ProviderIncomingRequestViewModel
import com.trustarx.pitgo.features.pittec.model.DistancePerimeter
import com.trustarx.pitgo.features.pittec.model.PitTecBottomTab
import com.trustarx.pitgo.features.pittec.model.ProviderLocationGate
import com.trustarx.pitgo.features.pittec.model.ServiceEstimate
import com.trustarx.pitgo.features.pittec.viewmodel.PitTecHomeUiState
import com.trustarx.pitgo.features.pittec.viewmodel.PitTecProviderViewModel
import com.trustarx.pitgo.features.providerdashboard.model.ProviderAvailabilityIssue
import com.trustarx.pitgo.features.providerdashboard.model.ProviderDashboardUiState
import com.trustarx.pitgo.features.providerdashboard.viewmodel.ProviderDashboardViewModel

/**
 * PitTec provider Home/alert/bottom-navigation shell (US1/US5 layout) backed entirely by real
 * data: [ProviderDashboardViewModel] (availability, mode, profile — unchanged real RPCs),
 * [PitTecProviderViewModel] (real device-location gate) and [ProviderIncomingRequestViewModel]
 * (real offer/accept/decline, already used by the existing ProviderRequest route). Only the
 * price/"Valores" figures are a fixed placeholder — PitTec has no real price in the backend.
 */
@Composable
fun PitTecProviderDashboardRoute(
    dashboardViewModel: ProviderDashboardViewModel,
    homeViewModel: PitTecProviderViewModel,
    incomingViewModel: ProviderIncomingRequestViewModel,
    onLogout: () -> Unit,
    onOpenTracking: (String) -> Unit,
) {
    val dashboardState by dashboardViewModel.state.collectAsStateWithLifecycle()
    val homeState by homeViewModel.state.collectAsStateWithLifecycle()
    val incomingState by incomingViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val activeAppointmentId = (incomingState as? ProviderIncomingUiState.Active)
        ?.snapshot?.appointment
        ?.takeIf { it.status in setOf(MobileAppointmentStatus.ACCEPTED, MobileAppointmentStatus.IN_PROGRESS, MobileAppointmentStatus.AWAITING_CLIENT_COMPLETION_CONFIRMATION) }
        ?.id
    LaunchedEffect(activeAppointmentId) { activeAppointmentId?.let(onOpenTracking) }

    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val hasFine = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val hasCoarse = hasFine || result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        homeViewModel.onPermissionChecked(hasFine, hasCoarse)
    }
    LifecycleStartEffect(Unit) {
        val hasFine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = hasFine || ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        homeViewModel.onPermissionChecked(hasFine, hasCoarse)
        homeViewModel.onVisible()
        onStopOrDispose { homeViewModel.onHidden() }
    }

    var ignoringBatteryOptimizations by remember { mutableStateOf(true) }
    LifecycleStartEffect(Unit) {
        ignoringBatteryOptimizations = isIgnoringBatteryOptimizations(context)
        onStopOrDispose {}
    }
    val batteryOptimizationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { ignoringBatteryOptimizations = isIgnoringBatteryOptimizations(context) }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { dashboardViewModel.enableTrackedAvailability(ProviderLocationMode.PITTEC) }
    val availabilityPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            if (Build.VERSION.SDK_INT >= 33) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                dashboardViewModel.enableTrackedAvailability(ProviderLocationMode.PITTEC)
            }
        } else dashboardViewModel.onPrecisePermissionDenied()
    }

    PitTecProviderDashboardScreen(
        dashboardState = dashboardState,
        homeState = homeState,
        incomingState = incomingState,
        styleController = remember { ParticipantMapStyleController() },
        onLogout = onLogout,
        onGrantLocationPermission = {
            locationPermission.launch(
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION),
            )
        },
        onOpenLocationSettings = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
        onOpenAppSettings = {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
            )
        },
        onRetryLocation = homeViewModel::retryLocation,
        onTabSelected = homeViewModel::onTabSelected,
        onViewportIdle = homeViewModel::onViewportIdle,
        onSurfaceState = homeViewModel::onSurfaceState,
        onRetryStyle = homeViewModel::retryStyle,
        onAccept = incomingViewModel::accept,
        onDecline = incomingViewModel::decline,
        onAvailabilityChanged = { available ->
            if (available) {
                availabilityPermission.launch(
                    arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION),
                )
            } else dashboardViewModel.setAvailability(false)
        },
        onOpenNotificationSettings = {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
            }
            context.startActivity(intent)
            dashboardViewModel.clearAvailabilityIssue()
        },
        showBatteryOptimizationBanner = !ignoringBatteryOptimizations,
        onRequestBatteryExemption = {
            batteryOptimizationLauncher.launch(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${context.packageName}"),
                ),
            )
        },
    )
}

private fun isIgnoringBatteryOptimizations(context: android.content.Context): Boolean {
    val powerManager = context.getSystemService(android.content.Context.POWER_SERVICE) as? PowerManager
        ?: return true
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
}

@Composable
private fun PitTecProviderDashboardScreen(
    dashboardState: ProviderDashboardUiState,
    homeState: PitTecHomeUiState,
    incomingState: ProviderIncomingUiState,
    styleController: ParticipantMapStyleController,
    onLogout: () -> Unit,
    onGrantLocationPermission: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onRetryLocation: () -> Unit,
    onTabSelected: (PitTecBottomTab) -> Unit,
    onViewportIdle: (MapViewport) -> Unit,
    onSurfaceState: (MapSurfaceState) -> Unit,
    onRetryStyle: () -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onAvailabilityChanged: (Boolean) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    showBatteryOptimizationBanner: Boolean,
    onRequestBatteryExemption: () -> Unit,
) {
    Scaffold(
        bottomBar = {
            PitGoBottomNavigation(
                items = PitTecBottomNavigationItems,
                selectedItem = homeState.selectedTab.toBottomItemId(),
                onItemClick = { onTabSelected(it.toPitTecTab()) },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (homeState.selectedTab) {
                PitTecBottomTab.MAP -> MapTab(
                    homeState = homeState,
                    incomingState = incomingState,
                    styleController = styleController,
                    onGrantLocationPermission = onGrantLocationPermission,
                    onOpenLocationSettings = onOpenLocationSettings,
                    onOpenAppSettings = onOpenAppSettings,
                    onRetryLocation = onRetryLocation,
                    onViewportIdle = onViewportIdle,
                    onSurfaceState = onSurfaceState,
                    onRetryStyle = onRetryStyle,
                    onAccept = onAccept,
                    onDecline = onDecline,
                    isAvailable = (dashboardState as? ProviderDashboardUiState.Content)?.provider?.available == true,
                )
                PitTecBottomTab.RIDE_DATA -> RideDataTab(incomingState)
                PitTecBottomTab.VALUES -> ValuesTab(incomingState)
                PitTecBottomTab.PROFILE -> ProfileTab(
                    state = dashboardState,
                    onAvailabilityChanged = onAvailabilityChanged,
                    onOpenLocationSettings = onOpenLocationSettings,
                    onOpenNotificationSettings = onOpenNotificationSettings,
                    showBatteryOptimizationBanner = showBatteryOptimizationBanner,
                    onRequestBatteryExemption = onRequestBatteryExemption,
                    onLogout = onLogout,
                )
            }
        }
    }
}

private fun PitTecBottomTab.toBottomItemId(): BottomItemId = when (this) {
    PitTecBottomTab.MAP -> BottomItemId.MAP
    PitTecBottomTab.RIDE_DATA -> BottomItemId.RIDE_DATA
    PitTecBottomTab.VALUES -> BottomItemId.VALUES
    PitTecBottomTab.PROFILE -> BottomItemId.PROFILE
}

private fun BottomItemId.toPitTecTab(): PitTecBottomTab = when (this) {
    BottomItemId.MAP -> PitTecBottomTab.MAP
    BottomItemId.RIDE_DATA -> PitTecBottomTab.RIDE_DATA
    BottomItemId.VALUES -> PitTecBottomTab.VALUES
    else -> PitTecBottomTab.PROFILE
}

@Composable
private fun MapTab(
    homeState: PitTecHomeUiState,
    incomingState: ProviderIncomingUiState,
    styleController: ParticipantMapStyleController,
    onGrantLocationPermission: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onRetryLocation: () -> Unit,
    onViewportIdle: (MapViewport) -> Unit,
    onSurfaceState: (MapSurfaceState) -> Unit,
    onRetryStyle: () -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    isAvailable: Boolean,
) {
    // The incoming-offer card must never be hidden behind the location gate: the gate flips back
    // to Locating on every ON_START (screen wake, app foregrounded again, ...) even when a fix was
    // already held (PitTecProviderViewModel.onPermissionChecked -> locate()), which used to hide a
    // real, ticking PitTec offer for up to 20s at a time behind a "localizando..." message — enough
    // to eat the whole 60s response window across a couple of screen-wake cycles. The card is now a
    // sibling drawn on top of whatever the gate shows, computed once outside the `when`.
    val request = (incomingState as? ProviderIncomingUiState.Request)
        ?.takeIf { it.snapshot.dispatchKind == MobileDispatchKind.PITTEC_AUTO_SEQUENTIAL }
    Box(Modifier.fillMaxSize()) {
        when (val gate = homeState.locationGate) {
            ProviderLocationGate.CheckingPermission -> LocationGateMessage(
                message = stringResource(R.string.pittec_mock_checking_permission),
                loading = true,
            )
            ProviderLocationGate.PermissionRequired -> LocationGateMessage(
                message = stringResource(R.string.pittec_mock_permission_required),
                actionLabel = stringResource(R.string.pittec_mock_grant_permission),
                onAction = onGrantLocationPermission,
            )
            ProviderLocationGate.ApproximateOnly -> LocationGateMessage(
                message = stringResource(R.string.pittec_mock_approximate_only),
                actionLabel = stringResource(R.string.pittec_mock_grant_permission),
                onAction = onOpenAppSettings,
            )
            ProviderLocationGate.ServicesDisabled -> LocationGateMessage(
                message = stringResource(R.string.pittec_mock_services_disabled),
                actionLabel = stringResource(R.string.pittec_mock_open_location_settings),
                onAction = onOpenLocationSettings,
            )
            ProviderLocationGate.Locating -> LocationGateMessage(
                message = stringResource(R.string.pittec_mock_locating),
                loading = true,
            )
            ProviderLocationGate.Unavailable -> LocationGateMessage(
                message = stringResource(R.string.pittec_mock_unavailable),
                actionLabel = stringResource(R.string.action_retry),
                onAction = onRetryLocation,
            )
            is ProviderLocationGate.Ready -> {
                MapLibreMapHost(
                    configuration = PitGoManagedMapStyle.configuration,
                    renderModel = MapRenderModel(
                        listOf(
                            MapMarkerSpec(
                                id = "pittec-self",
                                coordinate = gate.deviceLocation.coordinate,
                                kind = MapMarkerKind.LIVE,
                                accessibilityLabel = stringResource(R.string.pittec_mock_available_title),
                            ),
                        ),
                    ),
                    viewport = MapViewport(gate.deviceLocation.coordinate, 15.0),
                    cameraCommand = null,
                    styleReloadRevision = homeState.styleReloadRevision,
                    online = true,
                    styleController = styleController,
                    onViewportIdle = onViewportIdle,
                    onFeatureClick = {},
                    onSurfaceState = onSurfaceState,
                    modifier = Modifier.fillMaxSize(),
                )
                if (request == null) {
                    Column(
                        modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(PitGoSpacing.Small),
                        verticalArrangement = Arrangement.spacedBy(PitGoSpacing.Small),
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                        ) {
                            Column(
                                Modifier.padding(PitGoSpacing.Medium),
                                verticalArrangement = Arrangement.spacedBy(PitGoSpacing.ExtraSmall),
                            ) {
                                Text(
                                    stringResource(
                                        if (isAvailable) R.string.pittec_mock_available_title else R.string.provider_dashboard_available_off,
                                    ),
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.semantics { heading() },
                                )
                                Text(
                                    stringResource(
                                        if (isAvailable) R.string.pittec_mock_available_hint else R.string.pittec_home_unavailable_hint,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
        if (request != null) {
            Column(
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(PitGoSpacing.Small),
                verticalArrangement = Arrangement.spacedBy(PitGoSpacing.Small),
            ) {
                if (request.failure != null) {
                    Text(stringResource(if (request.failure == com.trustarx.pitgo.features.mobiledispatch.model.MobileDispatchFailure.PRICING_UNAVAILABLE)
                        R.string.pittec_pricing_unavailable else R.string.pittec_action_retry), color = MaterialTheme.colorScheme.error)
                }
                RealIncomingRequestCard(
                    offer = request.snapshot.offer,
                    revision = request.snapshot.revision,
                    pending = request.pending,
                    onAccept = onAccept,
                    onDecline = onDecline,
                )
            }
        }
    }
}

/**
 * Purple pulse card sourced from the real [PitTecOfferSummary] (approximate distance/region —
 * PitTec never reveals the client's identity/exact position before acceptance, see
 * contracts/pittec-rpcs.md). The total shown is a fixed placeholder: PitTec has no real price.
 */
@Composable
private fun RealIncomingRequestCard(
    offer: PitTecOfferSummary?,
    revision: Long,
    pending: DispatchPendingAction?,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    val pulseAlpha = remember(revision) { Animatable(0f) }
    LaunchedEffect(revision) {
        val scaleFactor = kotlin.coroutines.coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
        if (scaleFactor == 0f) return@LaunchedEffect
        repeat(3) {
            pulseAlpha.animateTo(1f, tween(350))
            pulseAlpha.animateTo(0f, tween(350))
        }
    }
    val purple = MaterialTheme.colorScheme.primary
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        border = BorderStroke(2.dp, purple),
        modifier = Modifier.fillMaxWidth()
            .background(purple.copy(alpha = pulseAlpha.value * 0.25f))
            .semantics { liveRegion = LiveRegionMode.Assertive },
    ) {
        Column(
            Modifier.padding(PitGoSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(PitGoSpacing.Small),
        ) {
            Text(
                stringResource(R.string.pittec_mock_incoming_heading),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            offer?.region?.takeIf { it.isNotBlank() }?.let { Text(it) }
            offer?.let {
                Text(
                    stringResource(
                        R.string.pittec_mock_incoming_distance,
                        formatDistanceMeters(it.approximateDistanceMeters),
                    ),
                )
            }
            Text(
                stringResource(R.string.pittec_value_on_accept),
                style = MaterialTheme.typography.titleMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(PitGoSpacing.Small)) {
                OutlinedButton(
                    onClick = onDecline,
                    enabled = pending == null,
                    modifier = Modifier.weight(1f).heightIn(min = PitGoSize.MinimumTouchTarget),
                ) { Text(stringResource(R.string.pittec_mock_decline)) }
                Button(
                    onClick = onAccept,
                    enabled = pending == null,
                    modifier = Modifier.weight(1f).heightIn(min = PitGoSize.MinimumTouchTarget),
                ) {
                    if (pending?.kind == PendingDispatchAction.ACCEPT) {
                        CircularProgressIndicator(Modifier.heightIn(max = PitGoSize.NavigationIcon))
                    } else Text(stringResource(R.string.pittec_mock_accept))
                }
            }
        }
    }
}

/** Bands a real approximate distance into the same 5 km buckets the mocked pricing uses. */
private fun bandedKilometers(approximateDistanceMeters: Int?): Int {
    val km = ((approximateDistanceMeters ?: 0) / 1000.0)
    val banded = (kotlin.math.ceil(km / 5.0) * 5).toInt()
    return banded.coerceAtLeast(5)
}

@Composable
private fun LocationGateMessage(
    message: String,
    loading: Boolean = false,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(PitGoSpacing.Medium),
            modifier = Modifier.padding(PitGoSpacing.Large).semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            if (loading) CircularProgressIndicator()
            Text(message, style = MaterialTheme.typography.bodyLarge)
            if (actionLabel != null) {
                Button(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

@Composable
private fun RideDataTab(incomingState: ProviderIncomingUiState) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(PitGoSpacing.Large),
        verticalArrangement = Arrangement.spacedBy(PitGoSpacing.Small),
    ) {
        Text(
            stringResource(R.string.pittec_mock_ride_data_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        val snapshot = when (incomingState) {
            is ProviderIncomingUiState.Request -> incomingState.snapshot
            is ProviderIncomingUiState.Active -> incomingState.snapshot
            else -> null
        }?.takeIf { it.dispatchKind == MobileDispatchKind.PITTEC_AUTO_SEQUENTIAL }
        if (snapshot?.appointment == null) {
            Text(stringResource(R.string.pittec_mock_ride_data_empty))
        } else {
            Text(snapshot.serviceName, style = MaterialTheme.typography.titleMedium)
            snapshot.offer?.region?.takeIf { it.isNotBlank() }?.let { Text(it) }
            snapshot.offer?.let {
                Text(
                    stringResource(
                        R.string.pittec_mock_ride_data_distance,
                        formatDistanceMeters(it.approximateDistanceMeters),
                    ),
                )
            }
            Text(stringResource(R.string.pittec_mock_ride_data_state, snapshot.appointment.status.name))
        }
    }
}

@Composable
private fun ValuesTab(incomingState: ProviderIncomingUiState) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(PitGoSpacing.Large),
        verticalArrangement = Arrangement.spacedBy(PitGoSpacing.Small),
    ) {
        Text(
            stringResource(R.string.pittec_mock_values_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        val snapshot = when (incomingState) {
            is ProviderIncomingUiState.Request -> incomingState.snapshot
            is ProviderIncomingUiState.Active -> incomingState.snapshot
            else -> null
        }?.takeIf { it.dispatchKind == MobileDispatchKind.PITTEC_AUTO_SEQUENTIAL }
        if (snapshot?.appointment == null) {
            Text(stringResource(R.string.pittec_mock_values_empty))
        } else {
            val appointment = snapshot.appointment
            val formatter = java.text.NumberFormat.getCurrencyInstance(java.util.Locale.forLanguageTag("pt-BR"))
            appointment.serviceAmount?.let { Text(stringResource(R.string.pittec_provider_service_value, formatter.format(it))) }
            appointment.distanceChargeAmount?.let { Text(stringResource(R.string.pittec_provider_distance_value, formatter.format(it))) }
            if (appointment.serviceAmount != null) {
                Text(stringResource(R.string.pittec_provider_total_value, formatter.format(appointment.quotedPrice)),
                    style = MaterialTheme.typography.titleMedium)
            } else Text(stringResource(R.string.pittec_value_on_accept))
        }
    }
}

@Composable
private fun ProfileTab(
    state: ProviderDashboardUiState,
    onAvailabilityChanged: (Boolean) -> Unit,
    onOpenLocationSettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    showBatteryOptimizationBanner: Boolean,
    onRequestBatteryExemption: () -> Unit,
    onLogout: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(PitGoSpacing.Large),
        verticalArrangement = Arrangement.spacedBy(PitGoSpacing.Medium),
    ) {
        Text(
            stringResource(R.string.pittec_mock_profile_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        if (showBatteryOptimizationBanner) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(
                    Modifier.padding(PitGoSpacing.Medium),
                    verticalArrangement = Arrangement.spacedBy(PitGoSpacing.Small),
                ) {
                    Text(
                        stringResource(R.string.provider_battery_optimization_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(stringResource(R.string.provider_battery_optimization_message))
                    Button(onClick = onRequestBatteryExemption) {
                        Text(stringResource(R.string.provider_battery_optimization_action))
                    }
                }
            }
        }
        when (state) {
            ProviderDashboardUiState.Loading -> Text(stringResource(R.string.session_verifying))
            ProviderDashboardUiState.Failure -> Text(stringResource(R.string.signup_failure_service))
            is ProviderDashboardUiState.Content -> {
                Text(state.provider.name, style = MaterialTheme.typography.titleMedium)
                Text(state.provider.email, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.provider_dashboard_available))
                    Switch(
                        checked = state.provider.available,
                        onCheckedChange = onAvailabilityChanged,
                        enabled = !state.updating,
                    )
                }
                state.availabilityIssue?.let { issue ->
                    Text(
                        text = stringResource(
                            when (issue) {
                                ProviderAvailabilityIssue.PRECISE_PERMISSION_REQUIRED -> R.string.provider_availability_precise_required
                                ProviderAvailabilityIssue.LOCATION_SERVICES_DISABLED -> R.string.provider_availability_location_disabled
                                ProviderAvailabilityIssue.PRECISE_FIX_UNAVAILABLE -> R.string.provider_availability_fix_unavailable
                                ProviderAvailabilityIssue.NOTIFICATIONS_REQUIRED -> R.string.pittec_notifications_required
                                ProviderAvailabilityIssue.UPDATE_FAILED -> R.string.provider_availability_update_failed
                            },
                        ),
                        color = MaterialTheme.colorScheme.error,
                    )
                    if (issue == ProviderAvailabilityIssue.LOCATION_SERVICES_DISABLED) {
                        Button(onClick = onOpenLocationSettings) {
                            Text(stringResource(R.string.provider_availability_open_location_settings))
                        }
                    }
                    if (issue == ProviderAvailabilityIssue.NOTIFICATIONS_REQUIRED) {
                        Button(onClick = onOpenNotificationSettings) {
                            Text(stringResource(R.string.pittec_open_notification_settings))
                        }
                    }
                }
            }
        }
        Button(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth().heightIn(min = PitGoSize.PrimaryActionMinHeight),
        ) { Text(stringResource(R.string.action_logout)) }
    }
}

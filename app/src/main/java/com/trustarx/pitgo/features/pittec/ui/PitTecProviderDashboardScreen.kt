package com.trustarx.pitgo.features.pittec.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trustarx.pitgo.R
import com.trustarx.pitgo.core.design.theme.PitGoSize
import com.trustarx.pitgo.core.design.theme.PitGoSpacing
import com.trustarx.pitgo.core.map.config.PitGoManagedMapStyle
import com.trustarx.pitgo.core.map.model.MapMarkerKind
import com.trustarx.pitgo.core.map.model.MapMarkerSpec
import com.trustarx.pitgo.core.map.model.MapRenderModel
import com.trustarx.pitgo.core.map.model.MapViewport
import com.trustarx.pitgo.core.map.ui.MapLibreMapHost
import com.trustarx.pitgo.core.map.ui.ParticipantMapStyleController
import com.trustarx.pitgo.core.ui.BottomItemId
import com.trustarx.pitgo.core.ui.PitGoBottomNavigation
import com.trustarx.pitgo.core.ui.PitTecBottomNavigationItems
import com.trustarx.pitgo.features.pittec.model.MockAppointment
import com.trustarx.pitgo.features.pittec.model.PitTecBottomTab
import com.trustarx.pitgo.features.pittec.model.PitTecProviderMockUiState
import com.trustarx.pitgo.features.pittec.model.PitTecProviderStage
import com.trustarx.pitgo.features.pittec.model.ProviderLocationGate
import com.trustarx.pitgo.features.pittec.viewmodel.PitTecProviderMockViewModel
import java.text.DateFormat
import java.util.Date

/**
 * Mocked PitTec provider shell: location-gated Home/alert (US1), participant tracking (US3),
 * bilateral confirmations (US4) and the four-tab bottom navigation (US5). See
 * contracts/provider-pittec-ui.md. Never instantiates the real ProviderDashboardViewModel/repos.
 */
@Composable
fun PitTecProviderDashboardRoute(
    viewModel: PitTecProviderMockViewModel,
    onLogout: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val hasFine = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val hasCoarse = hasFine || result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        viewModel.onPermissionChecked(hasFine, hasCoarse)
    }
    LifecycleStartEffect(Unit) {
        val hasFine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = hasFine || ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.onPermissionChecked(hasFine, hasCoarse)
        viewModel.onVisible()
        onStopOrDispose { viewModel.onHidden() }
    }
    PitTecProviderDashboardScreen(
        state = state,
        styleController = remember { ParticipantMapStyleController() },
        onLogout = onLogout,
        onGrantPermission = {
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
        onRetryLocation = viewModel::retryLocation,
        onSimulateRequest = viewModel::onSimulateRequest,
        onPulseStarted = viewModel::onPulseEffectStarted,
        onAccept = viewModel::onAccept,
        onDecline = viewModel::onDecline,
        onTabSelected = viewModel::onTabSelected,
        onViewportIdle = viewModel::onViewportIdle,
        onSurfaceState = viewModel::onSurfaceState,
        onRetryStyle = viewModel::retryStyle,
        onRecenter = viewModel::onRecenter,
        onMeetingConfirmed = viewModel::onMeetingConfirmed,
        onCompletionConfirmed = viewModel::onCompletionConfirmed,
        onReturnToAvailable = viewModel::onReturnToAvailable,
    )
}

@Composable
private fun PitTecProviderDashboardScreen(
    state: PitTecProviderMockUiState,
    styleController: ParticipantMapStyleController,
    onLogout: () -> Unit,
    onGrantPermission: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onRetryLocation: () -> Unit,
    onSimulateRequest: () -> Unit,
    onPulseStarted: () -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onTabSelected: (PitTecBottomTab) -> Unit,
    onViewportIdle: (MapViewport) -> Unit,
    onSurfaceState: (com.trustarx.pitgo.core.map.model.MapSurfaceState) -> Unit,
    onRetryStyle: () -> Unit,
    onRecenter: () -> Unit,
    onMeetingConfirmed: () -> Unit,
    onCompletionConfirmed: () -> Unit,
    onReturnToAvailable: () -> Unit,
) {
    Scaffold(
        bottomBar = {
            PitGoBottomNavigation(
                items = PitTecBottomNavigationItems,
                selectedItem = state.selectedTab.toBottomItemId(),
                onItemClick = { onTabSelected(it.toPitTecTab()) },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state.selectedTab) {
                PitTecBottomTab.MAP -> MapTab(
                    state = state,
                    styleController = styleController,
                    onGrantPermission = onGrantPermission,
                    onOpenLocationSettings = onOpenLocationSettings,
                    onOpenAppSettings = onOpenAppSettings,
                    onRetryLocation = onRetryLocation,
                    onSimulateRequest = onSimulateRequest,
                    onPulseStarted = onPulseStarted,
                    onAccept = onAccept,
                    onDecline = onDecline,
                    onViewportIdle = onViewportIdle,
                    onSurfaceState = onSurfaceState,
                    onRetryStyle = onRetryStyle,
                    onRecenter = onRecenter,
                    onMeetingConfirmed = onMeetingConfirmed,
                    onCompletionConfirmed = onCompletionConfirmed,
                    onReturnToAvailable = onReturnToAvailable,
                )
                PitTecBottomTab.RIDE_DATA -> RideDataTab(state.currentOrLastAppointment)
                PitTecBottomTab.VALUES -> ValuesTab(state.currentOrLastAppointment)
                PitTecBottomTab.PROFILE -> ProfileTab(state.provider.displayName, onLogout)
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
    state: PitTecProviderMockUiState,
    styleController: ParticipantMapStyleController,
    onGrantPermission: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onRetryLocation: () -> Unit,
    onSimulateRequest: () -> Unit,
    onPulseStarted: () -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onViewportIdle: (MapViewport) -> Unit,
    onSurfaceState: (com.trustarx.pitgo.core.map.model.MapSurfaceState) -> Unit,
    onRetryStyle: () -> Unit,
    onRecenter: () -> Unit,
    onMeetingConfirmed: () -> Unit,
    onCompletionConfirmed: () -> Unit,
    onReturnToAvailable: () -> Unit,
) {
    when (val gate = state.locationGate) {
        ProviderLocationGate.CheckingPermission -> LocationGateMessage(
            message = stringResource(R.string.pittec_mock_checking_permission),
            loading = true,
        )
        ProviderLocationGate.PermissionRequired -> LocationGateMessage(
            message = stringResource(R.string.pittec_mock_permission_required),
            actionLabel = stringResource(R.string.pittec_mock_grant_permission),
            onAction = onGrantPermission,
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
        is ProviderLocationGate.Ready -> ReadyMapContent(
            state = state,
            deviceCoordinate = gate.deviceLocation.coordinate,
            styleController = styleController,
            onSimulateRequest = onSimulateRequest,
            onPulseStarted = onPulseStarted,
            onAccept = onAccept,
            onDecline = onDecline,
            onViewportIdle = onViewportIdle,
            onSurfaceState = onSurfaceState,
            onRetryStyle = onRetryStyle,
            onRecenter = onRecenter,
            onMeetingConfirmed = onMeetingConfirmed,
            onCompletionConfirmed = onCompletionConfirmed,
            onReturnToAvailable = onReturnToAvailable,
        )
    }
}

@Composable
private fun ReadyMapContent(
    state: PitTecProviderMockUiState,
    deviceCoordinate: com.trustarx.pitgo.core.map.model.MapCoordinate,
    styleController: ParticipantMapStyleController,
    onSimulateRequest: () -> Unit,
    onPulseStarted: () -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onViewportIdle: (MapViewport) -> Unit,
    onSurfaceState: (com.trustarx.pitgo.core.map.model.MapSurfaceState) -> Unit,
    onRetryStyle: () -> Unit,
    onRecenter: () -> Unit,
    onMeetingConfirmed: () -> Unit,
    onCompletionConfirmed: () -> Unit,
    onReturnToAvailable: () -> Unit,
) {
    when (val stage = state.stage) {
        is PitTecProviderStage.Tracking -> PitTecMockTrackingContent(
            appointment = stage.appointment,
            isClientViewer = false,
            viewport = state.viewport,
            cameraCommand = state.cameraCommand,
            styleReloadRevision = state.styleReloadRevision,
            surfaceState = state.surfaceState,
            onViewportIdle = onViewportIdle,
            onSurfaceState = onSurfaceState,
            onRetryStyle = onRetryStyle,
            onRecenter = onRecenter,
            onMeetingConfirmed = onMeetingConfirmed,
            onCompletionConfirmed = onCompletionConfirmed,
            completedActionLabel = stringResource(R.string.pittec_mock_back_to_map),
            onCompletedAction = onReturnToAvailable,
            styleController = styleController,
        )
        is PitTecProviderStage.Completed -> PitTecMockTrackingContent(
            appointment = stage.appointment,
            isClientViewer = false,
            viewport = state.viewport,
            cameraCommand = state.cameraCommand,
            styleReloadRevision = state.styleReloadRevision,
            surfaceState = state.surfaceState,
            onViewportIdle = onViewportIdle,
            onSurfaceState = onSurfaceState,
            onRetryStyle = onRetryStyle,
            onRecenter = onRecenter,
            onMeetingConfirmed = onMeetingConfirmed,
            onCompletionConfirmed = onCompletionConfirmed,
            completedActionLabel = stringResource(R.string.pittec_mock_back_to_map),
            onCompletedAction = onReturnToAvailable,
            styleController = styleController,
        )
        PitTecProviderStage.Available,
        is PitTecProviderStage.IncomingRequest,
        -> Box(Modifier.fillMaxSize()) {
            MapLibreMapHost(
                configuration = PitGoManagedMapStyle.configuration,
                renderModel = MapRenderModel(
                    listOf(
                        MapMarkerSpec(
                            id = "pittec-mock-self",
                            coordinate = deviceCoordinate,
                            kind = MapMarkerKind.LIVE,
                            accessibilityLabel = stringResource(R.string.pittec_mock_available_title),
                        ),
                    ),
                ),
                viewport = MapViewport(deviceCoordinate, 15.0),
                cameraCommand = null,
                styleReloadRevision = state.styleReloadRevision,
                online = true,
                styleController = styleController,
                onViewportIdle = onViewportIdle,
                onFeatureClick = {},
                onSurfaceState = onSurfaceState,
                modifier = Modifier.fillMaxSize(),
            )
            Column(
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(PitGoSpacing.Small),
                verticalArrangement = Arrangement.spacedBy(PitGoSpacing.Small),
            ) {
                if (stage is PitTecProviderStage.IncomingRequest) {
                    IncomingRequestCard(
                        request = stage.request,
                        pulseConsumed = stage.request.pulseConsumed,
                        responded = false,
                        onPulseStarted = onPulseStarted,
                        onAccept = onAccept,
                        onDecline = onDecline,
                    )
                } else {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        tonalElevation = PitGoSpacing.ExtraSmall,
                        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                    ) {
                        Column(
                            Modifier.padding(PitGoSpacing.Medium),
                            verticalArrangement = Arrangement.spacedBy(PitGoSpacing.ExtraSmall),
                        ) {
                            Text(
                                stringResource(R.string.pittec_mock_available_title),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.semantics { heading() },
                            )
                            Text(stringResource(R.string.pittec_mock_available_hint))
                        }
                    }
                }
            }
            if (stage is PitTecProviderStage.Available) {
                Button(
                    onClick = onSimulateRequest,
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .padding(PitGoSpacing.Medium).heightIn(min = PitGoSize.PrimaryActionMinHeight),
                ) { Text(stringResource(R.string.pittec_mock_simulate_request)) }
            }
        }
    }
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
private fun RideDataTab(appointment: MockAppointment?) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(PitGoSpacing.Large),
        verticalArrangement = Arrangement.spacedBy(PitGoSpacing.Small),
    ) {
        Text(
            stringResource(R.string.pittec_mock_ride_data_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        if (appointment == null) {
            Text(stringResource(R.string.pittec_mock_ride_data_empty))
        } else {
            Text(stringResource(R.string.pittec_mock_ride_data_client, appointment.client.displayName))
            Text(stringResource(R.string.pittec_mock_ride_data_provider, appointment.provider.displayName))
            Text(stringResource(R.string.pittec_mock_ride_data_perimeter, appointment.estimate.perimeter.kilometers))
            Text(
                stringResource(
                    R.string.pittec_mock_ride_data_distance,
                    formatDistanceMeters(appointment.remainingDistanceMeters),
                ),
            )
            Text(stringResource(R.string.pittec_mock_ride_data_state, stringResource(statusLabel(appointment.status))))
            Text(stringResource(R.string.pittec_mock_ride_data_accepted_at, timeOf(appointment.acceptedAtEpochMillis)))
            appointment.startedAtEpochMillis?.let {
                Text(stringResource(R.string.pittec_mock_ride_data_started_at, timeOf(it)))
            }
            appointment.completedAtEpochMillis?.let {
                Text(stringResource(R.string.pittec_mock_ride_data_completed_at, timeOf(it)))
            }
        }
    }
}

@Composable
private fun ValuesTab(appointment: MockAppointment?) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(PitGoSpacing.Large),
        verticalArrangement = Arrangement.spacedBy(PitGoSpacing.Small),
    ) {
        Text(
            stringResource(R.string.pittec_mock_values_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        if (appointment == null) {
            Text(stringResource(R.string.pittec_mock_values_empty))
        } else {
            val estimate = appointment.estimate
            Text(stringResource(R.string.pittec_mock_values_fixed, formatCents(estimate.fixedCents)))
            Text(
                stringResource(
                    R.string.pittec_mock_values_additional,
                    estimate.bandCount,
                    formatCents(estimate.distanceAdditionalCents),
                ),
            )
            Text(
                stringResource(R.string.pittec_mock_values_total, formatCents(estimate.totalCents)),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun ProfileTab(providerName: String, onLogout: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(PitGoSpacing.Large),
        verticalArrangement = Arrangement.spacedBy(PitGoSpacing.Medium),
    ) {
        Text(
            stringResource(R.string.pittec_mock_profile_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        Text(providerName, style = MaterialTheme.typography.titleMedium)
        Button(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth().heightIn(min = PitGoSize.PrimaryActionMinHeight),
        ) { Text(stringResource(R.string.action_logout)) }
    }
}

private fun timeOf(epochMillis: Long): String =
    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(epochMillis))

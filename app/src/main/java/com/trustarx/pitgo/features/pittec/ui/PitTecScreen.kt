package com.trustarx.pitgo.features.pittec.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trustarx.pitgo.R
import com.trustarx.pitgo.core.design.theme.PitGoSpacing
import com.trustarx.pitgo.core.map.config.PitGoManagedMapStyle
import com.trustarx.pitgo.core.map.model.MapRenderModel
import com.trustarx.pitgo.core.map.model.MapResourceState
import com.trustarx.pitgo.core.map.model.MapStyleState
import com.trustarx.pitgo.core.map.ui.MapLibreMapHost
import com.trustarx.pitgo.core.design.theme.PitGoSize
import com.trustarx.pitgo.features.pittec.model.DistancePerimeter
import com.trustarx.pitgo.features.pittec.model.PitTecAppointmentStatus
import com.trustarx.pitgo.features.pittec.model.PitTecDispatchPhase
import com.trustarx.pitgo.features.pittec.model.PitTecDiscoveryState
import com.trustarx.pitgo.features.pittec.model.PitTecLocationState
import com.trustarx.pitgo.features.pittec.model.PitTecUiState
import com.trustarx.pitgo.features.pittec.model.ServiceEstimate
import com.trustarx.pitgo.features.pittec.viewmodel.PitTecViewModel

@Composable
fun PitTecRoute(
    viewModel: PitTecViewModel,
    onBack: () -> Unit,
    onAccepted: (appointmentId: String) -> Unit,
    onSessionExpired: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coarsePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        viewModel.onLocationPermissionResult(
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true,
        )
    }
    val precisePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) viewModel.requestPitTec()
    }
    LifecycleStartEffect(viewModel) {
        onStopOrDispose { viewModel.clearTransientLocation() }
    }
    LaunchedEffect(viewModel) {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.onLocationPermissionResult(true)
    }
    val content = state as? PitTecUiState.Content
    LaunchedEffect(content?.discoveryState) {
        if (content?.discoveryState == PitTecDiscoveryState.SessionExpired) onSessionExpired()
    }
    var navigatedAppointmentId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(content?.dispatch) {
        val active = content?.dispatch as? PitTecDispatchPhase.Active ?: return@LaunchedEffect
        val appointment = active.snapshot.appointment ?: return@LaunchedEffect
        if (appointment.status !in setOf(PitTecAppointmentStatus.ACCEPTED, PitTecAppointmentStatus.IN_PROGRESS)) return@LaunchedEffect
        if (navigatedAppointmentId == appointment.id) return@LaunchedEffect
        navigatedAppointmentId = appointment.id
        onAccepted(appointment.id)
    }
    val hasFinePermission = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
    PitTecScreen(
        state = state,
        styleController = remember { PitTecMapStyleController() },
        onBack = onBack,
        onUseLocation = {
            coarsePermission.launch(
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION),
            )
        },
        onRetryDiscovery = viewModel::retryDiscovery,
        onRetryStyle = viewModel::retryStyle,
        onViewportIdle = viewModel::onViewportIdle,
        onSurfaceState = viewModel::updateSurfaceState,
        onRequestPitTec = {
            if (hasFinePermission) viewModel.requestPitTec()
            else precisePermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
        },
        onCancelSearch = viewModel::cancelSearch,
        onRetryDispatch = viewModel::retryDispatch,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PitTecScreen(
    state: PitTecUiState,
    styleController: PitTecMapStyleController,
    onBack: () -> Unit,
    onUseLocation: () -> Unit,
    onRetryDiscovery: () -> Unit,
    onRetryStyle: () -> Unit,
    onViewportIdle: (com.trustarx.pitgo.core.map.model.MapViewport) -> Unit,
    onSurfaceState: (com.trustarx.pitgo.core.map.model.MapSurfaceState) -> Unit,
    onRequestPitTec: () -> Unit,
    onCancelSearch: () -> Unit,
    onRetryDispatch: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.pittec_map_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_back), stringResource(R.string.map_back))
                    }
                },
            )
        },
    ) { padding ->
        if (state !is PitTecUiState.Content) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.service_map_invalid))
            }
            return@Scaffold
        }
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize()) {
                DiscoveryStateBanner(state, onRetryDiscovery, onRetryStyle)
                val mapDescription = stringResource(R.string.pittec_map_description, state.markers.size)
                Box(Modifier.weight(1f).semantics { contentDescription = mapDescription }) {
                    MapLibreMapHost(
                        configuration = PitGoManagedMapStyle.configuration,
                        renderModel = MapRenderModel(state.mapMarkers),
                        viewport = state.viewport,
                        cameraCommand = state.cameraCommand,
                        styleReloadRevision = state.styleReloadRevision,
                        online = state.discoveryState != PitTecDiscoveryState.Offline,
                        styleController = styleController,
                        onViewportIdle = onViewportIdle,
                        onFeatureClick = {},
                        onSurfaceState = onSurfaceState,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (state.discoveryState == PitTecDiscoveryState.Loading ||
                        state.surfaceState.style is MapStyleState.Loading
                    ) {
                        CircularProgressIndicator(Modifier.align(Alignment.Center))
                    }
                    if (state.surfaceState.style is MapStyleState.Failed) {
                        Button(onClick = onRetryStyle, modifier = Modifier.align(Alignment.Center)) {
                            Text(stringResource(R.string.map_retry))
                        }
                    }
                    TextButton(
                        onClick = onUseLocation,
                        modifier = Modifier.align(Alignment.TopStart).padding(PitGoSpacing.Small),
                    ) { Text(stringResource(R.string.service_map_my_location)) }
                }
            }
            EstimateGatedDispatchOverlay(
                phase = state.dispatch,
                onRequestPitTec = onRequestPitTec,
                onCancelSearch = onCancelSearch,
                onRetryDispatch = onRetryDispatch,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun DiscoveryStateBanner(
    state: PitTecUiState.Content,
    onRetryDiscovery: () -> Unit,
    onRetryStyle: () -> Unit,
) {
    val message = when {
        state.discoveryState == PitTecDiscoveryState.Offline -> R.string.map_offline
        state.discoveryState == PitTecDiscoveryState.Failure -> R.string.mobile_dispatch_unavailable
        state.discoveryState == PitTecDiscoveryState.Empty -> R.string.pittec_empty_map
        state.discoveryState == PitTecDiscoveryState.UpdateRequired -> R.string.mobile_dispatch_unavailable
        state.locationState == PitTecLocationState.Denied -> R.string.pittec_location_required
        state.locationState == PitTecLocationState.Unavailable -> R.string.pittec_location_unavailable
        state.surfaceState.style is MapStyleState.Failed -> R.string.map_style_failure
        state.surfaceState.resources is MapResourceState.Degraded -> R.string.map_resource_degraded
        else -> null
    } ?: return
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth().padding(PitGoSpacing.Small)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(Modifier.padding(PitGoSpacing.Small), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(message), Modifier.weight(1f))
            val isStyleIssue = state.surfaceState.style is MapStyleState.Failed ||
                state.surfaceState.resources is MapResourceState.Degraded
            TextButton(onClick = if (isStyleIssue) onRetryStyle else onRetryDiscovery) {
                Text(stringResource(R.string.map_retry))
            }
        }
    }
}

/**
 * Adds a fixed-perimeter cost estimate step in front of the real dispatch flow — PitTec has no
 * real price in the backend, so this figure is a placeholder only (see EstimateCard). Every state
 * from "Buscar PitTec" onward (searching, awaiting response, found, empty, failure) is the
 * existing real [DispatchOverlay], unchanged: real search, real candidate, real acceptance.
 */
@Composable
private fun EstimateGatedDispatchOverlay(
    phase: PitTecDispatchPhase,
    onRequestPitTec: () -> Unit,
    onCancelSearch: () -> Unit,
    onRetryDispatch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (phase != PitTecDispatchPhase.None) {
        DispatchOverlay(phase, onRequestPitTec, onCancelSearch, onRetryDispatch, modifier)
        return
    }
    var estimateVisible by remember { mutableStateOf(false) }
    if (estimateVisible) {
        EstimateCard(
            estimate = ServiceEstimate(DistancePerimeter(5)),
            confirmEnabled = true,
            onConfirm = { estimateVisible = false; onRequestPitTec() },
            onDismiss = { estimateVisible = false },
            modifier = modifier,
        )
    } else {
        Button(
            onClick = { estimateVisible = true },
            modifier = modifier.fillMaxWidth().padding(PitGoSpacing.Medium)
                .heightIn(min = PitGoSize.PrimaryActionMinHeight),
        ) { Text(stringResource(R.string.pittec_mock_search_cta)) }
    }
}

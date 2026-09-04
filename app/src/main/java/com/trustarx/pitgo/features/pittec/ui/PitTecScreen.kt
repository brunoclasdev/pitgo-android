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
import androidx.compose.runtime.remember
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
import com.trustarx.pitgo.core.design.theme.PitGoSize
import com.trustarx.pitgo.core.design.theme.PitGoSpacing
import com.trustarx.pitgo.core.map.config.PitGoManagedMapStyle
import com.trustarx.pitgo.core.map.model.MapMarkerKind
import com.trustarx.pitgo.core.map.model.MapMarkerSpec
import com.trustarx.pitgo.core.map.model.MapRenderModel
import com.trustarx.pitgo.core.map.model.MapResourceState
import com.trustarx.pitgo.core.map.model.MapStyleState
import com.trustarx.pitgo.core.map.ui.MapLibreMapHost
import com.trustarx.pitgo.core.map.ui.ParticipantMapStyleController
import com.trustarx.pitgo.features.pittec.model.PitTecClientMockUiState
import com.trustarx.pitgo.features.pittec.model.PitTecClientStage
import com.trustarx.pitgo.features.pittec.model.PitTecLocationState
import com.trustarx.pitgo.features.pittec.viewmodel.PitTecClientMockViewModel

/**
 * Mocked client PitTec journey (US2–US4). The discovery presentation before "Buscar PitTec" stays
 * pixel-for-pixel the same as the original screen (FR-011); everything past it is entirely local —
 * see contracts/client-pittec-ui.md. No real repository or connectivity dependency is used here.
 */
@Composable
fun PitTecRoute(
    viewModel: PitTecClientMockViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        viewModel.onLocationPermissionResult(
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true,
        )
    }
    LaunchedEffect(viewModel) {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.onLocationPermissionResult(true)
    }
    LifecycleStartEffect(viewModel) {
        viewModel.onVisible()
        onStopOrDispose { viewModel.onHidden() }
    }
    PitTecScreen(
        state = state,
        discoveryStyleController = remember { PitTecMapStyleController() },
        trackingStyleController = remember { ParticipantMapStyleController() },
        onBack = onBack,
        onUseLocation = {
            locationPermission.launch(
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION),
            )
        },
        onRetryDiscovery = viewModel::retryDiscovery,
        onRetryStyle = viewModel::retryStyle,
        onViewportIdle = viewModel::onViewportIdle,
        onSurfaceState = viewModel::onSurfaceState,
        onSearchRequested = viewModel::onSearchRequested,
        onEstimateDismissed = viewModel::onEstimateDismissed,
        onEstimateConfirmed = viewModel::onEstimateConfirmed,
        onRetrySearch = viewModel::onRetrySearch,
        onBackFromNoProvider = viewModel::onBackFromNoProvider,
        onMeetingConfirmed = viewModel::onMeetingConfirmed,
        onCompletionConfirmed = viewModel::onCompletionConfirmed,
        onBackToHome = viewModel::onBackToHome,
        onRecenter = viewModel::onRecenter,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PitTecScreen(
    state: PitTecClientMockUiState,
    discoveryStyleController: PitTecMapStyleController,
    trackingStyleController: ParticipantMapStyleController,
    onBack: () -> Unit,
    onUseLocation: () -> Unit,
    onRetryDiscovery: () -> Unit,
    onRetryStyle: () -> Unit,
    onViewportIdle: (com.trustarx.pitgo.core.map.model.MapViewport) -> Unit,
    onSurfaceState: (com.trustarx.pitgo.core.map.model.MapSurfaceState) -> Unit,
    onSearchRequested: () -> Unit,
    onEstimateDismissed: () -> Unit,
    onEstimateConfirmed: () -> Unit,
    onRetrySearch: () -> Unit,
    onBackFromNoProvider: () -> Unit,
    onMeetingConfirmed: () -> Unit,
    onCompletionConfirmed: () -> Unit,
    onBackToHome: () -> Unit,
    onRecenter: () -> Unit,
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
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val stage = state.stage) {
                is PitTecClientStage.Tracking -> PitTecMockTrackingContent(
                    appointment = stage.appointment,
                    isClientViewer = true,
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
                    completedActionLabel = stringResource(R.string.pittec_mock_back_to_home),
                    onCompletedAction = onBackToHome,
                    styleController = trackingStyleController,
                )

                is PitTecClientStage.Completed -> PitTecMockTrackingContent(
                    appointment = stage.appointment,
                    isClientViewer = true,
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
                    completedActionLabel = stringResource(R.string.pittec_mock_back_to_home),
                    onCompletedAction = onBackToHome,
                    styleController = trackingStyleController,
                )

                else -> DiscoveryAndSearch(
                    state = state,
                    stage = stage,
                    styleController = discoveryStyleController,
                    onUseLocation = onUseLocation,
                    onRetryDiscovery = onRetryDiscovery,
                    onRetryStyle = onRetryStyle,
                    onViewportIdle = onViewportIdle,
                    onSurfaceState = onSurfaceState,
                    onSearchRequested = onSearchRequested,
                    onEstimateDismissed = onEstimateDismissed,
                    onEstimateConfirmed = onEstimateConfirmed,
                    onRetrySearch = onRetrySearch,
                    onBackFromNoProvider = onBackFromNoProvider,
                )
            }
        }
    }
}

@Composable
private fun DiscoveryAndSearch(
    state: PitTecClientMockUiState,
    stage: PitTecClientStage,
    styleController: PitTecMapStyleController,
    onUseLocation: () -> Unit,
    onRetryDiscovery: () -> Unit,
    onRetryStyle: () -> Unit,
    onViewportIdle: (com.trustarx.pitgo.core.map.model.MapViewport) -> Unit,
    onSurfaceState: (com.trustarx.pitgo.core.map.model.MapSurfaceState) -> Unit,
    onSearchRequested: () -> Unit,
    onEstimateDismissed: () -> Unit,
    onEstimateConfirmed: () -> Unit,
    onRetrySearch: () -> Unit,
    onBackFromNoProvider: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            DiscoveryStateBanner(state, onRetryDiscovery, onRetryStyle)
            val markers = clientMapMarkers(state)
            val mapDescription = stringResource(R.string.pittec_map_description, state.markers.size)
            Box(Modifier.weight(1f).semantics { contentDescription = mapDescription }) {
                MapLibreMapHost(
                    configuration = PitGoManagedMapStyle.configuration,
                    renderModel = MapRenderModel(markers),
                    viewport = state.viewport,
                    cameraCommand = null,
                    styleReloadRevision = state.styleReloadRevision,
                    online = true,
                    styleController = styleController,
                    onViewportIdle = onViewportIdle,
                    onFeatureClick = {},
                    onSurfaceState = onSurfaceState,
                    modifier = Modifier.fillMaxSize(),
                )
                if (state.locationState == PitTecLocationState.Resolving) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
                TextButton(
                    onClick = onUseLocation,
                    modifier = Modifier.align(Alignment.TopStart).padding(PitGoSpacing.Small),
                ) { Text(stringResource(R.string.service_map_my_location)) }
            }
        }
        when (stage) {
            PitTecClientStage.Discovery -> Button(
                onClick = onSearchRequested,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(PitGoSpacing.Medium)
                    .heightIn(min = PitGoSize.PrimaryActionMinHeight),
            ) { Text(stringResource(R.string.pittec_mock_search_cta)) }
            PitTecClientStage.Estimating -> EstimatingCard(
                modifier = Modifier.align(Alignment.BottomCenter),
            )
            is PitTecClientStage.AwaitingClientConfirmation -> EstimateCard(
                estimate = stage.estimate,
                confirmEnabled = true,
                onConfirm = onEstimateConfirmed,
                onDismiss = onEstimateDismissed,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
            is PitTecClientStage.Searching -> SearchFeedbackCard(
                search = stage.search,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
            is PitTecClientStage.NoProvider -> NoProviderCard(
                onRetry = onRetrySearch,
                onBack = onBackFromNoProvider,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
            else -> Unit
        }
    }
}

@Composable
private fun clientMapMarkers(state: PitTecClientMockUiState): List<MapMarkerSpec> {
    val approximateLabel = stringResource(R.string.pittec_marker_approximate)
    val clientLabel = stringResource(R.string.pittec_client_marker)
    val approximate = state.markers.map { marker ->
        MapMarkerSpec(
            id = marker.markerId,
            coordinate = marker.coordinate,
            kind = MapMarkerKind.PITTEC_APPROXIMATE,
            accessibilityLabel = approximateLabel,
        )
    }
    val client = state.clientCoordinate?.takeIf { it.isValid }?.let { coordinate ->
        MapMarkerSpec(
            id = "pittec-mock-client-location",
            coordinate = coordinate,
            kind = MapMarkerKind.CLIENT_LOCATION,
            accessibilityLabel = clientLabel,
        )
    }
    return approximate + listOfNotNull(client)
}

@Composable
private fun DiscoveryStateBanner(
    state: PitTecClientMockUiState,
    onRetryDiscovery: () -> Unit,
    onRetryStyle: () -> Unit,
) {
    val message = when {
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

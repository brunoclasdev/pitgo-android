package com.trustarx.pitgo.features.appointmenttracking.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trustarx.pitgo.R
import com.trustarx.pitgo.core.design.theme.PitGoSize
import com.trustarx.pitgo.core.design.theme.PitGoSpacing
import com.trustarx.pitgo.core.map.config.PitGoManagedMapStyle
import com.trustarx.pitgo.core.map.model.MapRenderModel
import com.trustarx.pitgo.core.map.model.MapResourceState
import com.trustarx.pitgo.core.map.model.MapStyleState
import com.trustarx.pitgo.core.map.ui.MapLibreMapHost
import com.trustarx.pitgo.features.appointmenttracking.model.AppointmentProviderLocation
import com.trustarx.pitgo.features.appointmenttracking.model.AppointmentTrackingUiState
import com.trustarx.pitgo.features.appointmenttracking.model.TrackingDataState
import com.trustarx.pitgo.features.appointmenttracking.viewmodel.AppointmentTrackingViewModel

@Composable
fun AppointmentTrackingRoute(
    viewModel: AppointmentTrackingViewModel,
    onBack: () -> Unit,
    onSessionExpired: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleStartEffect(viewModel) {
        viewModel.onVisible()
        onStopOrDispose { viewModel.onHidden() }
    }
    LaunchedEffect(state.data) {
        if (state.data is TrackingDataState.SessionExpired) onSessionExpired()
    }
    AppointmentTrackingScreen(
        state = state,
        styleController = remember { AppointmentMapStyleController() },
        onBack = onBack,
        onFollowChanged = viewModel::setFollowProvider,
        onViewportIdle = viewModel::onViewportIdle,
        onSurfaceState = viewModel::updateSurfaceState,
        onRetryStyle = viewModel::retryStyle,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppointmentTrackingScreen(
    state: AppointmentTrackingUiState,
    styleController: AppointmentMapStyleController,
    onBack: () -> Unit,
    onFollowChanged: (Boolean) -> Unit,
    onViewportIdle: (com.trustarx.pitgo.core.map.model.MapViewport) -> Unit,
    onSurfaceState: (com.trustarx.pitgo.core.map.model.MapSurfaceState) -> Unit,
    onRetryStyle: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tracking_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_back),
                            contentDescription = stringResource(R.string.map_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val location = state.visibleLocation
            if (location != null) {
                MapLibreMapHost(
                    configuration = PitGoManagedMapStyle.configuration,
                    renderModel = MapRenderModel(state.markers),
                    viewport = state.viewport,
                    cameraCommand = state.cameraCommand,
                    styleReloadRevision = state.styleReloadRevision,
                    online = state.data !is TrackingDataState.Offline,
                    styleController = styleController,
                    onViewportIdle = onViewportIdle,
                    onFeatureClick = {},
                    onSurfaceState = onSurfaceState,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                TrackingEmptyState(state.data, Modifier.fillMaxSize())
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(PitGoSpacing.Small),
                verticalArrangement = Arrangement.spacedBy(PitGoSpacing.Small),
            ) {
                TrackingStatus(state.data)
                MapSurfaceWarning(state, onRetryStyle)
                if (location != null) {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        tonalElevation = PitGoSpacing.ExtraSmall,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = PitGoSpacing.Medium),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(stringResource(R.string.tracking_follow))
                            Switch(
                                checked = state.followProvider,
                                onCheckedChange = onFollowChanged,
                                modifier = Modifier.sizeIn(
                                    minWidth = PitGoSize.MinimumTouchTarget,
                                    minHeight = PitGoSize.MinimumTouchTarget,
                                ),
                            )
                        }
                    }
                }
            }

            if (location != null) MapAttribution(Modifier.align(Alignment.BottomStart))
        }
    }
}

@Composable
private fun TrackingStatus(data: TrackingDataState) {
    val location = when (data) {
        is TrackingDataState.Live -> data.location
        is TrackingDataState.Stale -> data.location
        is TrackingDataState.Offline -> data.lastLocation
        is TrackingDataState.Failure -> data.lastLocation
        else -> null
    }
    val message = when (data) {
        is TrackingDataState.Live -> stringResource(
            R.string.tracking_live_with_time,
            location?.localizedPublishedTime().orEmpty(),
        )
        is TrackingDataState.Stale -> stringResource(
            R.string.tracking_stale_with_time,
            location?.localizedPublishedTime().orEmpty(),
        )
        is TrackingDataState.Offline -> stringResource(R.string.tracking_offline)
        is TrackingDataState.Failure -> stringResource(R.string.tracking_degraded)
        TrackingDataState.SessionExpired -> stringResource(R.string.tracking_session_expired)
        TrackingDataState.Ended -> stringResource(R.string.tracking_ended)
        TrackingDataState.Empty -> stringResource(R.string.tracking_empty)
        TrackingDataState.Loading -> stringResource(R.string.tracking_loading)
    }
    Surface(
        shape = MaterialTheme.shapes.large,
        tonalElevation = PitGoSpacing.ExtraSmall,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Text(
            message,
            modifier = Modifier.fillMaxWidth().padding(PitGoSpacing.Medium),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun TrackingEmptyState(data: TrackingDataState, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        when (data) {
            TrackingDataState.Loading -> CircularProgressIndicator()
            else -> Text(
                when (data) {
                    TrackingDataState.Empty -> stringResource(R.string.tracking_empty)
                    TrackingDataState.Ended -> stringResource(R.string.tracking_ended)
                    TrackingDataState.SessionExpired -> stringResource(R.string.tracking_session_expired)
                    is TrackingDataState.Offline -> stringResource(R.string.tracking_offline)
                    else -> stringResource(R.string.tracking_degraded)
                },
                modifier = Modifier.padding(PitGoSpacing.Large),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun MapSurfaceWarning(
    state: AppointmentTrackingUiState,
    onRetryStyle: () -> Unit,
) {
    val message = when {
        state.surfaceState.style is MapStyleState.Failed -> R.string.map_style_failure
        state.surfaceState.resources is MapResourceState.Degraded -> R.string.map_resource_degraded
        state.surfaceState.resources is MapResourceState.Offline -> R.string.map_offline
        else -> null
    } ?: return
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(PitGoSpacing.Small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                stringResource(message),
                modifier = Modifier.weight(1f).padding(horizontal = PitGoSpacing.Small),
            )
            Button(onClick = onRetryStyle) { Text(stringResource(R.string.map_retry)) }
        }
    }
}

@Composable
private fun MapAttribution(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    val configuration = PitGoManagedMapStyle.configuration
    TextButton(
        onClick = { uriHandler.openUri(configuration.attributionUrl) },
        modifier = modifier,
    ) {
        Text(
            stringResource(R.string.map_attribution, configuration.attribution),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun AppointmentProviderLocation.localizedPublishedTime(): String {
    val formatter = DateFormat.getTimeFormat(LocalContext.current)
    return formatter.format(java.util.Date(publishedAtEpochMillis))
}

package com.trustarx.pitgo.features.pittec.ui

import android.os.SystemClock
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import com.trustarx.pitgo.core.routing.ui.*
import com.trustarx.pitgo.R
import com.trustarx.pitgo.core.design.theme.PitGoSize
import com.trustarx.pitgo.core.design.theme.PitGoSpacing
import com.trustarx.pitgo.core.map.config.PitGoManagedMapStyle
import com.trustarx.pitgo.core.map.model.MapMarkerKind
import com.trustarx.pitgo.core.map.model.MapMarkerSpec
import com.trustarx.pitgo.core.map.model.MapRenderModel
import com.trustarx.pitgo.core.map.model.MapRoutePolyline
import com.trustarx.pitgo.core.map.model.MapRouteStyle
import com.trustarx.pitgo.core.map.model.MapStyleState
import com.trustarx.pitgo.core.map.model.MapSurfaceState
import com.trustarx.pitgo.core.map.model.MapViewport
import com.trustarx.pitgo.core.map.model.MapViewportCommand
import com.trustarx.pitgo.core.map.ui.MapLibreMapHost
import com.trustarx.pitgo.core.map.ui.ParticipantMapStyleController
import com.trustarx.pitgo.features.pittec.model.MockAppointment
import com.trustarx.pitgo.features.pittec.model.MockAppointmentStatus
import kotlinx.coroutines.delay
import java.util.Date

/**
 * Stateless two-participant map/summary surface shared by the client and provider mocked tracking
 * screens (US3/US4) — see contracts/mock-appointment-state.md. All text/state here is a pure
 * function of [appointment]; jobs, timers and revisions live only in the owning ViewModel.
 */
@Composable
fun PitTecMockTrackingContent(
    appointment: MockAppointment,
    isClientViewer: Boolean,
    viewport: MapViewport,
    cameraCommand: MapViewportCommand?,
    styleReloadRevision: Long,
    surfaceState: MapSurfaceState,
    onViewportIdle: (MapViewport) -> Unit,
    onSurfaceState: (MapSurfaceState) -> Unit,
    onRetryStyle: () -> Unit,
    onRecenter: () -> Unit,
    onMeetingConfirmed: () -> Unit,
    onCompletionConfirmed: () -> Unit,
    completedActionLabel: String,
    onCompletedAction: () -> Unit,
    styleController: ParticipantMapStyleController,
    modifier: Modifier = Modifier,
) {
    // Recomposes once per second only to re-evaluate staleness against the monotonic clock — the
    // appointment itself does not change (research.md #5: elapsedRealtime, never wall-clock age).
    var nowElapsedRealtimeMillis by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(appointment.status) {
        while (appointment.status != MockAppointmentStatus.COMPLETED) {
            delay(1_000)
            nowElapsedRealtimeMillis = SystemClock.elapsedRealtime()
        }
    }
    val isStale = appointment.providerLocation.isStale(nowElapsedRealtimeMillis)
    val journey = rememberJourneyRoute(appointment.providerLocation.coordinate, appointment.clientLocation.coordinate,
        enabled = appointment.status != MockAppointmentStatus.COMPLETED)

    Box(modifier.fillMaxSize()) {
        if (surfaceState.style !is MapStyleState.Failed) {
            val markers = listOf(
                MapMarkerSpec(
                    id = "pittec-mock-client",
                    coordinate = appointment.clientLocation.coordinate,
                    kind = MapMarkerKind.CLIENT_LOCATION,
                    accessibilityLabel = stringResource(R.string.pittec_mock_ride_data_client, appointment.client.displayName),
                ),
                MapMarkerSpec(
                    id = "pittec-mock-provider",
                    coordinate = appointment.providerLocation.coordinate,
                    kind = MapMarkerKind.PITTEC_LIVE,
                    accessibilityLabel = stringResource(R.string.pittec_mock_ride_data_provider, appointment.provider.displayName),
                ),
            )
            MapLibreMapHost(
                configuration = PitGoManagedMapStyle.configuration,
                renderModel = MapRenderModel(markers, journey.polyline),
                viewport = viewport,
                cameraCommand = rememberJourneyCamera(cameraCommand ?: remember(appointment.id) { MapViewportCommand(viewport, 1) }, journey.polyline),
                styleReloadRevision = styleReloadRevision,
                online = true,
                styleController = styleController,
                onViewportIdle = onViewportIdle,
                onFeatureClick = {},
                onSurfaceState = onSurfaceState,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(PitGoSpacing.Small),
                ) {
                    Text(stringResource(R.string.pittec_mock_map_unavailable))
                    Button(onClick = onRetryStyle) { Text(stringResource(R.string.action_retry)) }
                }
            }
        }

        Column(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(PitGoSpacing.Small),
            verticalArrangement = Arrangement.spacedBy(PitGoSpacing.Small),
        ) {
            AppointmentSummaryCard(appointment, isStale, journey)
            TextButton(onClick = onRecenter, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.pittec_mock_recenter))
            }
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(PitGoSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(PitGoSpacing.Small),
        ) {
            AppointmentConfirmationActions(
                appointment = appointment,
                isClientViewer = isClientViewer,
                onMeetingConfirmed = onMeetingConfirmed,
                onCompletionConfirmed = onCompletionConfirmed,
                completedActionLabel = completedActionLabel,
                onCompletedAction = onCompletedAction,
            )
        }
    }
}

@Composable
private fun AppointmentSummaryCard(appointment: MockAppointment, isStale: Boolean, journey: JourneyRouteUiState) {
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
                stringResource(
                    R.string.pittec_mock_participants,
                    appointment.client.displayName,
                    appointment.provider.displayName,
                ),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            JourneyRouteSummary(journey, stale = isStale)
            Text(stringResource(R.string.pittec_mock_total_value, formatCents(appointment.estimate.totalCents)))
            Text(stringResource(statusLabel(appointment.status)))
            val formatter = DateFormat.getTimeFormat(LocalContext.current)
            val time = formatter.format(Date(appointment.providerLocation.capturedAtEpochMillis))
            Text(
                if (isStale) stringResource(R.string.pittec_mock_position_stale, time) else stringResource(R.string.pittec_mock_position_updated_at, time),
                color = if (isStale) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AppointmentConfirmationActions(
    appointment: MockAppointment,
    isClientViewer: Boolean,
    onMeetingConfirmed: () -> Unit,
    onCompletionConfirmed: () -> Unit,
    completedActionLabel: String,
    onCompletedAction: () -> Unit,
) {
    // The party that already confirmed waits for the other role — worded from this viewer's side.
    val waitingForOtherRole = if (isClientViewer) R.string.pittec_mock_waiting_provider else R.string.pittec_mock_waiting_client
    when (appointment.status) {
        MockAppointmentStatus.TRAVELLING -> Button(
            onClick = onMeetingConfirmed,
            modifier = Modifier.fillMaxWidth().heightIn(min = PitGoSize.PrimaryActionMinHeight),
        ) { Text(stringResource(R.string.pittec_mock_meeting_action)) }

        MockAppointmentStatus.AWAITING_MEETING_CONFIRMATION -> Column(
            verticalArrangement = Arrangement.spacedBy(PitGoSpacing.ExtraSmall),
        ) {
            OutlinedButton(
                onClick = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth().heightIn(min = PitGoSize.PrimaryActionMinHeight),
            ) { Text(stringResource(R.string.pittec_mock_meeting_action)) }
            Text(stringResource(waitingForOtherRole), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }

        MockAppointmentStatus.IN_PROGRESS -> Button(
            onClick = onCompletionConfirmed,
            modifier = Modifier.fillMaxWidth().heightIn(min = PitGoSize.PrimaryActionMinHeight),
        ) { Text(stringResource(R.string.pittec_mock_completion_action)) }

        MockAppointmentStatus.AWAITING_COMPLETION_CONFIRMATION -> Column(
            verticalArrangement = Arrangement.spacedBy(PitGoSpacing.ExtraSmall),
        ) {
            OutlinedButton(
                onClick = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth().heightIn(min = PitGoSize.PrimaryActionMinHeight),
            ) { Text(stringResource(R.string.pittec_mock_completion_action)) }
            Text(stringResource(waitingForOtherRole), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }

        MockAppointmentStatus.COMPLETED -> Column(
            verticalArrangement = Arrangement.spacedBy(PitGoSpacing.Small),
        ) {
            Text(
                stringResource(R.string.pittec_mock_completed_summary),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Button(
                onClick = onCompletedAction,
                modifier = Modifier.fillMaxWidth().heightIn(min = PitGoSize.PrimaryActionMinHeight),
            ) { Text(completedActionLabel) }
        }
    }
}

internal fun statusLabel(status: MockAppointmentStatus): Int = when (status) {
    MockAppointmentStatus.TRAVELLING -> R.string.pittec_mock_status_travelling
    MockAppointmentStatus.AWAITING_MEETING_CONFIRMATION -> R.string.pittec_mock_status_awaiting_meeting
    MockAppointmentStatus.IN_PROGRESS -> R.string.pittec_mock_status_in_progress
    MockAppointmentStatus.AWAITING_COMPLETION_CONFIRMATION -> R.string.pittec_mock_status_awaiting_completion
    MockAppointmentStatus.COMPLETED -> R.string.pittec_mock_status_completed
}

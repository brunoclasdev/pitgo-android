package com.trustarx.pitgo.features.mobiledispatch.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.trustarx.pitgo.core.routing.ui.*
import com.trustarx.pitgo.R
import com.trustarx.pitgo.core.design.theme.PitGoSize
import com.trustarx.pitgo.core.design.theme.PitGoSpacing
import com.trustarx.pitgo.core.map.config.PitGoManagedMapStyle
import com.trustarx.pitgo.core.map.model.MapRenderModel
import com.trustarx.pitgo.core.map.ui.MapLibreMapHost
import com.trustarx.pitgo.core.map.ui.ParticipantMapStyleController
import com.trustarx.pitgo.features.mobiledispatch.model.ProviderMapState

/**
 * Label used inside dispatch action buttons (client and provider) that shows an inline spinner
 * while the action they trigger is being processed, instead of just going disabled silently.
 */
@Composable
internal fun ButtonLabel(text: String, isLoading: Boolean) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(PitGoSpacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(PitGoSize.NavigationIcon),
                strokeWidth = 2.dp,
            )
        }
        Text(text)
    }
}

/**
 * Provider's live map for an accepted appointment (US3): shows the provider's own position and
 * the client's, framed together, updating in real time via the same private channel the client's
 * AppointmentTrackingScreen already uses. Falls back to a text notice when neither position is
 * known yet — never an empty map or a marker at the origin.
 */
@Composable
internal fun ProviderAppointmentMap(mapState: ProviderMapState, onFollowChanged: (Boolean) -> Unit) {
    if (mapState.markers.isEmpty()) {
        Text(stringResource(R.string.tracking_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val journey = rememberJourneyRoute(mapState.providerLocation, mapState.clientLocation)
    Column(verticalArrangement = Arrangement.spacedBy(PitGoSpacing.Small)) {
        JourneyRouteSummary(journey)
        Box(Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 320.dp)) {
            MapLibreMapHost(
                configuration = PitGoManagedMapStyle.configuration,
                renderModel = MapRenderModel(mapState.markers, journey.polyline),
                viewport = mapState.viewport,
                cameraCommand = rememberJourneyCamera(mapState.cameraCommand, journey.polyline, mapState.followEnabled),
                styleReloadRevision = 0,
                online = true,
                styleController = remember { ParticipantMapStyleController() },
                onViewportIdle = {},
                onFeatureClick = {},
                onSurfaceState = {},
                modifier = Modifier.fillMaxSize(),
                onUserGesture = { onFollowChanged(false) },
            )
        }
        if (mapState.providerLocation == null || mapState.clientLocation == null) {
            Text(
                stringResource(R.string.tracking_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = PitGoSpacing.ExtraSmall) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = PitGoSpacing.Medium),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.tracking_follow))
                Switch(
                    checked = mapState.followEnabled,
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

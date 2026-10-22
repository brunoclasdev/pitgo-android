package com.trustarx.pitgo.core.routing.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.trustarx.pitgo.R
import com.trustarx.pitgo.core.map.model.*
import com.trustarx.pitgo.core.routing.model.*
import com.trustarx.pitgo.core.routing.repository.JourneyRouteRepository
import kotlinx.coroutines.delay
import java.util.Date
import kotlin.math.ceil

class JourneyRouteUiState {
    var snapshot by mutableStateOf<JourneyRoute?>(null)
        internal set
    var loading by mutableStateOf(false)
        internal set
    var failed by mutableStateOf(false)
        internal set
    internal var retry by mutableIntStateOf(0)
    var origin by mutableStateOf<MapCoordinate?>(null)
        internal set
    var destination by mutableStateOf<MapCoordinate?>(null)
        internal set
    internal var nowEpochMillis by mutableLongStateOf(System.currentTimeMillis())
    val progress: JourneyProgress? get() = origin?.let { snapshot?.remainingAt(it) }
    val polyline: MapRoutePolyline? get() = progress?.polyline
    val distanceMeters: Double? get() = progress?.distanceMeters

}

/** One request at a time; moving origins are sampled every 15s without cancelling an in-flight call. */
@Composable
fun rememberJourneyRoute(
    origin: MapCoordinate?,
    destination: MapCoordinate?,
    enabled: Boolean = true,
    reference: JourneyRoute? = null,
    online: Boolean = true,
    fetchDirections: Boolean = true,
    routeKey: Any? = null,
): JourneyRouteUiState {
    val from = origin?.takeIf { enabled && it.isValid }
    val to = destination?.takeIf { enabled && it.isValid }
    val state = remember(to, from != null, routeKey) {
        JourneyRouteUiState().apply {
            snapshot = reference?.takeIf { it.destination == to }
            loading = from != null && to != null && online && fetchDirections && snapshot == null
        }
    }
    state.origin = from
    state.destination = to
    val currentOrigin by rememberUpdatedState(from)
    val currentReference by rememberUpdatedState(reference?.takeIf { it.destination == to })
    val repository = remember { JourneyRouteRepository() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(state, reference) {
        currentReference?.let { if (it.generatedAt >= (state.snapshot?.generatedAt ?: 0)) state.snapshot = it }
    }
    LaunchedEffect(state, lifecycle, online, fetchDirections, state.retry) {
        if (from == null || to == null) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                state.nowEpochMillis = System.currentTimeMillis()
                val start = currentOrigin ?: break
                val external = currentReference
                if (external != null && (state.snapshot == null || external.generatedAt > state.snapshot!!.generatedAt)) {
                    state.snapshot = external
                }
                val cached = state.snapshot
                val needsRefresh = cached == null || cached.geometry.durationSeconds == null ||
                    distanceBetween(start, cached.origin) >= 50 || System.currentTimeMillis() - cached.generatedAt > 60_000
                if (online && fetchDirections && needsRefresh) {
                    state.loading = true
                    try {
                        val result = repository.directions(start, to)
                        state.failed = result == null
                        if (result != null) state.snapshot = result
                    } finally { state.loading = false }
                }
                delay(15_000)
            }
        }
    }
    return state
}

@Composable
fun JourneyRouteSummary(state: JourneyRouteUiState, modifier: Modifier = Modifier, stale: Boolean = false) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val meters = state.distanceMeters
        if (meters == null) {
            Text(stringResource(when {
                state.origin == null || state.destination == null -> R.string.journey_location_pending
                state.loading -> R.string.journey_calculating
                else -> R.string.journey_unavailable
            }))
            if (state.loading) LinearProgressIndicator()
            if (state.failed) TextButton(onClick = { state.retry++ }) { Text(stringResource(R.string.action_retry)) }
            return@Column
        }
        Text(stringResource(R.string.journey_distance, meters / 1000.0), style = MaterialTheme.typography.titleMedium)
        val seconds = state.progress?.durationSeconds
        if (seconds != null) {
            val minutes = ceil(seconds / 60.0).toInt()
            val duration = when {
                minutes == 0 -> stringResource(R.string.journey_arriving)
                minutes < 60 -> stringResource(R.string.journey_minutes, minutes)
                else -> stringResource(R.string.journey_hours_minutes, minutes / 60, minutes % 60)
            }
            val arrival = android.text.format.DateFormat.getTimeFormat(LocalContext.current)
                .format(Date(state.nowEpochMillis + seconds * 1000L))
            Text(stringResource(R.string.journey_eta, duration, arrival))
            Text(stringResource(R.string.journey_no_live_traffic), style = MaterialTheme.typography.bodySmall)
        } else {
            Text(stringResource(R.string.journey_eta_unavailable))
        }
        if (stale || state.failed && state.snapshot != null) Text(stringResource(R.string.journey_last_reference))
        if (state.loading) LinearProgressIndicator()
        if (state.failed) TextButton(onClick = { state.retry++ }) { Text(stringResource(R.string.action_retry)) }
    }
}

/** A local monotonic revision lets loaded geometry reframe without taking over after a gesture. */
@Composable
fun rememberJourneyCamera(command: MapViewportCommand?, route: MapRoutePolyline?, enabled: Boolean = true): MapViewportCommand? =
    remember(command, route?.points, enabled) {
        if (!enabled || command == null) null
        else command.copy(revision = System.nanoTime(), fitCoordinates = route?.points ?: command.fitCoordinates)
    }

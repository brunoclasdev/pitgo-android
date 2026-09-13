package com.trustarx.pitgo.features.mobiledispatch.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import com.trustarx.pitgo.R
import com.trustarx.pitgo.core.design.theme.PitGoSize
import com.trustarx.pitgo.core.design.theme.PitGoSpacing
import com.trustarx.pitgo.core.map.config.PitGoManagedMapStyle
import com.trustarx.pitgo.core.map.model.MapMarkerKind
import com.trustarx.pitgo.core.map.model.MapMarkerSpec
import com.trustarx.pitgo.core.map.model.MapRenderModel
import com.trustarx.pitgo.core.map.model.MapViewport
import com.trustarx.pitgo.core.map.model.MapViewportCommand
import com.trustarx.pitgo.core.map.ui.MapLibreMapHost
import com.trustarx.pitgo.features.payment.model.PaymentStatus
import com.trustarx.pitgo.features.mobiledispatch.model.ClientMobileDispatchUiState
import com.trustarx.pitgo.features.mobiledispatch.model.MobileDispatchFailure
import com.trustarx.pitgo.features.mobiledispatch.model.MobileDispatchStatus
import com.trustarx.pitgo.features.mobiledispatch.model.PendingDispatchAction
import com.trustarx.pitgo.features.mobiledispatch.viewmodel.ClientMobileDispatchViewModel
import java.text.NumberFormat
import java.util.Locale

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ClientMobileDispatchRoute(
    viewModel: ClientMobileDispatchViewModel,
    onBack: () -> Unit,
    onSessionExpired: () -> Unit,
    onAccepted: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val hasPrecisePermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true) viewModel.startWithPreciseLocation()
    }
    LaunchedEffect(state, hasPrecisePermission) {
        val locationState = state as? ClientMobileDispatchUiState.LocationRequired
        if (locationState != null && locationState.failure == null && hasPrecisePermission) {
            viewModel.startWithPreciseLocation()
        }
    }
    LaunchedEffect(state) {
        if (state == ClientMobileDispatchUiState.SessionExpired) onSessionExpired()
        val content = state as? ClientMobileDispatchUiState.Content
        val appointment = content?.snapshot?.appointment
        if (content?.snapshot?.status in setOf(MobileDispatchStatus.ACCEPTED, MobileDispatchStatus.IN_PROGRESS) && appointment != null) {
            onAccepted(appointment.id)
        }
    }
    ClientMobileDispatchScreen(
        state = state,
        onBack = onBack,
        onRequestLocation = {
            if (hasPrecisePermission) {
                viewModel.startWithPreciseLocation()
            } else {
                permission.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                    )
                )
            }
        },
        onRetry = viewModel::retry,
        onNext = viewModel::next,
        onRequest = viewModel::request,
    )
}

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
private fun ClientMobileDispatchScreen(
    state: ClientMobileDispatchUiState,
    onBack: () -> Unit,
    onRequestLocation: () -> Unit,
    onRetry: () -> Unit,
    onNext: () -> Unit,
    onRequest: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val refundedMessage = stringResource(R.string.payment_refunded_message)
    val refundPendingMessage = stringResource(R.string.payment_refund_pending_message)
    val refundNotice = (state as? ClientMobileDispatchUiState.Content)?.refundNotice
    LaunchedEffect(refundNotice) {
        when (refundNotice) {
            PaymentStatus.REFUNDED -> snackbarHostState.showSnackbar(refundedMessage)
            PaymentStatus.REFUND_FAILED -> snackbarHostState.showSnackbar(refundPendingMessage)
            else -> Unit
        }
    }
    Scaffold(
        topBar = { TopAppBar(
            title = { Text(stringResource(R.string.mobile_dispatch_title)) },
            navigationIcon = { IconButton(onBack) { Icon(painterResource(R.drawable.ic_back), stringResource(R.string.map_back)) } },
        ) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when (state) {
            ClientMobileDispatchUiState.Restoring -> Loading(Modifier.padding(padding), R.string.mobile_dispatch_restoring)
            ClientMobileDispatchUiState.Locating -> Loading(Modifier.padding(padding), R.string.mobile_dispatch_locating)
            ClientMobileDispatchUiState.StartingSearch -> Loading(
                Modifier.padding(padding),
                R.string.mobile_dispatch_starting_search,
            )
            is ClientMobileDispatchUiState.LocationRequired -> {
                val fixUnavailable = state.failure == MobileDispatchFailure.LOCATION_UNAVAILABLE
                CenterAction(
                    modifier = Modifier.padding(padding),
                    title = stringResource(
                        if (fixUnavailable) R.string.mobile_dispatch_location_unavailable_title
                        else R.string.mobile_dispatch_location_title
                    ),
                    message = stringResource(
                        if (fixUnavailable) R.string.mobile_dispatch_location_unavailable_message
                        else R.string.mobile_dispatch_location_message
                    ),
                    action = stringResource(
                        if (fixUnavailable) R.string.action_retry
                        else R.string.mobile_dispatch_use_location
                    ),
                    onAction = onRequestLocation,
                )
            }
            is ClientMobileDispatchUiState.Failure -> CenterAction(
                Modifier.padding(padding), stringResource(R.string.mobile_dispatch_failure_title),
                failureText(state.reason), stringResource(R.string.action_retry), onRetry,
            )
            is ClientMobileDispatchUiState.Empty -> CenterAction(
                Modifier.padding(padding), stringResource(R.string.mobile_dispatch_empty_title),
                stringResource(R.string.mobile_dispatch_empty_message), stringResource(R.string.action_retry), onRetry,
            )
            is ClientMobileDispatchUiState.Terminal -> CenterAction(
                Modifier.padding(padding), stringResource(R.string.mobile_dispatch_finished),
                state.status.name, stringResource(R.string.action_retry), onRetry,
            )
            ClientMobileDispatchUiState.SessionExpired -> Loading(Modifier.padding(padding), R.string.session_verifying)
            is ClientMobileDispatchUiState.Content -> DispatchContent(state, onNext, onRequest, Modifier.padding(padding))
        }
    }
}

@Composable
private fun DispatchContent(
    state: ClientMobileDispatchUiState.Content,
    onNext: () -> Unit,
    onRequest: () -> Unit,
    modifier: Modifier,
) {
    val candidate = state.snapshot.candidate
    val viewport = candidate?.coordinate?.let { MapViewport(it, 14.0) }
    // MapLibreMapHost only ever applies `viewport` to the camera on its very first composition
    // (see MapLibreHostHolder.applyLatest's `initializedCamera` guard) — every candidate change
    // after that needs a bumped MapViewportCommand to actually move the camera. The search's own
    // `revision` already increments on every "next" (and on every server update), so it's a ready
    // made monotonic counter — reusing it here instead of tracking a separate one.
    val cameraCommand = viewport?.let { MapViewportCommand(it, state.snapshot.revision) }
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(PitGoSpacing.Medium),
        verticalArrangement = Arrangement.spacedBy(PitGoSpacing.Medium),
    ) {
        item {
            Text(
                when (state.snapshot.status) {
                    MobileDispatchStatus.SEARCHING -> stringResource(R.string.mobile_dispatch_searching, state.snapshot.radiusMeters / 1000)
                    MobileDispatchStatus.AWAITING_PROVIDER -> stringResource(R.string.mobile_dispatch_awaiting)
                    else -> stringResource(R.string.mobile_dispatch_proposal)
                },
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading(); liveRegion = LiveRegionMode.Polite },
            )
        }
        if (candidate != null) {
            item {
                Box(Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 320.dp)) {
                    MapLibreMapHost(
                        configuration = PitGoManagedMapStyle.configuration,
                        renderModel = MapRenderModel(listOf(MapMarkerSpec(
                            candidate.id, candidate.coordinate, MapMarkerKind.MOBILE_PROVIDER,
                            selected = true, accessibilityLabel = candidate.providerName,
                        ))),
                        viewport = requireNotNull(viewport), cameraCommand = cameraCommand,
                        styleReloadRevision = 0, online = true,
                        styleController = remember { MobileDispatchMapStyleController() },
                        onViewportIdle = {}, onFeatureClick = {}, onSurfaceState = {},
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) { Column(
                    Modifier.padding(PitGoSpacing.Large),
                    verticalArrangement = Arrangement.spacedBy(PitGoSpacing.Small),
                ) {
                    Text(candidate.providerName, style = MaterialTheme.typography.titleLarge)
                    Text(candidate.locality)
                    Text(
                        if (candidate.distanceMeters < NEARBY_DISTANCE_METERS_THRESHOLD) {
                            stringResource(R.string.mobile_dispatch_distance_nearby)
                        } else {
                            stringResource(R.string.mobile_dispatch_distance, candidate.distanceMeters / 1000.0)
                        },
                    )
                    Text(
                        if (candidate.isFree) stringResource(R.string.mobile_dispatch_free)
                        else NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(candidate.price),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    state.remainingSeconds?.let { Text(stringResource(R.string.mobile_dispatch_seconds, it)) }
                } }
            }
            item { Button(
                onClick = onRequest, enabled = state.pending == null && state.snapshot.status == MobileDispatchStatus.OPTION_FOUND,
                modifier = Modifier.fillMaxWidth().heightIn(min = PitGoSize.PrimaryActionMinHeight),
            ) { ButtonLabel(stringResource(R.string.mobile_dispatch_request), state.pending?.kind == PendingDispatchAction.REQUEST) } }
            item { OutlinedButton(
                onClick = onNext, enabled = state.pending == null && state.snapshot.status == MobileDispatchStatus.OPTION_FOUND,
                modifier = Modifier.fillMaxWidth().heightIn(min = PitGoSize.MinimumTouchTarget),
            ) { ButtonLabel(stringResource(R.string.mobile_dispatch_next), state.pending?.kind == PendingDispatchAction.NEXT) } }
        }
        state.failure?.let { item { Text(failureText(it), color = MaterialTheme.colorScheme.error) } }
    }
}

@Composable private fun Loading(modifier: Modifier, textRes: Int) = Box(modifier.fillMaxSize(), Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator(); Text(stringResource(textRes)) }
}

@Composable private fun CenterAction(modifier: Modifier, title: String, message: String, action: String, onAction: () -> Unit) =
    Box(modifier.fillMaxSize(), Alignment.Center) { Column(
        Modifier.padding(PitGoSpacing.Large), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PitGoSpacing.Medium),
    ) { Text(title, style = MaterialTheme.typography.titleLarge); Text(message); Button(onAction) { Text(action) } } }

@Composable private fun failureText(failure: MobileDispatchFailure) = stringResource(when (failure) {
    MobileDispatchFailure.OFFLINE -> R.string.mobile_dispatch_offline
    MobileDispatchFailure.TIMEOUT -> R.string.mobile_dispatch_timeout
    MobileDispatchFailure.LOCATION_REQUIRED -> R.string.mobile_dispatch_location_message
    MobileDispatchFailure.LOCATION_UNAVAILABLE -> R.string.mobile_dispatch_location_unavailable_message
    else -> R.string.mobile_dispatch_unavailable
})

private const val NEARBY_DISTANCE_METERS_THRESHOLD = 500.0

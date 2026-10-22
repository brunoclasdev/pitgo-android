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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.trustarx.pitgo.core.navigation.ExternalNavigationLauncher
import com.trustarx.pitgo.core.navigation.ExternalNavigationLaunchResult
import com.trustarx.pitgo.features.appointmenttracking.model.ExternalNavigationState
import com.trustarx.pitgo.core.routing.ui.*
import com.trustarx.pitgo.core.routing.model.JourneyRoute
import com.trustarx.pitgo.R
import com.trustarx.pitgo.core.design.theme.PitGoSize
import com.trustarx.pitgo.core.design.theme.PitGoSpacing
import com.trustarx.pitgo.core.map.config.PitGoManagedMapStyle
import com.trustarx.pitgo.core.map.model.MapRenderModel
import com.trustarx.pitgo.core.map.model.MapResourceState
import com.trustarx.pitgo.core.map.model.MapStyleState
import com.trustarx.pitgo.core.map.ui.MapLibreMapHost
import com.trustarx.pitgo.core.map.ui.ParticipantMapStyleController
import com.trustarx.pitgo.features.appointmenttracking.model.AppointmentProviderLocation
import com.trustarx.pitgo.features.appointmenttracking.model.AppointmentTrackingUiState
import com.trustarx.pitgo.features.appointmenttracking.model.TrackingDataState
import com.trustarx.pitgo.features.appointmenttracking.viewmodel.AppointmentTrackingViewModel
import com.trustarx.pitgo.features.mobiledispatch.model.MobileAppointmentStatus
import com.trustarx.pitgo.features.mobiledispatch.model.MobileDispatchAction
import java.text.NumberFormat
import java.util.Locale

@Composable
fun AppointmentTrackingRoute(
    viewModel: AppointmentTrackingViewModel,
    isClient: Boolean,
    onBack: () -> Unit,
    onFinished: () -> Unit,
    onSessionExpired: () -> Unit,
    historical: Boolean = false,
    onOpenInspection: (String) -> Unit = {},
    onNeedsPayment: (com.trustarx.pitgo.features.mobiledispatch.model.MobileDispatchSnapshot) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleStartEffect(viewModel, historical) {
        viewModel.onVisible(historical)
        onStopOrDispose { viewModel.onHidden() }
    }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val openNavigation: () -> Unit = {
        val options = viewModel.state.value.navigationCoordinate?.let {
            ExternalNavigationLauncher.options(context, it)
        }.orEmpty()
        viewModel.openExternalNavigation(options)
    }
    LaunchedEffect(viewModel, context, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.state.collect { latest ->
                val ready = latest.externalNavigation as? ExternalNavigationState.ReadyToLaunch
                val request = ready?.let { viewModel.consumeExternalNavigation(it.request.id) }
                if (request != null && ExternalNavigationLauncher.open(context, request.navigator, request.target.coordinate) ==
                    ExternalNavigationLaunchResult.UNAVAILABLE
                ) {
                    viewModel.externalNavigationLaunchFailed(request.id,
                        ExternalNavigationLauncher.options(context, request.target.coordinate))
                }
            }
        }
    }
    ExternalNavigationChooser(
        state.externalNavigation,
        onChoose = viewModel::chooseExternalNavigator,
        onRetry = openNavigation,
        onDismiss = viewModel::dismissExternalNavigation,
    )
    var openingInspection by remember(viewModel) { mutableStateOf(false) }
    var wasDisputed by remember(viewModel) { mutableStateOf(false) }
    LaunchedEffect(state.dispatch?.appointment?.status, historical, isClient) {
        val snapshot = state.dispatch
        if (!historical && isClient && state.isGuincho &&
            snapshot?.appointment?.status == MobileAppointmentStatus.AWAITING_PAYMENT) onNeedsPayment(snapshot)
    }
    LaunchedEffect(state.data, state.dispatch?.appointment?.status) {
        if (state.data is TrackingDataState.SessionExpired) onSessionExpired()
        val status = state.dispatch?.appointment?.status
        if (status == MobileAppointmentStatus.DISPUTED) wasDisputed = true
        if (!historical && (status == MobileAppointmentStatus.COMPLETED ||
                (wasDisputed && status == MobileAppointmentStatus.CANCELLED))) onFinished()
    }
    LaunchedEffect(state.dispatch?.appointment?.arrivedAt, state.arriving, openingInspection) {
        if (openingInspection && state.dispatch?.appointment?.arrivedAt != null && state.dispatch?.guinchoInspection?.required == true) {
            openingInspection = false
            onOpenInspection(requireNotNull(state.dispatch?.appointment).id)
        } else if (openingInspection && !state.arriving && state.actionFailed) openingInspection = false
    }
    if(state.isGuincho) {
        com.trustarx.pitgo.features.guincho.ui.GuinchoTrackingContent(state,viewModel,isClient,onBack,
            onReturnHome = if(historical) onBack else onFinished,
            onOpenInspection = onOpenInspection, onArrive = { openingInspection = true; viewModel.arrive() },
            onOpenExternalNavigation = openNavigation)
        return
    }
    AppointmentTrackingScreen(
        state = state,
        isClient = isClient,
        styleController = remember { ParticipantMapStyleController() },
        onBack = onBack,
        onConfirmCompletion = viewModel::confirmCompletion,
        onFollowChanged = viewModel::setFollowProvider,
        onToggleSummary = viewModel::toggleSummary,
        onViewportIdle = viewModel::onViewportIdle,
        onSurfaceState = viewModel::updateSurfaceState,
        onRetryStyle = viewModel::retryStyle,
        onCancel = viewModel::cancel,
        onArrive = viewModel::arrive,
        onComplete = viewModel::complete,
        onAcknowledgeStart = viewModel::acknowledgeStart,
        onOpenExternalNavigation = openNavigation,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppointmentTrackingScreen(
    state: AppointmentTrackingUiState,
    isClient: Boolean,
    styleController: ParticipantMapStyleController,
    onBack: () -> Unit,
    onConfirmCompletion: () -> Unit,
    onFollowChanged: (Boolean) -> Unit,
    onToggleSummary: () -> Unit,
    onViewportIdle: (com.trustarx.pitgo.core.map.model.MapViewport) -> Unit,
    onSurfaceState: (com.trustarx.pitgo.core.map.model.MapSurfaceState) -> Unit,
    onRetryStyle: () -> Unit,
    onCancel: (String) -> Unit,
    onArrive: () -> Unit,
    onComplete: () -> Unit,
    onAcknowledgeStart: () -> Unit,
    onOpenExternalNavigation: () -> Unit,
) {
    val reference = state.route?.let { snapshot ->
        snapshot.geometry?.let { geometry ->
            JourneyRoute(geometry.points.first().toMapCoordinate(), state.clientLocation?.coordinate ?: geometry.points.last().toMapCoordinate(),
                geometry, snapshot.distanceMeters, snapshot.generatedAtEpochMillis)
        }
    }
    val trackable = (state.dispatch?.trackingAuthorized == true || !state.isPitTec && !state.isGuincho) &&
        state.dispatch?.appointment?.status in setOf(MobileAppointmentStatus.ACCEPTED, MobileAppointmentStatus.IN_PROGRESS)
    val journey = rememberJourneyRoute(state.visibleLocation?.coordinate, state.clientLocation?.coordinate,
        enabled = trackable, reference = reference, fetchDirections = !state.isPitTec,
        online = state.data !is TrackingDataState.Offline)
    var showCancel by remember { mutableStateOf(false) }
    var reason by remember { mutableStateOf("") }
    var summaryHeight by remember { mutableStateOf(0) }
    val summaryPadding = with(LocalDensity.current) { summaryHeight.toDp() }
    val maxSummaryHeight = (LocalConfiguration.current.screenHeightDp * 0.45f).dp
    val summaryDescription = stringResource(if (state.summaryExpanded) R.string.tracking_summary_expanded else R.string.tracking_summary_collapsed)
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
            val hasClientLocation = state.clientLocation?.isValid == true
            // The provider's position may still be pending (e.g. right after acceptance); the
            // client's own position, once known, is shown on its own rather than blanking the
            // whole map — see spec 014 US2 acceptance scenario 3.
            if ((location != null || hasClientLocation) && (!state.isPitTec || summaryHeight > 0)) {
                MapLibreMapHost(
                    configuration = PitGoManagedMapStyle.configuration,
                    renderModel = MapRenderModel(state.markers, journey.polyline),
                    viewport = state.viewport,
                    cameraCommand = rememberJourneyCamera(state.cameraCommand, journey.polyline, state.followProvider),
                    styleReloadRevision = state.styleReloadRevision,
                    online = state.data !is TrackingDataState.Offline,
                    styleController = styleController,
                    onViewportIdle = onViewportIdle,
                    onFeatureClick = {},
                    onSurfaceState = onSurfaceState,
                    modifier = Modifier.fillMaxSize().padding(bottom = if (state.isPitTec) summaryPadding else 0.dp),
                )
            } else {
                TrackingEmptyState(state.data, Modifier.fillMaxSize())
            }

            if (state.navigationLeg != null) {
                androidx.compose.material3.FilledTonalButton(
                    onClick = onOpenExternalNavigation,
                    modifier = Modifier.align(Alignment.TopEnd).padding(PitGoSpacing.Medium),
                ) {
                    Text(stringResource(R.string.external_navigation_open))
                }
            }

            Column(
                modifier = Modifier
                    .align(if (state.isPitTec) Alignment.BottomCenter else Alignment.TopCenter)
                    .fillMaxWidth()
                    .onSizeChanged { summaryHeight = it.height }
                    .then(if (state.isPitTec) Modifier.heightIn(max = maxSummaryHeight)
                        .verticalScroll(rememberScrollState()).semantics { stateDescription = summaryDescription }
                        else Modifier)
                    .padding(PitGoSpacing.Small),
                verticalArrangement = Arrangement.spacedBy(PitGoSpacing.Small),
            ) {
                TrackingStatus(state.data)
                if (state.isPitTec && state.dispatch?.trackingAuthorized == true &&
                    state.clientLocation == null && state.data != TrackingDataState.Loading
                ) {
                    Text(stringResource(R.string.pittec_client_location_unavailable))
                }
                state.clientLocation?.let { client ->
                    val capturedAt = client.capturedAtEpochMillis
                    if (capturedAt == null || System.currentTimeMillis() - capturedAt > 30_000) {
                        Text(stringResource(R.string.pittec_client_location_stale))
                    }
                }
                state.dispatch?.appointment?.let { appointment ->
                    Surface(shape = MaterialTheme.shapes.large) {
                        Column(Modifier.fillMaxWidth().padding(PitGoSpacing.Medium)) {
                            if (!state.isPitTec || state.summaryExpanded) Text(state.dispatch.serviceName, style = MaterialTheme.typography.titleMedium)
                            if (state.isPitTec) {
                                Text(
                                    stringResource(
                                        when {
                                            appointment.status == MobileAppointmentStatus.COMPLETED -> R.string.pittec_service_finished
                                            appointment.status == MobileAppointmentStatus.CANCELLED -> R.string.pittec_service_cancelled
                                            appointment.status == MobileAppointmentStatus.IN_PROGRESS -> R.string.pittec_service_started
                                            appointment.status == MobileAppointmentStatus.AWAITING_CLIENT_COMPLETION_CONFIRMATION -> if (isClient) R.string.pittec_completion_confirmation_title else R.string.pittec_completion_waiting_client
                                            appointment.arrivedAt != null -> R.string.pittec_arrived_waiting_start
                                            else -> R.string.pittec_on_the_way
                                        }
                                    ),
                                )
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    TextButton(onClick = onToggleSummary) {
                                        Text(stringResource(if (state.summaryExpanded) R.string.tracking_collapse_summary else R.string.tracking_expand_summary))
                                    }
                                    TextButton(onClick = { onFollowChanged(true) }) {
                                        Text(stringResource(R.string.tracking_recenter))
                                    }
                                }
                            }
                            if (trackable) JourneyRouteSummary(journey, stale = state.data !is TrackingDataState.Live)
                            if (!state.isPitTec || state.summaryExpanded ||
                                appointment.status == MobileAppointmentStatus.AWAITING_CLIENT_COMPLETION_CONFIRMATION) {
                                if (state.isPitTec) {
                                    val money = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"))
                                    if (!isClient) {
                                        appointment.serviceAmount?.let { Text(stringResource(R.string.pittec_provider_service_value, money.format(it))) }
                                        appointment.distanceChargeAmount?.let { Text(stringResource(R.string.pittec_provider_distance_value, money.format(it))) }
                                    }
                                    Text(stringResource(if (isClient) R.string.pittec_customer_total_value else R.string.pittec_provider_total_value, money.format(appointment.quotedPrice)))
                                } else {
                                    Text(stringResource(
                                        R.string.mobile_dispatch_price_frozen,
                                        NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(appointment.quotedPrice),
                                    ))
                                    Text(appointment.status.name)
                                }
                                if (state.actionFailed) Text(stringResource(R.string.pittec_action_retry), color = MaterialTheme.colorScheme.error)
                                if (isClient && state.isPitTec && appointment.status == MobileAppointmentStatus.IN_PROGRESS) {
                                    if (appointment.clientStartAcknowledgedAt == null) {
                                        Button(onClick = onAcknowledgeStart, enabled = !state.arriving) {
                                            Text(stringResource(R.string.pittec_acknowledge_start))
                                        }
                                    } else Text(stringResource(R.string.pittec_start_acknowledged))
                                }
                                if (isClient && state.isPitTec && appointment.status == MobileAppointmentStatus.AWAITING_CLIENT_COMPLETION_CONFIRMATION) {
                                    Surface(
                                        shape = MaterialTheme.shapes.large,
                                        tonalElevation = PitGoSpacing.Small,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Column(
                                            Modifier.fillMaxWidth().padding(PitGoSpacing.Medium),
                                            verticalArrangement = Arrangement.spacedBy(PitGoSpacing.Small),
                                        ) {
                                            Text(stringResource(R.string.pittec_completion_confirmation_title), style = MaterialTheme.typography.titleMedium)
                                            Text(stringResource(R.string.pittec_completion_confirmation_message))
                                            Button(
                                                onClick = onConfirmCompletion,
                                                enabled = !state.completing,
                                                modifier = Modifier.fillMaxWidth(),
                                            ) { Text(stringResource(R.string.pittec_completion_confirmation_action)) }
                                        }
                                    }
                                }
                                if (!isClient && state.isPitTec) {
                                    PitTecProviderActions(state, onArrive, onComplete)
                                }
                                if (!state.isPitTec || MobileDispatchAction.CANCEL in state.dispatch.allowedActions) TextButton(onClick = { showCancel = true }, enabled = !state.cancelling && state.data != TrackingDataState.Ended) {
                                    Text(stringResource(
                                        if (state.isPitTec) R.string.pittec_cancel_service
                                        else R.string.mobile_dispatch_cancel,
                                    ))
                                }
                            }
                        }
                    }
                }
                MapSurfaceWarning(state, onRetryStyle)
                if (location != null && !state.isPitTec) {
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

            if (location != null || hasClientLocation) MapAttribution(Modifier.align(Alignment.BottomStart)
                .padding(bottom = if (state.isPitTec) summaryPadding else 0.dp))
        }
    }
    if (showCancel) AlertDialog(
        onDismissRequest = { showCancel = false },
        title = { Text(stringResource(R.string.mobile_dispatch_cancel_title)) },
        text = { OutlinedTextField(
            value = reason, onValueChange = { reason = it }, minLines = 3,
            label = { Text(stringResource(R.string.mobile_dispatch_cancel_reason)) },
            isError = reason.isBlank(),
        ) },
        confirmButton = { Button(
            onClick = { onCancel(reason); showCancel = false }, enabled = reason.isNotBlank(),
        ) { Text(stringResource(R.string.mobile_dispatch_cancel_confirm)) } },
        dismissButton = { TextButton({ showCancel = false }) { Text(stringResource(R.string.action_back)) } },
    )
}

@Composable
private fun PitTecProviderActions(
    state: AppointmentTrackingUiState,
    onArrive: () -> Unit,
    onComplete: () -> Unit,
) {
    val appointment = state.dispatch?.appointment ?: return
    when (appointment.status) {
        MobileAppointmentStatus.ACCEPTED -> Button(
            onClick = onArrive,
            enabled = !state.arriving && state.data != TrackingDataState.Ended,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(if (appointment.arrivedAt == null) R.string.pittec_arrive else R.string.pittec_start_service)) }
        MobileAppointmentStatus.IN_PROGRESS -> Button(
            onClick = onComplete,
            enabled = !state.completing && state.data != TrackingDataState.Ended,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.pittec_complete)) }
        MobileAppointmentStatus.AWAITING_CLIENT_COMPLETION_CONFIRMATION ->
            Text(stringResource(R.string.pittec_completion_waiting_client))
        else -> Unit
    }
}

@Composable
internal fun TrackingStatus(data: TrackingDataState) {
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
        is TrackingDataState.Offline -> stringResource(
            if (location == null) R.string.tracking_offline_empty else R.string.tracking_offline,
        )
        is TrackingDataState.Failure -> stringResource(
            if (location == null) R.string.tracking_degraded else R.string.tracking_degraded_last_known,
        )
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
                    is TrackingDataState.Offline -> stringResource(
                        if (data.lastLocation == null) R.string.tracking_offline_empty else R.string.tracking_offline,
                    )
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

package com.trustarx.pitgo.features.guincho.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.trustarx.pitgo.core.routing.ui.*
import com.trustarx.pitgo.core.routing.model.JourneyRoute
import com.trustarx.pitgo.R
import com.trustarx.pitgo.core.map.config.PitGoManagedMapStyle
import com.trustarx.pitgo.core.map.model.*
import com.trustarx.pitgo.core.map.ui.MapLibreMapHost
import com.trustarx.pitgo.core.map.ui.ParticipantMapStyleController
import com.trustarx.pitgo.features.appointmenttracking.model.AppointmentTrackingUiState
import com.trustarx.pitgo.features.appointmenttracking.model.TrackingDataState
import com.trustarx.pitgo.features.appointmenttracking.viewmodel.AppointmentTrackingViewModel
import com.trustarx.pitgo.features.mobiledispatch.model.MobileAppointmentStatus
import com.trustarx.pitgo.features.payment.model.PaymentStatus
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuinchoTrackingContent(state: AppointmentTrackingUiState,viewModel: AppointmentTrackingViewModel,
    isClient: Boolean,onBack: () -> Unit,onReturnHome: () -> Unit = onBack,
    onOpenInspection: (String) -> Unit = {}, onArrive: () -> Unit = viewModel::arrive) {
    val snapshot=state.dispatch ?: return
    val appointment=snapshot.appointment ?: return
    val trip=snapshot.guinchoTrip
    if (trip == null) {
        val preparingPaidTrip = snapshot.paymentStatus == PaymentStatus.APPROVED && appointment.status in setOf(
            MobileAppointmentStatus.AWAITING_PAYMENT, MobileAppointmentStatus.ACCEPTED, MobileAppointmentStatus.IN_PROGRESS,
        )
        Scaffold(topBar={ TopAppBar(title={ Text(stringResource(R.string.guincho_title)) },
            navigationIcon={ TextButton(onClick=onBack) { Text(stringResource(R.string.guincho_back)) } }) }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                if (preparingPaidTrip) {
                    GuinchoRouteLoadingFeedback(transporting = appointment.status == MobileAppointmentStatus.IN_PROGRESS)
                } else {
                    Text(stringResource(if(appointment.status==MobileAppointmentStatus.CANCELLED)
                        R.string.guincho_cancelled else R.string.pittec_awaiting_payment))
                    if(appointment.status!=MobileAppointmentStatus.CANCELLED) Text(stringResource(R.string.guincho_details_after_payment))
                }
                TextButton(onClick=if(appointment.status==MobileAppointmentStatus.CANCELLED) onReturnHome else onBack) { Text(stringResource(R.string.guincho_home)) }
            }
        }
        return
    }
    var problem by remember(appointment.id) { mutableStateOf(false) }
    var reason by remember(appointment.id) { mutableStateOf("") }
    val money=remember { NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")) }
    val controller=remember { ParticipantMapStyleController() }
    val transporting = appointment.status == MobileAppointmentStatus.IN_PROGRESS
    val trackable = snapshot.trackingAuthorized && appointment.status in setOf(MobileAppointmentStatus.ACCEPTED, MobileAppointmentStatus.IN_PROGRESS)
    val destination = if (transporting) trip.destination else trip.pickup
    val reference = state.guinchoRoute.snapshot?.takeIf { transporting }?.let {
        JourneyRoute(it.origin, it.destination, it.geometry, it.distanceMeters, it.generatedAt)
    }
    val journey = rememberJourneyRoute(state.visibleLocation?.coordinate, destination,
        enabled = trackable, reference = reference,
        fetchDirections = isClient || !transporting || reference?.geometry?.durationSeconds == null,
        online = state.data !is com.trustarx.pitgo.features.appointmenttracking.model.TrackingDataState.Offline)
    val line = journey.polyline
    var recenterRevision by remember(appointment.id) { mutableLongStateOf(0L) }
    LaunchedEffect(journey.snapshot, state.visibleLocation?.revision) {
        if (com.trustarx.pitgo.BuildConfig.DEBUG) journey.snapshot?.let { route ->
            val points = route.geometry.points.map { it.toMapCoordinate() }
            val endpointError = com.trustarx.pitgo.core.routing.model.distanceBetween(points.last(), destination).toInt()
            val geometryMeters = points.zipWithNext { a, b -> com.trustarx.pitgo.core.routing.model.distanceBetween(a, b) }.sum().toInt()
            android.util.Log.d("PitGoRoute", "viewer=${if (isClient) "client" else "provider"} phase=${appointment.status} geometryPoints=${points.size} geometryMeters=$geometryMeters routeMeters=${route.distanceMeters} endpointErrorMeters=$endpointError")
        }
    }
    val waitingForLocation = state.visibleLocation == null &&
        (state.data is TrackingDataState.Loading || state.data is TrackingDataState.Empty)
    val calculatingRoute = journey.loading || (!isClient && transporting && state.guinchoRoute.loading)
    val viewport=if(state.cameraCommand != null) state.viewport else MapViewport(trip.pickup,15.0)
    val markers=buildList {
        add(MapMarkerSpec("pickup",trip.pickup,MapMarkerKind.CLIENT_LOCATION,accessibilityLabel=trip.pickupAddress))
        add(MapMarkerSpec("destination",trip.destination,MapMarkerKind.CLIENT_LOCATION,accessibilityLabel=trip.destinationAddress))
        if(snapshot.trackingAuthorized) state.visibleLocation?.let { add(MapMarkerSpec("tow",it.coordinate,
            if(state.data is com.trustarx.pitgo.features.appointmenttracking.model.TrackingDataState.Live) MapMarkerKind.LIVE else MapMarkerKind.STALE,
            accessibilityLabel=snapshot.providerName.orEmpty())) }
    }
    Scaffold(topBar={ TopAppBar(title={ Text(stringResource(R.string.guincho_title)) },
        navigationIcon={ TextButton(onClick=onBack) { Text(stringResource(R.string.guincho_back)) } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            snapshot.guinchoInspection?.let { inspection ->
                Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(stringResource(inspectionStateLabel(inspection.state)), style = MaterialTheme.typography.titleSmall)
                        if (inspection.state == com.trustarx.pitgo.features.guincho.model.InspectionVersionState.APPROVED &&
                            appointment.status == MobileAppointmentStatus.ACCEPTED && isClient) Text(stringResource(R.string.inspection_wait_start))
                        if (inspection.versionId != null) TextButton(onClick = { onOpenInspection(appointment.id) }) {
                            Text(stringResource(if (!isClient && inspection.state == com.trustarx.pitgo.features.guincho.model.InspectionVersionState.DRAFT)
                                R.string.inspection_continue else R.string.inspection_history))
                        }
                    }
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                MapLibreMapHost(PitGoManagedMapStyle.configuration,MapRenderModel(markers, route = line),viewport,rememberJourneyCamera(
                        (state.cameraCommand ?: MapViewportCommand(viewport, 0)).let { it.copy(revision = it.revision + recenterRevision) },
                        line, state.followProvider),
                    state.styleReloadRevision,state.data !is com.trustarx.pitgo.features.appointmenttracking.model.TrackingDataState.Offline,controller,viewModel::onViewportIdle,{},viewModel::updateSurfaceState,Modifier.fillMaxSize(), onUserGesture = { viewModel.setFollowProvider(false) }, onRouteRendered = viewModel::guinchoRouteFrame)
                if (line != null) {
                    Surface(Modifier.align(Alignment.BottomEnd).padding(12.dp), shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
                        TextButton(onClick = { recenterRevision++; viewModel.setFollowProvider(true) }) {
                            Text(stringResource(R.string.guincho_show_route))
                        }
                    }
                }
                if (trackable && (waitingForLocation || calculatingRoute)) {
                    GuinchoRouteLoadingFeedback(
                        modifier = Modifier.align(Alignment.TopCenter).padding(16.dp),
                        waitingForLocation = waitingForLocation,
                        updating = journey.snapshot != null,
                        transporting = transporting,
                    )
                }
            }
            Column(Modifier.fillMaxWidth().heightIn(max=330.dp).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(snapshot.providerName.orEmpty(),style=MaterialTheme.typography.titleMedium)
                Text(stringResource(if(isClient) R.string.pittec_customer_total_value else R.string.pittec_provider_total_value,
                    money.format(appointment.quotedPrice)))
                if(!isClient) { Text(trip.clientName); Text(trip.vehicle) }
                Text(stringResource(R.string.guincho_pickup_label,trip.pickupAddress))
                Text(stringResource(R.string.guincho_destination_label,trip.destinationAddress))
                if (trackable) {
                    Text(stringResource(if (transporting) R.string.journey_transport else R.string.journey_to_pickup))
                    JourneyRouteSummary(journey, stale = state.data !is com.trustarx.pitgo.features.appointmenttracking.model.TrackingDataState.Live)
                }
                if (!isClient && appointment.status == MobileAppointmentStatus.IN_PROGRESS) {
                    state.guinchoRoute.failure?.let {
                        Text(stringResource(if (state.guinchoRoute.snapshot == null) R.string.guincho_route_unavailable else R.string.guincho_route_reference))
                        TextButton(onClick = viewModel::retryGuinchoRoute) { Text(stringResource(R.string.inspection_retry)) }
                    }
                }
                if(state.actionFailed) Text(stringResource(R.string.pittec_action_retry),color=MaterialTheme.colorScheme.error)
                if(state.arriving || state.completing) LinearProgressIndicator(Modifier.fillMaxWidth())
                when(appointment.status) {
                    MobileAppointmentStatus.PENDING -> {
                        Text(stringResource(R.string.guincho_waiting_accept))
                        if(isClient) OutlinedButton(onClick={ viewModel.cancel("CLIENT_CANCELLED_BEFORE_ACCEPTANCE") },
                            enabled=!state.cancelling,modifier=Modifier.fillMaxWidth()) { Text(stringResource(R.string.guincho_cancel_request)) }
                    }
                    MobileAppointmentStatus.AWAITING_PAYMENT -> Text(stringResource(R.string.pittec_awaiting_payment))
                    MobileAppointmentStatus.ACCEPTED -> {
                        Text(stringResource(if(appointment.arrivedAt==null) R.string.guincho_on_way else R.string.guincho_arrived))
                        if(!isClient && snapshot.guinchoInspection != null) {
                            val inspection = snapshot.guinchoInspection
                            if (appointment.arrivedAt == null) Button(onClick = onArrive, enabled = !state.arriving, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.pittec_arrive))
                            } else if ((!inspection.required || inspection.state == com.trustarx.pitgo.features.guincho.model.InspectionVersionState.APPROVED) &&
                                com.trustarx.pitgo.features.mobiledispatch.model.MobileDispatchAction.START in snapshot.allowedActions) {
                                Button(onClick = viewModel::startTransport, enabled = !state.arriving, modifier = Modifier.fillMaxWidth()) {
                                    Text(stringResource(R.string.guincho_start_transport))
                                }
                            }
                        } else if (snapshot.guinchoInspection == null) Text(stringResource(R.string.inspection_error_unknown))
                    }
                    MobileAppointmentStatus.IN_PROGRESS -> {
                        Text(stringResource(R.string.guincho_transport_started))
                        if(!isClient) Button(onClick=viewModel::complete,enabled=!state.completing,modifier=Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.pittec_complete))
                        }
                    }
                    MobileAppointmentStatus.AWAITING_CLIENT_COMPLETION_CONFIRMATION -> {
                        if(isClient) {
                            Text(stringResource(R.string.pittec_completion_confirmation_title),style=MaterialTheme.typography.titleMedium)
                            Button(onClick=viewModel::confirmCompletion,enabled=!state.completing,modifier=Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.pittec_completion_confirmation_action))
                            }
                            OutlinedButton(onClick={ problem=true },enabled=!state.completing,modifier=Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.guincho_report_problem))
                            }
                        } else Text(stringResource(R.string.pittec_completion_waiting_client))
                    }
                    MobileAppointmentStatus.DISPUTED -> {
                        Text(stringResource(R.string.guincho_disputed))
                        snapshot.guinchoDispute?.let { Text(it.reason) }
                        TextButton(onClick=onBack) { Text(stringResource(R.string.guincho_home)) }
                    }
                    MobileAppointmentStatus.CANCELLED -> {
                        Text(stringResource(R.string.guincho_cancelled))
                        Text(stringResource(when(snapshot.paymentStatus) {
                            com.trustarx.pitgo.features.payment.model.PaymentStatus.REFUNDED -> R.string.payment_refunded
                            com.trustarx.pitgo.features.payment.model.PaymentStatus.REFUND_PENDING -> R.string.payment_refund_pending
                            com.trustarx.pitgo.features.payment.model.PaymentStatus.REFUND_FAILED -> R.string.payment_support
                            else -> R.string.payment_cancelled
                        }))
                        TextButton(onClick=onReturnHome) { Text(stringResource(R.string.guincho_home)) }
                    }
                    MobileAppointmentStatus.COMPLETED -> Text(stringResource(R.string.pittec_service_finished))
                    else -> Text(stringResource(R.string.mobile_dispatch_restoring))
                }
                snapshot.guinchoDispute?.justification?.let { Text(it) }
            }
        }
    }
    if(problem) AlertDialog(onDismissRequest={ if(!state.completing) problem=false },
        title={ Text(stringResource(R.string.guincho_report_problem)) },
        text={ OutlinedTextField(reason,{ reason=it.take(1000) },label={ Text(stringResource(R.string.guincho_problem_reason)) },
            isError=reason.isBlank(),minLines=3) },
        confirmButton={ Button(onClick={ viewModel.dispute(reason); problem=false },enabled=reason.trim().isNotEmpty() && !state.completing) {
            Text(stringResource(R.string.guincho_send_problem))
        } },dismissButton={ TextButton(onClick={ problem=false }) { Text(stringResource(R.string.guincho_back)) } })
}

@Composable
private fun GuinchoRouteLoadingFeedback(
    modifier: Modifier = Modifier,
    waitingForLocation: Boolean = false,
    updating: Boolean = false,
    transporting: Boolean = false,
) {
    Surface(
        modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            liveRegion = LiveRegionMode.Polite
        },
        shape = MaterialTheme.shapes.large,
        tonalElevation = 6.dp,
        shadowElevation = 2.dp,
    ) {
        Row(
            Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(if (updating) R.string.guincho_route_updating_title else R.string.guincho_route_calculating_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    stringResource(when {
                        waitingForLocation -> R.string.guincho_route_waiting_location
                        updating -> R.string.guincho_route_updating_message
                        transporting -> R.string.guincho_route_calculating_destination
                        else -> R.string.guincho_route_calculating_after_payment
                    }),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

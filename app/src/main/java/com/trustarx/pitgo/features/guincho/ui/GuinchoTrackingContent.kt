package com.trustarx.pitgo.features.guincho.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.trustarx.pitgo.R
import com.trustarx.pitgo.core.map.config.PitGoManagedMapStyle
import com.trustarx.pitgo.core.map.model.*
import com.trustarx.pitgo.core.map.ui.MapLibreMapHost
import com.trustarx.pitgo.core.map.ui.ParticipantMapStyleController
import com.trustarx.pitgo.features.appointmenttracking.model.AppointmentTrackingUiState
import com.trustarx.pitgo.features.appointmenttracking.viewmodel.AppointmentTrackingViewModel
import com.trustarx.pitgo.features.mobiledispatch.model.MobileAppointmentStatus
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuinchoTrackingContent(state: AppointmentTrackingUiState,viewModel: AppointmentTrackingViewModel,
    isClient: Boolean,onBack: () -> Unit) {
    val snapshot=state.dispatch ?: return
    val appointment=snapshot.appointment ?: return
    val trip=snapshot.guinchoTrip ?: return
    var problem by remember(appointment.id) { mutableStateOf(false) }
    var reason by remember(appointment.id) { mutableStateOf("") }
    val money=remember { NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")) }
    val controller=remember { ParticipantMapStyleController() }
    val viewport=remember(appointment.id) { framingViewport(trip.pickup,trip.destination) }
    val markers=buildList {
        add(MapMarkerSpec("pickup",trip.pickup,MapMarkerKind.CLIENT_LOCATION,accessibilityLabel=trip.pickupAddress))
        add(MapMarkerSpec("destination",trip.destination,MapMarkerKind.CLIENT_LOCATION,accessibilityLabel=trip.destinationAddress))
        if(snapshot.trackingAuthorized) state.visibleLocation?.let { add(MapMarkerSpec("tow",it.coordinate,MapMarkerKind.LIVE,
            accessibilityLabel=snapshot.providerName.orEmpty())) }
    }
    Scaffold(topBar={ TopAppBar(title={ Text(stringResource(R.string.guincho_title)) },
        navigationIcon={ TextButton(onClick=onBack) { Text(stringResource(R.string.guincho_back)) } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                MapLibreMapHost(PitGoManagedMapStyle.configuration,MapRenderModel(markers),viewport,state.cameraCommand,
                    state.styleReloadRevision,state.data !is com.trustarx.pitgo.features.appointmenttracking.model.TrackingDataState.Offline,controller,viewModel::onViewportIdle,{},viewModel::updateSurfaceState,Modifier.fillMaxSize())
            }
            Column(Modifier.fillMaxWidth().heightIn(max=330.dp).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(snapshot.providerName.orEmpty(),style=MaterialTheme.typography.titleMedium)
                Text(stringResource(if(isClient) R.string.pittec_customer_total_value else R.string.pittec_provider_total_value,
                    money.format(appointment.quotedPrice)))
                if(!isClient) { Text(trip.clientName); Text(trip.vehicle) }
                Text(stringResource(R.string.guincho_pickup_label,trip.pickupAddress))
                Text(stringResource(R.string.guincho_destination_label,trip.destinationAddress))
                if(state.actionFailed) Text(stringResource(R.string.pittec_action_retry),color=MaterialTheme.colorScheme.error)
                if(state.arriving || state.completing) LinearProgressIndicator(Modifier.fillMaxWidth())
                when(appointment.status) {
                    MobileAppointmentStatus.PENDING -> Text(stringResource(R.string.guincho_waiting_accept))
                    MobileAppointmentStatus.ACCEPTED -> {
                        Text(stringResource(if(appointment.arrivedAt==null) R.string.guincho_on_way else R.string.guincho_arrived))
                        if(!isClient) Button(onClick=viewModel::arrive,enabled=!state.arriving,modifier=Modifier.fillMaxWidth()) {
                            Text(stringResource(if(appointment.arrivedAt==null) R.string.pittec_arrive else R.string.guincho_start_transport))
                        }
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

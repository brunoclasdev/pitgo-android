package com.trustarx.pitgo.features.guincho.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.trustarx.pitgo.R
import com.trustarx.pitgo.features.mobiledispatch.model.*
import java.text.NumberFormat
import java.util.Locale

@Composable
fun GuinchoIncomingRequestCard(state: ProviderIncomingUiState.Request, onAccept: () -> Unit, onDecline: () -> Unit,
    onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val trip = state.snapshot.guinchoTrip
    val enabled = state.pending == null && state.failure == null && (state.remainingSeconds ?: 0) > 0
    Card(modifier.fillMaxWidth(), border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.guincho_incoming_title), style = MaterialTheme.typography.titleLarge)
            if (trip != null) {
                Text(trip.clientName + " • " + trip.vehicle)
                Text(stringResource(R.string.guincho_pickup_label, trip.pickupAddress))
                Text(stringResource(R.string.guincho_destination_label, trip.destinationAddress))
            } else Text(stringResource(R.string.guincho_details_after_payment))
            state.snapshot.appointment?.let { Text(NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"))
                .format(it.quotedPrice), style = MaterialTheme.typography.titleLarge) }
            Text(stringResource(R.string.mobile_dispatch_seconds, state.remainingSeconds ?: 0))
            if (state.pending != null) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.failure != null) {
                Text(stringResource(R.string.guincho_failure), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetry) { Text(stringResource(R.string.guincho_refresh)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAccept, enabled = enabled, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.mobile_dispatch_accept)) }
                OutlinedButton(onClick = onDecline, enabled = enabled, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.mobile_dispatch_decline)) }
            }
        }
    }
}

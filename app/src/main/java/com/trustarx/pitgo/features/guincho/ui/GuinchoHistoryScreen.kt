package com.trustarx.pitgo.features.guincho.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trustarx.pitgo.R
import com.trustarx.pitgo.features.guincho.viewmodel.GuinchoHistoryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuinchoHistoryScreen(viewModel: GuinchoHistoryViewModel,onBack: () -> Unit,onOpen: (String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { viewModel.load() }
    Scaffold(topBar={ TopAppBar(title={ Text(stringResource(R.string.guincho_history)) },
        navigationIcon={ TextButton(onClick=onBack) { Text(stringResource(R.string.guincho_back)) } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if(state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if(state.failed) item {
                Text(stringResource(R.string.guincho_failure))
                TextButton(onClick={ viewModel.load() }) { Text(stringResource(R.string.guincho_refresh)) }
            }
            if(state.rows.isEmpty() && !state.loading && !state.failed) item { Text(stringResource(R.string.guincho_history_empty)) }
            items(state.rows,key={ it.searchId }) { row ->
                Card(onClick={ row.appointment?.id?.let(onOpen) },modifier=Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text(row.providerName.orEmpty(),style=MaterialTheme.typography.titleMedium)
                        row.guinchoTrip?.let { Text(it.pickupAddress+" → "+it.destinationAddress) }
                        Text(stringResource(when {
                            row.guinchoDispute?.status==com.trustarx.pitgo.features.guincho.model.GuinchoDisputeStatus.OPEN -> R.string.guincho_disputed
                            row.appointment?.status==com.trustarx.pitgo.features.mobiledispatch.model.MobileAppointmentStatus.COMPLETED -> R.string.pittec_service_finished
                            row.appointment?.status==com.trustarx.pitgo.features.mobiledispatch.model.MobileAppointmentStatus.CANCELLED -> R.string.guincho_cancelled
                            else -> R.string.guincho_in_progress
                        }))
                    }
                }
            }
            if(state.hasMore) item { Button(onClick={ viewModel.load(true) },enabled=!state.loading) { Text(stringResource(R.string.payment_history_more)) } }
        }
    }
}

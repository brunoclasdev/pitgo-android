package com.trustarx.pitgo.features.pittec.ui

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.trustarx.pitgo.R
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trustarx.pitgo.features.pittec.viewmodel.SimulatedPitTecFlowViewModel
import com.trustarx.pitgo.core.map.model.*
import com.trustarx.pitgo.core.map.ui.ParticipantMapStyleController

@Composable
fun SimulatedPitTecFlowScreen(viewModel: SimulatedPitTecFlowViewModel, onBack: () -> Unit) {
    androidx.activity.compose.BackHandler { viewModel.leave(onBack) }
    val appointment by viewModel.state.collectAsStateWithLifecycle()
    var surface by remember { mutableStateOf(MapSurfaceState()) }
    var viewport by remember { mutableStateOf(MapViewport(appointment.clientLocation.coordinate, 14.0)) }
    var reload by remember { mutableLongStateOf(0) }
    val controller = remember { ParticipantMapStyleController() }
    Column(Modifier.fillMaxSize()) {
        Text(stringResource(R.string.environment_dev_appointment), modifier = Modifier.padding(16.dp))
        Row {
            Button(onClick = viewModel::advance) { Text(stringResource(R.string.environment_simulate_advance)) }
            TextButton(onClick = { viewModel.leave(onBack) }) { Text(stringResource(R.string.environment_simulate_cancel)) }
        }
        PitTecMockTrackingContent(appointment, true, viewport, null, reload, surface,
            { viewport = it }, { surface = it }, { reload++ }, {}, viewModel::advance, viewModel::advance,
            stringResource(R.string.environment_simulate_complete), { viewModel.leave(onBack) }, controller, Modifier.weight(1f))
    }
}

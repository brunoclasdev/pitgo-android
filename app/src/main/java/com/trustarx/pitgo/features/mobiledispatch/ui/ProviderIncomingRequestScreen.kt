package com.trustarx.pitgo.features.mobiledispatch.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trustarx.pitgo.R
import com.trustarx.pitgo.core.design.theme.PitGoSize
import com.trustarx.pitgo.core.design.theme.PitGoSpacing
import com.trustarx.pitgo.features.mobiledispatch.model.MobileAppointmentStatus
import com.trustarx.pitgo.features.mobiledispatch.model.MobileDispatchKind
import com.trustarx.pitgo.features.mobiledispatch.model.PendingDispatchAction
import com.trustarx.pitgo.features.mobiledispatch.model.ProviderIncomingUiState
import com.trustarx.pitgo.features.mobiledispatch.model.ProviderMapState
import com.trustarx.pitgo.features.mobiledispatch.viewmodel.ProviderIncomingRequestViewModel
import java.text.NumberFormat
import java.util.Locale

@Composable
fun ProviderIncomingRequestRoute(
    viewModel: ProviderIncomingRequestViewModel,
    onBack: () -> Unit,
    onSessionExpired: () -> Unit,
    onTrack: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val mapState by viewModel.mapState.collectAsStateWithLifecycle()
    if (state == ProviderIncomingUiState.SessionExpired) onSessionExpired()
    ProviderIncomingRequestScreen(
        state, mapState, onBack, viewModel::accept, viewModel::decline, viewModel::startService,
        viewModel::complete, viewModel::cancel, onTrack, viewModel::setFollowMap,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderIncomingRequestScreen(
    state: ProviderIncomingUiState,
    mapState: ProviderMapState,
    onBack: () -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onStart: () -> Unit,
    onComplete: () -> Unit,
    onCancel: (String) -> Unit,
    onTrack: (String) -> Unit,
    onFollowMapChanged: (Boolean) -> Unit,
) {
    var cancelling by remember { mutableStateOf(false) }
    var reason by remember { mutableStateOf("") }
    Scaffold(topBar = { TopAppBar(
        title = { Text(stringResource(R.string.mobile_dispatch_provider_title)) },
        navigationIcon = { IconButton(onBack) { Icon(painterResource(R.drawable.ic_back), stringResource(R.string.map_back)) } },
    ) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(PitGoSpacing.Large).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(PitGoSpacing.Medium),
        ) {
            when (state) {
                ProviderIncomingUiState.Loading -> Text(stringResource(R.string.mobile_dispatch_restoring))
                ProviderIncomingUiState.None -> Text(stringResource(R.string.mobile_dispatch_no_request))
                ProviderIncomingUiState.SessionExpired -> Text(stringResource(R.string.session_verifying))
                is ProviderIncomingUiState.Failure -> Text(stringResource(R.string.mobile_dispatch_unavailable))
                is ProviderIncomingUiState.Request -> {
                    val isPitTec = state.snapshot.dispatchKind == MobileDispatchKind.PITTEC_AUTO_SEQUENTIAL
                    val candidate = state.snapshot.candidate
                    val offer = state.snapshot.offer
                    val appointment = state.snapshot.appointment
                    Text(state.snapshot.serviceName, style = MaterialTheme.typography.headlineSmall)
                    if (isPitTec) {
                        Text(offer?.region.orEmpty())
                        offer?.let {
                            Text(
                                stringResource(
                                    R.string.mobile_dispatch_distance,
                                    it.approximateDistanceMeters / 1000.0,
                                ),
                            )
                        }
                    } else {
                        Text(candidate?.locality.orEmpty())
                        Text(NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(appointment?.quotedPrice))
                    }
                    state.remainingSeconds?.let { Text(stringResource(R.string.mobile_dispatch_seconds, it)) }
                    Button(onAccept, Modifier.fillMaxWidth().heightIn(min = PitGoSize.PrimaryActionMinHeight), enabled = state.pending == null) {
                        ButtonLabel(
                            stringResource(R.string.mobile_dispatch_accept),
                            state.pending?.kind == PendingDispatchAction.ACCEPT,
                        )
                    }
                    OutlinedButton(onDecline, Modifier.fillMaxWidth(), enabled = state.pending == null) {
                        ButtonLabel(
                            stringResource(R.string.mobile_dispatch_decline),
                            state.pending?.kind == PendingDispatchAction.DECLINE,
                        )
                    }
                }
                is ProviderIncomingUiState.Active -> {
                    val isPitTec = state.snapshot.dispatchKind == MobileDispatchKind.PITTEC_AUTO_SEQUENTIAL
                    val appointment = state.snapshot.appointment
                    Text(state.snapshot.serviceName, style = MaterialTheme.typography.headlineSmall)
                    if (!isPitTec) {
                        Text(stringResource(R.string.mobile_dispatch_price_frozen,
                            NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(appointment?.quotedPrice)))
                    }
                    ProviderAppointmentMap(mapState, onFollowMapChanged)
                    if (appointment?.status == MobileAppointmentStatus.ACCEPTED) {
                        Button(onStart, Modifier.fillMaxWidth(), enabled = state.pending == null) {
                            ButtonLabel(
                                stringResource(
                                    if (isPitTec) R.string.pittec_arrive
                                    else R.string.mobile_dispatch_start_service,
                                ),
                                state.pending?.kind == PendingDispatchAction.START,
                            )
                        }
                    }
                    appointment?.let { Button({ onTrack(it.id) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.mobile_dispatch_open_tracking)) } }
                    val canComplete = state.pending == null &&
                        (!isPitTec || appointment?.status == MobileAppointmentStatus.IN_PROGRESS)
                    Button(onComplete, Modifier.fillMaxWidth(), enabled = canComplete) {
                        ButtonLabel(
                            stringResource(R.string.mobile_dispatch_complete),
                            state.pending?.kind == PendingDispatchAction.COMPLETE,
                        )
                    }
                    OutlinedButton(
                        { cancelling = true },
                        Modifier.fillMaxWidth(),
                        enabled = state.pending == null,
                    ) { Text(stringResource(R.string.mobile_dispatch_cancel)) }
                }
            }
        }
    }
    if (cancelling) ModalBottomSheet(onDismissRequest = { cancelling = false }) {
        Column(
            Modifier.fillMaxWidth().padding(PitGoSpacing.Large).imePadding(),
            verticalArrangement = Arrangement.spacedBy(PitGoSpacing.Medium),
        ) {
            Text(stringResource(R.string.mobile_dispatch_cancel_title), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = reason, onValueChange = { reason = it }, modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.mobile_dispatch_cancel_reason)) }, minLines = 3,
                isError = reason.isBlank(),
            )
            Button(
                onClick = { onCancel(reason); cancelling = false }, enabled = reason.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.mobile_dispatch_cancel_confirm)) }
        }
    }
}

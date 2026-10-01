package com.trustarx.pitgo.features.guincho.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trustarx.pitgo.R
import com.trustarx.pitgo.core.map.config.PitGoManagedMapStyle
import com.trustarx.pitgo.core.map.model.MapRenderModel
import com.trustarx.pitgo.core.map.model.MapStyleState
import com.trustarx.pitgo.core.map.ui.MapLibreMapHost
import com.trustarx.pitgo.features.guincho.model.GuinchoQuote
import com.trustarx.pitgo.features.guincho.viewmodel.*
import com.trustarx.pitgo.features.providermap.ui.ProviderMapStyleController
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuinchoMapRoute(viewModel: GuinchoMapViewModel, onBack: () -> Unit,
    onContract: (com.trustarx.pitgo.features.mobiledispatch.model.MobileDispatchSnapshot) -> Unit,
    onGarage: () -> Unit, contractingEnabled: Boolean, onSessionExpired: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context=LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    androidx.lifecycle.compose.LifecycleStartEffect(viewModel) {
        viewModel.startDiscoveryUpdates()
        onStopOrDispose { viewModel.stopDiscoveryUpdates() }
    }
    LaunchedEffect(state.checkout?.searchId) { state.checkout?.let(onContract) }
    LaunchedEffect(state.failure) { if(state.failure==com.trustarx.pitgo.features.guincho.model.GuinchoFailure.SESSION_EXPIRED) onSessionExpired() }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { viewModel.loadVehicles() }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if(result.values.any { it }) viewModel.locate() else viewModel.locationDenied()
    }
    LaunchedEffect(viewModel) {
        if(state.stage==GuinchoMapStage.PICKUP && state.pickup==null) {
            if(ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED)
                viewModel.locate()
            else permission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION))
        }
    }
    val controller=remember { ProviderMapStyleController() }
    val money=remember { NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")) }
    Scaffold(topBar={ TopAppBar(title={ Text(stringResource(R.string.guincho_title)) },
        navigationIcon={ TextButton(onClick=onBack) { Text(stringResource(R.string.guincho_back)) } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                MapLibreMapHost(PitGoManagedMapStyle.configuration,MapRenderModel(state.markers),state.viewport,
                    state.camera,0,state.online,controller,viewModel::viewport,viewModel::select,viewModel::surface,
                    Modifier.fillMaxSize(),onUserGesture={ keyboard?.hide(); viewModel.mapGesture() })
                if(state.stage==GuinchoMapStage.PICKUP || state.stage==GuinchoMapStage.DESTINATION) {
                    val label=stringResource(R.string.guincho_confirm_point)
                    Surface(Modifier.align(Alignment.Center).semantics { contentDescription=label },shape=MaterialTheme.shapes.small) {
                        Text(stringResource(R.string.guincho_map_pin),Modifier.padding(8.dp))
                    }
                }
                TextButton(onClick={
                    if(ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED)
                        viewModel.locate()
                    else permission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION))
                },modifier=Modifier.align(Alignment.TopEnd)) { Text(stringResource(R.string.guincho_my_location)) }
            }
            // Separate measured region keeps the mini card outside the map, including large fonts.
            Column(Modifier.fillMaxWidth().heightIn(max=280.dp).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement=Arrangement.spacedBy(8.dp)) {
                if(state.operation!=GuinchoMapOperation.IDLE) LinearProgressIndicator(Modifier.fillMaxWidth())
                if(state.failure!=null) Text(stringResource(when(state.failure) {
                    com.trustarx.pitgo.features.guincho.model.GuinchoFailure.ROUTE_UNAVAILABLE -> R.string.guincho_route_failure
                    com.trustarx.pitgo.features.guincho.model.GuinchoFailure.QUOTE_EXPIRED,
                    com.trustarx.pitgo.features.guincho.model.GuinchoFailure.QUOTE_CONFLICT -> R.string.guincho_quote_changed
                    com.trustarx.pitgo.features.guincho.model.GuinchoFailure.PROVIDER_UNAVAILABLE -> R.string.guincho_provider_unavailable
                    com.trustarx.pitgo.features.guincho.model.GuinchoFailure.OFFLINE -> R.string.guincho_offline
                    else -> R.string.guincho_failure
                }),color=MaterialTheme.colorScheme.error)
                if(state.surface.style is MapStyleState.Failed) Text(stringResource(R.string.guincho_map_failure))
                if(state.locationUnavailable) Text(stringResource(R.string.guincho_location_manual))
                when(state.stage) {
                    GuinchoMapStage.PICKUP -> {
                        Text(stringResource(R.string.guincho_pickup_automatic))
                        if(state.locationUnavailable) {
                            Text(stringResource(R.string.guincho_pickup_instruction))
                            Button(onClick=viewModel::confirmPoint,enabled=state.operation==GuinchoMapOperation.IDLE,
                                modifier=Modifier.fillMaxWidth()) { Text(stringResource(R.string.guincho_confirm_point)) }
                        }
                    }
                    GuinchoMapStage.DESTINATION -> {
                        Text(stringResource(R.string.guincho_pickup_label,state.pickupAddress))
                        Text(stringResource(R.string.guincho_destination_instruction))
                        OutlinedTextField(value=state.destinationAddress,onValueChange=viewModel::address,
                            label={ Text(stringResource(R.string.guincho_destination_address)) },modifier=Modifier.fillMaxWidth(),
                            singleLine=true,keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),
                            keyboardActions=KeyboardActions(onSearch={ keyboard?.hide() }))
                        if (state.searchingAddress) {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            Text(stringResource(R.string.guincho_address_searching))
                        }
                        state.addressSuggestions.forEach { suggestion ->
                            TextButton(onClick={ keyboard?.hide(); viewModel.chooseDestination(suggestion) },
                                modifier=Modifier.fillMaxWidth()) {
                                Text(suggestion.label,modifier=Modifier.fillMaxWidth())
                            }
                        }
                        if (state.addressSearchFailure) Text(stringResource(R.string.guincho_address_search_failure))
                        else if (state.addressSearchCompleted && state.addressSuggestions.isEmpty())
                            Text(stringResource(R.string.guincho_address_no_results))
                        OutlinedTextField(value=state.destinationNumber,onValueChange=viewModel::number,
                            label={ Text(stringResource(R.string.guincho_destination_number)) },
                            placeholder={ Text(stringResource(R.string.guincho_destination_number_hint)) },
                            modifier=Modifier.fillMaxWidth(),singleLine=true,
                            keyboardOptions=KeyboardOptions(imeAction=ImeAction.Done),
                            keyboardActions=KeyboardActions(onDone={ keyboard?.hide() }))
                        if (state.destination!=null && state.destinationNumber.isNotBlank())
                            Text(stringResource(R.string.guincho_destination_label,state.fullDestinationAddress))
                        Button(onClick={ keyboard?.hide(); viewModel.confirmPoint() },enabled=state.operation==GuinchoMapOperation.IDLE &&
                            state.canConfirmDestination,
                            modifier=Modifier.fillMaxWidth()) { Text(stringResource(R.string.guincho_confirm_point)) }
                    }
                    GuinchoMapStage.CONFIRM -> {
                        Text(state.pickupAddress+" → "+state.fullDestinationAddress)
                        Text(stringResource(R.string.guincho_vehicle))
                        state.vehicles.forEach { vehicle ->
                            FilterChip(selected=state.vehicleId==vehicle.id,onClick={ viewModel.vehicle(vehicle.id) },
                                label={ Text(vehicle.displayName) })
                        }
                        if(state.vehicles.isEmpty()) TextButton(onClick=onGarage) { Text(stringResource(R.string.guincho_add_vehicle)) }
                        Button(onClick=viewModel::confirmTrip,enabled=state.operation==GuinchoMapOperation.IDLE && state.vehicleId!=null,
                            modifier=Modifier.fillMaxWidth()) { Text(stringResource(R.string.guincho_find)) }
                        TextButton(onClick=viewModel::editTrip) { Text(stringResource(R.string.guincho_edit)) }
                    }
                    GuinchoMapStage.DISCOVERY -> {
                        state.selected?.let { provider ->
                            Text(provider.name,style=MaterialTheme.typography.titleMedium)
                            state.quote?.let { quote -> Text(money.format(quote.total),style=MaterialTheme.typography.titleLarge) }
                            Button(onClick=viewModel::contract,enabled=contractingEnabled && state.quote!=null && state.operation==GuinchoMapOperation.IDLE && state.checkout==null,
                                modifier=Modifier.fillMaxWidth()) { Text(stringResource(R.string.guincho_contract)) }
                        } ?: Text(stringResource(if(state.providers.isEmpty() && state.operation==GuinchoMapOperation.IDLE)
                            R.string.guincho_empty else R.string.guincho_select))
                        Row {
                            TextButton(onClick=viewModel::refresh,enabled=state.operation==GuinchoMapOperation.IDLE) { Text(stringResource(R.string.guincho_refresh)) }
                            TextButton(onClick=viewModel::editTrip) { Text(stringResource(R.string.guincho_edit)) }
                        }
                    }
                }
            }
        }
    }
}

package com.trustarx.pitgo.features.profile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trustarx.pitgo.R
import com.trustarx.pitgo.core.account.ProviderKind
import com.trustarx.pitgo.core.design.theme.PitGoSpacing
import com.trustarx.pitgo.core.vehicle.ui.TransportCatalogPicker
import com.trustarx.pitgo.core.vehicle.ui.VehicleMakePicker
import com.trustarx.pitgo.features.profile.model.*
import com.trustarx.pitgo.features.profile.viewmodel.RegistrationEditViewModel
import com.trustarx.pitgo.features.providerdashboard.model.*
import com.trustarx.pitgo.features.providerdashboard.viewmodel.ProviderDashboardViewModel
import com.trustarx.pitgo.features.providerregistration.model.ProviderVehicle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegistrationEditRoute(viewModel: RegistrationEditViewModel, fleetViewModel: ProviderDashboardViewModel?,
    onBack: () -> Unit, onOpenGarage: () -> Unit, onSessionExpired: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val content=state as? RegistrationEditState.Content
    var dirtyVehicles by remember { mutableStateOf(emptySet<String>()) }
    var confirmAction by remember { mutableStateOf<String?>(null) }
    var formGeneration by remember { mutableIntStateOf(0) }
    val fleetState=fleetViewModel?.fleet?.collectAsStateWithLifecycle()?.value
    val busy=content?.busy==true || fleetState?.busy==true
    fun reloadForm() {
        dirtyVehicles=emptySet(); formGeneration++
        fleetViewModel?.discardNewGuinchoVehicleDraft()
        viewModel.reload(); fleetViewModel?.loadFleet()
    }
    fun leave(action:String) {
        if(busy) return
        if(content?.dirty==true || dirtyVehicles.isNotEmpty()) confirmAction=action
        else if(action=="RELOAD") reloadForm()
        else if(action=="GARAGE") onOpenGarage() else onBack()
    }
    BackHandler { leave("BACK") }
    LaunchedEffect(state) { if(state==RegistrationEditState.SessionExpired) onSessionExpired() }
    Scaffold(topBar={ TopAppBar(title={ Text(stringResource(R.string.registration_edit_title)) },
        navigationIcon={ TextButton(onClick={ leave("BACK") },enabled=!busy) { Text(stringResource(R.string.guincho_back)) } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(PitGoSpacing.Medium),
            verticalArrangement=Arrangement.spacedBy(PitGoSpacing.Medium)) {
            when(val current=state) {
                RegistrationEditState.Loading -> CircularProgressIndicator()
                RegistrationEditState.SessionExpired -> Text(stringResource(R.string.transport_session_expired))
                is RegistrationEditState.Failed -> {
                    Text(registrationFailureText(current.reason),color=MaterialTheme.colorScheme.error)
                    Button(onClick=viewModel::reload) { Text(stringResource(R.string.action_retry)) }
                }
                is RegistrationEditState.Content -> {
                    val d=current.draft
                    RegistrationSection(R.string.registration_contact_section) {
                        RegistrationInput(d.name,RegistrationField.NAME,if(current.snapshot.type==RegistrationAccountType.PROVIDER) R.string.provider_name_label else R.string.registration_name,current,viewModel::update)
                        RegistrationInput(d.phone,RegistrationField.PHONE,R.string.registration_phone,current,viewModel::update,KeyboardType.Phone)
                        OutlinedTextField(current.snapshot.email,{},label={ Text(stringResource(R.string.registration_email)) },readOnly=true,modifier=Modifier.fillMaxWidth())
                        OutlinedTextField(current.snapshot.document,{},label={ Text(stringResource(R.string.registration_document)) },readOnly=true,modifier=Modifier.fillMaxWidth())
                        if(current.snapshot.type==RegistrationAccountType.PROVIDER)
                            RegistrationInput(d.description,RegistrationField.DESCRIPTION,R.string.registration_description,current,viewModel::update)
                    }
                    RegistrationSection(R.string.profile_address_label) {
                        RegistrationInput(d.address.postalCode,RegistrationField.POSTAL_CODE,R.string.registration_postal_code,current,viewModel::update,KeyboardType.Number)
                        OutlinedButton(onClick=viewModel::lookupPostalCode,enabled=!current.busy) { Text(stringResource(R.string.registration_lookup_postal_code)) }
                        RegistrationInput(d.address.street,RegistrationField.STREET,R.string.provider_street_label,current,viewModel::update)
                        RegistrationInput(d.address.number,RegistrationField.NUMBER,R.string.provider_number_label,current,viewModel::update)
                        RegistrationInput(d.address.complement,RegistrationField.COMPLEMENT,R.string.provider_complement_label,current,viewModel::update)
                        RegistrationInput(d.address.neighborhood,RegistrationField.NEIGHBORHOOD,R.string.provider_neighborhood_label,current,viewModel::update)
                        RegistrationInput(d.address.city,RegistrationField.CITY,R.string.provider_city_label,current,viewModel::update)
                        RegistrationInput(d.address.state,RegistrationField.STATE,R.string.registration_state,current,viewModel::update)
                    }
                    current.failure?.let { Text(registrationFailureText(it),color=MaterialTheme.colorScheme.error) }
                    if(current.operation==RegistrationOperation.SAVED) Text(stringResource(R.string.registration_saved),color=MaterialTheme.colorScheme.primary)
                    if(current.busy) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(stringResource(if(current.operation==RegistrationOperation.LOOKING_UP_ADDRESS) R.string.registration_looking_up else R.string.registration_saving))
                    }
                    Button(onClick=viewModel::save,enabled=!busy && current.dirty,modifier=Modifier.fillMaxWidth()) { Text(stringResource(R.string.registration_save)) }
                    TextButton(onClick={ leave("RELOAD") },enabled=!busy) { Text(stringResource(R.string.registration_reload)) }
                    if(current.snapshot.type==RegistrationAccountType.CLIENT) {
                        OutlinedButton(onClick={ leave("GARAGE") },enabled=!busy,modifier=Modifier.fillMaxWidth()) { Text(stringResource(R.string.registration_manage_vehicles)) }
                    }
                    if(current.snapshot.providerKind==ProviderKind.GUINCHO && fleetViewModel!=null) {
                        key(formGeneration) {
                            GuinchoRegistrationVehicles(fleetViewModel,current.busy,{ id,dirty -> dirtyVehicles=if(dirty) dirtyVehicles+id else dirtyVehicles-id },fleetViewModel::loadFleet)
                        }
                    }
                }
            }
        }
    }
    if(confirmAction!=null) AlertDialog(onDismissRequest={ confirmAction=null },
        title={ Text(stringResource(R.string.registration_discard_title)) },text={ Text(stringResource(R.string.registration_discard_message)) },
        confirmButton={ TextButton(onClick={
            val action=confirmAction;confirmAction=null
            if(action=="RELOAD") reloadForm()
            else if(action=="GARAGE") onOpenGarage() else onBack()
        }) { Text(stringResource(R.string.registration_discard)) } },
        dismissButton={ TextButton(onClick={ confirmAction=null }) { Text(stringResource(R.string.registration_keep_editing)) } })
}

@Composable
private fun RegistrationSection(title:Int,content:@Composable ColumnScope.()->Unit) {
    Card(Modifier.fillMaxWidth(),border=BorderStroke(1.5.dp,MaterialTheme.colorScheme.primary)) {
        Column(Modifier.fillMaxWidth().padding(PitGoSpacing.Medium),verticalArrangement=Arrangement.spacedBy(PitGoSpacing.SmallMedium)) {
            Text(stringResource(title),style=MaterialTheme.typography.titleMedium)
            content()
        }
    }
}
@Composable
private fun RegistrationInput(value:String,field:RegistrationField,label:Int,state:RegistrationEditState.Content,
    onChange:(RegistrationField,String)->Unit,type:KeyboardType=KeyboardType.Text) {
    OutlinedTextField(value,{ onChange(field,it) },label={ Text(stringResource(label)) },enabled=!state.busy,
        isError=field in state.invalidFields,supportingText=if(field in state.invalidFields) ({ Text(stringResource(R.string.registration_invalid_field)) }) else null,
        singleLine=field!=RegistrationField.DESCRIPTION,keyboardOptions=KeyboardOptions(keyboardType=type,
            capitalization=if(type==KeyboardType.Text) KeyboardCapitalization.Sentences else KeyboardCapitalization.None),modifier=Modifier.fillMaxWidth())
}
@Composable
private fun registrationFailureText(reason:RegistrationFailure)=stringResource(when(reason) {
    RegistrationFailure.OFFLINE -> R.string.guincho_offline
    RegistrationFailure.TIMEOUT -> R.string.transport_catalog_timeout
    RegistrationFailure.SESSION_EXPIRED -> R.string.transport_session_expired
    RegistrationFailure.CONFLICT -> R.string.registration_conflict
    RegistrationFailure.INVALID_INPUT -> R.string.registration_invalid_input
    RegistrationFailure.ADDRESS_UNAVAILABLE -> R.string.registration_address_failure
    else -> R.string.registration_unavailable
})

@Composable
private fun GuinchoRegistrationVehicles(viewModel:ProviderDashboardViewModel,externalBusy:Boolean,onDirty:(String,Boolean)->Unit,onReload:()->Unit) {
    val state by viewModel.fleet.collectAsStateWithLifecycle()
    val busy=state.busy || externalBusy
    LaunchedEffect(state.newVehicle) { onDirty("new",state.newVehicle!=ProviderVehicle()) }
    RegistrationSection(R.string.registration_guincho_vehicles) {
        Text(stringResource(R.string.registration_guincho_hint))
        if(state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if(state.failed) Text(stringResource(if(state.operation==GuinchoFleetOperation.INVALID_INPUT) R.string.guincho_vehicle_validation else R.string.guincho_vehicle_update_failed),color=MaterialTheme.colorScheme.error)
        OutlinedButton(onClick=onReload,enabled=!busy) { Text(stringResource(R.string.registration_reload_vehicles)) }
        state.vehicles.forEach { vehicle ->
            key(vehicle.id) {
                val initial=ProviderVehicle(vehicle.plate,vehicle.make,vehicle.model,vehicle.color,vehicle.sizeId)
                var draft by remember(vehicle.id,vehicle.revision) { mutableStateOf(initial) }
                val dirty=draft!=initial
                LaunchedEffect(draft,initial) { onDirty(vehicle.id,dirty) }
                Card(Modifier.fillMaxWidth(),border=BorderStroke(1.dp,MaterialTheme.colorScheme.primary)) {
                    Column(Modifier.padding(PitGoSpacing.Medium),verticalArrangement=Arrangement.spacedBy(PitGoSpacing.Small)) {
                        if(vehicle.selected) Text(stringResource(R.string.guincho_vehicle_selected),style=MaterialTheme.typography.titleSmall)
                        GuinchoVehicleFields(draft,busy,{ field,value -> draft=updateVehicleInput(draft,field,value) })
                        TransportCatalogPicker(state.catalog,draft.guinchoSizeId,true,!busy,{ draft=draft.copy(guinchoSizeId=it) },onReload)
                        Button(onClick={ viewModel.saveGuinchoVehicle(vehicle,draft) },enabled=!busy && dirty,modifier=Modifier.fillMaxWidth()) { Text(stringResource(R.string.registration_save_vehicle)) }
                        if(!vehicle.selected) OutlinedButton(onClick={ vehicle.sizeId?.let { viewModel.selectGuinchoVehicle(vehicle,it) } },enabled=!busy && !dirty && vehicle.sizeId!=null,modifier=Modifier.fillMaxWidth()) { Text(stringResource(R.string.registration_use_vehicle)) }
                    }
                }
            }
        }
        if(state.vehicles.isEmpty()) {
            GuinchoVehicleFields(state.newVehicle,busy,viewModel::editNewGuinchoVehicle)
            TransportCatalogPicker(state.catalog,state.newVehicle.guinchoSizeId,true,!busy,
                { viewModel.editNewGuinchoVehicle(GuinchoFleetField.SIZE,it) },onReload)
            val hasSizes = (state.catalog as? com.trustarx.pitgo.core.vehicle.model.TransportCatalogState.Ready)
                ?.catalog?.sizes?.isNotEmpty() == true
            Button(onClick=viewModel::registerGuinchoVehicle,enabled=!busy && hasSizes,modifier=Modifier.fillMaxWidth()) { Text(stringResource(R.string.guincho_register_vehicle)) }
        }
    }
}
@Composable
private fun GuinchoVehicleFields(draft:ProviderVehicle,busy:Boolean,onChange:(GuinchoFleetField,String)->Unit) {
    val fields=listOf(Triple(GuinchoFleetField.PLATE,draft.plate,R.string.provider_vehicle_plate),Triple(GuinchoFleetField.MAKE,draft.make,R.string.provider_vehicle_make),
        Triple(GuinchoFleetField.MODEL,draft.model,R.string.provider_vehicle_model),Triple(GuinchoFleetField.COLOR,draft.color,R.string.provider_vehicle_color))
    fields.forEach { (field,value,label) ->
        if(field==GuinchoFleetField.MAKE) {
            VehicleMakePicker(value,busy.not()) { onChange(field,it) }
        } else {
            OutlinedTextField(value,{ onChange(field,it) },label={ Text(stringResource(label)) },enabled=!busy,singleLine=true,
                keyboardOptions=KeyboardOptions(capitalization=if(field==GuinchoFleetField.PLATE) KeyboardCapitalization.Characters else KeyboardCapitalization.Words),modifier=Modifier.fillMaxWidth())
        }
    }
}
private fun updateVehicleInput(draft:ProviderVehicle,field:GuinchoFleetField,value:String)=when(field) {
    GuinchoFleetField.PLATE -> draft.copy(plate=value.uppercase().filter(Char::isLetterOrDigit).take(7))
    GuinchoFleetField.MAKE -> draft.copy(make=value.take(100))
    GuinchoFleetField.MODEL -> draft.copy(model=value.take(100))
    GuinchoFleetField.COLOR -> draft.copy(color=value.take(60))
    GuinchoFleetField.SIZE -> draft.copy(guinchoSizeId=value)
}

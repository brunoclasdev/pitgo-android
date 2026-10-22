package com.trustarx.pitgo.features.guincho.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import com.trustarx.pitgo.core.location.model.DeviceLocationResult
import com.trustarx.pitgo.core.location.repository.DeviceLocationRepository
import com.trustarx.pitgo.core.map.model.*
import com.trustarx.pitgo.core.vehicle.model.Vehicle
import com.trustarx.pitgo.core.vehicle.model.VehicleListResult
import com.trustarx.pitgo.core.vehicle.repository.VehicleRepository
import com.trustarx.pitgo.features.guincho.model.*
import com.trustarx.pitgo.features.guincho.repository.GuinchoRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class GuinchoMapStage { PICKUP, DESTINATION, CONFIRM, DISCOVERY }
enum class GuinchoMapOperation { IDLE, LOCATING, RESOLVING_ADDRESS, SAVING, DISCOVERING, QUOTING, RESERVING }
data class GuinchoMapState(
    val stage: GuinchoMapStage = GuinchoMapStage.PICKUP,
    val operation: GuinchoMapOperation = GuinchoMapOperation.IDLE,
    val vehicles: List<Vehicle> = emptyList(),
    val vehicleId: String? = null,
    val pickupAddress: String = "",
    val destinationAddress: String = "",
    val destinationNumber: String = "",
    val pickup: MapCoordinate? = null,
    val destination: MapCoordinate? = null,
    val addressSuggestions: List<GuinchoAddressSuggestion> = emptyList(),
    val searchingAddress: Boolean = false,
    val addressSearchFailure: Boolean = false,
    val addressSearchCompleted: Boolean = false,
    val destinationNumberNotFound: Boolean = false,
    val viewport: MapViewport = MapViewport(MapCoordinate(-14.235, -51.9253), 4.0),
    val camera: MapViewportCommand? = null,
    val providers: List<GuinchoProvider> = emptyList(),
    val selected: GuinchoProvider? = null,
    val quote: GuinchoQuote? = null,
    val draft: GuinchoDraft? = null,
    val failure: GuinchoFailure? = null,
    val locationUnavailable: Boolean = false,
    val surface: MapSurfaceState = MapSurfaceState(),
    val online: Boolean = true,
    val checkout: com.trustarx.pitgo.features.mobiledispatch.model.MobileDispatchSnapshot? = null,
) {
    val fullDestinationAddress: String get() {
        val address = destinationAddress.split(',', limit = 2)
        return listOf(address.first().trim(), destinationNumber.trim(), address.getOrNull(1).orEmpty().trim())
            .filter { it.isNotEmpty() }.joinToString(", ")
    }
    val canConfirmDestination: Boolean get() = destination != null && !searchingAddress &&
        destinationAddress.isNotBlank() && destinationNumber.isNotBlank() && fullDestinationAddress.length <= 500
    val markers get() = providers.map { MapMarkerSpec(it.id, it.coordinate, MapMarkerKind.MOBILE_PROVIDER,
        selected = selected?.id == it.id, accessibilityLabel = it.name) }
}

class GuinchoMapViewModel(
    private val userId: String,
    private val repository: GuinchoRepository,
    private val vehicles: VehicleRepository,
    private val location: DeviceLocationRepository,
    private val savedState: SavedStateHandle,
    private val addresses: com.trustarx.pitgo.features.guincho.repository.GuinchoAddressRepository,
    connectivity: com.trustarx.pitgo.core.network.ConnectivityObserver,
) : ViewModel() {
    private val mutable = MutableStateFlow(GuinchoMapState())
    val state = mutable.asStateFlow()
    private var work: Job? = null
    private var locationJob: Job? = null
    private var discoveryJob: Job? = null
    private var addressJob: Job? = null
    private var addressRevision = 0L
    private var destinationMapGesture = false
    private var selectedDestinationSuggestion: GuinchoAddressSuggestion? = null
    fun startDiscoveryUpdates() {
        if (discoveryJob?.isActive == true) return
        discoveryJob = viewModelScope.launch {
            while (isActive) {
                delay(5_000L)
                val current = mutable.value
                val draft = current.draft
                if (current.stage != GuinchoMapStage.DISCOVERY || draft == null ||
                    current.operation != GuinchoMapOperation.IDLE || !current.online || current.checkout != null) continue
                when (val result = repository.discover(draft)) {
                    is GuinchoResult.Success -> mutable.update { latest ->
                        if (latest.draft != draft || latest.stage != GuinchoMapStage.DISCOVERY ||
                            latest.operation != GuinchoMapOperation.IDLE || latest.checkout != null) latest
                        else {
                            val selected = result.value.firstOrNull { it.id == latest.selected?.id }
                            latest.copy(providers = result.value, selected = selected,
                                quote = if (selected == null) null else latest.quote)
                        }
                    }
                    is GuinchoResult.Failure -> if (result.reason == GuinchoFailure.SESSION_EXPIRED)
                        mutable.update { it.copy(failure = result.reason) }
                }
            }
        }
    }
    fun stopDiscoveryUpdates() { discoveryJob?.cancel(); discoveryJob = null }
    init {
        loadVehicles()
        viewModelScope.launch { connectivity.state.collect { value -> mutable.update {
            it.copy(online=value==com.trustarx.pitgo.core.network.ConnectivityState.ONLINE)
        } } }
    }
    fun contract() {
        val current=mutable.value
        if(current.operation!=GuinchoMapOperation.IDLE || current.checkout!=null) return
        val quote=current.quote ?: return
        if(quote.expiresAtEpochMillis<=System.currentTimeMillis()) { failed(GuinchoFailure.QUOTE_EXPIRED); return }
        if(savedState.get<String>("contract_quote_id")!=quote.id) {
            savedState["contract_quote_id"]=quote.id; savedState["contract_operation_id"]=java.util.UUID.randomUUID().toString()
        }
        val operationId=requireNotNull(savedState.get<String>("contract_operation_id"))
        mutable.update { it.copy(operation=GuinchoMapOperation.RESERVING,failure=null) }
        work=viewModelScope.launch {
            when(val result=repository.reserve(quote,operationId)) {
                is GuinchoResult.Success -> mutable.update { it.copy(checkout=result.value,operation=GuinchoMapOperation.IDLE) }
                is GuinchoResult.Failure -> failed(result.reason)
            }
        }
    }
    fun loadVehicles() { viewModelScope.launch {
        when (val result = vehicles.listActiveVehicles(userId)) {
            is VehicleListResult.Success -> mutable.update { it.copy(vehicles=result.vehicles,
                vehicleId=it.vehicleId ?: result.vehicles.firstOrNull()?.id, failure=null) }
            is VehicleListResult.Failure -> mutable.update { it.copy(failure=GuinchoFailure.SERVICE_UNAVAILABLE) }
        }
    } }
    fun locationDenied() { mutable.update { it.copy(locationUnavailable=true) } }
    fun locate() {
        if (locationJob?.isActive == true || mutable.value.operation != GuinchoMapOperation.IDLE) return
        val selectingPickup = mutable.value.stage == GuinchoMapStage.PICKUP
        val selectingDestination = mutable.value.stage == GuinchoMapStage.DESTINATION
        if (selectingPickup || selectingDestination) mutable.update { it.copy(operation=GuinchoMapOperation.LOCATING) }
        locationJob = viewModelScope.launch {
            when (val result=location.currentPreciseFix(timeoutMillis=10_000L)) {
                is DeviceLocationResult.Success -> {
                    val coordinate=MapCoordinate(result.fix.latitude,result.fix.longitude)
                    val viewport=MapViewport(coordinate,15.0)
                    mutable.update { it.copy(viewport=viewport,camera=MapViewportCommand(viewport,System.nanoTime()),
                        locationUnavailable=false,operation=GuinchoMapOperation.IDLE) }
                    if (selectingPickup) applyPickup(coordinate)
                    else if (selectingDestination && mutable.value.stage == GuinchoMapStage.DESTINATION) {
                        destinationMapGesture = false
                        resolveDestinationPoint(coordinate)
                    }
                }
                else -> mutable.update { it.copy(locationUnavailable=true,operation=GuinchoMapOperation.IDLE) }
            }
        }
    }
    private suspend fun applyPickup(coordinate: MapCoordinate) {
        val address=addresses.pickupAddress(coordinate)
        mutable.update { it.copy(pickup=coordinate,pickupAddress=address,
            stage=GuinchoMapStage.DESTINATION,operation=GuinchoMapOperation.IDLE,failure=null,locationUnavailable=false) }
    }
    fun viewport(value: MapViewport) {
        mutable.update { it.copy(viewport=value) }
        if (destinationMapGesture && mutable.value.stage == GuinchoMapStage.DESTINATION) {
            destinationMapGesture = false
            resolveDestinationPoint(value.center)
        }
    }
    fun mapGesture() {
        if (mutable.value.stage != GuinchoMapStage.DESTINATION) return
        destinationMapGesture = true
        selectedDestinationSuggestion = null
        cancelAddressSearch()
        mutable.update { it.copy(destination=null,addressSuggestions=emptyList(),searchingAddress=false,
            addressSearchFailure=false,addressSearchCompleted=false,destinationNumberNotFound=false) }
    }
    private fun cancelAddressSearch() {
        addressRevision++; addressJob?.cancel(); addressJob=null
        if (mutable.value.operation == GuinchoMapOperation.RESOLVING_ADDRESS)
            mutable.update { it.copy(operation=GuinchoMapOperation.IDLE) }
    }
    private fun resolveDestinationPoint(coordinate: MapCoordinate) {
        if (!coordinate.isValid) return
        cancelAddressSearch()
        selectedDestinationSuggestion = null
        val revision = addressRevision
        mutable.update { it.copy(destination=null,destinationAddress="",searchingAddress=true,
            addressSuggestions=emptyList(),addressSearchFailure=false,addressSearchCompleted=false,destinationNumberNotFound=false) }
        addressJob = viewModelScope.launch {
            val label = addresses.destinationAddress(coordinate)
            if (revision == addressRevision && mutable.value.stage == GuinchoMapStage.DESTINATION)
                mutable.update { it.copy(destination=coordinate,destinationAddress=label,searchingAddress=false) }
        }
    }
    fun surface(value: MapSurfaceState) { mutable.update { it.copy(surface=value) } }
    fun address(value: String) {
        if (mutable.value.stage != GuinchoMapStage.DESTINATION) return
        cancelAddressSearch()
        destinationMapGesture = false
        selectedDestinationSuggestion = null
        val query = value.take(200)
        val revision = addressRevision
        mutable.update { it.copy(destinationAddress=query,destination=null,addressSuggestions=emptyList(),
            searchingAddress=query.trim().length>=3,addressSearchCompleted=false,addressSearchFailure=false,
            destinationNumberNotFound=false,failure=null) }
        if (query.trim().length < 3) return
        addressJob = viewModelScope.launch {
            delay(350L)
            val proximity = mutable.value.pickup ?: mutable.value.viewport.center
            val result = addresses.suggestions(query.trim(), proximity)
            if (revision != addressRevision || mutable.value.stage != GuinchoMapStage.DESTINATION) return@launch
            when (result) {
                is GuinchoResult.Success -> mutable.update { it.copy(addressSuggestions=result.value,
                    searchingAddress=false,addressSearchCompleted=true) }
                is GuinchoResult.Failure -> mutable.update { it.copy(searchingAddress=false,addressSearchFailure=true,
                    failure=if(result.reason==GuinchoFailure.SESSION_EXPIRED) result.reason else it.failure) }
            }
        }
    }
    fun chooseDestination(suggestion: GuinchoAddressSuggestion) {
        if (mutable.value.stage != GuinchoMapStage.DESTINATION ||
            suggestion !in mutable.value.addressSuggestions) return
        cancelAddressSearch()
        destinationMapGesture = false
        selectedDestinationSuggestion = suggestion
        mutable.update { it.copy(destination=suggestion.coordinate,destinationAddress=suggestion.label,
            addressSuggestions=emptyList(),
            searchingAddress=false,addressSearchCompleted=false,addressSearchFailure=false,destinationNumberNotFound=false,failure=null) }
    }
    fun number(value: String) {
        if (mutable.value.stage != GuinchoMapStage.DESTINATION) return
        if (mutable.value.operation == GuinchoMapOperation.RESOLVING_ADDRESS) cancelAddressSearch()
        mutable.update { it.copy(destinationNumber=value.filterNot(Char::isISOControl).take(20),
            destinationNumberNotFound=false,addressSearchFailure=false,failure=null) }
    }
    fun vehicle(id: String) { if(mutable.value.stage!=GuinchoMapStage.DISCOVERY) mutable.update { it.copy(vehicleId=id) } }
    fun confirmPoint() {
        val current=mutable.value
        if(current.operation!=GuinchoMapOperation.IDLE || current.stage !in setOf(GuinchoMapStage.PICKUP,GuinchoMapStage.DESTINATION)) return
        val invalidPickup = current.stage==GuinchoMapStage.PICKUP &&
            (!current.viewport.center.isValid || current.viewport.zoom<12.0)
        val invalidDestination = current.stage==GuinchoMapStage.DESTINATION &&
            (!current.canConfirmDestination || current.destination?.isValid != true || destinationMapGesture)
        if(invalidPickup || invalidDestination) {
            mutable.update { it.copy(failure=GuinchoFailure.INVALID_INPUT) }; return
        }
        if(current.stage==GuinchoMapStage.PICKUP) {
            mutable.update { it.copy(operation=GuinchoMapOperation.LOCATING) }
            locationJob=viewModelScope.launch { applyPickup(current.viewport.center) }
        } else {
            cancelAddressSearch()
            val suggestion = selectedDestinationSuggestion
            // A manually chosen point (or an address explicitly without a number) is
            // already positioned by the customer. Street suggestions need a full lookup.
            if (suggestion == null || current.destinationNumber.trim().equals("s/n",ignoreCase=true)) {
                confirmDestination(requireNotNull(current.destination))
                return
            }
            val revision = addressRevision
            mutable.update { it.copy(operation=GuinchoMapOperation.RESOLVING_ADDRESS,
                destinationNumberNotFound=false,addressSearchFailure=false,failure=null) }
            addressJob = viewModelScope.launch {
                val result = addresses.resolveDestination(current.fullDestinationAddress,
                    current.destinationNumber.trim(),suggestion.coordinate)
                if (revision != addressRevision || mutable.value.stage != GuinchoMapStage.DESTINATION) return@launch
                when (result) {
                    is GuinchoResult.Success -> {
                        val resolved = result.value.firstOrNull()
                        if (resolved != null) confirmDestination(resolved.coordinate)
                        else mutable.update { it.copy(operation=GuinchoMapOperation.IDLE,destinationNumberNotFound=true) }
                    }
                    is GuinchoResult.Failure -> mutable.update { it.copy(operation=GuinchoMapOperation.IDLE,
                        addressSearchFailure=true,
                        failure=if(result.reason==GuinchoFailure.SESSION_EXPIRED) result.reason else null) }
                }
            }
        }
    }
    private fun confirmDestination(coordinate: MapCoordinate) {
        val viewport = MapViewport(coordinate,16.0)
        mutable.update { it.copy(stage=GuinchoMapStage.CONFIRM,operation=GuinchoMapOperation.IDLE,
            destination=coordinate,addressSuggestions=emptyList(),destinationNumberNotFound=false,
            viewport=viewport,camera=MapViewportCommand(viewport,System.nanoTime())) }
    }
    fun editTrip() {
        if(mutable.value.operation==GuinchoMapOperation.RESERVING) return
        work?.cancel()
        locationJob?.cancel()
        cancelAddressSearch()
        destinationMapGesture = false
        mutable.update {
            it.copy(stage=GuinchoMapStage.DESTINATION,operation=GuinchoMapOperation.IDLE,
                providers=emptyList(),selected=null,quote=null,failure=null,
                searchingAddress=false,addressSuggestions=emptyList(),addressSearchCompleted=false,addressSearchFailure=false,
                destinationNumberNotFound=false)
        }
    }
    fun confirmTrip() {
        val current=mutable.value
        val input=GuinchoTripInput(current.vehicleId.orEmpty(),current.pickupAddress,
            current.pickup ?: current.viewport.center,current.fullDestinationAddress,
            current.destination ?: current.viewport.center)
        if(!input.isValid() || current.pickup==null || current.destination==null || current.destinationNumber.isBlank()) {
            mutable.update { it.copy(failure=GuinchoFailure.INVALID_INPUT) }; return
        }
        work?.cancel()
        work=viewModelScope.launch {
            mutable.update { it.copy(operation=GuinchoMapOperation.SAVING,failure=null) }
            when(val saved=repository.saveTrip(input,current.draft)) {
                is GuinchoResult.Failure -> failed(saved.reason)
                is GuinchoResult.Success -> {
                    mutable.update { it.copy(draft=saved.value,stage=GuinchoMapStage.DISCOVERY,
                        viewport=MapViewport(input.pickup,12.0),camera=MapViewportCommand(MapViewport(input.pickup,12.0),System.nanoTime())) }
                    discover(saved.value)
                }
            }
        }
    }
    fun refresh() {
        if(mutable.value.operation==GuinchoMapOperation.RESERVING) return
        val draft=mutable.value.draft ?: return
        work?.cancel(); work=viewModelScope.launch { discover(draft) }
    }
    private suspend fun discover(draft: GuinchoDraft) {
        mutable.update { it.copy(operation=GuinchoMapOperation.DISCOVERING,selected=null,quote=null,failure=null) }
        when(val result=repository.discover(draft)) {
            is GuinchoResult.Success -> mutable.update { it.copy(providers=result.value,operation=GuinchoMapOperation.IDLE) }
            is GuinchoResult.Failure -> failed(result.reason)
        }
    }
    fun select(selection: MapFeatureSelection) {
        if(mutable.value.operation==GuinchoMapOperation.RESERVING) return
        if(selection is MapFeatureSelection.NearbyCluster) {
            mutable.update { val viewport=MapViewport(selection.coordinate,(it.viewport.zoom+2).coerceAtMost(18.0))
                it.copy(viewport=viewport,camera=MapViewportCommand(viewport,System.nanoTime())) }; return
        }
        val id=(selection as? MapFeatureSelection.Single)?.markerId ?: return
        val current=mutable.value
        if(current.stage!=GuinchoMapStage.DISCOVERY) return
        val provider=current.providers.find { it.id==id } ?: return
        val draft=current.draft ?: return
        work?.cancel()
        mutable.update { it.copy(selected=provider,quote=null,operation=GuinchoMapOperation.QUOTING,failure=null) }
        work=viewModelScope.launch {
            when(val result=repository.quote(draft,provider.id)) {
                is GuinchoResult.Success -> mutable.update {
                    if(it.selected?.id==provider.id && it.draft==draft) it.copy(quote=result.value,operation=GuinchoMapOperation.IDLE) else it
                }
                is GuinchoResult.Failure -> failed(result.reason)
            }
        }
    }
    private fun failed(reason: GuinchoFailure) { mutable.update { it.copy(failure=reason,operation=GuinchoMapOperation.IDLE,quote=if(reason in setOf(GuinchoFailure.QUOTE_EXPIRED,GuinchoFailure.QUOTE_CONFLICT,GuinchoFailure.PROVIDER_UNAVAILABLE)) null else it.quote) } }
    companion object {
        fun factory(userId: String, repository: GuinchoRepository, vehicles: VehicleRepository,
            location: DeviceLocationRepository, addresses: com.trustarx.pitgo.features.guincho.repository.GuinchoAddressRepository, connectivity: com.trustarx.pitgo.core.network.ConnectivityObserver): ViewModelProvider.Factory = object: ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T: ViewModel> create(modelClass: Class<T>,extras: CreationExtras): T =
                GuinchoMapViewModel(userId,repository,vehicles,location,extras.createSavedStateHandle(),addresses,connectivity) as T
        }
    }
}

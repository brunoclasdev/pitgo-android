package com.trustarx.pitgo.features.providermap.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.trustarx.pitgo.core.location.model.ClientLocationPurpose
import com.trustarx.pitgo.core.location.model.DeviceLocationResult
import com.trustarx.pitgo.core.location.repository.DeviceLocationRepository
import com.trustarx.pitgo.core.map.model.MapCoordinate
import com.trustarx.pitgo.core.map.model.MapFeatureSelection
import com.trustarx.pitgo.core.map.model.MapSurfaceState
import com.trustarx.pitgo.core.map.model.MapViewport
import com.trustarx.pitgo.core.map.model.MapViewportCommand
import com.trustarx.pitgo.core.network.ConnectivityObserver
import com.trustarx.pitgo.core.network.ConnectivityState
import com.trustarx.pitgo.features.providermap.model.ClientLocationState
import com.trustarx.pitgo.features.providermap.model.DiscoveryDataState
import com.trustarx.pitgo.features.providermap.model.ProviderDiscoveryResult
import com.trustarx.pitgo.features.providermap.model.PublicProvider
import com.trustarx.pitgo.features.providermap.model.ServiceMapMode
import com.trustarx.pitgo.features.providermap.model.ServiceMapUiState
import com.trustarx.pitgo.features.providermap.repository.ServiceMapRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ServiceMapViewModel(
    private val serviceId: String,
    private val repository: ServiceMapRepository,
    private val locationRepository: DeviceLocationRepository,
    private val connectivityObserver: ConnectivityObserver,
) : ViewModel() {
    private val initialViewport = MapViewport(MapCoordinate(-14.235, -51.9253), 4.0)
    private val _state = MutableStateFlow<ServiceMapUiState>(
        if (!isUuid(serviceId)) ServiceMapUiState.InvalidService
        else ServiceMapUiState.Content(serviceId = serviceId, viewport = initialViewport),
    )
    val state: StateFlow<ServiceMapUiState> = _state.asStateFlow()

    private var searchJob: Job? = null
    private var generation = 0L
    private var cameraRevision = 0L

    init {
        if (_state.value is ServiceMapUiState.Content) search(initialViewport.center)
        viewModelScope.launch {
            connectivityObserver.state.collect { connectivity ->
                update { current ->
                    if (connectivity == ConnectivityState.OFFLINE) {
                        current.copy(dataState = DiscoveryDataState.Offline)
                    } else current
                }
            }
        }
    }

    fun search(center: MapCoordinate) {
        if (!center.isValid) return
        val current = _state.value as? ServiceMapUiState.Content ?: return
        generation += 1
        val expected = generation
        searchJob?.cancel()
        _state.value = current.copy(
            viewport = current.viewport.copy(center = center),
            dataState = DiscoveryDataState.Loading,
            searchGeneration = expected,
        )
        searchJob = viewModelScope.launch {
            val result = repository.discover(serviceId, center)
            if (expected != generation) return@launch
            when (result) {
                is ProviderDiscoveryResult.Success -> applyProviders(result.providers)
                ProviderDiscoveryResult.Offline -> update { it.copy(dataState = DiscoveryDataState.Offline) }
                ProviderDiscoveryResult.SessionExpired -> update {
                    it.copy(dataState = DiscoveryDataState.SessionExpired)
                }
                ProviderDiscoveryResult.InvalidInput -> _state.value = ServiceMapUiState.InvalidService
                ProviderDiscoveryResult.Timeout,
                ProviderDiscoveryResult.ServiceUnavailable,
                ProviderDiscoveryResult.Failure,
                -> update { it.copy(dataState = DiscoveryDataState.Failure) }
            }
        }
    }

    fun onLocationPermissionResult(granted: Boolean) {
        if (!granted) {
            update { it.copy(clientLocationState = ClientLocationState.Denied) }
            return
        }
        useDeviceLocation()
    }

    private fun useDeviceLocation() = viewModelScope.launch {
        update { it.copy(clientLocationState = ClientLocationState.Resolving) }
        locationRepository.beginVisibleUse(ClientLocationPurpose.PROVIDER_DISCOVERY)
        when (val result = locationRepository.currentClientFix()) {
            is DeviceLocationResult.Success -> {
                val center = MapCoordinate(result.fix.latitude, result.fix.longitude)
                if (!center.isValid) {
                    update { it.copy(clientLocationState = ClientLocationState.Unavailable) }
                    return@launch
                }
                cameraRevision += 1
                update {
                    val target = it.viewport.copy(center = center, zoom = maxOf(it.viewport.zoom, 12.0))
                    it.copy(
                        viewport = target,
                        cameraCommand = MapViewportCommand(target, cameraRevision),
                        clientLocationState = ClientLocationState.InUse,
                    )
                }
                search(center)
            }
            else -> update { it.copy(clientLocationState = ClientLocationState.Unavailable) }
        }
    }

    fun onViewportIdle(viewport: MapViewport) = update { it.copy(viewport = viewport) }

    fun onFeatureSelected(selection: MapFeatureSelection) {
        when (selection) {
            is MapFeatureSelection.Single -> selectProvider(selection.markerId)
            is MapFeatureSelection.CoincidentGroup -> update {
                it.copy(selectedGroupKey = selection.groupKey, selectedProviderId = null)
            }
            is MapFeatureSelection.NearbyCluster -> update { current ->
                if (current.viewport.zoom >= CLUSTER_MAX_ZOOM) {
                    current.copy(mode = ServiceMapMode.LIST)
                } else {
                    cameraRevision += 1
                    val target = MapViewport(
                        selection.coordinate,
                        minOf(current.viewport.zoom + 2.0, CLUSTER_MAX_ZOOM),
                    )
                    current.copy(
                        viewport = target,
                        cameraCommand = MapViewportCommand(target, cameraRevision),
                    )
                }
            }
            MapFeatureSelection.None -> Unit
        }
    }

    fun setMode(mode: ServiceMapMode) = update { it.copy(mode = mode) }

    fun zoomBy(delta: Double) = update { current ->
        cameraRevision += 1
        val target = current.viewport.copy(zoom = (current.viewport.zoom + delta).coerceIn(2.0, 18.0))
        current.copy(
            viewport = target,
            cameraCommand = MapViewportCommand(target, cameraRevision),
        )
    }

    fun selectProvider(id: String?) = update { current ->
        current.copy(
            selectedProviderId = id?.takeIf { candidate -> current.providers.any { it.id == candidate } },
            selectedGroupKey = null,
        )
    }

    fun dismissSelection() = update { it.copy(selectedProviderId = null, selectedGroupKey = null) }

    fun retryStyle() = update {
        it.copy(styleReloadRevision = it.styleReloadRevision + 1)
    }

    fun updateSurfaceState(surfaceState: MapSurfaceState) = update {
        it.copy(surfaceState = surfaceState)
    }

    fun clearTransientLocation() {
        locationRepository.endVisibleUse(ClientLocationPurpose.PROVIDER_DISCOVERY)
        update {
            it.copy(
                clientLocationState = if (it.clientLocationState == ClientLocationState.InUse) {
                    ClientLocationState.NotRequested
                } else it.clientLocationState,
            )
        }
    }

    private fun applyProviders(providers: List<PublicProvider>) = update { current ->
        val groups = providers
            .groupBy { provider ->
                "${provider.addressCoordinate.latitude.toBits()}:${provider.addressCoordinate.longitude.toBits()}"
            }
            .filterValues { it.size > 1 }
            .mapValues { (_, values) -> values.map(PublicProvider::id) }
        current.copy(
            providers = providers,
            coincidentGroups = groups,
            selectedProviderId = current.selectedProviderId?.takeIf { id -> providers.any { it.id == id } },
            selectedGroupKey = current.selectedGroupKey?.takeIf(groups::containsKey),
            dataState = if (providers.isEmpty()) DiscoveryDataState.Empty else DiscoveryDataState.Ready,
        )
    }

    private inline fun update(block: (ServiceMapUiState.Content) -> ServiceMapUiState.Content) {
        val current = _state.value as? ServiceMapUiState.Content ?: return
        _state.value = block(current)
    }

    private fun isUuid(value: String): Boolean = runCatching {
        java.util.UUID.fromString(value).toString().equals(value, ignoreCase = true)
    }.getOrDefault(false)

    companion object {
        private const val CLUSTER_MAX_ZOOM = 15.0

        fun factory(
            serviceId: String,
            repository: ServiceMapRepository,
            locationRepository: DeviceLocationRepository,
            connectivityObserver: ConnectivityObserver,
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ServiceMapViewModel(
                    serviceId,
                    repository,
                    locationRepository,
                    connectivityObserver,
                ) as T
        }
    }
}

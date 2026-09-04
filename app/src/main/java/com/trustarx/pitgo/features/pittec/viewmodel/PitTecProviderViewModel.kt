package com.trustarx.pitgo.features.pittec.viewmodel

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.trustarx.pitgo.core.location.model.DeviceLocationAvailability
import com.trustarx.pitgo.core.location.model.DeviceLocationResult
import com.trustarx.pitgo.core.location.repository.DeviceLocationRepository
import com.trustarx.pitgo.core.map.model.MapCoordinate
import com.trustarx.pitgo.core.map.model.MapSurfaceState
import com.trustarx.pitgo.core.map.model.MapViewport
import com.trustarx.pitgo.features.pittec.model.MockLocationSnapshot
import com.trustarx.pitgo.features.pittec.model.MockLocationSource
import com.trustarx.pitgo.features.pittec.model.PitTecBottomTab
import com.trustarx.pitgo.features.pittec.model.ProviderLocationGate
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PitTecHomeUiState(
    val selectedTab: PitTecBottomTab = PitTecBottomTab.MAP,
    val locationGate: ProviderLocationGate = ProviderLocationGate.CheckingPermission,
    val viewport: MapViewport = MapViewport(MapCoordinate(-14.235, -51.9253), 4.0),
    val surfaceState: MapSurfaceState = MapSurfaceState(),
    val styleReloadRevision: Long = 0L,
)

/**
 * Real device-location gate and bottom-navigation shell for the PitTec provider Home. Deliberately
 * scoped to only that: the incoming offer/accept/decline/live tracking are the existing, already
 * real [com.trustarx.pitgo.features.mobiledispatch.viewmodel.ProviderIncomingRequestViewModel] and
 * [com.trustarx.pitgo.features.appointmenttracking.viewmodel.AppointmentTrackingViewModel] — this
 * class never duplicates that RPC/Realtime logic, it only supplies the map/location layer the new
 * layout adds around them.
 */
class PitTecProviderViewModel(
    private val deviceLocationRepository: DeviceLocationRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(PitTecHomeUiState())
    val state: StateFlow<PitTecHomeUiState> = _state.asStateFlow()

    private var locateJob: Job? = null
    private var continuousJob: Job? = null
    private var locationRevision = 0L

    fun onPermissionChecked(hasFine: Boolean, hasCoarse: Boolean) {
        when {
            hasFine -> { setAvailability(DeviceLocationAvailability.GrantedPrecise); locate() }
            hasCoarse -> setAvailability(DeviceLocationAvailability.ApproximateOnly)
            else -> setAvailability(DeviceLocationAvailability.DeniedCanAskAgain)
        }
    }

    fun retryLocation() = locate()

    fun onVisible() {
        if (_state.value.locationGate is ProviderLocationGate.Ready) startContinuousUpdates()
    }

    fun onHidden() {
        continuousJob?.cancel()
        continuousJob = null
    }

    fun onTabSelected(tab: PitTecBottomTab) {
        _state.update { it.copy(selectedTab = tab) }
    }

    fun onViewportIdle(viewport: MapViewport) {
        _state.update { it.copy(viewport = viewport) }
    }

    fun onSurfaceState(surfaceState: MapSurfaceState) {
        _state.update { it.copy(surfaceState = surfaceState) }
    }

    fun retryStyle() {
        _state.update { it.copy(styleReloadRevision = it.styleReloadRevision + 1) }
    }

    override fun onCleared() {
        locateJob?.cancel()
        continuousJob?.cancel()
    }

    private fun locate() {
        if (locateJob?.isActive == true) return
        locateJob = viewModelScope.launch {
            _state.update { it.copy(locationGate = ProviderLocationGate.Locating) }
            when (val result = deviceLocationRepository.currentPreciseFix(timeoutMillis = 20_000L)) {
                is DeviceLocationResult.Success -> {
                    setAvailability(DeviceLocationAvailability.Ready(result.fix))
                    startContinuousUpdates()
                }
                DeviceLocationResult.ServicesDisabled -> setAvailability(DeviceLocationAvailability.ServicesDisabled)
                DeviceLocationResult.Timeout,
                DeviceLocationResult.Unavailable,
                -> setAvailability(DeviceLocationAvailability.FixUnavailable)
                DeviceLocationResult.PermissionDenied -> setAvailability(DeviceLocationAvailability.DeniedCanAskAgain)
            }
        }
    }

    private fun startContinuousUpdates() {
        if (continuousJob?.isActive == true) return
        continuousJob = viewModelScope.launch {
            deviceLocationRepository.preciseUpdates().collect { fix ->
                setAvailability(DeviceLocationAvailability.Ready(fix))
            }
        }
    }

    private fun setAvailability(value: DeviceLocationAvailability) {
        val gate = when (value) {
            DeviceLocationAvailability.NotRequested -> ProviderLocationGate.CheckingPermission
            DeviceLocationAvailability.Rationale,
            DeviceLocationAvailability.Requesting,
            DeviceLocationAvailability.DeniedCanAskAgain,
            DeviceLocationAvailability.DeniedPermanently,
            -> ProviderLocationGate.PermissionRequired
            DeviceLocationAvailability.ApproximateOnly -> ProviderLocationGate.ApproximateOnly
            DeviceLocationAvailability.ServicesDisabled -> ProviderLocationGate.ServicesDisabled
            DeviceLocationAvailability.Acquiring,
            DeviceLocationAvailability.GrantedPrecise,
            -> ProviderLocationGate.Locating
            DeviceLocationAvailability.FixUnavailable -> ProviderLocationGate.Unavailable
            is DeviceLocationAvailability.Ready -> {
                locationRevision += 1
                ProviderLocationGate.Ready(
                    MockLocationSnapshot(
                        coordinate = MapCoordinate(value.fix.latitude, value.fix.longitude),
                        capturedAtEpochMillis = value.fix.capturedAtEpochMillis,
                        capturedAtElapsedRealtimeMillis = SystemClock.elapsedRealtime(),
                        source = MockLocationSource.DEVICE,
                        revision = locationRevision,
                    ),
                )
            }
        }
        _state.update { it.copy(locationGate = gate) }
    }

    companion object {
        fun factory(deviceLocationRepository: DeviceLocationRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    PitTecProviderViewModel(deviceLocationRepository) as T
            }
    }
}

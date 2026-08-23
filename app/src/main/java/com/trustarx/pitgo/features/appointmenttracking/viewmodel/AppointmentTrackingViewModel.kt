package com.trustarx.pitgo.features.appointmenttracking.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.trustarx.pitgo.core.map.model.MapSurfaceState
import com.trustarx.pitgo.core.map.model.MapViewport
import com.trustarx.pitgo.core.map.model.MapViewportCommand
import com.trustarx.pitgo.features.appointmenttracking.model.AppointmentLocationEvent
import com.trustarx.pitgo.features.appointmenttracking.model.AppointmentProviderLocation
import com.trustarx.pitgo.features.appointmenttracking.model.AppointmentTrackingUiState
import com.trustarx.pitgo.features.appointmenttracking.model.LocationRevision
import com.trustarx.pitgo.features.appointmenttracking.model.TrackingDataState
import com.trustarx.pitgo.features.appointmenttracking.repository.AppointmentTrackingRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AppointmentTrackingViewModel(
    private val appointmentId: String,
    private val repository: AppointmentTrackingRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(AppointmentTrackingUiState())
    val state: StateFlow<AppointmentTrackingUiState> = _state.asStateFlow()
    private var observeJob: Job? = null
    private var latest: AppointmentProviderLocation? = null
    private var acceptedRevision: LocationRevision? = null
    private var terminal = false
    private var cameraRevision = 0L

    fun onVisible() {
        if (observeJob != null) return
        terminal = false
        observeJob = viewModelScope.launch {
            try {
                coroutineScope {
                    launch {
                        while (true) {
                            delay(1_000)
                            if (!terminal && _state.value.data !is TrackingDataState.Offline &&
                                _state.value.data !is TrackingDataState.Failure
                            ) latest?.let(::publishFreshness)
                        }
                    }
                    repository.observe(appointmentId).collect(::handle)
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Throwable) {
                if (!terminal) updateData(TrackingDataState.Failure(latest))
            }
        }
    }

    fun onHidden() {
        observeJob?.cancel()
        observeJob = null
        latest = null
        acceptedRevision = null
        terminal = false
        _state.value = AppointmentTrackingUiState()
    }

    fun updateSurfaceState(surfaceState: MapSurfaceState) {
        _state.value = _state.value.copy(surfaceState = surfaceState)
    }

    fun retryStyle() {
        _state.value = _state.value.copy(
            styleReloadRevision = _state.value.styleReloadRevision + 1,
        )
    }

    fun onViewportIdle(viewport: MapViewport) {
        _state.value = _state.value.copy(viewport = viewport)
    }

    fun setFollowProvider(enabled: Boolean) {
        _state.value = _state.value.copy(followProvider = enabled)
        if (enabled) latest?.let(::moveCameraTo)
    }

    private fun handle(event: AppointmentLocationEvent) {
        if (terminal) return
        when (event) {
            is AppointmentLocationEvent.Snapshot -> {
                val location = event.location
                if (location == null) {
                    if (acceptedRevision == null) updateData(TrackingDataState.Empty)
                } else accept(location)
            }
            is AppointmentLocationEvent.Upsert -> accept(event.location)
            is AppointmentLocationEvent.Removed -> {
                val accepted = acceptedRevision
                if (accepted == null || event.revision >= accepted) {
                    acceptedRevision = event.revision
                    latest = null
                    updateData(TrackingDataState.Empty)
                }
            }
            is AppointmentLocationEvent.Ended -> {
                terminal = true
                latest = null
                updateData(TrackingDataState.Ended)
            }
            AppointmentLocationEvent.SessionExpired -> {
                terminal = true
                latest = null
                updateData(TrackingDataState.SessionExpired)
            }
            AppointmentLocationEvent.Offline -> updateData(TrackingDataState.Offline(latest))
            AppointmentLocationEvent.Failure -> updateData(TrackingDataState.Failure(latest))
        }
    }

    private fun accept(location: AppointmentProviderLocation) {
        if (!location.isValid) return
        val accepted = acceptedRevision
        if (accepted != null && location.revision <= accepted) return
        acceptedRevision = location.revision
        latest = location
        publishFreshness(location)
        if (_state.value.followProvider) moveCameraTo(location)
    }

    private fun publishFreshness(location: AppointmentProviderLocation) {
        val age = System.currentTimeMillis() - location.publishedAtEpochMillis
        updateData(
            if (age in 0..STALE_AFTER_MS) TrackingDataState.Live(location)
            else TrackingDataState.Stale(location)
        )
    }

    private fun moveCameraTo(location: AppointmentProviderLocation) {
        cameraRevision += 1
        val target = MapViewport(location.coordinate, 16.0)
        _state.value = _state.value.copy(
            viewport = target,
            cameraCommand = MapViewportCommand(target, cameraRevision),
        )
    }

    private fun updateData(data: TrackingDataState) {
        _state.value = _state.value.copy(data = data)
    }

    companion object {
        private const val STALE_AFTER_MS = 30_000L

        fun factory(appointmentId: String, repository: AppointmentTrackingRepository) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    AppointmentTrackingViewModel(appointmentId, repository) as T
            }
    }
}

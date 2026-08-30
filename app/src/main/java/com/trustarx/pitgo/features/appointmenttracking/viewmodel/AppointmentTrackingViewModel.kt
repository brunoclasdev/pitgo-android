package com.trustarx.pitgo.features.appointmenttracking.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.trustarx.pitgo.core.location.model.ClientLocationPurpose
import com.trustarx.pitgo.core.location.model.DeviceLocationResult
import com.trustarx.pitgo.core.location.repository.DeviceLocationRepository
import com.trustarx.pitgo.core.map.model.MapSurfaceState
import com.trustarx.pitgo.core.map.model.MapViewport
import com.trustarx.pitgo.core.map.model.MapViewportCommand
import com.trustarx.pitgo.core.map.model.framingViewport
import com.trustarx.pitgo.features.appointmenttracking.model.AppointmentClientLocation
import com.trustarx.pitgo.features.appointmenttracking.model.AppointmentLocationEvent
import com.trustarx.pitgo.features.appointmenttracking.model.AppointmentProviderLocation
import com.trustarx.pitgo.features.appointmenttracking.model.AppointmentTrackingUiState
import com.trustarx.pitgo.features.appointmenttracking.model.LocationRevision
import com.trustarx.pitgo.features.appointmenttracking.model.TrackingDataState
import com.trustarx.pitgo.features.appointmenttracking.repository.AppointmentTrackingRepository
import com.trustarx.pitgo.features.mobiledispatch.repository.MobileDispatchRepository
import com.trustarx.pitgo.features.mobiledispatch.model.MobileDispatchResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant

class AppointmentTrackingViewModel(
    private val appointmentId: String,
    private val repository: AppointmentTrackingRepository,
    private val dispatchRepository: MobileDispatchRepository,
    private val deviceLocationRepository: DeviceLocationRepository,
    private val isClient: Boolean,
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
            when (val dispatch = dispatchRepository.restore()) {
                is MobileDispatchResult.Success -> _state.value = _state.value.copy(dispatch = dispatch.snapshot)
                else -> Unit
            }
            // Only the client publishes their own position (publish_client_location is a
            // client-only RPC). A provider viewing this same shared screen used to call this
            // unconditionally, which always failed with "Client required" (42501) after wasting
            // a GPS acquisition + up to 3 retries in parallel with the provider's own
            // ProviderLocationTrackingService — a real contributor to inconsistent provider
            // location updates.
            if (isClient) launch { publishOwnLocation() }
            // repository.observe()'s underlying channelFlow sends one Failure/Offline event and
            // then COMPLETES on any transient error (network blip, realtime disconnect while
            // backgrounded, etc.) — see AppointmentTrackingRepository.observe(). Without a retry,
            // one hiccup permanently froze this screen on its last known state, since nothing was
            // listening anymore for the appointment actually finishing or the provider's position
            // updating. Keeps resubscribing until the appointment reaches a terminal state.
            while (!terminal) {
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
                if (!terminal) delay(OBSERVE_RESUBSCRIBE_DELAY_MS)
            }
        }
    }

    fun onHidden() {
        observeJob?.cancel()
        observeJob = null
        latest = null
        acceptedRevision = null
        terminal = false
        deviceLocationRepository.endVisibleUse(ClientLocationPurpose.APPOINTMENT_TRACKING)
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
        if (enabled) latest?.let { moveCameraTo(it, _state.value.clientLocation) }
    }

    fun cancel(reason: String) {
        if (reason.isBlank() || _state.value.cancelling) return
        val appointment = _state.value.dispatch?.appointment ?: return
        _state.value = _state.value.copy(cancelling = true)
        viewModelScope.launch {
            when (val result = dispatchRepository.cancel(appointment.id, reason, appointment.revision)) {
                is MobileDispatchResult.Success -> _state.value = _state.value.copy(
                    dispatch = result.snapshot,
                    cancelling = false,
                )
                else -> _state.value = _state.value.copy(cancelling = false)
            }
        }
    }

    /**
     * One-shot capture of the client's own position, published to the backend so the provider's
     * map can also see it (data-model.md #3). Fired once per onVisible() — i.e. once per screen
     * open/reopen — never a continuous background stream (decision of /speckit-clarify).
     *
     * A raw-GPS cold fix (no Play Services fused provider, see AndroidLocationDataSource) can
     * easily take longer than a few seconds indoors or with Network provider disabled, so a
     * single short attempt was failing silently and leaving the client marker stuck on whatever
     * was last published (never, on a fresh appointment). Retries with a generous per-attempt
     * timeout instead of giving up after one 5s window, and never lets a failure here take down
     * the sibling coroutine that renders the provider's location.
     */
    private suspend fun publishOwnLocation() {
        deviceLocationRepository.beginVisibleUse(ClientLocationPurpose.APPOINTMENT_TRACKING)
        repeat(OWN_LOCATION_ATTEMPTS) { attempt ->
            try {
                val fix = deviceLocationRepository.currentClientFix(timeoutMillis = OWN_LOCATION_TIMEOUT_MS)
                if (fix is DeviceLocationResult.Success) {
                    applyClientLocation(AppointmentClientLocation(fix.fix.latitude, fix.fix.longitude))
                    repository.publishClientLocation(
                        appointmentId = appointmentId,
                        latitude = fix.fix.latitude,
                        longitude = fix.fix.longitude,
                        accuracyMeters = fix.fix.accuracyMeters.toDouble(),
                        capturedAt = Instant.ofEpochMilli(fix.fix.capturedAtEpochMillis),
                    )
                    return
                }
                Log.w(TAG, "publishOwnLocation attempt ${attempt + 1}/$OWN_LOCATION_ATTEMPTS failed: $fix")
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (t: Throwable) {
                Log.w(TAG, "publishOwnLocation attempt ${attempt + 1}/$OWN_LOCATION_ATTEMPTS threw", t)
            }
            if (attempt < OWN_LOCATION_ATTEMPTS - 1) delay(OWN_LOCATION_RETRY_DELAY_MS)
        }
    }

    /** Updates the client marker and, when the provider isn't known yet, centers on it alone. */
    private fun applyClientLocation(clientLocation: AppointmentClientLocation) {
        _state.value = _state.value.copy(clientLocation = clientLocation)
        val providerLocation = latest
        if (providerLocation != null) {
            if (_state.value.followProvider) moveCameraTo(providerLocation, clientLocation)
        } else {
            centerOnClientOnly(clientLocation)
        }
    }

    private fun centerOnClientOnly(clientLocation: AppointmentClientLocation) {
        cameraRevision += 1
        val target = MapViewport(clientLocation.coordinate, CLIENT_ONLY_ZOOM)
        _state.value = _state.value.copy(
            viewport = target,
            cameraCommand = MapViewportCommand(target, cameraRevision),
        )
    }

    private fun handle(event: AppointmentLocationEvent) {
        if (terminal) return
        when (event) {
            is AppointmentLocationEvent.Snapshot -> {
                event.clientLocation?.let(::applyClientLocation)
                val location = event.providerLocation
                if (location == null) {
                    if (acceptedRevision == null) updateData(TrackingDataState.Empty)
                } else accept(location)
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
        if (_state.value.followProvider) moveCameraTo(location, _state.value.clientLocation)
    }

    private fun publishFreshness(location: AppointmentProviderLocation) {
        val age = System.currentTimeMillis() - location.publishedAtEpochMillis
        updateData(
            if (age in 0..STALE_AFTER_MS) TrackingDataState.Live(location)
            else TrackingDataState.Stale(location)
        )
    }

    private fun moveCameraTo(location: AppointmentProviderLocation, clientLocation: AppointmentClientLocation?) {
        cameraRevision += 1
        val target = if (clientLocation != null && clientLocation.isValid) {
            framingViewport(location.coordinate, clientLocation.coordinate)
        } else MapViewport(location.coordinate, 16.0)
        _state.value = _state.value.copy(
            viewport = target,
            cameraCommand = MapViewportCommand(target, cameraRevision),
        )
    }

    private fun updateData(data: TrackingDataState) {
        _state.value = _state.value.copy(data = data)
    }

    companion object {
        private const val TAG = "AppointmentTracking"
        private const val STALE_AFTER_MS = 30_000L
        private const val CLIENT_ONLY_ZOOM = 15.0
        private const val OWN_LOCATION_ATTEMPTS = 3
        private const val OWN_LOCATION_TIMEOUT_MS = 20_000L
        private const val OWN_LOCATION_RETRY_DELAY_MS = 2_000L
        private const val OBSERVE_RESUBSCRIBE_DELAY_MS = 3_000L

        fun factory(
            appointmentId: String,
            repository: AppointmentTrackingRepository,
            dispatchRepository: MobileDispatchRepository,
            deviceLocationRepository: DeviceLocationRepository,
            isClient: Boolean,
        ) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    AppointmentTrackingViewModel(
                        appointmentId, repository, dispatchRepository, deviceLocationRepository, isClient,
                    ) as T
            }
    }
}

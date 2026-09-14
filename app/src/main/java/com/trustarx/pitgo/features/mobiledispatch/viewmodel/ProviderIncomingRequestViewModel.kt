package com.trustarx.pitgo.features.mobiledispatch.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.trustarx.pitgo.core.map.model.MapCoordinate
import com.trustarx.pitgo.core.map.model.MapViewport
import com.trustarx.pitgo.core.map.model.MapViewportCommand
import com.trustarx.pitgo.core.map.model.framingViewport
import com.trustarx.pitgo.features.appointmenttracking.model.AppointmentLocationEvent
import com.trustarx.pitgo.features.appointmenttracking.repository.AppointmentTrackingRepository
import com.trustarx.pitgo.features.mobiledispatch.model.*
import com.trustarx.pitgo.features.mobiledispatch.repository.MobileDispatchRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.ceil

class ProviderIncomingRequestViewModel(
    private val providerId: String,
    private val repository: MobileDispatchRepository,
    private val appointmentTrackingRepository: AppointmentTrackingRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<ProviderIncomingUiState>(ProviderIncomingUiState.Loading)
    val state = _state.asStateFlow()
    private val _mapState = MutableStateFlow(ProviderMapState())
    val mapState = _mapState.asStateFlow()
    private var actionJob: Job? = null
    private var locationJob: Job? = null
    private var observedAppointmentId: String? = null
    private var revision = 0L
    private var cameraRevision = 0L

    init {
        refresh()
        viewModelScope.launch {
            repository.observeProvider(providerId).collect { refresh() }
        }
        viewModelScope.launch {
            while (true) { updateCountdown(); delay(1_000) }
        }
        // Realtime is only a notification channel.  Keep the screen authoritative even when the
        // websocket is silently stale (a common case after backgrounding or changing networks).
        // The repository restore is status-aware and moves Request -> Active/None as soon as the
        // server changes the appointment, so the UI never remains indefinitely on PENDING.
        viewModelScope.launch {
            while (true) {
                delay(POLL_INTERVAL_MS)
                refresh()
            }
        }
        viewModelScope.launch {
            state.collect { current ->
                val appointmentId = (current as? ProviderIncomingUiState.Active)?.snapshot?.appointment?.id
                if (appointmentId != observedAppointmentId) observeAppointmentLocation(appointmentId)
            }
        }
    }

    fun refresh() = submit(null) { repository.restoreAnyDispatchKind() }
    fun accept() = appointmentAction(PendingDispatchAction.ACCEPT) { id, rev -> repository.respond(id, true, rev) }
    fun decline() = appointmentAction(PendingDispatchAction.DECLINE) { id, rev -> repository.respond(id, false, rev) }
    fun startService() = appointmentAction(PendingDispatchAction.START) { id, rev -> repository.startAppointment(id, rev) }
    fun complete() = appointmentAction(PendingDispatchAction.COMPLETE) { id, rev -> repository.complete(id, rev) }
    fun cancel(reason: String) = appointmentAction(PendingDispatchAction.CANCEL) { id, rev -> repository.cancel(id, reason, rev) }

    fun setFollowMap(enabled: Boolean) {
        _mapState.value = _mapState.value.copy(followEnabled = enabled)
        if (enabled) moveMapCamera(_mapState.value.providerLocation, _mapState.value.clientLocation)
    }

    private fun appointmentAction(
        pending: PendingDispatchAction,
        block: suspend (String, Long) -> MobileDispatchResult,
    ) {
        val snapshot = when (val current = _state.value) {
            is ProviderIncomingUiState.Request -> current.snapshot
            is ProviderIncomingUiState.Active -> current.snapshot
            else -> return
        }
        val appointment = snapshot.appointment ?: return
        submit(pending) { block(appointment.id, appointment.revision) }
    }

    private fun submit(pending: PendingDispatchAction?, block: suspend () -> MobileDispatchResult) {
        if (actionJob?.isActive == true) return
        val requestState = _state.value as? ProviderIncomingUiState.Request
        val activeState = _state.value as? ProviderIncomingUiState.Active
        if (pending != null) {
            val marker = DispatchPendingAction(pending, ++revision)
            when {
                requestState != null -> _state.value = requestState.copy(pending = marker)
                activeState != null -> _state.value = activeState.copy(pending = marker)
            }
        }
        actionJob = viewModelScope.launch {
            when (val result = block()) {
                MobileDispatchResult.None -> _state.value = ProviderIncomingUiState.None
                is MobileDispatchResult.Failure -> _state.value = when (result.reason) {
                    MobileDispatchFailure.SESSION_EXPIRED -> ProviderIncomingUiState.SessionExpired
                    else -> requestState?.copy(pending = null, failure = result.reason)
                        ?: activeState?.copy(pending = null)
                        ?: ProviderIncomingUiState.Failure(result.reason)
                }
                is MobileDispatchResult.Success -> {
                    val appointment = result.snapshot.appointment
                    _state.value = if (appointment?.status == MobileAppointmentStatus.PENDING) {
                        ProviderIncomingUiState.Request(result.snapshot)
                    } else ProviderIncomingUiState.Active(result.snapshot)
                }
            }
        }
    }

    private fun updateCountdown() {
        val current = _state.value as? ProviderIncomingUiState.Request ?: return
        val deadline = current.snapshot.appointment?.responseDeadlineEpochMillis ?: return
        val offset = current.snapshot.serverNowEpochMillis - System.currentTimeMillis()
        _state.value = current.copy(
            remainingSeconds = ceil((deadline - (System.currentTimeMillis() + offset)) / 1000.0)
                .toLong().coerceAtLeast(0),
        )
    }

    /**
     * Reuses the exact same private tracking channel/RPCs the client's AppointmentTrackingScreen
     * already uses (US2) instead of a second backend surface: from the provider's own device,
     * "providerLocation" is this device's own live position and "clientLocation" is the client's
     * one-shot fix — see specs/014 US3 and research.md #3.
     */
    private fun observeAppointmentLocation(appointmentId: String?) {
        locationJob?.cancel()
        observedAppointmentId = appointmentId
        _mapState.value = ProviderMapState()
        if (appointmentId == null) { locationJob = null; return }
        locationJob = viewModelScope.launch {
            appointmentTrackingRepository.observe(appointmentId).collect(::handleLocationEvent)
        }
    }

    private fun handleLocationEvent(event: AppointmentLocationEvent) {
        if (event !is AppointmentLocationEvent.Snapshot) return
        val providerCoordinate = event.providerLocation?.coordinate?.takeIf { it.isValid }
        val clientCoordinate = event.clientLocation?.coordinate?.takeIf { it.isValid }
        _mapState.value = _mapState.value.copy(
            providerLocation = providerCoordinate,
            clientLocation = clientCoordinate,
        )
        if (_mapState.value.followEnabled) moveMapCamera(providerCoordinate, clientCoordinate)
    }

    private fun moveMapCamera(providerCoordinate: MapCoordinate?, clientCoordinate: MapCoordinate?) {
        val target = when {
            providerCoordinate != null && clientCoordinate != null ->
                framingViewport(providerCoordinate, clientCoordinate)
            providerCoordinate != null -> MapViewport(providerCoordinate, 15.0)
            clientCoordinate != null -> MapViewport(clientCoordinate, 15.0)
            else -> return
        }
        cameraRevision += 1
        _mapState.value = _mapState.value.copy(
            viewport = target,
            cameraCommand = MapViewportCommand(target, cameraRevision),
        )
    }

    companion object {
        private const val POLL_INTERVAL_MS = 5_000L

        fun factory(
            providerId: String,
            repository: MobileDispatchRepository,
            appointmentTrackingRepository: AppointmentTrackingRepository,
        ) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ProviderIncomingRequestViewModel(providerId, repository, appointmentTrackingRepository) as T
            }
    }
}

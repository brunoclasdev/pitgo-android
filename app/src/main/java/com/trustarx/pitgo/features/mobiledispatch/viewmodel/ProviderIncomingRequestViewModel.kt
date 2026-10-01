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
import kotlinx.coroutines.isActive
import kotlin.math.ceil

class ProviderIncomingRequestViewModel(
    private val providerId: String,
    private val repository: MobileDispatchRepository,
    private val appointmentTrackingRepository: AppointmentTrackingRepository,
    private val automaticStart: Boolean = true,
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

    private var observing: Job? = null
    private var clockOffset = 0L
    private val refreshVersion = MutableStateFlow(0L)
    val refreshSequence = refreshVersion.asStateFlow()
    var validatedState: ProviderIncomingUiState = ProviderIncomingUiState.Loading
        private set
    private val response = MutableStateFlow<PendingDispatchAction?>(null)
    val completedResponse = response.asStateFlow()
    fun consumeResponse() { response.value = null }
    init { if (automaticStart) startObserving() }

    fun startObserving() {
        if (observing?.isActive == true) return
        refresh()
        observing = viewModelScope.launch {
            launch {
                while (isActive) {
                    try { repository.observeProvider(providerId).collect { refresh() } }
                    catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
                    catch (_: Exception) { refresh() }
                    delay(3_000)
                }
            }
            launch { while (isActive) { updateCountdown(); delay(1_000) } }
            launch { while (isActive) { delay(POLL_INTERVAL_MS); refresh() } }
            if (automaticStart) launch {
                state.collect { current ->
                    val id = (current as? ProviderIncomingUiState.Active)?.snapshot?.appointment?.id
                    if (id != observedAppointmentId) observeAppointmentLocation(id)
                }
            }
        }
    }
    fun stopObserving() {
        observing?.cancel(); observing = null
        actionJob?.cancel(); actionJob = null
        locationJob?.cancel(); locationJob = null
        observedAppointmentId = null
        _state.value = ProviderIncomingUiState.Loading
        validatedState = ProviderIncomingUiState.Loading
        _mapState.value = ProviderMapState()
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
        if (snapshot.dispatchKind == MobileDispatchKind.GUINCHO_DIRECT &&
            pending in setOf(PendingDispatchAction.ACCEPT, PendingDispatchAction.DECLINE)) {
            val current = _state.value as? ProviderIncomingUiState.Request ?: return
            if (current.failure != null || current.pending != null ||
                (appointment.responseDeadlineEpochMillis ?: 0L) <= System.currentTimeMillis() + clockOffset) return
        }
        submit(pending) { block(appointment.id, appointment.revision) }
    }

    private fun submit(pending: PendingDispatchAction?, block: suspend () -> MobileDispatchResult) {
        val requestState = _state.value as? ProviderIncomingUiState.Request
        val activeState = _state.value as? ProviderIncomingUiState.Active
        if (actionJob?.isActive == true) {
            if (pending == null || requestState?.pending != null || activeState?.pending != null) return
            // A user response takes precedence over a read-only background refresh.
            actionJob?.cancel()
        }
        if (pending != null) {
            val marker = DispatchPendingAction(pending, ++revision)
            when {
                requestState != null -> _state.value = requestState.copy(pending = marker)
                activeState != null -> _state.value = activeState.copy(pending = marker)
            }
        }
        actionJob = viewModelScope.launch {
            val result = block()
            when (result) {
                MobileDispatchResult.None -> {
                    _state.value = ProviderIncomingUiState.None
                    if (pending == PendingDispatchAction.DECLINE) response.value = pending
                }
                is MobileDispatchResult.Failure -> _state.value = when (result.reason) {
                    MobileDispatchFailure.SESSION_EXPIRED -> ProviderIncomingUiState.SessionExpired
                    else -> requestState?.copy(pending = null, failure = result.reason)
                        ?: activeState?.copy(pending = null)
                        ?: ProviderIncomingUiState.Failure(result.reason)
                }
                is MobileDispatchResult.Success -> {
                    clockOffset = result.snapshot.serverNowEpochMillis - System.currentTimeMillis()
                    val appointment = result.snapshot.appointment
                    if (pending in setOf(PendingDispatchAction.ACCEPT, PendingDispatchAction.DECLINE)) response.value = pending
                    _state.value = if (appointment?.status == MobileAppointmentStatus.PENDING) {
                        ProviderIncomingUiState.Request(result.snapshot, remainingSeconds = ceil(((appointment.responseDeadlineEpochMillis ?: 0L) - result.snapshot.serverNowEpochMillis) / 1000.0).toLong().coerceAtLeast(0))
                    } else ProviderIncomingUiState.Active(result.snapshot)
                }
            }
            validatedState = _state.value
            refreshVersion.value += 1
        }
    }

    private fun updateCountdown() {
        val current = _state.value as? ProviderIncomingUiState.Request ?: return
        val deadline = current.snapshot.appointment?.responseDeadlineEpochMillis ?: return
        val offset = clockOffset
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
        private const val POLL_INTERVAL_MS = 2_000L

        fun factory(
            providerId: String,
            repository: MobileDispatchRepository,
            appointmentTrackingRepository: AppointmentTrackingRepository,
            automaticStart: Boolean = true,
        ) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ProviderIncomingRequestViewModel(providerId, repository, appointmentTrackingRepository, automaticStart) as T
            }
    }
}

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
import com.trustarx.pitgo.core.map.model.MapViewportCommand
import com.trustarx.pitgo.core.map.model.framingViewport
import com.trustarx.pitgo.features.pittec.model.MockAppointment
import com.trustarx.pitgo.features.pittec.model.MockAppointmentStatus
import com.trustarx.pitgo.features.pittec.model.MockLocationSnapshot
import com.trustarx.pitgo.features.pittec.model.MockLocationSource
import com.trustarx.pitgo.features.pittec.model.MockParticipant
import com.trustarx.pitgo.features.pittec.model.MockParticipantRole
import com.trustarx.pitgo.features.pittec.model.PitTecBottomTab
import com.trustarx.pitgo.features.pittec.model.PitTecMockFixtures
import com.trustarx.pitgo.features.pittec.model.PitTecProviderMockUiState
import com.trustarx.pitgo.features.pittec.model.PitTecProviderStage
import com.trustarx.pitgo.features.pittec.model.ProviderLocationGate
import com.trustarx.pitgo.features.pittec.model.advanceMovement
import com.trustarx.pitgo.features.pittec.model.confirmCompletionCounterpart
import com.trustarx.pitgo.features.pittec.model.confirmCompletionLocal
import com.trustarx.pitgo.features.pittec.model.confirmMeetingCounterpart
import com.trustarx.pitgo.features.pittec.model.confirmMeetingLocal
import com.trustarx.pitgo.features.pittec.model.distanceMetersTo
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Independent, host-scoped state machine for the mocked PitTec provider Home/alert/tracking flow
 * (US1, US3–US5). Everything here is in-memory only; see contracts/provider-pittec-ui.md and
 * contracts/mock-appointment-state.md. Never touches the real dispatch/tracking repositories.
 */
class PitTecProviderMockViewModel(
    providerDisplayName: String,
    private val deviceLocationRepository: DeviceLocationRepository,
) : ViewModel() {
    private val provider = MockParticipant("self-provider", providerDisplayName, MockParticipantRole.PITTEC)
    private val _state = MutableStateFlow(PitTecProviderMockUiState(provider = provider))
    val state: StateFlow<PitTecProviderMockUiState> = _state.asStateFlow()

    private var locateJob: Job? = null
    private var continuousJob: Job? = null
    private var movementJob: Job? = null
    private var autoRequestTriggered = false
    private var requestSequence = 0L
    private var respondedRequestRevision: Long? = null
    private var pendingMovementPath: List<MapCoordinate> = emptyList()
    private var locationRevision = 0L
    private var cameraRevision = 0L

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
        startMovementJobIfNeeded()
    }

    fun onHidden() {
        continuousJob?.cancel(); continuousJob = null
        movementJob?.cancel(); movementJob = null
    }

    fun onTabSelected(tab: PitTecBottomTab) {
        _state.update { it.copy(selectedTab = tab) }
    }

    fun onSimulateRequest() {
        if (_state.value.locationGate !is ProviderLocationGate.Ready) return
        triggerIncomingRequest()
    }

    fun onPulseEffectStarted() {
        val stage = _state.value.stage as? PitTecProviderStage.IncomingRequest ?: return
        if (stage.request.pulseConsumed) return
        _state.update { it.copy(stage = PitTecProviderStage.IncomingRequest(stage.request.copy(pulseConsumed = true))) }
    }

    fun onAccept() {
        val stage = _state.value.stage as? PitTecProviderStage.IncomingRequest ?: return
        val request = stage.request
        if (respondedRequestRevision == request.revision) return
        respondedRequestRevision = request.revision
        val deviceSnapshot = currentDeviceSnapshot() ?: return
        val nowEpoch = System.currentTimeMillis()
        val appointment = MockAppointment(
            id = "mock-appointment-${request.id}",
            client = request.client,
            provider = provider,
            estimate = request.estimate,
            clientLocation = MockLocationSnapshot(
                coordinate = request.clientCoordinate,
                capturedAtEpochMillis = nowEpoch,
                capturedAtElapsedRealtimeMillis = SystemClock.elapsedRealtime(),
                source = MockLocationSource.FIXTURE,
                revision = 1,
            ),
            providerLocation = deviceSnapshot,
            movementPath = pendingMovementPath,
            remainingDistanceMeters = deviceSnapshot.coordinate.distanceMetersTo(request.clientCoordinate),
            status = MockAppointmentStatus.TRAVELLING,
            acceptedAtEpochMillis = nowEpoch,
        )
        _state.update {
            it.copy(
                stage = PitTecProviderStage.Tracking(appointment),
                currentOrLastAppointment = appointment,
                viewport = framingViewport(appointment.clientLocation.coordinate, appointment.providerLocation.coordinate),
                cameraCommand = framingCommand(appointment),
            )
        }
        startMovementJobIfNeeded()
    }

    fun onDecline() {
        val stage = _state.value.stage as? PitTecProviderStage.IncomingRequest ?: return
        if (respondedRequestRevision == stage.request.revision) return
        respondedRequestRevision = stage.request.revision
        _state.update { it.copy(stage = PitTecProviderStage.Available) }
    }

    fun onMeetingConfirmed() {
        val tracking = _state.value.stage as? PitTecProviderStage.Tracking ?: return
        val updated = tracking.appointment.confirmMeetingLocal(System.currentTimeMillis())
        if (updated === tracking.appointment) return
        applyAppointment(updated)
        val confirmationRevision = updated.meetingConfirmation!!.revision
        viewModelScope.launch {
            delay(3_000)
            val current = (_state.value.stage as? PitTecProviderStage.Tracking)?.appointment ?: return@launch
            if (current.id != updated.id) return@launch
            applyAppointment(current.confirmMeetingCounterpart(System.currentTimeMillis(), confirmationRevision))
        }
    }

    fun onCompletionConfirmed() {
        val tracking = _state.value.stage as? PitTecProviderStage.Tracking ?: return
        val updated = tracking.appointment.confirmCompletionLocal(System.currentTimeMillis())
        if (updated === tracking.appointment) return
        applyAppointment(updated)
        val confirmationRevision = updated.completionConfirmation!!.revision
        viewModelScope.launch {
            delay(3_000)
            val current = (_state.value.stage as? PitTecProviderStage.Tracking)?.appointment ?: return@launch
            if (current.id != updated.id) return@launch
            applyAppointment(current.confirmCompletionCounterpart(System.currentTimeMillis(), confirmationRevision))
        }
    }

    fun onReturnToAvailable() {
        if (_state.value.stage !is PitTecProviderStage.Completed) return
        _state.update { it.copy(stage = PitTecProviderStage.Available) }
    }

    fun onRecenter() {
        val appointment = currentOrLastTrackedAppointment() ?: return
        _state.update { it.copy(cameraCommand = framingCommand(appointment)) }
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
        movementJob?.cancel()
    }

    private fun triggerIncomingRequest() {
        val stage = _state.value.stage
        if (stage !is PitTecProviderStage.Available && stage !is PitTecProviderStage.Completed) return
        val anchor = currentDeviceCoordinate() ?: return
        requestSequence += 1
        val fixture = PitTecMockFixtures.providerIncoming(
            anchor = anchor,
            requestId = "mock-request-$requestSequence",
            revision = requestSequence,
            nowEpochMillis = System.currentTimeMillis(),
        )
        pendingMovementPath = fixture.movementPath
        respondedRequestRevision = null
        _state.update { it.copy(stage = PitTecProviderStage.IncomingRequest(fixture.request)) }
    }

    private fun locate() {
        if (locateJob?.isActive == true) return
        locateJob = viewModelScope.launch {
            _state.update { it.copy(locationGate = ProviderLocationGate.Locating) }
            when (val result = deviceLocationRepository.currentPreciseFix(timeoutMillis = 20_000L)) {
                is DeviceLocationResult.Success -> {
                    setAvailability(DeviceLocationAvailability.Ready(result.fix))
                    startContinuousUpdates()
                    maybeAutoTriggerIncomingRequest()
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

    private fun maybeAutoTriggerIncomingRequest() {
        if (autoRequestTriggered) return
        autoRequestTriggered = true
        viewModelScope.launch {
            delay(2_000)
            if (_state.value.stage is PitTecProviderStage.Available) triggerIncomingRequest()
        }
    }

    private fun startMovementJobIfNeeded() {
        val tracking = _state.value.stage as? PitTecProviderStage.Tracking ?: return
        if (tracking.appointment.status != MockAppointmentStatus.TRAVELLING) return
        if (movementJob?.isActive == true) return
        movementJob = viewModelScope.launch {
            while (true) {
                delay(MockLocationSnapshot.MOVEMENT_STEP_MILLIS)
                val current = (_state.value.stage as? PitTecProviderStage.Tracking)?.appointment ?: return@launch
                if (current.status != MockAppointmentStatus.TRAVELLING) return@launch
                val updated = current.advanceMovement(System.currentTimeMillis(), SystemClock.elapsedRealtime())
                applyAppointment(updated)
            }
        }
    }

    private fun applyAppointment(updated: MockAppointment) {
        _state.update { current ->
            val stage = if (updated.status == MockAppointmentStatus.COMPLETED) {
                PitTecProviderStage.Completed(updated)
            } else {
                PitTecProviderStage.Tracking(updated)
            }
            current.copy(stage = stage, currentOrLastAppointment = updated)
        }
        if (updated.status == MockAppointmentStatus.COMPLETED) {
            movementJob?.cancel()
            movementJob = null
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

    private fun currentDeviceSnapshot(): MockLocationSnapshot? =
        (_state.value.locationGate as? ProviderLocationGate.Ready)?.deviceLocation

    private fun currentDeviceCoordinate(): MapCoordinate? = currentDeviceSnapshot()?.coordinate

    private fun currentOrLastTrackedAppointment(): MockAppointment? = when (val stage = _state.value.stage) {
        is PitTecProviderStage.Tracking -> stage.appointment
        is PitTecProviderStage.Completed -> stage.appointment
        else -> null
    }

    private fun framingCommand(appointment: MockAppointment): MapViewportCommand {
        cameraRevision += 1
        return MapViewportCommand(
            framingViewport(appointment.clientLocation.coordinate, appointment.providerLocation.coordinate),
            cameraRevision,
        )
    }

    companion object {
        fun factory(
            providerDisplayName: String,
            deviceLocationRepository: DeviceLocationRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                PitTecProviderMockViewModel(providerDisplayName, deviceLocationRepository) as T
        }
    }
}

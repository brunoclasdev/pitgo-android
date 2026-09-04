package com.trustarx.pitgo.features.pittec.viewmodel

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.trustarx.pitgo.core.location.model.DeviceLocationResult
import com.trustarx.pitgo.core.location.repository.DeviceLocationRepository
import com.trustarx.pitgo.core.map.model.MapCoordinate
import com.trustarx.pitgo.core.map.model.MapSurfaceState
import com.trustarx.pitgo.core.map.model.MapViewport
import com.trustarx.pitgo.core.map.model.MapViewportCommand
import com.trustarx.pitgo.core.map.model.framingViewport
import com.trustarx.pitgo.features.pittec.model.DistancePerimeter
import com.trustarx.pitgo.features.pittec.model.MockAppointment
import com.trustarx.pitgo.features.pittec.model.MockAppointmentStatus
import com.trustarx.pitgo.features.pittec.model.MockCandidateResponse
import com.trustarx.pitgo.features.pittec.model.MockLocationSnapshot
import com.trustarx.pitgo.features.pittec.model.MockLocationSource
import com.trustarx.pitgo.features.pittec.model.MockPitTecCandidate
import com.trustarx.pitgo.features.pittec.model.MockSearch
import com.trustarx.pitgo.features.pittec.model.MockSearchFeedback
import com.trustarx.pitgo.features.pittec.model.NearbyPitTecMarker
import com.trustarx.pitgo.features.pittec.model.PitTecClientMockUiState
import com.trustarx.pitgo.features.pittec.model.PitTecClientStage
import com.trustarx.pitgo.features.pittec.model.PitTecLocationState
import com.trustarx.pitgo.features.pittec.model.PitTecMockFixtures
import com.trustarx.pitgo.features.pittec.model.PitTecMockScenario
import com.trustarx.pitgo.features.pittec.model.ServiceEstimate
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
 * Independent, host-scoped state machine for the mocked PitTec client estimate/search/tracking
 * flow (US2–US4). See contracts/client-pittec-ui.md and contracts/mock-appointment-state.md.
 * Reuses [DeviceLocationRepository] only to anchor the preserved discovery map on the client's own
 * position — never for a real `list_nearby_pittecs`/dispatch call.
 */
class PitTecClientMockViewModel(
    serviceId: String,
    private val deviceLocationRepository: DeviceLocationRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(PitTecClientMockUiState(serviceId = serviceId))
    val state: StateFlow<PitTecClientMockUiState> = _state.asStateFlow()

    private var searchJob: Job? = null
    private var movementJob: Job? = null
    private var attemptIndex = 0
    private var searchSequence = 0L
    private var cameraRevision = 0L

    fun onLocationPermissionResult(granted: Boolean) {
        if (!granted) {
            _state.update { it.copy(locationState = PitTecLocationState.Denied) }
            return
        }
        if (_state.value.locationState == PitTecLocationState.InUse) return
        _state.update { it.copy(locationState = PitTecLocationState.Resolving) }
        viewModelScope.launch {
            when (val result = deviceLocationRepository.currentClientFix()) {
                is DeviceLocationResult.Success -> {
                    val coordinate = MapCoordinate(result.fix.latitude, result.fix.longitude)
                    _state.update {
                        it.copy(
                            locationState = PitTecLocationState.InUse,
                            clientCoordinate = coordinate,
                            viewport = MapViewport(coordinate, 14.0),
                            markers = decorativeMarkersAround(coordinate),
                        )
                    }
                }
                DeviceLocationResult.ServicesDisabled,
                DeviceLocationResult.Timeout,
                DeviceLocationResult.Unavailable,
                -> _state.update { it.copy(locationState = PitTecLocationState.Unavailable) }
                DeviceLocationResult.PermissionDenied ->
                    _state.update { it.copy(locationState = PitTecLocationState.Denied) }
            }
        }
    }

    fun retryDiscovery() = onLocationPermissionResult(true)

    fun onViewportIdle(viewport: MapViewport) {
        _state.update { it.copy(viewport = viewport) }
    }

    fun onSurfaceState(surfaceState: MapSurfaceState) {
        _state.update { it.copy(surfaceState = surfaceState) }
    }

    fun retryStyle() {
        _state.update { it.copy(styleReloadRevision = it.styleReloadRevision + 1) }
    }

    fun onVisible() {
        startMovementJobIfNeeded()
    }

    fun onHidden() {
        movementJob?.cancel()
        movementJob = null
    }

    fun onSearchRequested() {
        if (_state.value.stage != PitTecClientStage.Discovery) return
        _state.update { it.copy(stage = PitTecClientStage.Estimating) }
        viewModelScope.launch {
            delay(400)
            if (_state.value.stage != PitTecClientStage.Estimating) return@launch
            val estimate = ServiceEstimate(DistancePerimeter(5))
            _state.update { it.copy(stage = PitTecClientStage.AwaitingClientConfirmation(estimate)) }
        }
    }

    fun onEstimateDismissed() {
        if (_state.value.stage !is PitTecClientStage.AwaitingClientConfirmation) return
        _state.update { it.copy(stage = PitTecClientStage.Discovery) }
    }

    fun onEstimateConfirmed() {
        if (_state.value.stage !is PitTecClientStage.AwaitingClientConfirmation) return
        startNewSearch()
    }

    fun onRetrySearch() {
        if (_state.value.stage !is PitTecClientStage.NoProvider) return
        startNewSearch()
    }

    fun onBackFromNoProvider() {
        if (_state.value.stage !is PitTecClientStage.NoProvider) return
        _state.update { it.copy(stage = PitTecClientStage.Discovery) }
    }

    fun onMeetingConfirmed() {
        val tracking = _state.value.stage as? PitTecClientStage.Tracking ?: return
        val updated = tracking.appointment.confirmMeetingLocal(System.currentTimeMillis())
        if (updated === tracking.appointment) return
        applyAppointment(updated)
        val confirmationRevision = updated.meetingConfirmation!!.revision
        viewModelScope.launch {
            delay(3_000)
            val current = (_state.value.stage as? PitTecClientStage.Tracking)?.appointment ?: return@launch
            if (current.id != updated.id) return@launch
            applyAppointment(current.confirmMeetingCounterpart(System.currentTimeMillis(), confirmationRevision))
        }
    }

    fun onCompletionConfirmed() {
        val tracking = _state.value.stage as? PitTecClientStage.Tracking ?: return
        val updated = tracking.appointment.confirmCompletionLocal(System.currentTimeMillis())
        if (updated === tracking.appointment) return
        applyAppointment(updated)
        val confirmationRevision = updated.completionConfirmation!!.revision
        viewModelScope.launch {
            delay(3_000)
            val current = (_state.value.stage as? PitTecClientStage.Tracking)?.appointment ?: return@launch
            if (current.id != updated.id) return@launch
            applyAppointment(current.confirmCompletionCounterpart(System.currentTimeMillis(), confirmationRevision))
        }
    }

    fun onBackToHome() {
        if (_state.value.stage !is PitTecClientStage.Completed) return
        movementJob?.cancel()
        movementJob = null
        _state.update { it.copy(stage = PitTecClientStage.Discovery, cameraCommand = null) }
    }

    fun onRecenter() {
        val appointment = currentOrCompletedAppointment() ?: return
        _state.update { it.copy(cameraCommand = framingCommand(appointment)) }
    }

    override fun onCleared() {
        searchJob?.cancel()
        movementJob?.cancel()
    }

    private fun startNewSearch() {
        val anchor = _state.value.clientCoordinate ?: return
        searchJob?.cancel()
        val scenario = nextScenario(anchor)
        val firstCandidate = scenario.candidates.firstOrNull()
        if (firstCandidate == null) {
            searchSequence += 1
            val emptySearch = MockSearch(
                id = "mock-search-$searchSequence",
                scenarioId = scenario.id,
                currentPerimeter = DistancePerimeter(5),
                candidateIndex = 0,
                estimate = ServiceEstimate(DistancePerimeter(5)),
                feedback = MockSearchFeedback.NOT_FOUND,
                revision = 1,
                confirmed = true,
            )
            _state.update { it.copy(stage = PitTecClientStage.NoProvider(emptySearch)) }
            return
        }
        searchSequence += 1
        val search = MockSearch(
            id = "mock-search-$searchSequence",
            scenarioId = scenario.id,
            currentPerimeter = firstCandidate.perimeter,
            candidateIndex = 0,
            estimate = ServiceEstimate(firstCandidate.perimeter),
            feedback = MockSearchFeedback.SEARCHING,
            revision = 1,
            confirmed = true,
        )
        _state.update { it.copy(stage = PitTecClientStage.Searching(search)) }
        searchJob = viewModelScope.launch { runSearchLoop(scenario, search) }
    }

    private suspend fun runSearchLoop(scenario: PitTecMockScenario, initialSearch: MockSearch) {
        var search = initialSearch
        while (true) {
            if (search.candidateIndex >= scenario.candidates.size) {
                search = search.copy(feedback = MockSearchFeedback.NOT_FOUND, revision = search.revision + 1)
                _state.update { it.copy(stage = PitTecClientStage.NoProvider(search)) }
                return
            }
            val candidate = scenario.candidates[search.candidateIndex]
            search = search.copy(
                currentPerimeter = candidate.perimeter,
                estimate = ServiceEstimate(candidate.perimeter),
                feedback = MockSearchFeedback.SEARCHING,
                revision = search.revision + 1,
            )
            _state.update { it.copy(stage = PitTecClientStage.Searching(search)) }
            delay(500)
            search = search.copy(feedback = MockSearchFeedback.AWAITING_RESPONSE, revision = search.revision + 1)
            _state.update { it.copy(stage = PitTecClientStage.Searching(search)) }
            delay(candidate.responseDelayMillis)
            when (candidate.response) {
                MockCandidateResponse.DECLINE -> {
                    search = search.copy(
                        rejectedCandidateIds = search.rejectedCandidateIds + candidate.id,
                        candidateIndex = search.candidateIndex + 1,
                        feedback = MockSearchFeedback.DECLINED_CONTINUING,
                        revision = search.revision + 1,
                    )
                    _state.update { it.copy(stage = PitTecClientStage.Searching(search)) }
                    delay(900)
                }
                MockCandidateResponse.ACCEPT -> {
                    search = search.copy(feedback = MockSearchFeedback.FOUND, revision = search.revision + 1)
                    _state.update { it.copy(stage = PitTecClientStage.Searching(search)) }
                    delay(400)
                    val appointment = createAppointment(scenario, candidate, search.estimate)
                    _state.update {
                        it.copy(
                            stage = PitTecClientStage.Tracking(appointment),
                            viewport = framingViewport(
                                appointment.clientLocation.coordinate,
                                appointment.providerLocation.coordinate,
                            ),
                            cameraCommand = framingCommand(appointment),
                        )
                    }
                    startMovementJobIfNeeded()
                    return
                }
            }
        }
    }

    private fun nextScenario(anchor: MapCoordinate): PitTecMockScenario {
        val scenario = if (attemptIndex == 0) {
            PitTecMockFixtures.progressiveSuccess(anchor)
        } else {
            PitTecMockFixtures.noProvider(anchor)
        }
        attemptIndex += 1
        return scenario
    }

    private fun createAppointment(
        scenario: PitTecMockScenario,
        candidate: MockPitTecCandidate,
        estimate: ServiceEstimate,
    ): MockAppointment {
        val nowEpoch = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        val clientLocation = MockLocationSnapshot(
            scenario.clientCoordinate, nowEpoch, nowElapsed, MockLocationSource.DEVICE, revision = 1,
        )
        val providerLocation = MockLocationSnapshot(
            candidate.initialCoordinate, nowEpoch, nowElapsed, MockLocationSource.FIXTURE, revision = 1,
        )
        return MockAppointment(
            id = "mock-appointment-${scenario.id}-${candidate.id}",
            client = scenario.client,
            provider = candidate.participant,
            estimate = estimate,
            clientLocation = clientLocation,
            providerLocation = providerLocation,
            movementPath = scenario.movementPath,
            remainingDistanceMeters = providerLocation.coordinate.distanceMetersTo(clientLocation.coordinate),
            status = MockAppointmentStatus.TRAVELLING,
            acceptedAtEpochMillis = nowEpoch,
        )
    }

    private fun startMovementJobIfNeeded() {
        val tracking = _state.value.stage as? PitTecClientStage.Tracking ?: return
        if (tracking.appointment.status != MockAppointmentStatus.TRAVELLING) return
        if (movementJob?.isActive == true) return
        movementJob = viewModelScope.launch {
            while (true) {
                delay(MockLocationSnapshot.MOVEMENT_STEP_MILLIS)
                val current = (_state.value.stage as? PitTecClientStage.Tracking)?.appointment ?: return@launch
                if (current.status != MockAppointmentStatus.TRAVELLING) return@launch
                applyAppointment(current.advanceMovement(System.currentTimeMillis(), SystemClock.elapsedRealtime()))
            }
        }
    }

    private fun applyAppointment(updated: MockAppointment) {
        _state.update { current ->
            val stage = if (updated.status == MockAppointmentStatus.COMPLETED) {
                PitTecClientStage.Completed(updated)
            } else {
                PitTecClientStage.Tracking(updated)
            }
            current.copy(stage = stage)
        }
        if (updated.status == MockAppointmentStatus.COMPLETED) {
            movementJob?.cancel()
            movementJob = null
        }
    }

    private fun currentOrCompletedAppointment(): MockAppointment? = when (val stage = _state.value.stage) {
        is PitTecClientStage.Tracking -> stage.appointment
        is PitTecClientStage.Completed -> stage.appointment
        else -> null
    }

    private fun framingCommand(appointment: MockAppointment): MapViewportCommand {
        cameraRevision += 1
        return MapViewportCommand(
            framingViewport(appointment.clientLocation.coordinate, appointment.providerLocation.coordinate),
            cameraRevision,
        )
    }

    private fun decorativeMarkersAround(anchor: MapCoordinate): List<NearbyPitTecMarker> = listOf(
        NearbyPitTecMarker(
            "mock-discovery-marker-1",
            anchor.copy(latitude = anchor.latitude + 0.01, longitude = anchor.longitude + 0.006),
            250,
        ),
        NearbyPitTecMarker(
            "mock-discovery-marker-2",
            anchor.copy(latitude = anchor.latitude - 0.008, longitude = anchor.longitude + 0.012),
            250,
        ),
    )

    companion object {
        fun factory(
            serviceId: String,
            deviceLocationRepository: DeviceLocationRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                PitTecClientMockViewModel(serviceId, deviceLocationRepository) as T
        }
    }
}

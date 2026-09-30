package com.trustarx.pitgo.features.pittec.repository

import android.os.SystemClock
import com.trustarx.pitgo.core.map.model.MapCoordinate
import com.trustarx.pitgo.features.pittec.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class SimulatedPitTecFlowRepository {
    private val coordinate = MapCoordinate(-23.5505, -46.6333)
    private fun appointment(): MockAppointment {
        val now = System.currentTimeMillis()
        val location = MockLocationSnapshot(coordinate, now, SystemClock.elapsedRealtime(), MockLocationSource.FIXTURE, 1)
        return MockAppointment(UUID.randomUUID().toString(), MockParticipant("sim-client", "Cliente simulado", MockParticipantRole.CLIENT),
            MockParticipant("sim-provider", "PitTec simulado", MockParticipantRole.PITTEC), ServiceEstimate(DistancePerimeter(5)),
            location, location.copy(coordinate = MapCoordinate(-23.553, -46.636)), listOf(coordinate), remainingDistanceMeters = 500,
            status = MockAppointmentStatus.TRAVELLING, acceptedAtEpochMillis = now)
    }
    private val mutable = MutableStateFlow(appointment())
    val state = mutable.asStateFlow()
    fun advance() {
        val current = mutable.value
        mutable.value = when (current.status) {
            MockAppointmentStatus.TRAVELLING -> current.copy(status = MockAppointmentStatus.AWAITING_MEETING_CONFIRMATION, remainingDistanceMeters = 0, providerLocation = current.clientLocation)
            MockAppointmentStatus.AWAITING_MEETING_CONFIRMATION -> current.copy(status = MockAppointmentStatus.IN_PROGRESS, startedAtEpochMillis = System.currentTimeMillis())
            MockAppointmentStatus.IN_PROGRESS, MockAppointmentStatus.AWAITING_COMPLETION_CONFIRMATION -> current.copy(status = MockAppointmentStatus.COMPLETED, completedAtEpochMillis = System.currentTimeMillis())
            MockAppointmentStatus.COMPLETED -> current
        }.copy(revision = current.revision + 1)
    }
    fun reset() { mutable.value = appointment() }
}

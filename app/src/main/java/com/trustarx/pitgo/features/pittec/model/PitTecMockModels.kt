package com.trustarx.pitgo.features.pittec.model

import com.trustarx.pitgo.core.map.model.MapCoordinate
import com.trustarx.pitgo.core.map.model.MapSurfaceState
import com.trustarx.pitgo.core.map.model.MapViewport
import com.trustarx.pitgo.core.map.model.MapViewportCommand
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Local, in-memory model for the entirely mocked PitTec prototype (specs/017-pittec-mocked-ui).
 * Nothing here is persisted, serialized or sent to a backend — see the feature's "Forbidden side
 * effects" contracts. Client and provider each hold independent instances; matching fixture names
 * do not represent synchronized people.
 */

enum class MockParticipantRole { CLIENT, PITTEC }

data class MockParticipant(
    val id: String,
    val displayName: String,
    val role: MockParticipantRole,
)

/** A distance band in multiples of 5 km, the unit the mocked search/pricing expands by. */
data class DistancePerimeter(val kilometers: Int) {
    init { require(kilometers > 0 && kilometers % 5 == 0) { "kilometers must be a positive multiple of 5" } }

    val bandCount: Int get() = kilometers / 5

    fun next(): DistancePerimeter = DistancePerimeter(kilometers + 5)
}

/** Provisional cent-based pricing: R$ 50,00 fixed plus R$ 10,00 per 5 km band. */
data class ServiceEstimate(
    val perimeter: DistancePerimeter,
    val fixedCents: Long = FIXED_CENTS,
    val bandUnitCents: Long = BAND_UNIT_CENTS,
) {
    val bandCount: Int get() = perimeter.bandCount
    val distanceAdditionalCents: Long get() = bandUnitCents * bandCount
    val totalCents: Long get() = fixedCents + distanceAdditionalCents

    companion object {
        const val FIXED_CENTS = 5_000L
        const val BAND_UNIT_CENTS = 1_000L
    }
}

enum class MockLocationSource { DEVICE, FIXTURE, SIMULATED_STEP }

data class MockLocationSnapshot(
    val coordinate: MapCoordinate,
    val capturedAtEpochMillis: Long,
    val capturedAtElapsedRealtimeMillis: Long,
    val source: MockLocationSource,
    val revision: Long,
) {
    fun isStale(nowElapsedRealtimeMillis: Long): Boolean =
        nowElapsedRealtimeMillis - capturedAtElapsedRealtimeMillis > STALE_THRESHOLD_MILLIS

    companion object {
        const val STALE_THRESHOLD_MILLIS = 35_000L
        const val MOVEMENT_STEP_MILLIS = 30_000L
    }
}

enum class MockCandidateResponse { ACCEPT, DECLINE }

data class MockPitTecCandidate(
    val participant: MockParticipant,
    val perimeter: DistancePerimeter,
    val initialCoordinate: MapCoordinate,
    val response: MockCandidateResponse,
    val responseDelayMillis: Long,
) {
    val id: String get() = participant.id
}

data class PitTecMockScenario(
    val id: String,
    val client: MockParticipant,
    val clientCoordinate: MapCoordinate,
    val candidates: List<MockPitTecCandidate>,
    val movementPath: List<MapCoordinate>,
)

enum class MockSearchFeedback { SEARCHING, AWAITING_RESPONSE, DECLINED_CONTINUING, FOUND, NOT_FOUND }

data class MockSearch(
    val id: String,
    val scenarioId: String,
    val currentPerimeter: DistancePerimeter,
    val candidateIndex: Int,
    val rejectedCandidateIds: Set<String> = emptySet(),
    val estimate: ServiceEstimate,
    val feedback: MockSearchFeedback,
    val revision: Long,
    val confirmed: Boolean,
)

data class MockServiceRequest(
    val id: String,
    val client: MockParticipant,
    val clientCoordinate: MapCoordinate,
    val estimate: ServiceEstimate,
    val approximateDistanceMeters: Int,
    val createdAtEpochMillis: Long,
    val revision: Long,
    val pulseConsumed: Boolean,
)

enum class MockConfirmationKind { MEETING, COMPLETION }

data class ParticipantConfirmation(
    val kind: MockConfirmationKind,
    val localConfirmedAtEpochMillis: Long? = null,
    val counterpartConfirmedAtEpochMillis: Long? = null,
    val revision: Long = 0L,
) {
    val waitingForCounterpart: Boolean
        get() = localConfirmedAtEpochMillis != null && counterpartConfirmedAtEpochMillis == null
    val complete: Boolean
        get() = localConfirmedAtEpochMillis != null && counterpartConfirmedAtEpochMillis != null
}

enum class MockAppointmentStatus {
    TRAVELLING,
    AWAITING_MEETING_CONFIRMATION,
    IN_PROGRESS,
    AWAITING_COMPLETION_CONFIRMATION,
    COMPLETED,
}

data class MockAppointment(
    val id: String,
    val client: MockParticipant,
    val provider: MockParticipant,
    val estimate: ServiceEstimate,
    val clientLocation: MockLocationSnapshot,
    val providerLocation: MockLocationSnapshot,
    val movementPath: List<MapCoordinate>,
    val nextStepIndex: Int = 0,
    val remainingDistanceMeters: Int,
    val status: MockAppointmentStatus,
    val meetingConfirmation: ParticipantConfirmation? = null,
    val completionConfirmation: ParticipantConfirmation? = null,
    val acceptedAtEpochMillis: Long,
    val startedAtEpochMillis: Long? = null,
    val completedAtEpochMillis: Long? = null,
    val revision: Long = 1L,
)

/**
 * Pure appointment transitions shared, by value, between the independent client and provider
 * ViewModels (see contracts/mock-appointment-state.md). Sharing the math — not a coordinator or
 * any mutable state — keeps both profiles' identical rules from drifting apart without
 * synchronizing the two independent instances.
 */
fun MockAppointment.advanceMovement(nowEpochMillis: Long, nowElapsedRealtimeMillis: Long): MockAppointment {
    if (status != MockAppointmentStatus.TRAVELLING) return this
    if (movementPath.isEmpty() || nextStepIndex >= movementPath.size) return this
    val nextCoordinate = movementPath[nextStepIndex]
    val newLocation = MockLocationSnapshot(
        coordinate = nextCoordinate,
        capturedAtEpochMillis = nowEpochMillis,
        capturedAtElapsedRealtimeMillis = nowElapsedRealtimeMillis,
        source = MockLocationSource.SIMULATED_STEP,
        revision = providerLocation.revision + 1,
    )
    return copy(
        providerLocation = newLocation,
        nextStepIndex = nextStepIndex + 1,
        remainingDistanceMeters = nextCoordinate.distanceMetersTo(clientLocation.coordinate),
        revision = revision + 1,
    )
}

fun MockAppointment.confirmMeetingLocal(nowEpochMillis: Long): MockAppointment {
    if (status != MockAppointmentStatus.TRAVELLING) return this
    if (meetingConfirmation?.localConfirmedAtEpochMillis != null) return this
    return copy(
        status = MockAppointmentStatus.AWAITING_MEETING_CONFIRMATION,
        meetingConfirmation = ParticipantConfirmation(
            kind = MockConfirmationKind.MEETING,
            localConfirmedAtEpochMillis = nowEpochMillis,
            revision = (meetingConfirmation?.revision ?: 0L) + 1,
        ),
        revision = revision + 1,
    )
}

fun MockAppointment.confirmMeetingCounterpart(nowEpochMillis: Long, expectedRevision: Long): MockAppointment {
    val confirmation = meetingConfirmation ?: return this
    if (status != MockAppointmentStatus.AWAITING_MEETING_CONFIRMATION) return this
    if (confirmation.revision != expectedRevision) return this
    if (confirmation.counterpartConfirmedAtEpochMillis != null) return this
    return copy(
        status = MockAppointmentStatus.IN_PROGRESS,
        meetingConfirmation = confirmation.copy(counterpartConfirmedAtEpochMillis = nowEpochMillis),
        startedAtEpochMillis = startedAtEpochMillis ?: nowEpochMillis,
        revision = revision + 1,
    )
}

fun MockAppointment.confirmCompletionLocal(nowEpochMillis: Long): MockAppointment {
    if (status != MockAppointmentStatus.IN_PROGRESS) return this
    if (completionConfirmation?.localConfirmedAtEpochMillis != null) return this
    return copy(
        status = MockAppointmentStatus.AWAITING_COMPLETION_CONFIRMATION,
        completionConfirmation = ParticipantConfirmation(
            kind = MockConfirmationKind.COMPLETION,
            localConfirmedAtEpochMillis = nowEpochMillis,
            revision = (completionConfirmation?.revision ?: 0L) + 1,
        ),
        revision = revision + 1,
    )
}

fun MockAppointment.confirmCompletionCounterpart(nowEpochMillis: Long, expectedRevision: Long): MockAppointment {
    val confirmation = completionConfirmation ?: return this
    if (status != MockAppointmentStatus.AWAITING_COMPLETION_CONFIRMATION) return this
    if (confirmation.revision != expectedRevision) return this
    if (confirmation.counterpartConfirmedAtEpochMillis != null) return this
    return copy(
        status = MockAppointmentStatus.COMPLETED,
        completionConfirmation = confirmation.copy(counterpartConfirmedAtEpochMillis = nowEpochMillis),
        completedAtEpochMillis = nowEpochMillis,
        revision = revision + 1,
    )
}

/** Local haversine estimate in meters; never a routed distance (no route Edge Function call). */
fun MapCoordinate.distanceMetersTo(other: MapCoordinate): Int {
    if (!isValid || !other.isValid) return 0
    val earthRadiusMeters = 6_371_000.0
    val lat1 = Math.toRadians(latitude)
    val lat2 = Math.toRadians(other.latitude)
    val deltaLat = Math.toRadians(other.latitude - latitude)
    val deltaLon = Math.toRadians(other.longitude - longitude)
    val h = sin(deltaLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(deltaLon / 2).pow(2)
    return (2 * earthRadiusMeters * asin(sqrt(h))).roundToInt()
}

private fun sqrt(value: Double) = kotlin.math.sqrt(value)

/** Deterministic offset used only to place fixture coordinates around a real/anchor point. */
private fun MapCoordinate.offset(metersNorth: Double, metersEast: Double): MapCoordinate {
    val earthRadiusMeters = 6_371_000.0
    val latDelta = Math.toDegrees(metersNorth / earthRadiusMeters)
    val lonDelta = Math.toDegrees(metersEast / (earthRadiusMeters * cos(Math.toRadians(latitude))))
    return MapCoordinate(latitude + latDelta, longitude + lonDelta)
}

private fun interpolatedPath(from: MapCoordinate, to: MapCoordinate, steps: Int): List<MapCoordinate> =
    (0..steps).map { step ->
        val fraction = step.toDouble() / steps
        MapCoordinate(
            from.latitude + (to.latitude - from.latitude) * fraction,
            from.longitude + (to.longitude - from.longitude) * fraction,
        )
    }

/** Result of [PitTecMockFixtures.providerIncoming]: a ready-to-show request plus its approach path. */
data class ProviderRequestFixture(
    val request: MockServiceRequest,
    val movementPath: List<MapCoordinate>,
)

/**
 * Deterministic fixtures (research.md #4): no randomness, no JSON — reproducible scenarios so
 * SC-003/SC-004 hold every run. Coordinates are offsets from the screen's real/current anchor.
 */
object PitTecMockFixtures {
    const val PROGRESSIVE_SUCCESS_ID = "progressive-success"
    const val NO_PROVIDER_ID = "no-provider"

    /** Declines at 5 km and 10 km, accepts at 15 km — demonstrates R$ 60/70/80 and auto-expansion. */
    fun progressiveSuccess(anchor: MapCoordinate): PitTecMockScenario {
        val client = MockParticipant("mock-client", "Você", MockParticipantRole.CLIENT)
        val candidate5 = MockPitTecCandidate(
            participant = MockParticipant("mock-pittec-5km", "Marcos PitTec", MockParticipantRole.PITTEC),
            perimeter = DistancePerimeter(5),
            initialCoordinate = anchor.offset(metersNorth = 4_500.0, metersEast = 1_500.0),
            response = MockCandidateResponse.DECLINE,
            responseDelayMillis = 1_800L,
        )
        val candidate10 = MockPitTecCandidate(
            participant = MockParticipant("mock-pittec-10km", "Bruno PitTec", MockParticipantRole.PITTEC),
            perimeter = DistancePerimeter(10),
            initialCoordinate = anchor.offset(metersNorth = 8_500.0, metersEast = 4_500.0),
            response = MockCandidateResponse.DECLINE,
            responseDelayMillis = 1_800L,
        )
        val candidate15 = MockPitTecCandidate(
            participant = MockParticipant("mock-pittec-15km", "Diego PitTec", MockParticipantRole.PITTEC),
            perimeter = DistancePerimeter(15),
            initialCoordinate = anchor.offset(metersNorth = 13_000.0, metersEast = 7_000.0),
            response = MockCandidateResponse.ACCEPT,
            responseDelayMillis = 2_200L,
        )
        return PitTecMockScenario(
            id = PROGRESSIVE_SUCCESS_ID,
            client = client,
            clientCoordinate = anchor,
            candidates = listOf(candidate5, candidate10, candidate15),
            movementPath = interpolatedPath(candidate15.initialCoordinate, anchor, steps = 6),
        )
    }

    /** Every candidate declines and the fixture ends — demonstrates the empty/retry state. */
    fun noProvider(anchor: MapCoordinate): PitTecMockScenario {
        val client = MockParticipant("mock-client", "Você", MockParticipantRole.CLIENT)
        val candidate5 = MockPitTecCandidate(
            participant = MockParticipant("mock-pittec-empty-5km", "Elias PitTec", MockParticipantRole.PITTEC),
            perimeter = DistancePerimeter(5),
            initialCoordinate = anchor.offset(metersNorth = 4_000.0, metersEast = -2_000.0),
            response = MockCandidateResponse.DECLINE,
            responseDelayMillis = 1_500L,
        )
        val candidate10 = MockPitTecCandidate(
            participant = MockParticipant("mock-pittec-empty-10km", "Fábio PitTec", MockParticipantRole.PITTEC),
            perimeter = DistancePerimeter(10),
            initialCoordinate = anchor.offset(metersNorth = 8_000.0, metersEast = -4_500.0),
            response = MockCandidateResponse.DECLINE,
            responseDelayMillis = 1_500L,
        )
        return PitTecMockScenario(
            id = NO_PROVIDER_ID,
            client = client,
            clientCoordinate = anchor,
            candidates = listOf(candidate5, candidate10),
            movementPath = emptyList(),
        )
    }

    /** Single 10 km/R$ 70,00 request used only by the independent provider Home flow. */
    fun providerIncoming(
        anchor: MapCoordinate,
        requestId: String,
        revision: Long,
        nowEpochMillis: Long,
    ): ProviderRequestFixture {
        val client = MockParticipant("mock-client-incoming", "Ana Cliente", MockParticipantRole.CLIENT)
        val clientCoordinate = anchor.offset(metersNorth = 6_500.0, metersEast = 3_500.0)
        val estimate = ServiceEstimate(DistancePerimeter(10))
        val request = MockServiceRequest(
            id = requestId,
            client = client,
            clientCoordinate = clientCoordinate,
            estimate = estimate,
            approximateDistanceMeters = anchor.distanceMetersTo(clientCoordinate),
            createdAtEpochMillis = nowEpochMillis,
            revision = revision,
            pulseConsumed = false,
        )
        return ProviderRequestFixture(
            request = request,
            movementPath = interpolatedPath(anchor, clientCoordinate, steps = 6),
        )
    }
}

// --- UI state roots (data-model.md "UI state roots") ---

sealed interface PitTecClientStage {
    data object Discovery : PitTecClientStage
    data object Estimating : PitTecClientStage
    data class AwaitingClientConfirmation(val estimate: ServiceEstimate) : PitTecClientStage
    data class Searching(val search: MockSearch) : PitTecClientStage
    data class Tracking(val appointment: MockAppointment) : PitTecClientStage
    data class Completed(val appointment: MockAppointment) : PitTecClientStage
    data class NoProvider(val lastSearch: MockSearch) : PitTecClientStage
}

data class PitTecClientMockUiState(
    val serviceId: String,
    val viewport: MapViewport = MapViewport(MapCoordinate(-14.235, -51.9253), 4.0),
    val cameraCommand: MapViewportCommand? = null,
    val clientCoordinate: MapCoordinate? = null,
    val locationState: PitTecLocationState = PitTecLocationState.NotRequested,
    val markers: List<NearbyPitTecMarker> = emptyList(),
    val surfaceState: MapSurfaceState = MapSurfaceState(),
    val styleReloadRevision: Long = 0L,
    val stage: PitTecClientStage = PitTecClientStage.Discovery,
)

sealed interface ProviderLocationGate {
    data object CheckingPermission : ProviderLocationGate
    data object PermissionRequired : ProviderLocationGate
    data object ApproximateOnly : ProviderLocationGate
    data object ServicesDisabled : ProviderLocationGate
    data object Locating : ProviderLocationGate
    data class Ready(val deviceLocation: MockLocationSnapshot) : ProviderLocationGate
    data object Unavailable : ProviderLocationGate
}

enum class PitTecBottomTab { MAP, RIDE_DATA, VALUES, PROFILE }

sealed interface PitTecProviderStage {
    data object Available : PitTecProviderStage
    data class IncomingRequest(val request: MockServiceRequest) : PitTecProviderStage
    data class Tracking(val appointment: MockAppointment) : PitTecProviderStage
    data class Completed(val appointment: MockAppointment) : PitTecProviderStage
}

data class PitTecProviderMockUiState(
    val provider: MockParticipant,
    val selectedTab: PitTecBottomTab = PitTecBottomTab.MAP,
    val locationGate: ProviderLocationGate = ProviderLocationGate.CheckingPermission,
    val cameraCommand: MapViewportCommand? = null,
    val viewport: MapViewport = MapViewport(MapCoordinate(-14.235, -51.9253), 4.0),
    val surfaceState: MapSurfaceState = MapSurfaceState(),
    val styleReloadRevision: Long = 0L,
    val currentOrLastAppointment: MockAppointment? = null,
    val stage: PitTecProviderStage = PitTecProviderStage.Available,
)

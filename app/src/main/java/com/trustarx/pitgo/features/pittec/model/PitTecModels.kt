package com.trustarx.pitgo.features.pittec.model

import com.trustarx.pitgo.core.location.model.DeviceLocationFix
import com.trustarx.pitgo.core.map.model.MapCoordinate
import com.trustarx.pitgo.core.map.model.MapMarkerKind
import com.trustarx.pitgo.core.map.model.MapMarkerSpec
import com.trustarx.pitgo.core.map.model.MapSurfaceState
import com.trustarx.pitgo.core.map.model.MapViewport
import com.trustarx.pitgo.core.map.model.MapViewportCommand
import com.trustarx.pitgo.features.payment.model.PaymentStatus

/** One anonymized, server-approximated PitTec position on the pre-request discovery map. */
data class NearbyPitTecMarker(
    val markerId: String,
    val coordinate: MapCoordinate,
    val accuracyRadiusMeters: Int,
) {
    fun isValid() = markerId.isNotBlank() && coordinate.isValid && accuracyRadiusMeters > 0
}

sealed interface PitTecLocationState {
    data object NotRequested : PitTecLocationState
    data object Resolving : PitTecLocationState
    data object InUse : PitTecLocationState
    data object Denied : PitTecLocationState
    data object Unavailable : PitTecLocationState
}

sealed interface PitTecDiscoveryState {
    data object Loading : PitTecDiscoveryState
    data object Ready : PitTecDiscoveryState
    data object Empty : PitTecDiscoveryState
    data object Offline : PitTecDiscoveryState
    data object Failure : PitTecDiscoveryState
    data object SessionExpired : PitTecDiscoveryState
    data object UpdateRequired : PitTecDiscoveryState
}

sealed interface PitTecDiscoveryResult {
    data class Success(val markers: List<NearbyPitTecMarker>) : PitTecDiscoveryResult
    data object InvalidInput : PitTecDiscoveryResult
    data object Offline : PitTecDiscoveryResult
    data object Timeout : PitTecDiscoveryResult
    data object SessionExpired : PitTecDiscoveryResult
    data object ServiceUnavailable : PitTecDiscoveryResult
    data object UnsupportedDispatch : PitTecDiscoveryResult
    data object Failure : PitTecDiscoveryResult
}

/** Mirrors the shared `service_searches`/`appointments` status enums PitTec reuses server-side. */
enum class PitTecDispatchStatus { SEARCHING, AWAITING_PROVIDER, ACCEPTED, IN_PROGRESS, CANCELLED, COMPLETED, EXHAUSTED }

enum class PitTecAppointmentStatus { PENDING, ACCEPTED, IN_PROGRESS, COMPLETED, CANCELLED, PROVIDER_REJECTED, EXPIRED }

enum class PitTecDispatchAction { CANCEL, ACCEPT, DECLINE, ARRIVE, COMPLETE }

enum class PitTecLastEvent { PROVIDER_REJECTED, OFFER_EXPIRED, PROVIDER_CANCELLED }

/** Approximate, decision-safe offer metadata shown only to the currently offered provider. */
data class PitTecOffer(
    val approximateDistanceMeters: Int,
    val region: String,
) {
    fun isValid() = approximateDistanceMeters >= 0 && region.isNotBlank()
}

data class PitTecProvider(
    val providerId: String,
    val displayName: String,
) {
    fun isValid() = providerId.isUuid() && displayName.isNotBlank()
}

/**
 * Candidato ainda não aceito, visível só ao cliente enquanto `appointment.status == PENDING`
 * (`019-pittec-request-flow`) — espelha `PitTecOffer` (que mostra a mesma distância ao prestador),
 * nunca coexiste com `PitTecProvider` no mesmo snapshot (mutuamente exclusivos por status).
 */
data class PitTecCandidate(
    val providerName: String,
    val approximateDistanceMeters: Int,
) {
    fun isValid() = providerName.isNotBlank() && approximateDistanceMeters >= 0
}

data class PitTecAppointment(
    val id: String,
    val status: PitTecAppointmentStatus,
    val responseDeadlineEpochMillis: Long?,
    val revision: Long,
    val acceptedAtEpochMillis: Long?,
    val arrivedAtEpochMillis: Long?,
    val cancelledBy: String?,
) {
    fun isValid() = id.isUuid() && revision > 0
}

data class PitTecDispatchSnapshot(
    val searchId: String,
    val serviceId: String,
    val serviceName: String,
    val status: PitTecDispatchStatus,
    val radiusMeters: Int,
    val revision: Long,
    val serverNowEpochMillis: Long,
    val lastEvent: PitTecLastEvent?,
    val lastEventAtEpochMillis: Long?,
    val allowedActions: Set<PitTecDispatchAction>,
    val offer: PitTecOffer?,
    val candidate: PitTecCandidate?,
    val provider: PitTecProvider?,
    val appointment: PitTecAppointment?,
) {
    fun isValid() = searchId.isUuid() && serviceId.isUuid() && serviceName.isNotBlank() &&
        radiusMeters in setOf(10_000, 25_000, 50_000) && revision > 0 && serverNowEpochMillis > 0 &&
        offer?.isValid() != false && candidate?.isValid() != false &&
        provider?.isValid() != false && appointment?.isValid() != false
}

enum class PitTecFailure {
    LOCATION_REQUIRED, LOCATION_UNAVAILABLE, OFFLINE, TIMEOUT, SESSION_EXPIRED,
    SERVICE_UNAVAILABLE, UNSUPPORTED_DISPATCH, CONTRACT, UNKNOWN,
}

sealed interface PitTecDispatchResult {
    data class Success(val snapshot: PitTecDispatchSnapshot) : PitTecDispatchResult
    data object None : PitTecDispatchResult
    data class Failure(val reason: PitTecFailure) : PitTecDispatchResult
}

sealed interface PitTecDispatchEvent {
    data class Snapshot(val value: PitTecDispatchSnapshot?) : PitTecDispatchEvent
    data class Failure(val reason: PitTecFailure) : PitTecDispatchEvent
}

enum class PitTecPendingAction { START, CANCEL }

data class PitTecPending(val kind: PitTecPendingAction, val operationId: Long)

/**
 * Pre-accept dispatch overlay shown on top of the discovery map. Once the appointment's payment is
 * confirmed (`ConfirmingValues` resolved) the screen navigates away to the shared
 * AppointmentTracking flow (US3), so this phase never needs to model ACCEPTED/IN_PROGRESS beyond
 * that one transitional card (`019-pittec-request-flow`).
 */
sealed interface PitTecDispatchPhase {
    data object None : PitTecDispatchPhase
    data object Restoring : PitTecDispatchPhase
    data object Locating : PitTecDispatchPhase
    data object StartingSearch : PitTecDispatchPhase
    data class Active(
        val snapshot: PitTecDispatchSnapshot,
        val pending: PitTecPending? = null,
        val failure: PitTecFailure? = null,
        val remainingSeconds: Long? = null,
    ) : PitTecDispatchPhase

    /**
     * `AWAITING_PROVIDER` com um candidato específico (`snapshot.candidate` não nulo) — o cliente vê
     * nome e distância e pode confirmar (continua aguardando o prestador) ou recusar (cancela este
     * appointment; a busca segue para o próximo candidato). Nenhuma cobrança acontece aqui.
     */
    data class CandidateFound(
        val snapshot: PitTecDispatchSnapshot,
        val pending: PitTecPending? = null,
    ) : PitTecDispatchPhase

    /**
     * `ACCEPTED` — sinaliza que a tela de pagamento real (`018`, reaproveitada sem alteração) deve
     * ser aberta antes de prosseguir para o acompanhamento. `snapshot.provider` já está preenchido
     * aqui. `paymentApproved` só vira `true` quando `PitTecViewModel` observa
     * `payments.status = APPROVED` para este `search_id` — é o gatilho para `onAccepted(...)`.
     */
    data class ConfirmingValues(
        val snapshot: PitTecDispatchSnapshot,
        val paymentApproved: Boolean = false,
    ) : PitTecDispatchPhase

    data class NoProvider(val serviceName: String) : PitTecDispatchPhase
    data class Terminal(val status: PitTecDispatchStatus) : PitTecDispatchPhase
    data class Failure(val reason: PitTecFailure) : PitTecDispatchPhase
}

sealed interface PitTecUiState {
    data class Content(
        val serviceId: String,
        val viewport: MapViewport = MapViewport(MapCoordinate(-14.235, -51.9253), 4.0),
        val cameraCommand: MapViewportCommand? = null,
        val clientCoordinate: MapCoordinate? = null,
        val locationState: PitTecLocationState = PitTecLocationState.NotRequested,
        val discoveryState: PitTecDiscoveryState = PitTecDiscoveryState.Loading,
        val markers: List<NearbyPitTecMarker> = emptyList(),
        val surfaceState: MapSurfaceState = MapSurfaceState(),
        val styleReloadRevision: Long = 0L,
        val dispatch: PitTecDispatchPhase = PitTecDispatchPhase.Restoring,
        // Set when a payment approved for this call moves to REFUND_PENDING/REFUNDED/
        // REFUND_FAILED (spec 018, FR-015/FR-017/FR-018) — surfaced as a banner by the screen.
        val refundNotice: PaymentStatus? = null,
    ) : PitTecUiState {
        val mapMarkers: List<MapMarkerSpec>
            get() {
                val approximate = markers.map { marker ->
                    MapMarkerSpec(
                        id = marker.markerId,
                        coordinate = marker.coordinate,
                        kind = MapMarkerKind.PITTEC_APPROXIMATE,
                        accessibilityLabel = "PitTec disponível em posição aproximada",
                    )
                }
                val client = clientCoordinate?.takeIf(MapCoordinate::isValid)?.let { coordinate ->
                    MapMarkerSpec(
                        id = CLIENT_MARKER_ID,
                        coordinate = coordinate,
                        kind = MapMarkerKind.CLIENT_LOCATION,
                        accessibilityLabel = "Sua posição atual",
                    )
                }
                return approximate + listOfNotNull(client)
            }

        private companion object {
            const val CLIENT_MARKER_ID = "pittec-client-location"
        }
    }

    data object InvalidService : PitTecUiState
}

data class PitTecStartCommand(
    val requestId: String,
    val serviceId: String,
    val fix: DeviceLocationFix,
)

internal fun String.isUuid(): Boolean = runCatching {
    java.util.UUID.fromString(this).toString().equals(this, ignoreCase = true)
}.getOrDefault(false)

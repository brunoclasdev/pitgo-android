package com.trustarx.pitgo.features.pittec.model

import com.trustarx.pitgo.core.location.model.DeviceLocationFix
import com.trustarx.pitgo.core.map.model.MapCoordinate
import com.trustarx.pitgo.core.map.model.MapMarkerKind
import com.trustarx.pitgo.core.map.model.MapMarkerSpec
import com.trustarx.pitgo.core.map.model.MapSurfaceState
import com.trustarx.pitgo.core.map.model.MapViewport
import com.trustarx.pitgo.core.map.model.MapViewportCommand
import com.trustarx.pitgo.features.payment.model.PaymentStatus
import java.math.BigDecimal

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
enum class PitTecDispatchStatus {
    SEARCHING, AWAITING_PROVIDER, AWAITING_CLIENT_CONFIRMATION, AWAITING_PAYMENT,
    ACCEPTED, IN_PROGRESS, CANCELLED, COMPLETED, EXHAUSTED,
}

enum class PitTecAppointmentStatus {
    PENDING, AWAITING_CLIENT_CONFIRMATION, AWAITING_PAYMENT,
    ACCEPTED, IN_PROGRESS, COMPLETED, CANCELLED, PROVIDER_REJECTED, EXPIRED,
}

enum class PitTecDispatchAction {
    CANCEL, ACCEPT, DECLINE, CONFIRM_QUOTE, DECLINE_QUOTE, OPEN_PAYMENT, OPEN_TRACKING,
    ARRIVE, COMPLETE,
}

enum class PitTecLastEvent {
    PROVIDER_REJECTED, OFFER_EXPIRED, PROVIDER_CANCELLED, SEARCH_CONTINUES,
    CLIENT_CONFIRMATION_TIMEOUT, QUOTE_DECLINED,
}

data class PitTecQuote(
    val amount: BigDecimal,
    val currency: String,
    val version: Long,
    val clientConfirmationDeadlineEpochMillis: Long?,
    val clientConfirmedAtEpochMillis: Long?,
    val providerAmount: BigDecimal? = null,
    val markupAmount: BigDecimal? = null,
    val customerFeeAmount: BigDecimal? = null,
    val customerTotalAmount: BigDecimal? = null,
    val expiresAtEpochMillis: Long? = null,
) {
    fun isValid() = amount.signum() > 0 && currency == "BRL" && version > 0
}

data class PricingBreakdown(
    val markupAmount: BigDecimal,
    val customerFeeAmount: BigDecimal,
    val customerTotalAmount: BigDecimal,
    val currency: String,
    val version: Long,
    val expiresAtEpochMillis: Long?,
)

/** Approximate, decision-safe offer metadata shown only to the currently offered provider. */
data class PitTecOffer(
    val approximateDistanceMeters: Int,
    val region: String,
    val quote: PitTecQuote,
) {
    fun isValid() = approximateDistanceMeters >= 0 && region.isNotBlank() && quote.isValid()
}

data class PitTecProvider(
    val providerId: String,
    val displayName: String,
) {
    fun isValid() = providerId.isUuid() && displayName.isNotBlank()
}

data class PitTecAppointment(
    val id: String,
    val status: PitTecAppointmentStatus,
    val responseDeadlineEpochMillis: Long?,
    val revision: Long,
    val acceptedAtEpochMillis: Long?,
    val arrivedAtEpochMillis: Long?,
    val cancelledBy: String?,
    val quote: PitTecQuote?,
) {
    fun isValid() = id.isUuid() && revision > 0 && quote?.isValid() != false
}

data class PitTecDispatchSnapshot(
    val searchId: String,
    val serviceId: String,
    val serviceName: String,
    val status: PitTecDispatchStatus,
    val radiusMeters: Int,
    val revision: Long,
    val serverNowEpochMillis: Long,
    val nextDispatchAtEpochMillis: Long?,
    val lastEvent: PitTecLastEvent?,
    val lastEventAtEpochMillis: Long?,
    val allowedActions: Set<PitTecDispatchAction>,
    val offer: PitTecOffer?,
    val provider: PitTecProvider?,
    val appointment: PitTecAppointment?,
    val paymentStatus: PaymentStatus?,
    val trackingAuthorized: Boolean,
) {
    fun isValid() = searchId.isUuid() && serviceId.isUuid() && serviceName.isNotBlank() &&
        radiusMeters in setOf(10_000, 25_000, 50_000) && revision > 0 && serverNowEpochMillis > 0 &&
        offer?.isValid() != false &&
        provider?.isValid() != false && appointment?.isValid() != false
}

enum class PitTecFailure {
    LOCATION_REQUIRED, LOCATION_UNAVAILABLE, OFFLINE, TIMEOUT, SESSION_EXPIRED,
    SERVICE_UNAVAILABLE, UNSUPPORTED_DISPATCH, CONFIRMATION_EXPIRED, REVISION_CONFLICT,
    INVALID_TRANSITION, CONTRACT, UNKNOWN,
}

enum class PitTecQuoteOutcome {
    CONFIRMED, DECLINED, CONFIRMATION_EXPIRED, REVISION_CONFLICT, INVALID_TRANSITION, NOT_FOUND,
}

sealed interface PitTecQuoteResult {
    data class Success(
        val outcome: PitTecQuoteOutcome,
        val snapshot: PitTecDispatchSnapshot?,
    ) : PitTecQuoteResult

    data class Failure(val reason: PitTecFailure) : PitTecQuoteResult
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

enum class PitTecPendingAction { START, CANCEL, CONFIRM_QUOTE, DECLINE_QUOTE }

data class PitTecPending(val kind: PitTecPendingAction, val operationId: Long)

/** Server-derived, mutually exclusive client phases for the complete PitTec call flow. */
sealed interface PitTecDispatchPhase {
    data object None : PitTecDispatchPhase
    data object Restoring : PitTecDispatchPhase
    data object Locating : PitTecDispatchPhase
    data object StartingSearch : PitTecDispatchPhase
    data class Searching(
        val snapshot: PitTecDispatchSnapshot,
        val pending: PitTecPending? = null,
        val failure: PitTecFailure? = null,
        val remainingSeconds: Long? = null,
    ) : PitTecDispatchPhase

    data class AwaitingClientConfirmation(
        val snapshot: PitTecDispatchSnapshot,
        val pending: PitTecPending? = null,
        val failure: PitTecFailure? = null,
        val remainingSeconds: Long,
    ) : PitTecDispatchPhase

    data class AwaitingPayment(
        val snapshot: PitTecDispatchSnapshot,
        val failure: PitTecFailure? = null,
    ) : PitTecDispatchPhase

    data class TrackingReady(val snapshot: PitTecDispatchSnapshot) : PitTecDispatchPhase
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

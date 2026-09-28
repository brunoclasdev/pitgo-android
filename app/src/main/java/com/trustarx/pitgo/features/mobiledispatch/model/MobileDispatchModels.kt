package com.trustarx.pitgo.features.mobiledispatch.model

import com.trustarx.pitgo.core.location.model.DeviceLocationFix
import com.trustarx.pitgo.core.map.model.MapCoordinate
import com.trustarx.pitgo.core.map.model.MapMarkerKind
import com.trustarx.pitgo.core.map.model.MapMarkerSpec
import com.trustarx.pitgo.core.map.model.MapViewport
import com.trustarx.pitgo.core.map.model.MapViewportCommand
import com.trustarx.pitgo.features.payment.model.PaymentStatus
import java.math.BigDecimal

enum class MobileDispatchStatus {
    SEARCHING, OPTION_FOUND, AWAITING_PROVIDER, AWAITING_CLIENT_CONFIRMATION, AWAITING_PAYMENT,
    ACCEPTED, IN_PROGRESS,
    CANCELLED, COMPLETED, EXHAUSTED,
}

/**
 * Provider-side offers can come from either dispatch kind through this shared transport (see
 * PitTecRepository for the client-only, dedicated PitTec contract). Defaults to MOBILE_SEQUENTIAL
 * so legacy `private.dispatch_snapshot()` responses — which never include this field — keep
 * working unchanged.
 */
enum class MobileDispatchKind { MOBILE_SEQUENTIAL, PITTEC_AUTO_SEQUENTIAL }

/** Approximate, decision-safe offer metadata PitTec exposes to the currently offered provider. */
data class PitTecOfferSummary(
    val approximateDistanceMeters: Int,
    val region: String,
    val quotedPrice: BigDecimal,
    val currency: String,
    val quoteVersion: Long,
) {
    fun isValid() = approximateDistanceMeters >= 0 && region.isNotBlank() &&
        quotedPrice.signum() > 0 && currency == "BRL" && quoteVersion > 0
}

enum class MobileAppointmentStatus {
    PENDING, AWAITING_CLIENT_CONFIRMATION, AWAITING_PAYMENT,
    ACCEPTED, IN_PROGRESS, COMPLETED, CANCELLED, PROVIDER_REJECTED, EXPIRED,
}

enum class MobileDispatchAction {
    NEXT, REQUEST, CANCEL, ACCEPT, DECLINE, CONFIRM_QUOTE, DECLINE_QUOTE,
    OPEN_PAYMENT, OPEN_TRACKING, START, ARRIVE, COMPLETE,
}

data class MobileCandidate(
    val id: String,
    val providerId: String,
    val providerName: String,
    val locality: String,
    val coordinate: MapCoordinate,
    val distanceMeters: Double,
    val price: BigDecimal,
    val isFree: Boolean,
) {
    fun isValid() = id.isUuid() && providerId.isUuid() && providerName.isNotBlank() &&
        locality.isNotBlank() && coordinate.isValid && distanceMeters.isFinite() &&
        distanceMeters >= 0 && price.signum() >= 0
}

data class MobileAppointment(
    val id: String,
    val status: MobileAppointmentStatus,
    val quotedPrice: BigDecimal,
    val isFree: Boolean,
    val responseDeadlineEpochMillis: Long?,
    val revision: Long,
    val cancelledBy: String?,
    val quoteVersion: Long = 1,
    val clientConfirmationDeadlineEpochMillis: Long? = null,
    val clientConfirmedAtEpochMillis: Long? = null,
    val arrivedAt: String? = null,
    val serviceStartedAt: String? = null,
    val clientStartAcknowledgedAt: String? = null,
    val serviceAmount: BigDecimal? = null,
    val distanceChargeAmount: BigDecimal? = null,
) {
    fun isValid() = id.isUuid() && quotedPrice.signum() >= 0 && revision > 0 &&
        (responseDeadlineEpochMillis == null || responseDeadlineEpochMillis > 0)
}

data class MobileDispatchSnapshot(
    val searchId: String,
    val serviceId: String,
    val serviceName: String,
    val status: MobileDispatchStatus,
    val radiusMeters: Int,
    val revision: Long,
    val serverNowEpochMillis: Long,
    val allowedActions: Set<MobileDispatchAction>,
    val candidate: MobileCandidate?,
    val appointment: MobileAppointment?,
    val dispatchKind: MobileDispatchKind = MobileDispatchKind.MOBILE_SEQUENTIAL,
    val offer: PitTecOfferSummary? = null,
    val paymentStatus: PaymentStatus? = null,
    val trackingAuthorized: Boolean = false,
) {
    fun isValid() = searchId.isUuid() && serviceId.isUuid() && serviceName.isNotBlank() &&
        radiusMeters in setOf(10_000, 25_000, 50_000) && revision > 0 &&
        serverNowEpochMillis > 0 && candidate?.isValid() != false && appointment?.isValid() != false &&
        offer?.isValid() != false
}

enum class MobileDispatchFailure {
    LOCATION_REQUIRED, LOCATION_UNAVAILABLE, OFFLINE, TIMEOUT, SESSION_EXPIRED,
    SERVICE_UNAVAILABLE, PRICING_UNAVAILABLE, CONTRACT, UNKNOWN,
}

sealed interface MobileDispatchResult {
    data class Success(val snapshot: MobileDispatchSnapshot) : MobileDispatchResult
    data object None : MobileDispatchResult
    data class Failure(val reason: MobileDispatchFailure) : MobileDispatchResult
}

sealed interface MobileDispatchEvent {
    data class Snapshot(val value: MobileDispatchSnapshot?) : MobileDispatchEvent
    data class Failure(val reason: MobileDispatchFailure) : MobileDispatchEvent
}

enum class PendingDispatchAction { START, NEXT, REQUEST, ACCEPT, DECLINE, CANCEL, COMPLETE }

data class DispatchPendingAction(
    val kind: PendingDispatchAction,
    val operationId: Long,
)

sealed interface ClientMobileDispatchUiState {
    data object Restoring : ClientMobileDispatchUiState
    data object Locating : ClientMobileDispatchUiState
    data object StartingSearch : ClientMobileDispatchUiState
    data class LocationRequired(val failure: MobileDispatchFailure? = null) : ClientMobileDispatchUiState
    data class Content(
        val snapshot: MobileDispatchSnapshot,
        val pending: DispatchPendingAction? = null,
        val failure: MobileDispatchFailure? = null,
        val remainingSeconds: Long? = null,
        // Set when a payment approved for this search moves to REFUND_PENDING/REFUNDED/
        // REFUND_FAILED (spec 018, FR-015/FR-017/FR-018) — surfaced as a banner by the screen.
        val refundNotice: PaymentStatus? = null,
    ) : ClientMobileDispatchUiState
    data class Empty(val serviceName: String) : ClientMobileDispatchUiState
    data class Terminal(val status: MobileDispatchStatus) : ClientMobileDispatchUiState
    data class Failure(val reason: MobileDispatchFailure) : ClientMobileDispatchUiState
    data object SessionExpired : ClientMobileDispatchUiState
}

sealed interface ProviderIncomingUiState {
    data object Loading : ProviderIncomingUiState
    data object None : ProviderIncomingUiState
    data class Request(
        val snapshot: MobileDispatchSnapshot,
        val pending: DispatchPendingAction? = null,
        val remainingSeconds: Long? = null,
        val failure: MobileDispatchFailure? = null,
    ) : ProviderIncomingUiState
    data class Active(
        val snapshot: MobileDispatchSnapshot,
        val pending: DispatchPendingAction? = null,
    ) : ProviderIncomingUiState
    data class Failure(val reason: MobileDispatchFailure) : ProviderIncomingUiState
    data object SessionExpired : ProviderIncomingUiState
}

/**
 * Map state for the provider's own accepted-appointment screen (US3): the provider's own live
 * position and the client's one-shot fix, both read from the same private tracking channel/RPCs
 * already used by the client's AppointmentTrackingRepository — no new backend surface for this.
 */
data class ProviderMapState(
    val providerLocation: MapCoordinate? = null,
    val clientLocation: MapCoordinate? = null,
    val viewport: MapViewport = MapViewport(MapCoordinate(-14.235, -51.9253), 4.0),
    val cameraCommand: MapViewportCommand? = null,
    val followEnabled: Boolean = true,
) {
    val markers: List<MapMarkerSpec>
        get() = buildList {
            providerLocation?.takeIf(MapCoordinate::isValid)?.let {
                add(
                    MapMarkerSpec(
                        id = "provider-self",
                        coordinate = it,
                        kind = MapMarkerKind.LIVE,
                        accessibilityLabel = "PitTec — sua posição",
                    )
                )
            }
            clientLocation?.takeIf(MapCoordinate::isValid)?.let {
                add(
                    MapMarkerSpec(
                        id = "provider-client",
                        coordinate = it,
                        kind = MapMarkerKind.CLIENT_LOCATION,
                        accessibilityLabel = "Local do atendimento",
                    )
                )
            }
        }
}

data class MobileDispatchStartCommand(
    val searchId: String,
    val serviceId: String,
    val fix: DeviceLocationFix,
)

internal fun String.isUuid(): Boolean = runCatching {
    java.util.UUID.fromString(this).toString().equals(this, ignoreCase = true)
}.getOrDefault(false)

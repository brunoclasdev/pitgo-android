package com.trustarx.pitgo.features.guincho.model

import com.trustarx.pitgo.core.map.model.MapCoordinate
import java.math.BigDecimal

data class GuinchoTripInput(
    val vehicleId: String,
    val pickupAddress: String,
    val pickup: MapCoordinate,
    val destinationAddress: String,
    val destination: MapCoordinate,
) {
    fun isValid() = runCatching { java.util.UUID.fromString(vehicleId) }.isSuccess &&
        pickupAddress.trim().length in 1..500 && destinationAddress.trim().length in 1..500 &&
        pickup.isValid && destination.isValid
}

data class GuinchoDraft(val id: String, val revision: Long)
data class GuinchoProvider(
    val id: String,
    val offerId: String,
    val name: String,
    val coordinate: MapCoordinate,
    val distanceMeters: Double,
    val ratingSummary: com.trustarx.pitgo.features.rating.model.ProviderRatingSummary? = null,
)
data class GuinchoQuote(
    val id: String,
    val version: Long,
    val providerId: String,
    val providerName: String,
    val total: BigDecimal,
    val expiresAtEpochMillis: Long,
    val durationMinutes: Int? = null,
    val pricingRuleVersion: Long? = null,
)
enum class GuinchoDisputeStatus { OPEN, RESOLVED }
enum class GuinchoDisputeResolution { COMPLETE, CANCEL_FULL_REFUND }
data class GuinchoDispute(
    val id: String,
    val reason: String,
    val status: GuinchoDisputeStatus,
    val resolution: GuinchoDisputeResolution?,
    val justification: String?,
)
data class GuinchoTripSummary(
    val clientName: String,
    val vehicle: String,
    val pickupAddress: String,
    val destinationAddress: String,
    val pickup: MapCoordinate,
    val destination: MapCoordinate,
)
enum class GuinchoFailure {
    INVALID_INPUT, SESSION_EXPIRED, OFFLINE, TIMEOUT, ROUTE_UNAVAILABLE,
    PROVIDER_UNAVAILABLE, QUOTE_EXPIRED, QUOTE_CONFLICT, PRICING_UNAVAILABLE,
    SERVICE_UNAVAILABLE, CONTRACT, UNKNOWN,
}
sealed interface GuinchoResult<out T> {
    data class Success<T>(val value: T) : GuinchoResult<T>
    data class Failure(val reason: GuinchoFailure) : GuinchoResult<Nothing>
}

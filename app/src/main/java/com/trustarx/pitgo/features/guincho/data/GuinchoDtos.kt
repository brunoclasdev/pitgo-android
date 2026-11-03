package com.trustarx.pitgo.features.guincho.data

import com.trustarx.pitgo.core.common.time.parseSupabaseInstant
import com.trustarx.pitgo.core.map.model.MapCoordinate
import com.trustarx.pitgo.features.guincho.model.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigDecimal

@Serializable
internal data class GuinchoDraftDto(@SerialName("draft_id") val id: String, val revision: Long) {
    fun domain() = GuinchoDraft(id, revision).also {
        java.util.UUID.fromString(id)
        require(revision > 0)
    }
}
@Serializable
internal data class GuinchoProviderDto(
    @SerialName("provider_id") val id: String,
    @SerialName("provider_service_id") val offerId: String,
    @SerialName("display_name") val name: String,
    val latitude: Double,
    val longitude: Double,
    @SerialName("distance_meters") val distanceMeters: Double,
    @SerialName("dispatch_vehicle_id") val dispatchVehicleId: String? = null,
    @SerialName("size_id") val sizeId: String? = null,
    @SerialName("size_name") val sizeName: String? = null,
    @SerialName("rating_summary") val ratingSummary: com.trustarx.pitgo.features.rating.model.ProviderRatingSummary? = null,
) {
    fun domain() = GuinchoProvider(id, offerId, name, MapCoordinate(latitude, longitude), distanceMeters, dispatchVehicleId,sizeId,sizeName,ratingSummary?.validate()).also {
        java.util.UUID.fromString(id)
        java.util.UUID.fromString(offerId)
        dispatchVehicleId?.let { java.util.UUID.fromString(it) }
        sizeId?.let { java.util.UUID.fromString(it) }
        require(sizeName==null || sizeName.isNotBlank())
        require(name.isNotBlank() && it.coordinate.isValid && distanceMeters.isFinite() && distanceMeters >= 0)
    }
}
@Serializable
internal data class GuinchoQuoteDto(
    @SerialName("quote_id") val id: String,
    val version: Long,
    @SerialName("provider_id") val providerId: String,
    @SerialName("provider_name") val providerName: String,
    @SerialName("customer_total_amount") val total: JsonElement,
    val currency: String,
    @SerialName("expires_at") val expiresAt: String,
    @SerialName("duration_minutes") val durationMinutes: Int? = null,
    @SerialName("pricing_rule_version") val pricingRuleVersion: Long? = null,
    @SerialName("dispatch_vehicle_id") val dispatchVehicleId: String? = null,
    @SerialName("size_id") val sizeId: String? = null,
    @SerialName("size_name") val sizeName: String? = null,
    @SerialName("size_price_version") val sizePriceVersion: Long? = null,
) {
    fun domain() = GuinchoQuote(id, version, providerId, providerName,
        BigDecimal(total.jsonPrimitive.content), parseSupabaseInstant(expiresAt).toEpochMilli(), durationMinutes, pricingRuleVersion,dispatchVehicleId,sizeId,sizeName,sizePriceVersion).also {
        java.util.UUID.fromString(id)
        java.util.UUID.fromString(providerId)
        dispatchVehicleId?.let { java.util.UUID.fromString(it) }
        sizeId?.let { java.util.UUID.fromString(it) }
        require(sizePriceVersion==null || sizePriceVersion>0)
        require(sizeName==null || sizeName.isNotBlank())
        require(version > 0 && currency == "BRL" && it.total.signum() > 0 && providerName.isNotBlank())
    }
}

@Serializable
internal data class GuinchoTripDto(
    @SerialName("client_name") val clientName: String,
    val vehicle: GuinchoVehicleDto,
    @SerialName("pickup_address") val pickupAddress: String,
    @SerialName("destination_address") val destinationAddress: String,
    @SerialName("pickup_latitude") val pickupLatitude: Double,
    @SerialName("pickup_longitude") val pickupLongitude: Double,
    @SerialName("destination_latitude") val destinationLatitude: Double,
    @SerialName("destination_longitude") val destinationLongitude: Double,
) {
    fun summary() = GuinchoTripSummary(clientName,
        listOfNotNull(vehicle.brand, vehicle.model, vehicle.plate, vehicle.color).joinToString(" · "),
        pickupAddress, destinationAddress, MapCoordinate(pickupLatitude,pickupLongitude),
        MapCoordinate(destinationLatitude,destinationLongitude)).also {
            require(it.pickup.isValid && it.destination.isValid && it.clientName.isNotBlank())
        }
}
@Serializable
internal data class GuinchoVehicleDto(
    val brand: String, val model: String, val plate: String? = null, val color: String? = null,
)
@Serializable
internal data class GuinchoDisputeDto(
    val id: String, val reason: String, val status: String,
    val resolution: String? = null, val justification: String? = null,
) {
    fun domain() = GuinchoDispute(id, reason, GuinchoDisputeStatus.valueOf(status),
        resolution?.let(GuinchoDisputeResolution::valueOf), justification)
}

@Serializable
internal data class GuinchoResumeDraftDto(val id: String, val revision: Long,
    @SerialName("vehicle_id") val vehicleId: String,
    @SerialName("pickup_address") val pickupAddress: String,
    @SerialName("pickup_latitude") val pickupLatitude: Double,
    @SerialName("pickup_longitude") val pickupLongitude: Double,
    @SerialName("destination_address") val destinationAddress: String,
    @SerialName("destination_latitude") val destinationLatitude: Double,
    @SerialName("destination_longitude") val destinationLongitude: Double)
@Serializable
internal data class GuinchoSearchResumeDto(val outcome: String,
    @SerialName("context_id") val contextId: String? = null, val generation: Long? = null,
    val draft: GuinchoResumeDraftDto? = null,
    @SerialName("rejected_appointment_id") val rejectedAppointmentId: String? = null,
    val snapshot: com.trustarx.pitgo.features.mobiledispatch.data.MobileDispatchSnapshotDto? = null) {
    fun domain(): GuinchoSearchResume = when(outcome) {
        "NONE" -> GuinchoSearchResume.None
        "ACTIVE_DISPATCH" -> GuinchoSearchResume.Active(requireNotNull(snapshot?.toDomainOrNull()))
        "RESUMABLE_SEARCH" -> {
            val d = requireNotNull(draft)
            val trip = GuinchoTripInput(d.vehicleId, d.pickupAddress, MapCoordinate(d.pickupLatitude,d.pickupLongitude),
                d.destinationAddress, MapCoordinate(d.destinationLatitude,d.destinationLongitude))
            require(trip.isValid() && d.revision > 0 && requireNotNull(generation) > 0)
            java.util.UUID.fromString(requireNotNull(contextId))
            GuinchoSearchResume.Search(contextId, generation, GuinchoDraft(d.id,d.revision), trip, rejectedAppointmentId)
        }
        else -> error("Invalid resume contract")
    }
}

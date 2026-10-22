package com.trustarx.pitgo.features.pittec.data

import com.trustarx.pitgo.core.common.time.parseSupabaseInstant
import com.trustarx.pitgo.core.map.model.MapCoordinate
import com.trustarx.pitgo.features.pittec.model.NearbyPitTecMarker
import com.trustarx.pitgo.features.pittec.model.PitTecAppointment
import com.trustarx.pitgo.features.pittec.model.PitTecAppointmentStatus
import com.trustarx.pitgo.features.pittec.model.PitTecDispatchAction
import com.trustarx.pitgo.features.pittec.model.PitTecDispatchSnapshot
import com.trustarx.pitgo.features.pittec.model.PitTecDispatchStatus
import com.trustarx.pitgo.features.pittec.model.PitTecLastEvent
import com.trustarx.pitgo.features.pittec.model.PitTecOffer
import com.trustarx.pitgo.features.pittec.model.PitTecProvider
import com.trustarx.pitgo.features.pittec.model.PitTecQuote
import com.trustarx.pitgo.features.payment.model.PaymentStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigDecimal

@Serializable
internal data class NearbyPitTecMarkerDto(
    @SerialName("marker_id") val markerId: String,
    val latitude: Double,
    val longitude: Double,
    @SerialName("accuracy_radius_meters") val accuracyRadiusMeters: Int,
) {
    fun toDomainOrNull(): NearbyPitTecMarker? {
        val coordinate = MapCoordinate(latitude, longitude)
        val marker = NearbyPitTecMarker(markerId, coordinate, accuracyRadiusMeters)
        return marker.takeIf(NearbyPitTecMarker::isValid)
    }
}

/**
 * Every RPC used by the client's PitTec flow returns either `{outcome, snapshot: {...}}` or the
 * bare snapshot object directly (`private.pittec_dispatch_snapshot()`'s own shape). Declaring the
 * snapshot fields flatly here too — mirroring MobileDispatchEnvelopeDto — lets [domainOrNull]
 * handle both shapes without one repository-side branch per RPC.
 */
@Serializable
internal data class PitTecDispatchEnvelopeDto(
    val outcome: String? = null,
    val snapshot: PitTecDispatchSnapshotDto? = null,
    @SerialName("search_id") val searchId: String? = null,
    @SerialName("service_id") val serviceId: String? = null,
    @SerialName("service_name") val serviceName: String? = null,
    val status: String? = null,
    @SerialName("radius_meters") val radiusMeters: Int? = null,
    val revision: Long? = null,
    @SerialName("server_now") val serverNow: String? = null,
    @SerialName("next_dispatch_at") val nextDispatchAt: String? = null,
    @SerialName("last_event") val lastEvent: String? = null,
    @SerialName("last_event_at") val lastEventAt: String? = null,
    @SerialName("allowed_actions") val allowedActions: List<String> = emptyList(),
    val offer: PitTecOfferDto? = null,
    val provider: PitTecProviderDto? = null,
    val appointment: PitTecAppointmentDto? = null,
    val payment: PitTecPaymentDto? = null,
    @SerialName("tracking_authorized") val trackingAuthorized: Boolean = false,
) {
    fun domainOrNull(): PitTecDispatchSnapshot? = (snapshot ?: PitTecDispatchSnapshotDto(
        searchId = searchId,
        serviceId = serviceId,
        serviceName = serviceName,
        status = status,
        radiusMeters = radiusMeters,
        revision = revision,
        serverNow = serverNow,
        nextDispatchAt = nextDispatchAt,
        lastEvent = lastEvent,
        lastEventAt = lastEventAt,
        allowedActions = allowedActions,
        offer = offer,
        provider = provider,
        appointment = appointment,
        payment = payment,
        trackingAuthorized = trackingAuthorized,
    )).toDomainOrNull()
}

@Serializable
internal data class PitTecDispatchSnapshotDto(
    @SerialName("search_id") val searchId: String? = null,
    @SerialName("service_id") val serviceId: String? = null,
    @SerialName("service_name") val serviceName: String? = null,
    val status: String? = null,
    @SerialName("radius_meters") val radiusMeters: Int? = null,
    val revision: Long? = null,
    @SerialName("server_now") val serverNow: String? = null,
    @SerialName("next_dispatch_at") val nextDispatchAt: String? = null,
    @SerialName("last_event") val lastEvent: String? = null,
    @SerialName("last_event_at") val lastEventAt: String? = null,
    @SerialName("allowed_actions") val allowedActions: List<String> = emptyList(),
    val offer: PitTecOfferDto? = null,
    val provider: PitTecProviderDto? = null,
    val appointment: PitTecAppointmentDto? = null,
    val payment: PitTecPaymentDto? = null,
    @SerialName("tracking_authorized") val trackingAuthorized: Boolean = false,
) {
    fun toDomainOrNull(): PitTecDispatchSnapshot? = runCatching {
        PitTecDispatchSnapshot(
            searchId = requireNotNull(searchId),
            serviceId = requireNotNull(serviceId),
            serviceName = requireNotNull(serviceName),
            status = PitTecDispatchStatus.valueOf(requireNotNull(status)),
            radiusMeters = requireNotNull(radiusMeters),
            revision = requireNotNull(revision),
            serverNowEpochMillis = parseSupabaseInstant(requireNotNull(serverNow)).toEpochMilli(),
            nextDispatchAtEpochMillis = nextDispatchAt?.let { parseSupabaseInstant(it).toEpochMilli() },
            lastEvent = lastEvent?.let { runCatching { PitTecLastEvent.valueOf(it) }.getOrNull() },
            lastEventAtEpochMillis = lastEventAt?.let { parseSupabaseInstant(it).toEpochMilli() },
            allowedActions = allowedActions.mapNotNull {
                runCatching { PitTecDispatchAction.valueOf(it) }.getOrNull()
            }.toSet(),
            offer = offer?.toDomain(),
            provider = provider?.toDomain(),
            appointment = appointment?.toDomain(),
            paymentStatus = payment?.toStatusOrNull(),
            trackingAuthorized = trackingAuthorized,
        ).takeIf(PitTecDispatchSnapshot::isValid)
    }.getOrNull()
}

@Serializable
internal data class PitTecOfferDto(
    @SerialName("approximate_distance_meters") val approximateDistanceMeters: Int,
    val region: String,
    @SerialName("quoted_price") val quotedPrice: JsonElement,
    val currency: String,
    @SerialName("quote_version") val quoteVersion: Long,
) {
    fun toDomain() = PitTecOffer(
        approximateDistanceMeters,
        region,
        PitTecQuote(
            amount = BigDecimal(quotedPrice.jsonPrimitive.content),
            currency = currency,
            version = quoteVersion,
            clientConfirmationDeadlineEpochMillis = null,
            clientConfirmedAtEpochMillis = null,
            providerAmount = BigDecimal(quotedPrice.jsonPrimitive.content),
        ),
    )
}

@Serializable
internal data class PitTecProviderDto(
    @SerialName("provider_id") val providerId: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("rating_summary") val ratingSummary: com.trustarx.pitgo.features.rating.model.ProviderRatingSummary? = null,
) {
    fun toDomain() = PitTecProvider(providerId, displayName, ratingSummary?.validate())
}

@Serializable
internal data class PitTecAppointmentDto(
    @SerialName("appointment_id") val id: String,
    val status: String,
    @SerialName("response_deadline") val responseDeadline: String? = null,
    val revision: Long,
    @SerialName("accepted_at") val acceptedAt: String? = null,
    @SerialName("arrived_at") val arrivedAt: String? = null,
    @SerialName("cancelled_by") val cancelledBy: String? = null,
    val quote: PitTecQuoteDto? = null,
) {
    fun toDomain() = PitTecAppointment(
        id = id,
        status = PitTecAppointmentStatus.valueOf(status),
        responseDeadlineEpochMillis = responseDeadline?.let { parseSupabaseInstant(it).toEpochMilli() },
        revision = revision,
        acceptedAtEpochMillis = acceptedAt?.let { parseSupabaseInstant(it).toEpochMilli() },
        arrivedAtEpochMillis = arrivedAt?.let { parseSupabaseInstant(it).toEpochMilli() },
        cancelledBy = cancelledBy,
        quote = quote?.toDomain(),
    )
}

@Serializable
internal data class PitTecQuoteDto(
    @SerialName("service_amount") val serviceAmount: JsonElement? = null,
    @SerialName("distance_charge_amount") val distanceChargeAmount: JsonElement? = null,
    val amount: JsonElement? = null,
    @SerialName("provider_amount") val providerAmount: JsonElement? = null,
    @SerialName("markup_amount") val markupAmount: JsonElement? = null,
    @SerialName("customer_fee_amount") val customerFeeAmount: JsonElement? = null,
    @SerialName("customer_total_amount") val customerTotalAmount: JsonElement? = null,
    val currency: String = "BRL",
    val version: Long = 0,
    @SerialName("client_confirmation_deadline") val clientConfirmationDeadline: String? = null,
    @SerialName("client_confirmed_at") val clientConfirmedAt: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
) {
    fun toDomain() = PitTecQuote(
        amount = (customerTotalAmount ?: providerAmount ?: amount ?: error("quote amount missing")).jsonPrimitive.content.toBigDecimal(),
        currency = currency,
        version = version,
        // The customer-safe snapshot exposes this same deadline as `expires_at`.
        // Keep both field names compatible so the quote countdown and confirm action
        // do not become disabled when the provider accepts the request.
        clientConfirmationDeadlineEpochMillis = (clientConfirmationDeadline ?: expiresAt)
            ?.let { parseSupabaseInstant(it).toEpochMilli() },
        clientConfirmedAtEpochMillis = clientConfirmedAt
            ?.let { parseSupabaseInstant(it).toEpochMilli() },
        providerAmount = providerAmount?.jsonPrimitive?.content?.toBigDecimal(),
        markupAmount = markupAmount?.jsonPrimitive?.content?.toBigDecimal(),
        customerFeeAmount = customerFeeAmount?.jsonPrimitive?.content?.toBigDecimal(),
        customerTotalAmount = customerTotalAmount?.jsonPrimitive?.content?.toBigDecimal(),
        expiresAtEpochMillis = (expiresAt ?: clientConfirmationDeadline)
            ?.let { parseSupabaseInstant(it).toEpochMilli() },
    )
}

@Serializable
internal data class PitTecPaymentDto(val status: String) {
    fun toStatusOrNull(): PaymentStatus? = runCatching { PaymentStatus.valueOf(status) }.getOrNull()
}

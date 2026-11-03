package com.trustarx.pitgo.features.mobiledispatch.data

import com.trustarx.pitgo.core.map.model.MapCoordinate
import com.trustarx.pitgo.core.common.time.parseSupabaseInstant
import com.trustarx.pitgo.features.mobiledispatch.model.MobileAppointment
import com.trustarx.pitgo.features.mobiledispatch.model.MobileAppointmentStatus
import com.trustarx.pitgo.features.mobiledispatch.model.MobileCandidate
import com.trustarx.pitgo.features.mobiledispatch.model.MobileDispatchAction
import com.trustarx.pitgo.features.mobiledispatch.model.MobileDispatchKind
import com.trustarx.pitgo.features.mobiledispatch.model.MobileDispatchSnapshot
import com.trustarx.pitgo.features.mobiledispatch.model.MobileDispatchStatus
import com.trustarx.pitgo.features.mobiledispatch.model.PitTecOfferSummary
import com.trustarx.pitgo.features.payment.model.PaymentStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigDecimal

@Serializable
internal data class MobileDispatchEnvelopeDto(
    val outcome: String? = null,
    val snapshot: MobileDispatchSnapshotDto? = null,
    @SerialName("search_id") val searchId: String? = null,
    @SerialName("service_id") val serviceId: String? = null,
    @SerialName("service_name") val serviceName: String? = null,
    @SerialName("dispatch_kind") val dispatchKind: String? = null,
    val status: String? = null,
    @SerialName("radius_meters") val radiusMeters: Int? = null,
    val revision: Long? = null,
    @SerialName("server_now") val serverNow: String? = null,
    @SerialName("allowed_actions") val allowedActions: List<String> = emptyList(),
    val candidate: MobileCandidateDto? = null,
    val appointment: MobileAppointmentDto? = null,
    val offer: PitTecOfferSummaryDto? = null,
    val payment: MobilePaymentSummaryDto? = null,
    @SerialName("tracking_authorized") val trackingAuthorized: Boolean = false,
    val trip: com.trustarx.pitgo.features.guincho.data.GuinchoTripDto? = null,
    val dispute: com.trustarx.pitgo.features.guincho.data.GuinchoDisputeDto? = null,
    val inspection: com.trustarx.pitgo.features.guincho.model.GuinchoInspectionSummary? = null,
    val provider: MobileProviderSummaryDto? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("rating_state") val ratingState: com.trustarx.pitgo.features.rating.model.ServiceRatingState? = null,
) {
    fun domainOrNull(): MobileDispatchSnapshot? = (snapshot ?: MobileDispatchSnapshotDto(
        searchId = searchId,
        serviceId = serviceId,
        serviceName = serviceName,
        dispatchKind = dispatchKind,
        status = status,
        radiusMeters = radiusMeters,
        revision = revision,
        serverNow = serverNow,
        allowedActions = allowedActions,
        candidate = candidate,
        appointment = appointment,
        offer = offer,
        payment = payment,
        trackingAuthorized = trackingAuthorized,
        trip = trip, dispute = dispute, provider = provider,
        inspection = inspection,
        createdAt = createdAt, ratingState = ratingState,
    )).toDomainOrNull()
}

@Serializable
internal data class MobileDispatchSnapshotDto(
    @SerialName("search_id") val searchId: String? = null,
    @SerialName("service_id") val serviceId: String? = null,
    @SerialName("service_name") val serviceName: String? = null,
    @SerialName("dispatch_kind") val dispatchKind: String? = null,
    val status: String? = null,
    @SerialName("radius_meters") val radiusMeters: Int? = null,
    val revision: Long? = null,
    @SerialName("server_now") val serverNow: String? = null,
    @SerialName("allowed_actions") val allowedActions: List<String> = emptyList(),
    val candidate: MobileCandidateDto? = null,
    val appointment: MobileAppointmentDto? = null,
    val offer: PitTecOfferSummaryDto? = null,
    val payment: MobilePaymentSummaryDto? = null,
    @SerialName("tracking_authorized") val trackingAuthorized: Boolean = false,
    val trip: com.trustarx.pitgo.features.guincho.data.GuinchoTripDto? = null,
    val dispute: com.trustarx.pitgo.features.guincho.data.GuinchoDisputeDto? = null,
    val inspection: com.trustarx.pitgo.features.guincho.model.GuinchoInspectionSummary? = null,
    val provider: MobileProviderSummaryDto? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("rating_state") val ratingState: com.trustarx.pitgo.features.rating.model.ServiceRatingState? = null,
) {
    fun toDomainOrNull(): MobileDispatchSnapshot? = runCatching {
        MobileDispatchSnapshot(
            searchId = requireNotNull(searchId),
            serviceId = requireNotNull(serviceId),
            serviceName = requireNotNull(serviceName),
            status = MobileDispatchStatus.valueOf(requireNotNull(status)),
            radiusMeters = requireNotNull(radiusMeters),
            revision = requireNotNull(revision),
            serverNowEpochMillis = parseSupabaseInstant(requireNotNull(serverNow)).toEpochMilli(),
            allowedActions = allowedActions.mapNotNull {
                runCatching { MobileDispatchAction.valueOf(it) }.getOrNull()
            }.toSet(),
            candidate = candidate?.toDomain(),
            appointment = appointment?.toDomain(),
            dispatchKind = dispatchKind
                ?.let { runCatching { MobileDispatchKind.valueOf(it) }.getOrNull() }
                ?: MobileDispatchKind.MOBILE_SEQUENTIAL,
            offer = offer?.toDomain(),
            paymentStatus = payment?.toStatusOrNull(),
            trackingAuthorized = trackingAuthorized,
            guinchoTrip = trip?.summary(), guinchoDispute = dispute?.domain(),
            providerName = provider?.displayName,
            createdAt = createdAt,
            guinchoInspection = inspection?.validate(),
            ratingState = ratingState?.also { require(!(it.canRate && it.submitted)) },
        ).takeIf(MobileDispatchSnapshot::isValid)
    }.getOrNull()

}

@Serializable
internal data class PitTecOfferSummaryDto(
    @SerialName("approximate_distance_meters") val approximateDistanceMeters: Int,
    val region: String,
    @SerialName("quoted_price") val quotedPrice: JsonElement,
    val currency: String,
    @SerialName("quote_version") val quoteVersion: Long,
) {
    fun toDomain() = PitTecOfferSummary(
        approximateDistanceMeters,
        region,
        BigDecimal(quotedPrice.jsonPrimitive.content),
        currency,
        quoteVersion,
    )
}

@Serializable
internal data class MobileCandidateDto(
    @SerialName("candidate_id") val id: String,
    @SerialName("provider_id") val providerId: String,
    @SerialName("provider_name") val providerName: String,
    val locality: String,
    val latitude: Double,
    val longitude: Double,
    @SerialName("distance_meters") val distanceMeters: Double,
    val price: JsonElement,
    @SerialName("is_free") val isFree: Boolean,
) {
    fun toDomain() = MobileCandidate(
        id, providerId, providerName, locality, MapCoordinate(latitude, longitude),
        distanceMeters, BigDecimal(price.jsonPrimitive.content), isFree,
    )
}

@Serializable
internal data class MobileAppointmentDto(
    @SerialName("appointment_id") val id: String,
    val status: String,
    @SerialName("arrived_at") val arrivedAt: String? = null,
    @SerialName("service_started_at") val serviceStartedAt: String? = null,
    @SerialName("client_start_acknowledged_at") val clientStartAcknowledgedAt: String? = null,
    val quote: com.trustarx.pitgo.features.pittec.data.PitTecQuoteDto? = null,
    // Absent for PitTec: `private.pittec_dispatch_snapshot()` never includes a price — the PitTec
    // request UI never shows one (see data-model.md `provider_services`).
    @SerialName("quoted_price") val quotedPrice: JsonElement? = null,
    @SerialName("is_free") val isFree: Boolean = false,
    @SerialName("response_deadline") val responseDeadline: String? = null,
    val revision: Long,
    @SerialName("cancelled_by") val cancelledBy: String? = null,
    @SerialName("cancellation_reason") val cancellationReason: String? = null,
    @SerialName("quote_version") val quoteVersion: Long = 1,
    @SerialName("client_confirmation_deadline") val clientConfirmationDeadline: String? = null,
    @SerialName("client_confirmed_at") val clientConfirmedAt: String? = null,
    @SerialName("completion_requested_at") val completionRequestedAt: String? = null,
    @SerialName("client_completion_confirmed_at") val clientCompletionConfirmedAt: String? = null,
) {
    fun toDomain() = MobileAppointment(
        id = id,
        status = MobileAppointmentStatus.valueOf(status),
        quotedPrice = (quote?.providerAmount ?: quote?.customerTotalAmount ?: quotedPrice)?.jsonPrimitive?.content?.let(::BigDecimal) ?: BigDecimal.ZERO,
        arrivedAt = arrivedAt,
        serviceStartedAt = serviceStartedAt,
        clientStartAcknowledgedAt = clientStartAcknowledgedAt,
        serviceAmount = quote?.serviceAmount?.jsonPrimitive?.content?.let(::BigDecimal),
        distanceChargeAmount = quote?.distanceChargeAmount?.jsonPrimitive?.content?.let(::BigDecimal),
        isFree = isFree,
        responseDeadlineEpochMillis = responseDeadline?.let { parseSupabaseInstant(it).toEpochMilli() },
        revision = revision,
        cancelledBy = cancelledBy,
        cancellationReason = cancellationReason,
        quoteVersion = quoteVersion,
        clientConfirmationDeadlineEpochMillis = clientConfirmationDeadline
            ?.let { parseSupabaseInstant(it).toEpochMilli() },
        clientConfirmedAtEpochMillis = clientConfirmedAt
            ?.let { parseSupabaseInstant(it).toEpochMilli() },
        completionRequestedAtEpochMillis = completionRequestedAt
            ?.let { parseSupabaseInstant(it).toEpochMilli() },
        clientCompletionConfirmedAtEpochMillis = clientCompletionConfirmedAt
            ?.let { parseSupabaseInstant(it).toEpochMilli() },
    )
}

@Serializable
internal data class MobilePaymentSummaryDto(val status: String) {
    fun toStatusOrNull(): PaymentStatus? = runCatching { PaymentStatus.valueOf(status) }.getOrNull()
}

@Serializable
internal data class MobileProviderSummaryDto(@SerialName("display_name") val displayName: String)

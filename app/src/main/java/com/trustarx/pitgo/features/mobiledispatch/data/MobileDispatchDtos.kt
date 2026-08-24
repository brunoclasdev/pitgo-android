package com.trustarx.pitgo.features.mobiledispatch.data

import com.trustarx.pitgo.core.map.model.MapCoordinate
import com.trustarx.pitgo.core.common.time.parseSupabaseInstant
import com.trustarx.pitgo.features.mobiledispatch.model.MobileAppointment
import com.trustarx.pitgo.features.mobiledispatch.model.MobileAppointmentStatus
import com.trustarx.pitgo.features.mobiledispatch.model.MobileCandidate
import com.trustarx.pitgo.features.mobiledispatch.model.MobileDispatchAction
import com.trustarx.pitgo.features.mobiledispatch.model.MobileDispatchSnapshot
import com.trustarx.pitgo.features.mobiledispatch.model.MobileDispatchStatus
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
    val status: String? = null,
    @SerialName("radius_meters") val radiusMeters: Int? = null,
    val revision: Long? = null,
    @SerialName("server_now") val serverNow: String? = null,
    @SerialName("allowed_actions") val allowedActions: List<String> = emptyList(),
    val candidate: MobileCandidateDto? = null,
    val appointment: MobileAppointmentDto? = null,
) {
    fun domainOrNull(): MobileDispatchSnapshot? = (snapshot ?: MobileDispatchSnapshotDto(
        searchId, serviceId, serviceName, status, radiusMeters, revision, serverNow,
        allowedActions, candidate, appointment,
    )).toDomainOrNull()
}

@Serializable
internal data class MobileDispatchSnapshotDto(
    @SerialName("search_id") val searchId: String? = null,
    @SerialName("service_id") val serviceId: String? = null,
    @SerialName("service_name") val serviceName: String? = null,
    val status: String? = null,
    @SerialName("radius_meters") val radiusMeters: Int? = null,
    val revision: Long? = null,
    @SerialName("server_now") val serverNow: String? = null,
    @SerialName("allowed_actions") val allowedActions: List<String> = emptyList(),
    val candidate: MobileCandidateDto? = null,
    val appointment: MobileAppointmentDto? = null,
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
        ).takeIf(MobileDispatchSnapshot::isValid)
    }.getOrNull()

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
    @SerialName("quoted_price") val quotedPrice: JsonElement,
    @SerialName("is_free") val isFree: Boolean,
    @SerialName("response_deadline") val responseDeadline: String? = null,
    val revision: Long,
    @SerialName("cancelled_by") val cancelledBy: String? = null,
) {
    fun toDomain() = MobileAppointment(
        id = id,
        status = MobileAppointmentStatus.valueOf(status),
        quotedPrice = BigDecimal(quotedPrice.jsonPrimitive.content),
        isFree = isFree,
        responseDeadlineEpochMillis = responseDeadline?.let { parseSupabaseInstant(it).toEpochMilli() },
        revision = revision,
        cancelledBy = cancelledBy,
    )
}

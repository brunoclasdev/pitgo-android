package com.trustarx.pitgo.features.pittec.data

import com.trustarx.pitgo.core.common.time.parseSupabaseInstant
import com.trustarx.pitgo.core.map.model.MapCoordinate
import com.trustarx.pitgo.features.pittec.model.NearbyPitTecMarker
import com.trustarx.pitgo.features.pittec.model.PitTecAppointment
import com.trustarx.pitgo.features.pittec.model.PitTecAppointmentStatus
import com.trustarx.pitgo.features.pittec.model.PitTecCandidate
import com.trustarx.pitgo.features.pittec.model.PitTecDispatchAction
import com.trustarx.pitgo.features.pittec.model.PitTecDispatchSnapshot
import com.trustarx.pitgo.features.pittec.model.PitTecDispatchStatus
import com.trustarx.pitgo.features.pittec.model.PitTecLastEvent
import com.trustarx.pitgo.features.pittec.model.PitTecOffer
import com.trustarx.pitgo.features.pittec.model.PitTecProvider
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
    @SerialName("last_event") val lastEvent: String? = null,
    @SerialName("last_event_at") val lastEventAt: String? = null,
    @SerialName("allowed_actions") val allowedActions: List<String> = emptyList(),
    val offer: PitTecOfferDto? = null,
    val candidate: PitTecCandidateDto? = null,
    val provider: PitTecProviderDto? = null,
    val appointment: PitTecAppointmentDto? = null,
) {
    fun domainOrNull(): PitTecDispatchSnapshot? = (snapshot ?: PitTecDispatchSnapshotDto(
        searchId, serviceId, serviceName, status, radiusMeters, revision, serverNow,
        lastEvent, lastEventAt, allowedActions, offer, candidate, provider, appointment,
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
    @SerialName("last_event") val lastEvent: String? = null,
    @SerialName("last_event_at") val lastEventAt: String? = null,
    @SerialName("allowed_actions") val allowedActions: List<String> = emptyList(),
    val offer: PitTecOfferDto? = null,
    val candidate: PitTecCandidateDto? = null,
    val provider: PitTecProviderDto? = null,
    val appointment: PitTecAppointmentDto? = null,
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
            lastEvent = lastEvent?.let { runCatching { PitTecLastEvent.valueOf(it) }.getOrNull() },
            lastEventAtEpochMillis = lastEventAt?.let { parseSupabaseInstant(it).toEpochMilli() },
            allowedActions = allowedActions.mapNotNull {
                runCatching { PitTecDispatchAction.valueOf(it) }.getOrNull()
            }.toSet(),
            offer = offer?.toDomain(),
            candidate = candidate?.toDomain(),
            provider = provider?.toDomain(),
            appointment = appointment?.toDomain(),
        ).takeIf(PitTecDispatchSnapshot::isValid)
    }.getOrNull()
}

@Serializable
internal data class PitTecOfferDto(
    @SerialName("approximate_distance_meters") val approximateDistanceMeters: Int,
    val region: String,
) {
    fun toDomain() = PitTecOffer(approximateDistanceMeters, region)
}

@Serializable
internal data class PitTecCandidateDto(
    @SerialName("provider_name") val providerName: String,
    @SerialName("approximate_distance_meters") val approximateDistanceMeters: Int,
) {
    fun toDomain() = PitTecCandidate(providerName, approximateDistanceMeters)
}

@Serializable
internal data class PitTecProviderDto(
    @SerialName("provider_id") val providerId: String,
    @SerialName("display_name") val displayName: String,
) {
    fun toDomain() = PitTecProvider(providerId, displayName)
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
) {
    fun toDomain() = PitTecAppointment(
        id = id,
        status = PitTecAppointmentStatus.valueOf(status),
        responseDeadlineEpochMillis = responseDeadline?.let { parseSupabaseInstant(it).toEpochMilli() },
        revision = revision,
        acceptedAtEpochMillis = acceptedAt?.let { parseSupabaseInstant(it).toEpochMilli() },
        arrivedAtEpochMillis = arrivedAt?.let { parseSupabaseInstant(it).toEpochMilli() },
        cancelledBy = cancelledBy,
    )
}

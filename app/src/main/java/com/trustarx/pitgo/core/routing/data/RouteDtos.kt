package com.trustarx.pitgo.core.routing.data

import com.trustarx.pitgo.core.common.time.parseSupabaseInstant
import com.trustarx.pitgo.core.routing.model.RouteGeometry
import com.trustarx.pitgo.core.routing.model.RoutePoint
import com.trustarx.pitgo.core.routing.model.RoutePresentation
import com.trustarx.pitgo.core.routing.model.RouteSnapshot
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class RouteRequestDto(
    @SerialName("appointment_id") val appointmentId: String,
    @SerialName("provider_location_revision") val providerLocationRevision: Long,
)

@Serializable
internal data class RouteSuccessDto(
    val status: String,
    @SerialName("appointment_id") val appointmentId: String,
    @SerialName("provider_location_revision") val providerLocationRevision: Long,
    @SerialName("distance_meters") val distanceMeters: Int,
    val geometry: RouteGeometryDto,
    @SerialName("generated_at") val generatedAt: String,
) {
    fun toDomainOrNull(): RouteSnapshot? = runCatching {
        RouteSnapshot(
            presentation = RoutePresentation.ROUTED,
            distanceMeters = distanceMeters,
            geometry = geometry.toDomain(),
            providerLocationRevision = providerLocationRevision,
            generatedAtEpochMillis = parseSupabaseInstant(generatedAt).toEpochMilli(),
        ).takeIf(RouteSnapshot::isValid)
    }.getOrNull()
}

@Serializable
internal data class RouteGeometryDto(
    val type: String,
    val coordinates: List<List<Double>>,
) {
    fun toDomain() = RouteGeometry(
        coordinates.mapNotNull { pair ->
            val longitude = pair.getOrNull(0) ?: return@mapNotNull null
            val latitude = pair.getOrNull(1) ?: return@mapNotNull null
            RoutePoint(longitude, latitude)
        },
    )
}

@Serializable
internal data class RouteErrorDto(
    val status: String? = null,
    val code: String? = null,
    val retryable: Boolean = true,
)

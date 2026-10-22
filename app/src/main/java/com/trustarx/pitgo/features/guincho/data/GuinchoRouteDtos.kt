package com.trustarx.pitgo.features.guincho.data

import com.trustarx.pitgo.core.map.model.MapCoordinate
import com.trustarx.pitgo.core.common.time.parseSupabaseInstant
import com.trustarx.pitgo.core.routing.model.*
import com.trustarx.pitgo.features.guincho.model.GuinchoRouteSnapshot
import kotlinx.serialization.*

@Serializable internal data class GuinchoRouteRequestDto(
    @SerialName("appointment_id") val appointmentId: String, val reason: String,
    @SerialName("known_route_version") val knownVersion: Long)
@Serializable internal data class GuinchoRouteCoordinateDto(val latitude: Double, val longitude: Double)
@Serializable internal data class GuinchoRouteOriginDto(val latitude: Double, val longitude: Double,
    @SerialName("accuracy_m") val accuracy: Double,
    @SerialName("captured_at") val capturedAt: String,
    @SerialName("published_at") val publishedAt: String)
@Serializable internal data class GuinchoRouteGeometryDto(val type: String, val coordinates: List<List<Double>>,
    @SerialName("duration_seconds") val durationSeconds: Int? = null)
@Serializable internal data class GuinchoRouteDto(
    val status: String? = null, val outcome: String? = null, val code: String? = null, val retryable: Boolean = true,
    @SerialName("retry_after_ms") val retryAfterMs: Long = 1000,
    @SerialName("appointment_id") val appointmentId: String? = null,
    @SerialName("route_version") val version: Long? = null,
    val origin: GuinchoRouteOriginDto? = null, val destination: GuinchoRouteCoordinateDto? = null,
    val geometry: GuinchoRouteGeometryDto? = null,
    @SerialName("distance_meters") val distance: Int? = null,
    @SerialName("provider_location_revision") val providerRevision: Long? = null,
    @SerialName("provider_location_published_at") val publishedAt: String? = null,
    @SerialName("generated_at") val generatedAt: String? = null,
) {
    fun snapshot(expected: String): GuinchoRouteSnapshot {
        require(status == "ROUTE" || status == null && outcome == "FOUND")
        require(appointmentId == expected && (version ?: 0) > 0 && (providerRevision ?: 0) > 0 && distance != null && distance in 0..2_000_000)
        val from = MapCoordinate(requireNotNull(origin).latitude, origin.longitude)
        val to = MapCoordinate(requireNotNull(destination).latitude, destination.longitude)
        require(origin.accuracy.isFinite() && origin.accuracy > 0 && origin.accuracy <= 100)
        val captured = parseSupabaseInstant(origin.capturedAt)
        val originPublished = parseSupabaseInstant(origin.publishedAt)
        val published = parseSupabaseInstant(requireNotNull(publishedAt))
        val generated = parseSupabaseInstant(requireNotNull(generatedAt))
        require(captured.toEpochMilli() > 0 && originPublished == published)
        require(generated.toEpochMilli() > 0)
        val geo = requireNotNull(geometry)
        require(from.isValid && to.isValid && geo.type == "LineString" && geo.coordinates.size in 2..10_000)
        val route = RouteGeometry(geo.coordinates.map { require(it.size == 2); RoutePoint(it[0], it[1]) }, geo.durationSeconds)
        require(route.isValid)
        return GuinchoRouteSnapshot(expected, requireNotNull(version), from, to, route, distance, requireNotNull(providerRevision),
            published.toEpochMilli(), generated.toEpochMilli())
    }
}

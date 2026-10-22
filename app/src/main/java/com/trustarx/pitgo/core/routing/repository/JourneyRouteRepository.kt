package com.trustarx.pitgo.core.routing.repository

import com.trustarx.pitgo.core.map.model.MapCoordinate
import com.trustarx.pitgo.core.network.SupabaseClientProvider
import com.trustarx.pitgo.core.routing.data.RouteGeometryDto
import com.trustarx.pitgo.core.routing.model.JourneyRoute
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.ktor.client.call.body
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class JourneyRouteRepository(private val client: SupabaseClient = SupabaseClientProvider.client) {
    suspend fun directions(origin: MapCoordinate, destination: MapCoordinate): JourneyRoute? {
        if (!origin.isValid || !destination.isValid) return null
        return try {
            val dto = client.functions.invoke("journey-route", JourneyRequest(
                JourneyCoordinate(origin.latitude, origin.longitude), JourneyCoordinate(destination.latitude, destination.longitude),
            )).body<JourneyResponse>()
            val geometry = dto.geometry.toDomain()
            if (dto.status != "ROUTE" || dto.geometry.type != "LineString" || !geometry.isValid ||
                geometry.durationSeconds == null || dto.distanceMeters !in 0..2_000_000) null
            else JourneyRoute(origin, destination, geometry, dto.distanceMeters, System.currentTimeMillis())
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            null
        }
    }
}

@Serializable private data class JourneyCoordinate(val latitude: Double, val longitude: Double)
@Serializable private data class JourneyRequest(val origin: JourneyCoordinate, val destination: JourneyCoordinate)
@Serializable private data class JourneyResponse(
    val status: String,
    val geometry: RouteGeometryDto,
    @SerialName("distance_meters") val distanceMeters: Int,
)

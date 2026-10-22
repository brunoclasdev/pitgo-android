package com.trustarx.pitgo.core.routing.model

import com.trustarx.pitgo.core.map.model.MapCoordinate
import com.trustarx.pitgo.core.map.model.MapRoutePolyline
import com.trustarx.pitgo.core.map.model.MapRouteStyle
import kotlin.math.*

/** Directions for coordinates already visible to the viewer. Duration excludes live traffic. */
data class JourneyRoute(
    val origin: MapCoordinate,
    val destination: MapCoordinate,
    val geometry: RouteGeometry,
    val distanceMeters: Int,
    val generatedAt: Long,
) {
    fun remainingAt(position: MapCoordinate): JourneyProgress {
        val points = geometry.points.map(RoutePoint::toMapCoordinate)
        val lengths = points.zipWithNext(::distanceBetween)
        val total = lengths.sum()
        var travelled = 0.0
        var bestDistance = Double.POSITIVE_INFINITY
        var bestProgress = 0.0
        var bestIndex = 0
        var projected = points.first()
        val scale = cos(Math.toRadians(position.latitude))
        points.zipWithNext().forEachIndexed { index, (a, b) ->
            val ax = (a.longitude - position.longitude) * scale
            val ay = a.latitude - position.latitude
            val dx = (b.longitude - a.longitude) * scale
            val dy = b.latitude - a.latitude
            val denominator = dx * dx + dy * dy
            val t = if (denominator == 0.0) 0.0 else (-(ax * dx + ay * dy) / denominator).coerceIn(0.0, 1.0)
            val distance = hypot(ax + t * dx, ay + t * dy)
            if (distance < bestDistance) {
                bestDistance = distance
                bestProgress = travelled + t * lengths[index]
                bestIndex = index
                projected = MapCoordinate(a.latitude + t * (b.latitude - a.latitude), a.longitude + t * (b.longitude - a.longitude))
            }
            travelled += lengths[index]
        }
        // Off-route fixes cannot be interpreted as progress along the old route.
        val onRoute = distanceBetween(position, projected) <= 100.0
        val fraction = when {
            total == 0.0 -> 0.0
            !onRoute -> 1.0
            else -> (1.0 - bestProgress / total).coerceIn(0.0, 1.0)
        }
        val remainingPoints = if (onRoute) listOf(projected) + points.drop(bestIndex + 1).dropWhile { it == projected } else points
        return JourneyProgress(
            distanceMeters = distanceMeters * fraction,
            durationSeconds = geometry.durationSeconds?.let { ceil(it * fraction).toInt() },
            polyline = MapRoutePolyline("journey-route", remainingPoints.takeIf { it.size >= 2 } ?: listOf(projected, projected), MapRouteStyle.SOLID),
        )
    }
}

data class JourneyProgress(val distanceMeters: Double, val durationSeconds: Int?, val polyline: MapRoutePolyline)

fun distanceBetween(a: MapCoordinate, b: MapCoordinate): Double {
    val lat = Math.toRadians(b.latitude - a.latitude)
    val lon = Math.toRadians(b.longitude - a.longitude)
    val h = sin(lat / 2).pow(2) + cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sin(lon / 2).pow(2)
    return 12_742_000.0 * asin(sqrt(h.coerceIn(0.0, 1.0)))
}

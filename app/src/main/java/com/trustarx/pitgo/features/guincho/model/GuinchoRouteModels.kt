package com.trustarx.pitgo.features.guincho.model

import com.trustarx.pitgo.core.map.model.MapCoordinate
import com.trustarx.pitgo.core.routing.model.RouteGeometry
import com.trustarx.pitgo.features.appointmenttracking.model.AppointmentProviderLocation
import kotlin.math.*

enum class GuinchoRouteReason { INITIAL, RESUME, DEVIATION, RETRY }
data class GuinchoRouteSnapshot(val appointmentId: String, val version: Long, val origin: MapCoordinate,
    val destination: MapCoordinate, val geometry: RouteGeometry, val distanceMeters: Int,
    val providerRevision: Long, val providerPublishedAt: Long, val generatedAt: Long)
data class GuinchoRouteState(val snapshot: GuinchoRouteSnapshot? = null, val loading: Boolean = false,
    val failure: String? = null, val retryAt: Long = 0)
sealed interface GuinchoRouteResult {
    data class Success(val snapshot: GuinchoRouteSnapshot) : GuinchoRouteResult
    data class Failure(val code: String, val retryable: Boolean = true, val retryAfterMs: Long = 1000) : GuinchoRouteResult
}
class GuinchoRouteDeviation {
    private val fixes = ArrayDeque<AppointmentProviderLocation>()
    @Synchronized fun reset() { fixes.clear() }
    @Synchronized fun accept(fix: AppointmentProviderLocation, geometry: RouteGeometry, now: Long): Boolean {
        if (now - fix.capturedAtEpochMillis !in -5_000L..10_000L || fix.accuracyMeters > 35 || !fix.isValid ||
            distanceToRoute(fix.coordinate, geometry) <= max(50.0, 2 * fix.accuracyMeters)) { reset(); return false }
        if (fixes.lastOrNull()?.revision == fix.revision) return false
        if (fixes.lastOrNull()?.capturedAtEpochMillis?.let { fix.capturedAtEpochMillis <= it } == true) return false
        fixes.addLast(fix)
        while (fixes.size > 3) fixes.removeFirst()
        return fixes.size == 3 && fixes.last().capturedAtEpochMillis - fixes.first().capturedAtEpochMillis >= 4000
    }
}
private fun wrapLongitude(value: Double): Double = ((value + 180) % 360 + 360) % 360 - 180
fun distanceToRoute(point: MapCoordinate, route: RouteGeometry): Double {
    val metersPerDegree = 111_195.0
    val lonScale = metersPerDegree * cos(Math.toRadians(point.latitude))
    var nearest = Double.POSITIVE_INFINITY
    for (i in 0 until route.points.lastIndex) {
        val a = route.points[i]; val b = route.points[i + 1]
        val ax = wrapLongitude(a.longitude - point.longitude) * lonScale
        val ay = (a.latitude - point.latitude) * metersPerDegree
        val bx = ax + wrapLongitude(b.longitude - a.longitude) * lonScale
        val by = (b.latitude - point.latitude) * metersPerDegree
        val dx = bx - ax; val dy = by - ay; val length = dx * dx + dy * dy
        val t = if (length == 0.0) 0.0 else (-(ax * dx + ay * dy) / length).coerceIn(0.0, 1.0)
        nearest = min(nearest, hypot(ax + t * dx, ay + t * dy))
    }
    return nearest
}

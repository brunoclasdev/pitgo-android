package com.trustarx.pitgo.core.routing.model

import com.trustarx.pitgo.core.map.model.MapCoordinate

/** One `[longitude, latitude]` pair, matching GeoJSON coordinate order (see pittec-routing-ui.md). */
data class RoutePoint(val longitude: Double, val latitude: Double) {
    val isValid: Boolean
        get() = longitude.isFinite() && latitude.isFinite() &&
            longitude in -180.0..180.0 && latitude in -90.0..90.0

    fun toMapCoordinate() = MapCoordinate(latitude, longitude)
}

data class RouteGeometry(val points: List<RoutePoint>, val durationSeconds: Int? = null) {
    val isValid: Boolean get() = points.size in 2..10_000 && points.all(RoutePoint::isValid) &&
        (durationSeconds == null || durationSeconds in 0..604_800)
}

enum class RoutePresentation { ROUTED, ESTIMATED_DIRECT, UNAVAILABLE }

data class RouteSnapshot(
    val presentation: RoutePresentation,
    val distanceMeters: Int,
    val geometry: RouteGeometry?,
    val providerLocationRevision: Long,
    val generatedAtEpochMillis: Long,
) {
    fun isValid(): Boolean = distanceMeters in 0..2_000_000 && providerLocationRevision > 0 &&
        (presentation != RoutePresentation.ROUTED || geometry?.isValid == true)
}

enum class RouteFailure {
    AUTH_REQUIRED, NOT_PARTICIPANT, LOCATION_NOT_READY, APPOINTMENT_NOT_TRACKABLE,
    RATE_LIMITED, ROUTER_FAILURE, ROUTER_TIMEOUT, OFFLINE, SERVICE_UNAVAILABLE, CONTRACT, UNKNOWN,
}

sealed interface RouteResult {
    data class Success(val snapshot: RouteSnapshot) : RouteResult
    data class Failure(val reason: RouteFailure, val retryable: Boolean) : RouteResult
}

package com.trustarx.pitgo.core.map.model

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class MapCoordinate(
    val latitude: Double,
    val longitude: Double,
) {
    val isValid: Boolean
        get() = latitude.isFinite() && longitude.isFinite() &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0
}

data class MapViewport(
    val center: MapCoordinate,
    val zoom: Double,
)

/**
 * Viewport centered at the midpoint of [first] and [second], with a zoom level picked from the
 * distance between them so both stay visible without needing true bounds-fitting support from
 * the map host. Used to frame client+provider (or provider+client) on the appointment maps.
 */
fun framingViewport(first: MapCoordinate, second: MapCoordinate): MapViewport {
    val center = MapCoordinate(
        (first.latitude + second.latitude) / 2.0,
        (first.longitude + second.longitude) / 2.0,
    )
    val distanceMeters = haversineMeters(first, second)
    val zoom = when {
        distanceMeters <= 500.0 -> 15.0
        distanceMeters <= 2_000.0 -> 13.0
        distanceMeters <= 8_000.0 -> 11.0
        distanceMeters <= 30_000.0 -> 9.0
        else -> 7.0
    }
    return MapViewport(center, zoom)
}

private fun haversineMeters(a: MapCoordinate, b: MapCoordinate): Double {
    val earthRadiusMeters = 6_371_000.0
    val lat1 = Math.toRadians(a.latitude)
    val lat2 = Math.toRadians(b.latitude)
    val deltaLat = Math.toRadians(b.latitude - a.latitude)
    val deltaLon = Math.toRadians(b.longitude - a.longitude)
    val h = sin(deltaLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(deltaLon / 2).pow(2)
    return 2 * earthRadiusMeters * asin(sqrt(h))
}

data class MapViewportCommand(
    val viewport: MapViewport,
    val revision: Long,
)

enum class MapMarkerKind {
    ADDRESS,
    MOBILE_PROVIDER,
    CLIENT_LOCATION,
    LIVE,
    STALE,
    COINCIDENT_GROUP,
    /** Anonymized, server-approximated PitTec position shown before a request is accepted. */
    PITTEC_APPROXIMATE,
}

data class MapMarkerSpec(
    val id: String,
    val coordinate: MapCoordinate,
    val kind: MapMarkerKind,
    val selected: Boolean = false,
    val groupKey: String? = null,
    val accessibilityLabel: String,
)

sealed interface MapFeatureSelection {
    data class Single(val markerId: String) : MapFeatureSelection
    data class CoincidentGroup(val groupKey: String) : MapFeatureSelection
    data class NearbyCluster(val coordinate: MapCoordinate) : MapFeatureSelection
    data object None : MapFeatureSelection
}

sealed interface MapStyleState {
    data object Uninitialized : MapStyleState
    data class Loading(val revision: Long) : MapStyleState
    data class Ready(val revision: Long) : MapStyleState
    data class Failed(val revision: Long) : MapStyleState
}

enum class MapResourceFailureKind {
    TILE,
    GLYPH,
    SPRITE,
    RENDER,
}

sealed interface MapResourceState {
    data object Waiting : MapResourceState
    data object Ready : MapResourceState
    data class Degraded(val kind: MapResourceFailureKind) : MapResourceState
    data object Offline : MapResourceState
}

data class MapSurfaceState(
    val style: MapStyleState = MapStyleState.Uninitialized,
    val resources: MapResourceState = MapResourceState.Waiting,
)

data class MapRenderModel(
    val markers: List<MapMarkerSpec> = emptyList(),
)

package com.trustarx.pitgo.core.map.model

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

data class MapViewportCommand(
    val viewport: MapViewport,
    val revision: Long,
)

enum class MapMarkerKind {
    ADDRESS,
    LIVE,
    STALE,
    COINCIDENT_GROUP,
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

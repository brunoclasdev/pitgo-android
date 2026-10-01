package com.trustarx.pitgo.features.guincho.model

import com.trustarx.pitgo.core.location.model.DeviceLocationFix
import com.trustarx.pitgo.core.map.model.*

sealed interface GuinchoProviderPosition {
    data object Loading : GuinchoProviderPosition
    data class Current(val fix: DeviceLocationFix) : GuinchoProviderPosition
    data class Stale(val fix: DeviceLocationFix) : GuinchoProviderPosition
    data object PermissionDenied : GuinchoProviderPosition
    data object LocationDisabled : GuinchoProviderPosition
    data object Unavailable : GuinchoProviderPosition
}

data class GuinchoProviderHomeState(
    val position: GuinchoProviderPosition = GuinchoProviderPosition.Loading,
    val viewport: MapViewport = MapViewport(MapCoordinate(-14.235, -51.9253), 4.0),
    val camera: MapViewportCommand? = null,
    val follow: Boolean = true,
    val surface: MapSurfaceState = MapSurfaceState(),
    val styleRevision: Long = 0,
)

package com.trustarx.pitgo.features.appointmenttracking.model

import com.trustarx.pitgo.core.map.model.MapCoordinate
import com.trustarx.pitgo.core.map.model.MapMarkerKind
import com.trustarx.pitgo.core.map.model.MapMarkerSpec
import com.trustarx.pitgo.core.map.model.MapRoutePolyline
import com.trustarx.pitgo.core.map.model.MapRouteStyle
import com.trustarx.pitgo.core.map.model.MapSurfaceState
import com.trustarx.pitgo.core.map.model.MapViewport
import com.trustarx.pitgo.core.map.model.MapViewportCommand
import com.trustarx.pitgo.core.routing.model.RouteSnapshot
import com.trustarx.pitgo.features.mobiledispatch.model.MobileDispatchKind
import com.trustarx.pitgo.features.mobiledispatch.model.MobileDispatchSnapshot
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class LocationRevision(
    val publishedAtEpochMillis: Long,
    val version: Long,
) : Comparable<LocationRevision> {
    override fun compareTo(other: LocationRevision): Int =
        compareValuesBy(this, other, LocationRevision::publishedAtEpochMillis, LocationRevision::version)
}

data class AppointmentProviderLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double,
    val capturedAtEpochMillis: Long,
    val publishedAtEpochMillis: Long,
    val version: Long,
) {
    val coordinate = MapCoordinate(latitude, longitude)
    val revision = LocationRevision(publishedAtEpochMillis, version)

    val isValid: Boolean
        get() = coordinate.isValid && accuracyMeters.isFinite() && accuracyMeters > 0 &&
            capturedAtEpochMillis > 0 && publishedAtEpochMillis > 0 && version > 0
}

/**
 * The client's one-shot GPS fix during the appointment (decision of `/speckit-clarify`,
 * 2026-08-23): no continuous tracking, just the latest reading published from the tracking
 * screen. Used to show the client centered on their own map and as the destination marker on the
 * provider's map.
 */
data class AppointmentClientLocation(
    val latitude: Double,
    val longitude: Double,
    val capturedAtEpochMillis: Long? = null,
) {
    val coordinate = MapCoordinate(latitude, longitude)
    val isValid: Boolean get() = coordinate.isValid
}

enum class TerminalAppointmentStatus { COMPLETED, CANCELLED }

sealed interface AppointmentLocationEvent {
    /**
     * Authoritative snapshot of both locations for the appointment. Every insert/update/delete on
     * either provider_current_locations or client_current_locations re-fetches this via RPC
     * instead of trusting the raw broadcast payload — same "broadcast just wakes the repository up"
     * philosophy already used elsewhere in the app (see specs/013 realtime contract).
     */
    data class Snapshot(
        val providerLocation: AppointmentProviderLocation?,
        val clientLocation: AppointmentClientLocation?,
    ) : AppointmentLocationEvent
    data class Ended(val status: TerminalAppointmentStatus) : AppointmentLocationEvent
    data object SessionExpired : AppointmentLocationEvent
    data object Offline : AppointmentLocationEvent
    data object Failure : AppointmentLocationEvent
}

sealed interface TrackingDataState {
    data object Loading : TrackingDataState
    data object Empty : TrackingDataState
    data class Live(val location: AppointmentProviderLocation) : TrackingDataState
    data class Stale(val location: AppointmentProviderLocation) : TrackingDataState
    data class Offline(val lastLocation: AppointmentProviderLocation?) : TrackingDataState
    data class Failure(val lastLocation: AppointmentProviderLocation?) : TrackingDataState
    data object Ended : TrackingDataState
    data object SessionExpired : TrackingDataState
}

data class AppointmentTrackingUiState(
    val data: TrackingDataState = TrackingDataState.Loading,
    val clientLocation: AppointmentClientLocation? = null,
    val surfaceState: MapSurfaceState = MapSurfaceState(),
    val viewport: MapViewport = MapViewport(MapCoordinate(-14.235, -51.9253), 4.0),
    val cameraCommand: MapViewportCommand? = null,
    val followProvider: Boolean = true,
    val summaryExpanded: Boolean = false,
    val styleReloadRevision: Long = 0L,
    val dispatch: MobileDispatchSnapshot? = null,
    val cancelling: Boolean = false,
    val arriving: Boolean = false,
    val actionFailed: Boolean = false,
    val completing: Boolean = false,
    /** Only populated for PitTec — Guincho keeps its existing distance-free experience. */
    val route: RouteSnapshot? = null,
) {
    val isPitTec: Boolean get() = dispatch?.dispatchKind == MobileDispatchKind.PITTEC_AUTO_SEQUENTIAL

    val visibleLocation: AppointmentProviderLocation?
        get() = when (val current = data) {
            is TrackingDataState.Live -> current.location
            is TrackingDataState.Stale -> current.location
            is TrackingDataState.Offline -> current.lastLocation
            is TrackingDataState.Failure -> current.lastLocation
            else -> null
        }

    /**
     * Straight-line fallback distance in meters, computed locally from the two exact participant
     * points whenever a routed geometry is unavailable — never stored, never sent anywhere (see
     * pittec-routing-ui.md `Fallback`).
     */
    val haversineDistanceMeters: Double?
        get() {
            val provider = visibleLocation?.coordinate ?: return null
            val client = clientLocation?.coordinate?.takeIf(MapCoordinate::isValid) ?: return null
            return haversineMeters(provider, client)
        }

    val markers: List<MapMarkerSpec>
        get() = buildList {
            visibleLocation?.let { location ->
                add(
                    MapMarkerSpec(
                        id = "appointment-provider",
                        coordinate = location.coordinate,
                        kind = when {
                            data !is TrackingDataState.Live -> MapMarkerKind.STALE
                            isPitTec -> MapMarkerKind.PITTEC_LIVE
                            else -> MapMarkerKind.LIVE
                        },
                        accessibilityLabel = if (data is TrackingDataState.Live) {
                            if (isPitTec) "PitTec ao vivo" else "Prestador ao vivo"
                        } else "Última posição do prestador, desatualizada",
                    )
                )
            }
            clientLocation?.takeIf(AppointmentClientLocation::isValid)?.let { location ->
                add(
                    MapMarkerSpec(
                        id = "appointment-client",
                        coordinate = location.coordinate,
                        kind = MapMarkerKind.CLIENT_LOCATION,
                        accessibilityLabel = if (isPitTec) "Local do atendimento" else "Sua posição",
                    )
                )
            }
        }

    /** Solid routed polyline when available; otherwise a dashed direct line between both points. */
    val routePolyline: MapRoutePolyline?
        get() {
            if (!isPitTec) return null
            val provider = visibleLocation?.coordinate ?: return null
            val clientCoordinate = clientLocation?.coordinate?.takeIf(MapCoordinate::isValid) ?: return null
            val geometry = route?.geometry?.takeIf { it.isValid }
            return if (geometry != null) {
                MapRoutePolyline(
                    id = "appointment-route",
                    points = geometry.points.map { it.toMapCoordinate() },
                    style = MapRouteStyle.SOLID,
                )
            } else {
                MapRoutePolyline(
                    id = "appointment-route-direct",
                    points = listOf(provider, clientCoordinate),
                    style = MapRouteStyle.DASHED,
                )
            }
        }
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

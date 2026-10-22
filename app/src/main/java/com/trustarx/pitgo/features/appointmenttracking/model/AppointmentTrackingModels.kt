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
import com.trustarx.pitgo.core.navigation.ExternalNavigationOption
import com.trustarx.pitgo.core.navigation.ExternalNavigator
import com.trustarx.pitgo.features.mobiledispatch.model.MobileAppointmentStatus
import com.trustarx.pitgo.features.mobiledispatch.model.MobileDispatchFailure
import com.trustarx.pitgo.features.mobiledispatch.model.MobileDispatchKind
import com.trustarx.pitgo.features.mobiledispatch.model.MobileDispatchSnapshot

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

sealed interface AppointmentClientLocationResult {
    data class Found(val location: AppointmentClientLocation) : AppointmentClientLocationResult
    data object Unavailable : AppointmentClientLocationResult
    data class Failure(val reason: MobileDispatchFailure) : AppointmentClientLocationResult
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

enum class TrackingViewer { CLIENT, PROVIDER, HISTORICAL }

enum class ExternalNavigationLeg { GUINCHO_PICKUP, GUINCHO_DESTINATION, PITTEC_CLIENT }

data class ExternalNavigationTarget(
    val appointmentId: String,
    val snapshotRevision: Long,
    val appointmentRevision: Long,
    val leg: ExternalNavigationLeg,
    val coordinate: MapCoordinate,
)

data class ExternalNavigationRequest(
    val id: Long,
    val navigator: ExternalNavigator,
    val target: ExternalNavigationTarget,
)

enum class ExternalNavigationFailure {
    DESTINATION_UNAVAILABLE, CONTEXT_CHANGED, CONTEXT_REFRESH_FAILED, APP_UNAVAILABLE,
}

sealed interface ExternalNavigationState {
    data object Closed : ExternalNavigationState
    data class Choosing(val options: List<ExternalNavigationOption>) : ExternalNavigationState
    data class Preparing(val options: List<ExternalNavigationOption>) : ExternalNavigationState
    data class ReadyToLaunch(val request: ExternalNavigationRequest) : ExternalNavigationState
    data class Failure(
        val reason: ExternalNavigationFailure,
        val options: List<ExternalNavigationOption>,
    ) : ExternalNavigationState
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
    /** Authoritative PitTec road route; other journey previews use the shared directions surface. */
    val route: RouteSnapshot? = null,
    val guinchoRoute: com.trustarx.pitgo.features.guincho.model.GuinchoRouteState = com.trustarx.pitgo.features.guincho.model.GuinchoRouteState(),
    val guinchoPolyline: MapRoutePolyline? = null,
    val viewer: TrackingViewer = TrackingViewer.HISTORICAL,
    val externalNavigation: ExternalNavigationState = ExternalNavigationState.Closed,
) {
    val isPitTec: Boolean get() = dispatch?.dispatchKind == MobileDispatchKind.PITTEC_AUTO_SEQUENTIAL
    val isGuincho: Boolean get() = dispatch?.dispatchKind == MobileDispatchKind.GUINCHO_DIRECT

    val navigationLeg: ExternalNavigationLeg?
        get() {
            val snapshot = dispatch ?: return null
            val appointment = snapshot.appointment ?: return null
            if (viewer != TrackingViewer.PROVIDER || !snapshot.trackingAuthorized ||
                data is TrackingDataState.SessionExpired || data is TrackingDataState.Ended ||
                arriving || cancelling || completing
            ) return null
            return when {
                isGuincho && appointment.status == MobileAppointmentStatus.ACCEPTED -> ExternalNavigationLeg.GUINCHO_PICKUP
                isGuincho && appointment.status == MobileAppointmentStatus.IN_PROGRESS -> ExternalNavigationLeg.GUINCHO_DESTINATION
                isPitTec && appointment.status == MobileAppointmentStatus.ACCEPTED && appointment.arrivedAt == null -> ExternalNavigationLeg.PITTEC_CLIENT
                else -> null
            }
        }

    val navigationCoordinate: MapCoordinate?
        get() = when (navigationLeg) {
            ExternalNavigationLeg.GUINCHO_PICKUP -> dispatch?.guinchoTrip?.pickup
            ExternalNavigationLeg.GUINCHO_DESTINATION -> dispatch?.guinchoTrip?.destination
            ExternalNavigationLeg.PITTEC_CLIENT -> clientLocation?.coordinate
            null -> null
        }?.takeIf(MapCoordinate::isValid)

    val visibleLocation: AppointmentProviderLocation?
        get() = when (val current = data) {
            is TrackingDataState.Live -> current.location
            is TrackingDataState.Stale -> current.location
            is TrackingDataState.Offline -> current.lastLocation
            is TrackingDataState.Failure -> current.lastLocation
            else -> null
        }

    val markers: List<MapMarkerSpec>
        get() = buildList {
            visibleLocation?.takeIf { it.isValid }?.let { location ->
                add(
                    MapMarkerSpec(
                        id = "appointment-provider",
                        coordinate = location.coordinate,
                        kind = when {
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

    /** Only a validated road geometry can be rendered as a journey. */
    val routePolyline: MapRoutePolyline?
        get() = route?.geometry?.takeIf { it.isValid }?.let { geometry ->
            MapRoutePolyline("appointment-route", geometry.points.map { it.toMapCoordinate() }, MapRouteStyle.SOLID)
        }
}

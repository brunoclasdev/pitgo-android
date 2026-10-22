package com.trustarx.pitgo.core.routing

import com.trustarx.pitgo.core.map.model.MapCoordinate
import com.trustarx.pitgo.core.routing.data.RouteGeometryDto
import com.trustarx.pitgo.core.routing.model.*
import org.junit.Assert.*
import org.junit.Test

class JourneyRouteTest {
    private val origin = MapCoordinate(0.0, 0.0)
    private val corner = MapCoordinate(0.0, 0.01)
    private val destination = MapCoordinate(0.01, 0.01)
    private fun route(points: List<MapCoordinate> = listOf(origin, corner, destination), duration: Int? = 600) = JourneyRoute(
        points.first(), points.last(), RouteGeometry(points.map { RoutePoint(it.longitude, it.latitude) }, duration), 2_000, 1,
    )

    @Test fun distanceAndTimeComeFromTheRoadRoute() {
        val progress = route().remainingAt(origin)
        assertEquals(2_000.0, progress.distanceMeters, 0.001)
        assertEquals(600, progress.durationSeconds)
        assertTrue(progress.distanceMeters > distanceBetween(origin, destination))
        assertEquals(3, progress.polyline.points.size)
    }

    @Test fun progressTrimsTheTravelledSegmentsAndReducesBothMetrics() {
        val progress = route().remainingAt(corner)
        assertEquals(1_000.0, progress.distanceMeters, 0.1)
        assertEquals(300, progress.durationSeconds)
        assertEquals(listOf(corner, destination), progress.polyline.points)
    }

    @Test fun midwayThroughASegmentInterpolatesRemainingRoute() {
        val progress = route().remainingAt(MapCoordinate(0.0, 0.005))
        assertEquals(1_500.0, progress.distanceMeters, 0.1)
        assertEquals(450, progress.durationSeconds)
    }

    @Test fun nearbyDestinationDoesNotShortcutTheRoadRoute() {
        val nearbyDestination = MapCoordinate(0.0001, 0.0)
        val progress = route(listOf(origin, corner, MapCoordinate(0.0001, 0.01), nearbyDestination)).remainingAt(origin)
        assertEquals(2_000.0, progress.distanceMeters, 0.1)
        assertEquals(600, progress.durationSeconds)
    }

    @Test fun offRouteFixKeepsTheLastRouteAsReference() {
        val progress = route().remainingAt(MapCoordinate(1.0, 1.0))
        assertEquals(2_000.0, progress.distanceMeters, 0.001)
        assertEquals(600, progress.durationSeconds)
        assertEquals(listOf(origin, corner, destination), progress.polyline.points)
    }

    @Test fun reachingTheLastRoutePointHasZeroRemainingDistanceAndTime() {
        val progress = route().remainingAt(destination)
        assertEquals(0.0, progress.distanceMeters, 0.001)
        assertEquals(0, progress.durationSeconds)
    }

    @Test fun legacyRouteWithoutDurationDoesNotInventAnEta() {
        assertNull(route(duration = null).remainingAt(origin).durationSeconds)
    }

    @Test fun invalidDurationOrCoordinatesInvalidateTheRoute() {
        assertFalse(route(duration = -1).geometry.isValid)
        assertFalse(route(duration = 604_801).geometry.isValid)
        assertFalse(RouteGeometry(listOf(RoutePoint(181.0, 0.0), RoutePoint(0.0, 0.0))).isValid)
    }

    @Test(expected = IllegalArgumentException::class)
    fun malformedCoordinatePairCannotSilentlyShortenGeometry() {
        RouteGeometryDto("LineString", listOf(listOf(0.0, 0.0), listOf(1.0), listOf(2.0, 2.0))).toDomain()
    }
}

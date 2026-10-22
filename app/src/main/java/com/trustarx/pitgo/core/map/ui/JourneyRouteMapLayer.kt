package com.trustarx.pitgo.core.map.ui

import android.util.Log
import com.trustarx.pitgo.BuildConfig
import com.trustarx.pitgo.core.map.model.MapRoutePolyline
import com.trustarx.pitgo.core.map.model.MapRouteStyle
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.eq
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.*

/** Road lines share one source, with solid and dashed styling installed independently. */
class JourneyRouteMapLayer(private val id: String) {
    private val sourceId = "$id-source"
    private val casingId = "$id-casing"
    private val dashedId = "$id-dashed"
    private var previous: MapRoutePolyline? = null

    fun install(style: Style) {
        previous = null
        listOf(id, dashedId, casingId).forEach(style::removeLayer)
        style.removeSource(sourceId)
        style.addSource(GeoJsonSource(sourceId, FeatureCollection.fromFeatures(emptyList()),
            GeoJsonOptions().withSynchronousUpdate(true)))
        style.addLayer(LineLayer(casingId, sourceId).withProperties(
            lineColor(0xFFFFFFFF.toInt()), lineWidth(9f), lineOpacity(1f), lineCap("round"), lineJoin("round"),
        ))
        // A solid route must use the native solid-line renderer, without a dash pattern.
        style.addLayer(LineLayer(id, sourceId)
            .withFilter(eq(get("route_style"), MapRouteStyle.SOLID.name))
            .withProperties(lineColor(0xFF6C36D9.toInt()), lineWidth(6f), lineOpacity(1f), lineCap("round"), lineJoin("round")))
        style.addLayer(LineLayer(dashedId, sourceId)
            .withFilter(eq(get("route_style"), MapRouteStyle.DASHED.name))
            .withProperties(lineColor(0xFF8A8A8A.toInt()), lineWidth(5f), lineOpacity(1f),
                lineDasharray(arrayOf(2f, 2f)), lineCap("round"), lineJoin("round")))
    }

    fun update(style: Style, route: MapRoutePolyline?) {
        if (route == previous) return
        val source = style.getSourceAs<GeoJsonSource>(sourceId) ?: return
        if (route == null || route.points.size < 2 || route.points.any { !it.isValid }) {
            source.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        } else {
            val line = LineString.fromLngLats(route.points.map { Point.fromLngLat(it.longitude, it.latitude) })
            val feature = Feature.fromGeometry(line, null, route.id).apply { addStringProperty("route_style", route.style.name) }
            source.setGeoJson(FeatureCollection.fromFeature(feature))
            if (BuildConfig.DEBUG) Log.d("PitGoRoute", "source=$id points=${route.points.size} distinct=${route.points.distinct().size} style=${route.style}")
        }
        previous = route
    }

    fun isRendered(map: MapLibreMap, route: MapRoutePolyline, width: Int, height: Int): Boolean =
        map.queryRenderedFeatures(android.graphics.RectF(0f, 0f, width.toFloat(), height.toFloat()), id, dashedId)
            .any { it.id() == route.id }
}

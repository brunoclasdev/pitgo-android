package com.trustarx.pitgo.core.map.ui

import android.content.Context
import com.trustarx.pitgo.R
import com.trustarx.pitgo.core.map.model.MapFeatureSelection
import com.trustarx.pitgo.core.map.model.MapMarkerKind
import com.trustarx.pitgo.core.map.model.MapRenderModel
import com.trustarx.pitgo.core.map.model.MapRouteStyle
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.eq
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconSize
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineDasharray
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/**
 * Exact-participant map styling (client/PitTec markers plus a direct line between them), shared
 * by the real AppointmentTracking screen and the mocked PitTec tracking content — see
 * specs/017-pittec-mocked-ui research.md #7. Promoted out of the `appointmenttracking` feature
 * because it now has two independent consumers and no dispatch-specific behavior.
 */
class ParticipantMapStyleController : MapLibreStyleController {
    override val clickableLayerIds: Array<String> = emptyArray()

    override fun install(context: Context, style: Style) {
        listOf(ROUTE_LAYER, LIVE_LAYER, STALE_LAYER, CLIENT_LAYER, PITTEC_LIVE_LAYER).forEach(style::removeLayer)
        style.removeSource(SOURCE_ID)
        style.removeSource(ROUTE_SOURCE_ID)
        listOf(LIVE_IMAGE, STALE_IMAGE, CLIENT_IMAGE, PITTEC_LIVE_IMAGE).forEach(style::removeImage)
        context.getDrawable(R.drawable.ic_map_live)?.let { style.addImage(LIVE_IMAGE, it) }
        context.getDrawable(R.drawable.ic_map_stale)?.let { style.addImage(STALE_IMAGE, it) }
        context.getDrawable(R.drawable.ic_map_client_vehicle)?.let { style.addImage(CLIENT_IMAGE, it) }
        context.getDrawable(R.drawable.ic_map_pittec_accepted)?.let { style.addImage(PITTEC_LIVE_IMAGE, it) }
        style.addSource(GeoJsonSource(ROUTE_SOURCE_ID, FeatureCollection.fromFeatures(emptyList())))
        style.addSource(
            GeoJsonSource(
                SOURCE_ID,
                FeatureCollection.fromFeatures(emptyList()),
                GeoJsonOptions().withSynchronousUpdate(true),
            )
        )
        // Route sits under the markers so the participant icons remain on top of the line.
        style.addLayer(
            LineLayer(ROUTE_LAYER, ROUTE_SOURCE_ID)
                .withProperties(
                    lineColor(COLOR_ROUTE),
                    lineWidth(4f),
                    lineCap("round"),
                    lineJoin("round"),
                )
        )
        style.addLayer(markerLayer(LIVE_LAYER, LIVE_IMAGE, MapMarkerKind.LIVE))
        style.addLayer(markerLayer(STALE_LAYER, STALE_IMAGE, MapMarkerKind.STALE))
        style.addLayer(markerLayer(PITTEC_LIVE_LAYER, PITTEC_LIVE_IMAGE, MapMarkerKind.PITTEC_LIVE))
        style.addLayer(markerLayer(CLIENT_LAYER, CLIENT_IMAGE, MapMarkerKind.CLIENT_LOCATION))
    }

    override fun update(style: Style, model: MapRenderModel) {
        val source = style.getSourceAs<GeoJsonSource>(SOURCE_ID) ?: return
        val features = model.markers.filter { it.coordinate.isValid }.map { marker ->
            Feature.fromGeometry(
                Point.fromLngLat(marker.coordinate.longitude, marker.coordinate.latitude),
                null,
                marker.id,
            ).apply { addStringProperty(PROPERTY_KIND, marker.kind.name) }
        }
        source.setGeoJson(FeatureCollection.fromFeatures(features))

        val routeLayer = style.getLayerAs<LineLayer>(ROUTE_LAYER)
        val route = model.route
        if (route != null && route.points.size >= 2) {
            routeLayer?.setProperties(
                lineDasharray(if (route.style == MapRouteStyle.DASHED) arrayOf(2f, 2f) else arrayOf(1f)),
                lineColor(if (route.style == MapRouteStyle.DASHED) COLOR_ROUTE_ESTIMATED else COLOR_ROUTE),
            )
            val line = LineString.fromLngLats(
                route.points.map { Point.fromLngLat(it.longitude, it.latitude) },
            )
            style.getSourceAs<GeoJsonSource>(ROUTE_SOURCE_ID)
                ?.setGeoJson(FeatureCollection.fromFeature(Feature.fromGeometry(line)))
        } else {
            style.getSourceAs<GeoJsonSource>(ROUTE_SOURCE_ID)?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        }
    }

    override fun selection(features: List<Feature>) = MapFeatureSelection.None

    private fun markerLayer(id: String, image: String, kind: MapMarkerKind) =
        SymbolLayer(id, SOURCE_ID)
            .withFilter(eq(get(PROPERTY_KIND), kind.name))
            .withProperties(
                iconImage(image),
                iconSize(1f),
                iconAllowOverlap(true),
                iconIgnorePlacement(true),
            )

    private companion object {
        const val SOURCE_ID = "appointment-location-source"
        const val ROUTE_SOURCE_ID = "appointment-route-source"
        const val LIVE_LAYER = "appointment-location-live-layer"
        const val STALE_LAYER = "appointment-location-stale-layer"
        const val CLIENT_LAYER = "appointment-location-client-layer"
        const val PITTEC_LIVE_LAYER = "appointment-location-pittec-live-layer"
        const val ROUTE_LAYER = "appointment-route-layer"
        const val LIVE_IMAGE = "appointment-location-live-image"
        const val STALE_IMAGE = "appointment-location-stale-image"
        const val CLIENT_IMAGE = "appointment-location-client-image"
        const val PITTEC_LIVE_IMAGE = "appointment-location-pittec-live-image"
        const val PROPERTY_KIND = "kind"
        const val COLOR_ROUTE = 0xFF6C36D9.toInt()
        const val COLOR_ROUTE_ESTIMATED = 0xFF8A8A8A.toInt()
    }
}

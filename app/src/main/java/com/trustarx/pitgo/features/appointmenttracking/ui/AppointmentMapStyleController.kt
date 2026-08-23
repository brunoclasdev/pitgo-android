package com.trustarx.pitgo.features.appointmenttracking.ui

import android.content.Context
import com.trustarx.pitgo.R
import com.trustarx.pitgo.core.map.model.MapFeatureSelection
import com.trustarx.pitgo.core.map.model.MapMarkerKind
import com.trustarx.pitgo.core.map.model.MapRenderModel
import com.trustarx.pitgo.core.map.ui.MapLibreStyleController
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.eq
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconSize
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

class AppointmentMapStyleController : MapLibreStyleController {
    override val clickableLayerIds: Array<String> = emptyArray()

    override fun install(context: Context, style: Style) {
        listOf(LIVE_LAYER, STALE_LAYER).forEach(style::removeLayer)
        style.removeSource(SOURCE_ID)
        listOf(LIVE_IMAGE, STALE_IMAGE).forEach(style::removeImage)
        context.getDrawable(R.drawable.ic_map_live)?.let { style.addImage(LIVE_IMAGE, it) }
        context.getDrawable(R.drawable.ic_map_stale)?.let { style.addImage(STALE_IMAGE, it) }
        style.addSource(
            GeoJsonSource(
                SOURCE_ID,
                FeatureCollection.fromFeatures(emptyList()),
                GeoJsonOptions().withSynchronousUpdate(true),
            )
        )
        style.addLayer(markerLayer(LIVE_LAYER, LIVE_IMAGE, MapMarkerKind.LIVE))
        style.addLayer(markerLayer(STALE_LAYER, STALE_IMAGE, MapMarkerKind.STALE))
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
        const val LIVE_LAYER = "appointment-location-live-layer"
        const val STALE_LAYER = "appointment-location-stale-layer"
        const val LIVE_IMAGE = "appointment-location-live-image"
        const val STALE_IMAGE = "appointment-location-stale-image"
        const val PROPERTY_KIND = "kind"
    }
}

package com.trustarx.pitgo.features.pittec.ui

import android.content.Context
import com.trustarx.pitgo.R
import com.trustarx.pitgo.core.map.model.MapFeatureSelection
import com.trustarx.pitgo.core.map.model.MapMarkerKind
import com.trustarx.pitgo.core.map.model.MapRenderModel
import com.trustarx.pitgo.core.map.ui.MapLibreStyleController
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.eq
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleOpacity
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconSize
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

/**
 * Renders the client's own position plus anonymized, server-approximated PitTec markers. There is
 * no tappable provider identity before acceptance (spec FR privacy rules), so this controller has
 * no click layers and [selection] always reports [MapFeatureSelection.None] — the underlying grid
 * approximation already makes individual providers indistinguishable, and exposing a click target
 * would invite building a UI on top of data the backend deliberately keeps anonymous.
 */
class PitTecMapStyleController : MapLibreStyleController {
    override val clickableLayerIds: Array<String> = emptyArray()

    override fun install(context: Context, style: Style) {
        removeRuntimeArtifacts(style)
        context.getDrawable(R.drawable.ic_map_client_location)?.let { style.addImage(IMAGE_CLIENT, it) }
        style.addSource(GeoJsonSource(SOURCE_ID, FeatureCollection.fromFeatures(emptyList())))
        style.addSource(GeoJsonSource(CLIENT_SOURCE_ID, FeatureCollection.fromFeatures(emptyList())))
        // A soft, larger halo behind each dot communicates the ~500 m uncertainty radius without
        // relying on color alone — the dot underneath still reads clearly at every zoom level.
        style.addLayer(
            CircleLayer(HALO_LAYER, SOURCE_ID)
                .withFilter(eq(get(PROPERTY_KIND), MapMarkerKind.PITTEC_APPROXIMATE.name))
                .withProperties(
                    circleColor(COLOR_PURPLE),
                    circleOpacity(0.18f),
                    circleRadius(26f),
                )
        )
        style.addLayer(
            CircleLayer(DOT_LAYER, SOURCE_ID)
                .withFilter(eq(get(PROPERTY_KIND), MapMarkerKind.PITTEC_APPROXIMATE.name))
                .withProperties(
                    circleColor(COLOR_PURPLE),
                    circleRadius(9f),
                    circleStrokeColor(COLOR_WHITE),
                    circleStrokeWidth(3f),
                )
        )
        style.addLayer(
            SymbolLayer(CLIENT_LAYER, CLIENT_SOURCE_ID)
                .withProperties(
                    iconImage(IMAGE_CLIENT),
                    iconSize(1f),
                    iconAllowOverlap(true),
                    iconIgnorePlacement(true),
                )
        )
    }

    override fun update(style: Style, model: MapRenderModel) {
        val approximate = model.markers
            .asSequence()
            .filter { it.kind == MapMarkerKind.PITTEC_APPROXIMATE && it.coordinate.isValid }
            .map { marker ->
                Feature.fromGeometry(
                    Point.fromLngLat(marker.coordinate.longitude, marker.coordinate.latitude),
                    null,
                    marker.id,
                ).apply { addStringProperty(PROPERTY_KIND, marker.kind.name) }
            }
            .toList()
        style.getSourceAs<GeoJsonSource>(SOURCE_ID)?.setGeoJson(FeatureCollection.fromFeatures(approximate))

        val client = model.markers
            .asSequence()
            .filter { it.kind == MapMarkerKind.CLIENT_LOCATION && it.coordinate.isValid }
            .map { marker ->
                Feature.fromGeometry(
                    Point.fromLngLat(marker.coordinate.longitude, marker.coordinate.latitude),
                    null,
                    marker.id,
                )
            }
            .toList()
        style.getSourceAs<GeoJsonSource>(CLIENT_SOURCE_ID)?.setGeoJson(FeatureCollection.fromFeatures(client))
    }

    override fun selection(features: List<Feature>): MapFeatureSelection = MapFeatureSelection.None

    private fun removeRuntimeArtifacts(style: Style) {
        listOf(CLIENT_LAYER, DOT_LAYER, HALO_LAYER).forEach(style::removeLayer)
        style.removeSource(CLIENT_SOURCE_ID)
        style.removeSource(SOURCE_ID)
        style.removeImage(IMAGE_CLIENT)
    }

    private companion object {
        const val SOURCE_ID = "pittec-approximate-source"
        const val CLIENT_SOURCE_ID = "pittec-client-source"
        const val HALO_LAYER = "pittec-approximate-halo-layer"
        const val DOT_LAYER = "pittec-approximate-dot-layer"
        const val CLIENT_LAYER = "pittec-client-layer"
        const val IMAGE_CLIENT = "pittec-client-image"
        const val PROPERTY_KIND = "kind"
        const val COLOR_PURPLE = 0xFF6C36D9.toInt()
        const val COLOR_WHITE = 0xFFFFFFFF.toInt()
    }
}

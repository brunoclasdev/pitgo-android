package com.trustarx.pitgo.features.mobiledispatch.ui

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
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconSize
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

class MobileDispatchMapStyleController : MapLibreStyleController {
    override val clickableLayerIds = arrayOf(CANDIDATE_LAYER)

    override fun install(context: Context, style: Style) {
        style.removeLayer(CANDIDATE_LAYER); style.removeSource(SOURCE)
        style.removeImage(CAR_IMAGE)
        context.getDrawable(R.drawable.ic_map_mobile_provider_selected)?.let { style.addImage(CAR_IMAGE, it) }
        style.addSource(GeoJsonSource(SOURCE, FeatureCollection.fromFeatures(emptyList())))
        style.addLayer(
            SymbolLayer(CANDIDATE_LAYER, SOURCE)
                .withFilter(eq(get("kind"), MapMarkerKind.MOBILE_PROVIDER.name))
                .withProperties(iconImage(CAR_IMAGE), iconSize(1f), iconAllowOverlap(true)),
        )
    }

    override fun update(style: Style, model: MapRenderModel) {
        val features = model.markers.filter { it.coordinate.isValid }.map { marker ->
            Feature.fromGeometry(Point.fromLngLat(marker.coordinate.longitude, marker.coordinate.latitude), null, marker.id)
                .apply { addStringProperty("id", marker.id); addStringProperty("kind", marker.kind.name) }
        }
        style.getSourceAs<GeoJsonSource>(SOURCE)?.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    override fun selection(features: List<Feature>): MapFeatureSelection = features.firstOrNull()
        ?.getStringProperty("id")?.let(MapFeatureSelection::Single) ?: MapFeatureSelection.None

    private companion object {
        const val SOURCE = "mobile-dispatch-source"
        const val CANDIDATE_LAYER = "mobile-dispatch-candidate-layer"
        const val CAR_IMAGE = "mobile-dispatch-car"
    }
}

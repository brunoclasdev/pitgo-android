package com.trustarx.pitgo.features.providermap.ui

import android.content.Context
import com.trustarx.pitgo.R
import com.trustarx.pitgo.core.map.model.MapCoordinate
import com.trustarx.pitgo.core.map.model.MapFeatureSelection
import com.trustarx.pitgo.core.map.model.MapMarkerKind
import com.trustarx.pitgo.core.map.model.MapRenderModel
import com.trustarx.pitgo.core.map.ui.MapLibreStyleController
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.all
import org.maplibre.android.style.expressions.Expression.eq
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.has
import org.maplibre.android.style.expressions.Expression.not
import org.maplibre.android.style.expressions.Expression.toString
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconSize
import org.maplibre.android.style.layers.PropertyFactory.textAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.PropertyFactory.textSize
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

class ProviderMapStyleController : MapLibreStyleController {
    override val clickableLayerIds = arrayOf(
        CLUSTER_LAYER,
        GROUP_LAYER,
        SELECTED_LAYER,
        PROVIDER_LAYER,
        MOBILE_SELECTED_LAYER,
        MOBILE_LAYER,
        SELECTED_DOT_LAYER,
        PROVIDER_DOT_LAYER,
    )

    override fun install(context: Context, style: Style) {
        removeRuntimeArtifacts(style)
        context.getDrawable(R.drawable.ic_map_provider_address)?.let {
            style.addImage(IMAGE_PROVIDER, it)
        }
        context.getDrawable(R.drawable.ic_map_provider_selected)?.let {
            style.addImage(IMAGE_SELECTED, it)
        }
        context.getDrawable(R.drawable.ic_map_provider_group)?.let {
            style.addImage(IMAGE_GROUP, it)
        }
        context.getDrawable(R.drawable.ic_map_mobile_provider)?.let {
            style.addImage(IMAGE_MOBILE, it)
        }
        context.getDrawable(R.drawable.ic_map_mobile_provider_selected)?.let {
            style.addImage(IMAGE_MOBILE_SELECTED, it)
        }
        context.getDrawable(R.drawable.ic_map_client_location)?.let {
            style.addImage(IMAGE_CLIENT_LOCATION, it)
        }
        style.addSource(
            GeoJsonSource(
                SOURCE_ID,
                FeatureCollection.fromFeatures(emptyList()),
                GeoJsonOptions()
                    .withCluster(true)
                    .withClusterRadius(50)
                    .withClusterMaxZoom(CLUSTER_MAX_ZOOM),
            )
        )
        style.addSource(
            GeoJsonSource(USER_SOURCE_ID, FeatureCollection.fromFeatures(emptyList()))
        )
        style.addLayer(
            CircleLayer(CLUSTER_LAYER, SOURCE_ID)
                .withFilter(has(PROPERTY_POINT_COUNT))
                .withProperties(
                    circleColor(COLOR_PURPLE),
                    circleRadius(22f),
                    circleStrokeColor(COLOR_WHITE),
                    circleStrokeWidth(3f),
                )
        )
        style.addLayer(
            SymbolLayer(CLUSTER_COUNT_LAYER, SOURCE_ID)
                .withFilter(has(PROPERTY_POINT_COUNT))
                .withProperties(
                    textField(toString(get(PROPERTY_POINT_COUNT_ABBREVIATED))),
                    textSize(14f),
                    textColor(COLOR_WHITE),
                    textAllowOverlap(true),
                )
        )
        style.addLayer(
            CircleLayer(PROVIDER_DOT_LAYER, SOURCE_ID)
                .withFilter(
                    all(
                        not(has(PROPERTY_POINT_COUNT)),
                        eq(get(PROPERTY_KIND), MapMarkerKind.ADDRESS.name),
                    )
                )
                .withProperties(
                    circleColor(COLOR_PURPLE),
                    circleRadius(11f),
                    circleStrokeColor(COLOR_WHITE),
                    circleStrokeWidth(4f),
                )
        )
        style.addLayer(
            CircleLayer(SELECTED_DOT_LAYER, SOURCE_ID)
                .withFilter(
                    all(
                        not(has(PROPERTY_POINT_COUNT)),
                        eq(get(PROPERTY_KIND), MapMarkerKind.ADDRESS.name),
                        eq(get(PROPERTY_SELECTED), true),
                    )
                )
                .withProperties(
                    circleColor(COLOR_SELECTED),
                    circleRadius(16f),
                    circleStrokeColor(COLOR_WHITE),
                    circleStrokeWidth(5f),
                )
        )
        style.addLayer(markerLayer(PROVIDER_LAYER, IMAGE_PROVIDER, MapMarkerKind.ADDRESS, false))
        style.addLayer(markerLayer(SELECTED_LAYER, IMAGE_SELECTED, MapMarkerKind.ADDRESS, true))
        style.addLayer(markerLayer(MOBILE_LAYER, IMAGE_MOBILE, MapMarkerKind.MOBILE_PROVIDER, false))
        style.addLayer(markerLayer(MOBILE_SELECTED_LAYER, IMAGE_MOBILE_SELECTED, MapMarkerKind.MOBILE_PROVIDER, true))
        style.addLayer(markerLayer(GROUP_LAYER, IMAGE_GROUP, MapMarkerKind.COINCIDENT_GROUP, null))
        style.addLayer(
            SymbolLayer(USER_LAYER, USER_SOURCE_ID)
                .withProperties(
                    iconImage(IMAGE_CLIENT_LOCATION),
                    iconSize(1f),
                    iconAllowOverlap(true),
                    iconIgnorePlacement(true),
                )
        )
    }

    override fun update(style: Style, model: MapRenderModel) {
        val source = style.getSourceAs<GeoJsonSource>(SOURCE_ID) ?: return
        val features = model.markers
            .asSequence()
            .filter { it.kind != MapMarkerKind.CLIENT_LOCATION }
            .filter { it.coordinate.isValid }
            .map { marker ->
                Feature.fromGeometry(
                    Point.fromLngLat(marker.coordinate.longitude, marker.coordinate.latitude),
                    null,
                    marker.id,
                ).apply {
                    addStringProperty(PROPERTY_ID, marker.id)
                    addStringProperty(PROPERTY_KIND, marker.kind.name)
                    addBooleanProperty(PROPERTY_SELECTED, marker.selected)
                    marker.groupKey?.let { addStringProperty(PROPERTY_GROUP_KEY, it) }
                }
            }
            .toList()
        source.setGeoJson(FeatureCollection.fromFeatures(features))
        val userFeatures = model.markers
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
        style.getSourceAs<GeoJsonSource>(USER_SOURCE_ID)
            ?.setGeoJson(FeatureCollection.fromFeatures(userFeatures))
    }

    override fun selection(features: List<Feature>): MapFeatureSelection {
        val feature = features.firstOrNull() ?: return MapFeatureSelection.None
        if (feature.hasProperty(PROPERTY_POINT_COUNT)) {
            val point = feature.geometry() as? Point ?: return MapFeatureSelection.None
            return MapFeatureSelection.NearbyCluster(
                MapCoordinate(point.latitude(), point.longitude())
            )
        }
        feature.getStringProperty(PROPERTY_GROUP_KEY)?.takeIf(String::isNotBlank)?.let {
            return MapFeatureSelection.CoincidentGroup(it)
        }
        return feature.getStringProperty(PROPERTY_ID)
            ?.takeIf(String::isNotBlank)
            ?.let(MapFeatureSelection::Single)
            ?: MapFeatureSelection.None
    }

    private fun markerLayer(
        id: String,
        image: String,
        kind: MapMarkerKind,
        selected: Boolean?,
    ) = SymbolLayer(id, SOURCE_ID)
        .withFilter(
            all(*buildList {
                add(not(has(PROPERTY_POINT_COUNT)))
                add(eq(get(PROPERTY_KIND), kind.name))
                selected?.let { add(eq(get(PROPERTY_SELECTED), it)) }
            }.toTypedArray())
        )
        .withProperties(
            iconImage(image),
            iconSize(1f),
            iconAllowOverlap(true),
            iconIgnorePlacement(true),
        )

    private fun removeRuntimeArtifacts(style: Style) {
        listOf(
            USER_LAYER,
            CLUSTER_COUNT_LAYER,
            CLUSTER_LAYER,
            GROUP_LAYER,
            SELECTED_LAYER,
            PROVIDER_LAYER,
            MOBILE_SELECTED_LAYER,
            MOBILE_LAYER,
            SELECTED_DOT_LAYER,
            PROVIDER_DOT_LAYER,
        )
            .forEach(style::removeLayer)
        style.removeSource(USER_SOURCE_ID)
        style.removeSource(SOURCE_ID)
        listOf(
            IMAGE_PROVIDER,
            IMAGE_SELECTED,
            IMAGE_GROUP,
            IMAGE_MOBILE,
            IMAGE_MOBILE_SELECTED,
            IMAGE_CLIENT_LOCATION,
        )
            .forEach(style::removeImage)
    }

    companion object {
        const val CLUSTER_MAX_ZOOM = 15
        private const val SOURCE_ID = "provider-address-source"
        private const val USER_SOURCE_ID = "client-location-source"
        private const val CLUSTER_LAYER = "provider-cluster-layer"
        private const val CLUSTER_COUNT_LAYER = "provider-cluster-count-layer"
        private const val PROVIDER_LAYER = "provider-address-layer"
        private const val SELECTED_LAYER = "provider-address-selected-layer"
        private const val GROUP_LAYER = "provider-address-group-layer"
        private const val MOBILE_LAYER = "provider-mobile-layer"
        private const val MOBILE_SELECTED_LAYER = "provider-mobile-selected-layer"
        private const val USER_LAYER = "client-location-layer"
        private const val PROVIDER_DOT_LAYER = "provider-address-dot-layer"
        private const val SELECTED_DOT_LAYER = "provider-address-selected-dot-layer"
        private const val IMAGE_PROVIDER = "provider-address-image"
        private const val IMAGE_SELECTED = "provider-address-selected-image"
        private const val IMAGE_GROUP = "provider-address-group-image"
        private const val IMAGE_MOBILE = "provider-mobile-image"
        private const val IMAGE_MOBILE_SELECTED = "provider-mobile-selected-image"
        private const val IMAGE_CLIENT_LOCATION = "client-location-image"
        private const val PROPERTY_ID = "id"
        private const val PROPERTY_KIND = "kind"
        private const val PROPERTY_SELECTED = "selected"
        private const val PROPERTY_GROUP_KEY = "groupKey"
        private const val PROPERTY_POINT_COUNT = "point_count"
        private const val PROPERTY_POINT_COUNT_ABBREVIATED = "point_count_abbreviated"
        private const val COLOR_PURPLE = 0xFF6C36D9.toInt()
        private const val COLOR_SELECTED = 0xFF4A148C.toInt()
        private const val COLOR_WHITE = 0xFFFFFFFF.toInt()
    }
}

package com.trustarx.pitgo.core.map.ui

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.graphics.PointF
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.trustarx.pitgo.core.map.config.ManagedMapStyleConfiguration
import com.trustarx.pitgo.core.map.model.MapCoordinate
import com.trustarx.pitgo.core.map.model.MapFeatureSelection
import com.trustarx.pitgo.core.map.model.MapRenderModel
import com.trustarx.pitgo.core.map.model.MapResourceFailureKind
import com.trustarx.pitgo.core.map.model.MapResourceState
import com.trustarx.pitgo.core.map.model.MapStyleState
import com.trustarx.pitgo.core.map.model.MapSurfaceState
import com.trustarx.pitgo.core.map.model.MapViewport
import com.trustarx.pitgo.core.map.model.MapViewportCommand
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.tile.TileOperation

interface MapLibreStyleController {
    val clickableLayerIds: Array<String>

    fun install(context: Context, style: Style)

    fun update(style: Style, model: MapRenderModel)

    fun selection(features: List<org.maplibre.geojson.Feature>): MapFeatureSelection
}

@Composable
fun MapLibreMapHost(
    configuration: ManagedMapStyleConfiguration,
    renderModel: MapRenderModel,
    viewport: MapViewport,
    cameraCommand: MapViewportCommand?,
    styleReloadRevision: Long,
    online: Boolean,
    styleController: MapLibreStyleController,
    onViewportIdle: (MapViewport) -> Unit,
    onFeatureClick: (MapFeatureSelection) -> Unit,
    onSurfaceState: (MapSurfaceState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val clickRadiusPx = with(LocalDensity.current) { 24f * density }
    val holder = remember {
        MapLibreHostHolder(
            context = context.applicationContext,
            configuration = configuration,
            onViewportIdle = onViewportIdle,
            onFeatureClick = onFeatureClick,
            onSurfaceState = onSurfaceState,
        )
    }
    var boundView by remember { mutableStateOf<MapView?>(null) }

    holder.updateCallbacks(onViewportIdle, onFeatureClick, onSurfaceState)
    holder.update(
        model = renderModel,
        viewport = viewport,
        cameraCommand = cameraCommand,
        styleReloadRevision = styleReloadRevision,
        online = online,
        controller = styleController,
        clickRadiusPx = clickRadiusPx,
    )

    DisposableEffect(lifecycleOwner, boundView) {
        val observer = object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) { boundView?.onStart() }
            override fun onResume(owner: LifecycleOwner) { boundView?.onResume() }
            override fun onPause(owner: LifecycleOwner) { boundView?.onPause() }
            override fun onStop(owner: LifecycleOwner) { boundView?.onStop() }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AndroidView(
        factory = { viewContext ->
            MapView(
                viewContext,
                MapLibreMapOptions.createFromAttributes(viewContext)
                    .logoEnabled(true)
                    .attributionEnabled(true)
                    .compassEnabled(false),
            ).also { mapView ->
                boundView = mapView
                holder.bind(mapView)
            }
        },
        update = { holder.applyLatest() },
        onRelease = { mapView ->
            if (boundView === mapView) boundView = null
            holder.release(mapView)
        },
        modifier = modifier,
    )
}

private class MapLibreHostHolder(
    private val context: Context,
    private val configuration: ManagedMapStyleConfiguration,
    onViewportIdle: (MapViewport) -> Unit,
    onFeatureClick: (MapFeatureSelection) -> Unit,
    onSurfaceState: (MapSurfaceState) -> Unit,
) : ComponentCallbacks2 {
    private val handler = Handler(Looper.getMainLooper())
    private var mapView: MapView? = null
    private var map: MapLibreMap? = null
    private var style: Style? = null
    private var controller: MapLibreStyleController? = null
    private var latestModel = MapRenderModel()
    private var latestViewport = MapViewport(MapCoordinate(0.0, 0.0), 2.0)
    private var latestCommand: MapViewportCommand? = null
    private var latestStyleRevision = 0L
    private var appliedStyleRevision = Long.MIN_VALUE
    private var appliedCameraRevision = Long.MIN_VALUE
    private var initializedCamera = false
    private var clickRadiusPx = 24f
    private var online = true
    private var destroyed = false
    private var tileErrors = 0
    private var surfaceState = MapSurfaceState()
    private var onViewportIdle = onViewportIdle
    private var onFeatureClick = onFeatureClick
    private var onSurfaceState = onSurfaceState

    private val tileDegrade = Runnable {
        if (tileErrors >= TILE_ERROR_THRESHOLD && online) {
            publishResources(MapResourceState.Degraded(MapResourceFailureKind.TILE))
        }
    }
    private val cameraIdleListener = MapLibreMap.OnCameraIdleListener {
        val current = map?.cameraPosition ?: return@OnCameraIdleListener
        val target = current.target ?: return@OnCameraIdleListener
        onViewportIdle(
            MapViewport(
                center = MapCoordinate(target.latitude, target.longitude),
                zoom = current.zoom,
            )
        )
    }
    private val mapClickListener = MapLibreMap.OnMapClickListener { latLng ->
        val currentMap = map ?: return@OnMapClickListener false
        val currentController = controller ?: return@OnMapClickListener false
        val point = currentMap.projection.toScreenLocation(latLng)
        val area = RectF(
            point.x - clickRadiusPx,
            point.y - clickRadiusPx,
            point.x + clickRadiusPx,
            point.y + clickRadiusPx,
        )
        val features = currentMap.queryRenderedFeatures(area, *currentController.clickableLayerIds)
        val selection = currentController.selection(features)
        if (selection != MapFeatureSelection.None) onFeatureClick(selection)
        selection != MapFeatureSelection.None
    }
    private val mapFailedListener = MapView.OnDidFailLoadingMapListener {
        if (style == null) publishStyle(MapStyleState.Failed(appliedStyleRevision))
        else publishResources(MapResourceState.Degraded(MapResourceFailureKind.RENDER))
    }
    private val tileActionListener = MapView.OnTileActionListener { operation, _, _, _, _, _, _ ->
        when (operation) {
            TileOperation.Error -> {
                tileErrors += 1
                handler.removeCallbacks(tileDegrade)
                handler.postDelayed(tileDegrade, TILE_ERROR_WINDOW_MS)
            }
            TileOperation.LoadFromCache,
            TileOperation.LoadFromNetwork,
            -> {
                tileErrors = 0
                handler.removeCallbacks(tileDegrade)
                if (online) publishResources(MapResourceState.Ready)
            }
            else -> Unit
        }
    }
    private val glyphErrorListener = MapView.OnGlyphsErrorListener { _, _, _ ->
        if (online) publishResources(MapResourceState.Degraded(MapResourceFailureKind.GLYPH))
    }
    private val spriteErrorListener = MapView.OnSpriteErrorListener { _, _ ->
        if (online) publishResources(MapResourceState.Degraded(MapResourceFailureKind.SPRITE))
    }
    private val renderErrorListener = MapView.OnRenderErrorListener {
        if (online) publishResources(MapResourceState.Degraded(MapResourceFailureKind.RENDER))
    }
    private val renderedListener = MapView.OnDidFinishRenderingMapListener { fully ->
        if (fully && online) publishResources(MapResourceState.Ready)
    }

    fun updateCallbacks(
        viewport: (MapViewport) -> Unit,
        feature: (MapFeatureSelection) -> Unit,
        surface: (MapSurfaceState) -> Unit,
    ) {
        onViewportIdle = viewport
        onFeatureClick = feature
        onSurfaceState = surface
    }

    fun bind(view: MapView) {
        if (mapView === view) return
        mapView = view
        destroyed = false
        context.registerComponentCallbacks(this)
        view.onCreate(null)
        view.addOnDidFailLoadingMapListener(mapFailedListener)
        view.addOnTileActionListener(tileActionListener)
        view.addOnGlyphsErrorListener(glyphErrorListener)
        view.addOnSpriteErrorListener(spriteErrorListener)
        view.addOnRenderErrorListener(renderErrorListener)
        view.addOnDidFinishRenderingMapListener(renderedListener)
        view.getMapAsync { readyMap ->
            if (destroyed || mapView !== view) return@getMapAsync
            map = readyMap
            readyMap.addOnCameraIdleListener(cameraIdleListener)
            readyMap.addOnMapClickListener(mapClickListener)
            applyLatest()
        }
    }

    fun update(
        model: MapRenderModel,
        viewport: MapViewport,
        cameraCommand: MapViewportCommand?,
        styleReloadRevision: Long,
        online: Boolean,
        controller: MapLibreStyleController,
        clickRadiusPx: Float,
    ) {
        latestModel = model
        latestViewport = viewport
        latestCommand = cameraCommand
        latestStyleRevision = styleReloadRevision
        this.controller = controller
        this.clickRadiusPx = clickRadiusPx
        if (this.online != online) {
            this.online = online
            if (!online) publishResources(MapResourceState.Offline)
            else if (style != null) publishResources(MapResourceState.Ready)
        }
    }

    fun applyLatest() {
        val currentMap = map ?: return
        if (!initializedCamera && latestViewport.center.isValid) {
            currentMap.moveCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(latestViewport.center.latitude, latestViewport.center.longitude),
                    latestViewport.zoom,
                )
            )
            initializedCamera = true
        }
        latestCommand?.let { command ->
            if (command.revision > appliedCameraRevision && command.viewport.center.isValid) {
                appliedCameraRevision = command.revision
                currentMap.easeCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(
                            command.viewport.center.latitude,
                            command.viewport.center.longitude,
                        ),
                        command.viewport.zoom,
                    ),
                    CAMERA_ANIMATION_MS,
                )
            }
        }
        if (latestStyleRevision != appliedStyleRevision) {
            loadStyle(currentMap, latestStyleRevision)
        } else {
            style?.let { loaded -> controller?.update(loaded, latestModel) }
        }
    }

    private fun loadStyle(currentMap: MapLibreMap, revision: Long) {
        appliedStyleRevision = revision
        style = null
        publishStyle(MapStyleState.Loading(revision))
        publishResources(if (online) MapResourceState.Waiting else MapResourceState.Offline)
        currentMap.setStyle(configuration.styleUrl) { loaded ->
            if (destroyed || revision != appliedStyleRevision) return@setStyle
            style = loaded
            controller?.install(context, loaded)
            controller?.update(loaded, latestModel)
            publishStyle(MapStyleState.Ready(revision))
            publishResources(if (online) MapResourceState.Ready else MapResourceState.Offline)
        }
    }

    fun release(view: MapView) {
        if (destroyed || mapView !== view) return
        destroyed = true
        handler.removeCallbacksAndMessages(null)
        map?.removeOnCameraIdleListener(cameraIdleListener)
        map?.removeOnMapClickListener(mapClickListener)
        view.removeOnDidFailLoadingMapListener(mapFailedListener)
        view.removeOnTileActionListener(tileActionListener)
        view.removeOnGlyphsErrorListener(glyphErrorListener)
        view.removeOnSpriteErrorListener(spriteErrorListener)
        view.removeOnRenderErrorListener(renderErrorListener)
        view.removeOnDidFinishRenderingMapListener(renderedListener)
        runCatching { context.unregisterComponentCallbacks(this) }
        view.onPause()
        view.onStop()
        view.onDestroy()
        map = null
        style = null
        mapView = null
    }

    private fun publishStyle(value: MapStyleState) {
        surfaceState = surfaceState.copy(style = value)
        onSurfaceState(surfaceState)
    }

    private fun publishResources(value: MapResourceState) {
        surfaceState = surfaceState.copy(resources = value)
        onSurfaceState(surfaceState)
    }

    override fun onLowMemory() { mapView?.onLowMemory() }
    override fun onConfigurationChanged(newConfig: Configuration) = Unit
    override fun onTrimMemory(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) mapView?.onLowMemory()
    }

    private companion object {
        const val TILE_ERROR_THRESHOLD = 3
        const val TILE_ERROR_WINDOW_MS = 1_500L
        const val CAMERA_ANIMATION_MS = 350
    }
}

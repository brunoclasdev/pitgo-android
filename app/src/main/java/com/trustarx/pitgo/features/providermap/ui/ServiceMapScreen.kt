package com.trustarx.pitgo.features.providermap.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trustarx.pitgo.core.routing.ui.*
import com.trustarx.pitgo.R
import com.trustarx.pitgo.core.design.theme.PitGoSize
import com.trustarx.pitgo.core.design.theme.PitGoSpacing
import com.trustarx.pitgo.core.map.config.PitGoManagedMapStyle
import com.trustarx.pitgo.core.map.model.MapRenderModel
import com.trustarx.pitgo.core.map.model.MapResourceState
import com.trustarx.pitgo.core.map.model.MapStyleState
import com.trustarx.pitgo.core.map.ui.MapLibreMapHost
import com.trustarx.pitgo.features.providermap.model.ClientLocationState
import com.trustarx.pitgo.features.providermap.model.DiscoveryDataState
import com.trustarx.pitgo.features.providermap.model.ProviderLocationMode
import com.trustarx.pitgo.features.providermap.model.PublicProvider
import com.trustarx.pitgo.features.providermap.model.ServiceMapMode
import com.trustarx.pitgo.features.providermap.model.ServiceMapUiState
import com.trustarx.pitgo.features.providermap.viewmodel.ServiceMapViewModel

@Composable
fun ServiceMapRoute(
    viewModel: ServiceMapViewModel,
    onBack: () -> Unit,
    onRequestService: (providerId: String, serviceId: String) -> Unit,
    onSessionExpired: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        viewModel.onLocationPermissionResult(
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true,
        )
    }
    LifecycleStartEffect(viewModel) {
        onStopOrDispose { viewModel.clearTransientLocation() }
    }
    LaunchedEffect(viewModel) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.onLocationPermissionResult(true)
    }
    val content = state as? ServiceMapUiState.Content
    LaunchedEffect(content?.dataState) {
        if (content?.dataState == DiscoveryDataState.SessionExpired) onSessionExpired()
    }
    ServiceMapScreen(
        state = state,
        styleController = remember { ProviderMapStyleController() },
        onBack = onBack,
        onUseLocation = {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                )
            )
        },
        onSearch = { content?.let { viewModel.search(it.viewport.center) } },
        onMode = viewModel::setMode,
        onSelect = viewModel::selectProvider,
        onDismissSelection = viewModel::dismissSelection,
        onFeatureSelected = viewModel::onFeatureSelected,
        onViewportIdle = viewModel::onViewportIdle,
        onSurfaceState = viewModel::updateSurfaceState,
        onRetryStyle = viewModel::retryStyle,
        onZoom = viewModel::zoomBy,
        onRequestService = onRequestService,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServiceMapScreen(
    state: ServiceMapUiState,
    styleController: ProviderMapStyleController,
    onBack: () -> Unit,
    onUseLocation: () -> Unit,
    onSearch: () -> Unit,
    onMode: (ServiceMapMode) -> Unit,
    onSelect: (String?) -> Unit,
    onDismissSelection: () -> Unit,
    onFeatureSelected: (com.trustarx.pitgo.core.map.model.MapFeatureSelection) -> Unit,
    onViewportIdle: (com.trustarx.pitgo.core.map.model.MapViewport) -> Unit,
    onSurfaceState: (com.trustarx.pitgo.core.map.model.MapSurfaceState) -> Unit,
    onRetryStyle: () -> Unit,
    onZoom: (Double) -> Unit,
    onRequestService: (providerId: String, serviceId: String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.service_map_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.ic_back),
                            contentDescription = stringResource(R.string.map_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (state !is ServiceMapUiState.Content) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.service_map_invalid))
            }
            return@Scaffold
        }
        val selected = state.selectedProvider
        val mobile = selected?.locationMode == ProviderLocationMode.MOBILE
        val journey = rememberJourneyRoute(
            origin = if (mobile) selected?.addressCoordinate else state.clientCoordinate,
            destination = if (mobile) state.clientCoordinate else selected?.addressCoordinate,
            enabled = selected != null,
            online = state.dataState != DiscoveryDataState.Offline,
            routeKey = selected?.id,
        )
        Column(Modifier.fillMaxSize().padding(padding)) {
            ModeAndLocationControls(state, onMode, onUseLocation)
            StateBanner(state, onSearch, onRetryStyle)
            when (state.mode) {
                ServiceMapMode.MAP -> ProviderMap(
                    state = state,
                    journey = journey,
                    styleController = styleController,
                    onSearch = onSearch,
                    onFeatureSelected = onFeatureSelected,
                    onViewportIdle = onViewportIdle,
                    onSurfaceState = onSurfaceState,
                    onRetryStyle = onRetryStyle,
                    onZoom = onZoom,
                    modifier = Modifier.weight(1f),
                )
                ServiceMapMode.LIST -> ProviderList(
                    providers = state.providers,
                    onSelect = onSelect,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        state.selectedProvider?.let { provider ->
            ProviderSummarySheet(
                provider = provider,
                journey = journey,
                onDismiss = onDismissSelection,
                onRequest = { onRequestService(provider.id, state.serviceId) },
            )
        }
        if (state.selectedGroupProviders.isNotEmpty()) {
            ModalBottomSheet(onDismissRequest = onDismissSelection) {
                Text(
                    stringResource(R.string.service_map_group_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier
                        .padding(horizontal = PitGoSpacing.Medium)
                        .semantics { heading() },
                )
                ProviderList(
                    providers = state.selectedGroupProviders,
                    onSelect = onSelect,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModeAndLocationControls(
    state: ServiceMapUiState.Content,
    onMode: (ServiceMapMode) -> Unit,
    onUseLocation: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = PitGoSpacing.Medium),
        verticalArrangement = Arrangement.spacedBy(PitGoSpacing.ExtraSmall),
    ) {
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(PitGoSpacing.Small),
            verticalArrangement = Arrangement.spacedBy(PitGoSpacing.ExtraSmall),
        ) {
            FilterChip(
                selected = state.mode == ServiceMapMode.MAP,
                onClick = { onMode(ServiceMapMode.MAP) },
                label = { Text(stringResource(R.string.service_map_map)) },
            )
            FilterChip(
                selected = state.mode == ServiceMapMode.LIST,
                onClick = { onMode(ServiceMapMode.LIST) },
                label = { Text(stringResource(R.string.service_map_list)) },
            )
            TextButton(onClick = onUseLocation) {
                Text(stringResource(R.string.service_map_my_location))
            }
        }
        Text(
            stringResource(R.string.service_map_result_count, state.providers.size),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StateBanner(
    state: ServiceMapUiState.Content,
    onRetryData: () -> Unit,
    onRetryStyle: () -> Unit,
) {
    val message = when {
        state.dataState == DiscoveryDataState.Offline -> R.string.map_offline
        state.dataState == DiscoveryDataState.Failure -> R.string.service_map_failure
        state.dataState == DiscoveryDataState.Empty -> R.string.service_map_empty
        state.dataState == DiscoveryDataState.SessionExpired -> R.string.service_map_session_expired
        state.clientLocationState == ClientLocationState.Denied -> R.string.service_map_location_denied
        state.clientLocationState == ClientLocationState.Unavailable -> R.string.service_map_location_unavailable
        state.surfaceState.style is MapStyleState.Failed -> R.string.map_style_failure
        state.surfaceState.resources is MapResourceState.Degraded -> R.string.map_resource_degraded
        else -> null
    }
    if (message != null) {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PitGoSpacing.Medium)
                .semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Row(
                Modifier.padding(PitGoSpacing.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(message), Modifier.weight(1f))
                if (state.surfaceState.style is MapStyleState.Failed ||
                    state.surfaceState.resources is MapResourceState.Degraded
                ) {
                    TextButton(onClick = onRetryStyle) { Text(stringResource(R.string.map_retry)) }
                } else if (state.dataState == DiscoveryDataState.Failure ||
                    state.dataState == DiscoveryDataState.Offline
                ) {
                    TextButton(onClick = onRetryData) { Text(stringResource(R.string.map_retry)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProviderMap(
    state: ServiceMapUiState.Content,
    journey: JourneyRouteUiState,
    styleController: ProviderMapStyleController,
    onSearch: () -> Unit,
    onFeatureSelected: (com.trustarx.pitgo.core.map.model.MapFeatureSelection) -> Unit,
    onViewportIdle: (com.trustarx.pitgo.core.map.model.MapViewport) -> Unit,
    onSurfaceState: (com.trustarx.pitgo.core.map.model.MapSurfaceState) -> Unit,
    onRetryStyle: () -> Unit,
    onZoom: (Double) -> Unit,
    modifier: Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val mapDescription = stringResource(R.string.map_region_description, state.providers.size)
    Box(
        modifier.semantics {
            contentDescription = mapDescription
        },
    ) {
        MapLibreMapHost(
            configuration = PitGoManagedMapStyle.configuration,
            renderModel = MapRenderModel(state.markers, journey.polyline),
            viewport = state.viewport,
            cameraCommand = rememberJourneyCamera(state.cameraCommand, journey.polyline),
            styleReloadRevision = state.styleReloadRevision,
            online = state.dataState != DiscoveryDataState.Offline,
            styleController = styleController,
            onViewportIdle = onViewportIdle,
            onFeatureClick = onFeatureSelected,
            onSurfaceState = onSurfaceState,
            modifier = Modifier.fillMaxSize(),
        )
        FlowRow(
            Modifier.align(Alignment.TopCenter).padding(PitGoSpacing.Small),
            horizontalArrangement = Arrangement.spacedBy(PitGoSpacing.Small),
            verticalArrangement = Arrangement.spacedBy(PitGoSpacing.ExtraSmall),
        ) {
            Button(onClick = onSearch) { Text(stringResource(R.string.service_map_search_area)) }
            Button(onClick = { onZoom(1.0) }, modifier = Modifier.sizeIn(minWidth = PitGoSize.MinimumTouchTarget)) {
                Text("+")
            }
            Button(onClick = { onZoom(-1.0) }, modifier = Modifier.sizeIn(minWidth = PitGoSize.MinimumTouchTarget)) {
                Text("−")
            }
        }
        TextButton(
            onClick = { uriHandler.openUri(PitGoManagedMapStyle.configuration.attributionUrl) },
            modifier = Modifier.align(Alignment.BottomStart),
        ) {
            Text(
                PitGoManagedMapStyle.configuration.attribution,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        if (state.dataState == DiscoveryDataState.Loading ||
            state.surfaceState.style is MapStyleState.Loading
        ) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
        if (state.surfaceState.style is MapStyleState.Failed) {
            Button(onClick = onRetryStyle, modifier = Modifier.align(Alignment.Center)) {
                Text(stringResource(R.string.map_retry))
            }
        }
    }
}

@Composable
private fun ProviderList(
    providers: List<PublicProvider>,
    onSelect: (String?) -> Unit,
    modifier: Modifier,
) {
    LazyColumn(
        modifier,
        contentPadding = PaddingValues(PitGoSpacing.Medium),
        verticalArrangement = Arrangement.spacedBy(PitGoSpacing.Small),
    ) {
        items(providers, key = PublicProvider::id) { provider ->
            Card(onClick = { onSelect(provider.id) }, modifier = Modifier.fillMaxWidth()) {
                ProviderSummary(provider, Modifier.padding(PitGoSpacing.Medium))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderSummarySheet(
    provider: PublicProvider,
    journey: JourneyRouteUiState,
    onDismiss: () -> Unit,
    onRequest: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 600.dp).verticalScroll(rememberScrollState()).padding(PitGoSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(PitGoSpacing.Medium),
        ) {
            ProviderSummary(provider)
            journey.polyline?.let { line ->
                val mobile = provider.locationMode == ProviderLocationMode.MOBILE
                val clientPoint = if (mobile) journey.destination else journey.origin
                MapLibreMapHost(
                    configuration = PitGoManagedMapStyle.configuration,
                    renderModel = MapRenderModel(listOf(
                        com.trustarx.pitgo.core.map.model.MapMarkerSpec("journey-origin", clientPoint ?: line.points.first(), com.trustarx.pitgo.core.map.model.MapMarkerKind.CLIENT_LOCATION, accessibilityLabel = stringResource(R.string.service_map_my_location)),
                        com.trustarx.pitgo.core.map.model.MapMarkerSpec(provider.id, provider.addressCoordinate, if (mobile) com.trustarx.pitgo.core.map.model.MapMarkerKind.MOBILE_PROVIDER else com.trustarx.pitgo.core.map.model.MapMarkerKind.ADDRESS, accessibilityLabel = provider.displayName),
                    ), line),
                    viewport = com.trustarx.pitgo.core.map.model.MapViewport(line.points.first(), 13.0),
                    cameraCommand = rememberJourneyCamera(com.trustarx.pitgo.core.map.model.MapViewportCommand(
                        com.trustarx.pitgo.core.map.model.MapViewport(line.points.first(), 13.0), 1), line),
                    styleReloadRevision = 0, online = true,
                    styleController = remember { ProviderMapStyleController() },
                    onViewportIdle = {}, onFeatureClick = {}, onSurfaceState = {},
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                )
            }
            JourneyRouteSummary(journey)
            Button(
                onClick = onRequest,
                modifier = Modifier.fillMaxWidth().heightIn(min = PitGoSize.PrimaryActionMinHeight),
            ) { Text(stringResource(R.string.service_map_request)) }
        }
    }
}

@Composable
private fun ProviderSummary(provider: PublicProvider, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(PitGoSpacing.ExtraSmall)) {
        Text(
            provider.displayName,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(
                if (provider.locationMode == ProviderLocationMode.FIXED) {
                    R.string.service_map_provider_mode_fixed
                } else R.string.service_map_provider_mode_mobile
            )
        )
        provider.minimumPrice?.let { Text(stringResource(R.string.service_map_price, it)) }
    }
}

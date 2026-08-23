package com.trustarx.pitgo.features.providermap.model

import com.trustarx.pitgo.core.map.model.MapCoordinate
import com.trustarx.pitgo.core.map.model.MapMarkerKind
import com.trustarx.pitgo.core.map.model.MapMarkerSpec
import com.trustarx.pitgo.core.map.model.MapSurfaceState
import com.trustarx.pitgo.core.map.model.MapViewport
import com.trustarx.pitgo.core.map.model.MapViewportCommand

typealias MapCenter = MapCoordinate

enum class ProviderLocationMode { FIXED, MOBILE }

data class PublicProvider(
    val id: String,
    val displayName: String,
    val locationMode: ProviderLocationMode,
    val addressCoordinate: MapCoordinate,
    val distanceMeters: Double,
    val minimumPrice: String?,
)

enum class ServiceMapMode { MAP, LIST }

sealed interface DiscoveryDataState {
    data object Loading : DiscoveryDataState
    data object Ready : DiscoveryDataState
    data object Empty : DiscoveryDataState
    data object Offline : DiscoveryDataState
    data object Failure : DiscoveryDataState
    data object SessionExpired : DiscoveryDataState
}

sealed interface ClientLocationState {
    data object NotRequested : ClientLocationState
    data object Resolving : ClientLocationState
    data object InUse : ClientLocationState
    data object Denied : ClientLocationState
    data object Unavailable : ClientLocationState
}

sealed interface ServiceMapUiState {
    data class Content(
        val serviceId: String,
        val viewport: MapViewport,
        val cameraCommand: MapViewportCommand? = null,
        val providers: List<PublicProvider> = emptyList(),
        val dataState: DiscoveryDataState = DiscoveryDataState.Loading,
        val surfaceState: MapSurfaceState = MapSurfaceState(),
        val mode: ServiceMapMode = ServiceMapMode.MAP,
        val selectedProviderId: String? = null,
        val selectedGroupKey: String? = null,
        val coincidentGroups: Map<String, List<String>> = emptyMap(),
        val clientLocationState: ClientLocationState = ClientLocationState.NotRequested,
        val styleReloadRevision: Long = 0L,
        val searchGeneration: Long = 0L,
    ) : ServiceMapUiState {
        val selectedProvider: PublicProvider?
            get() = providers.firstOrNull { it.id == selectedProviderId }

        val selectedGroupProviders: List<PublicProvider>
            get() = selectedGroupKey
                ?.let(coincidentGroups::get)
                .orEmpty()
                .mapNotNull { id -> providers.firstOrNull { it.id == id } }

        val markers: List<MapMarkerSpec>
            get() {
                val groupedIds = coincidentGroups.values.flatten().toSet()
                val singles = providers.filterNot { it.id in groupedIds }.map { provider ->
                    MapMarkerSpec(
                        id = provider.id,
                        coordinate = provider.addressCoordinate,
                        kind = MapMarkerKind.ADDRESS,
                        selected = provider.id == selectedProviderId,
                        accessibilityLabel = provider.displayName,
                    )
                }
                val groups = coincidentGroups.mapNotNull { (key, ids) ->
                    val first = providers.firstOrNull { it.id == ids.firstOrNull() }
                        ?: return@mapNotNull null
                    MapMarkerSpec(
                        id = "group:$key",
                        coordinate = first.addressCoordinate,
                        kind = MapMarkerKind.COINCIDENT_GROUP,
                        selected = key == selectedGroupKey,
                        groupKey = key,
                        accessibilityLabel = "${ids.size} prestadores no mesmo endereço",
                    )
                }
                return singles + groups
            }
    }

    data object InvalidService : ServiceMapUiState
}

sealed interface ProviderDiscoveryResult {
    data class Success(val providers: List<PublicProvider>) : ProviderDiscoveryResult
    data object InvalidInput : ProviderDiscoveryResult
    data object Offline : ProviderDiscoveryResult
    data object Timeout : ProviderDiscoveryResult
    data object SessionExpired : ProviderDiscoveryResult
    data object ServiceUnavailable : ProviderDiscoveryResult
    data object Failure : ProviderDiscoveryResult
}

package com.trustarx.pitgo.core.vehicle.model

data class TransportCategory(val id: String, val code: String, val name: String)
data class GuinchoSize(val id: String, val code: String, val name: String, val revision: Long, val vehicleCategoryIds: List<String>)
data class TransportCatalog(val categories: List<TransportCategory>, val sizes: List<GuinchoSize>)
sealed interface TransportCatalogState {
    data object Loading : TransportCatalogState
    data class Ready(val catalog: TransportCatalog) : TransportCatalogState
    data class Failed(val reason: TransportCatalogFailure) : TransportCatalogState
}

enum class TransportCatalogFailure { SESSION_EXPIRED, OFFLINE, TIMEOUT, CONTRACT, UNAVAILABLE }

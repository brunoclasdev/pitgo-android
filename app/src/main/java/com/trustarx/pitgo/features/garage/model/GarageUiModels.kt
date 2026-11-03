package com.trustarx.pitgo.features.garage.model

import com.trustarx.pitgo.core.vehicle.model.Vehicle

enum class GarageFailureReason { OFFLINE, TIMEOUT, SESSION_EXPIRED, SERVICE, UNKNOWN }

sealed interface GarageUiState {
    data object Loading : GarageUiState
    data object Empty : GarageUiState
    data class Content(
        val vehicles: List<Vehicle>,
        val changingPrimaryVehicleId: String? = null,
        val transportCatalog: com.trustarx.pitgo.core.vehicle.model.TransportCatalogState = com.trustarx.pitgo.core.vehicle.model.TransportCatalogState.Loading,
        val categoryUpdateFailed: Boolean = false,
    ) : GarageUiState
    data class Failure(val reason: GarageFailureReason) : GarageUiState
}

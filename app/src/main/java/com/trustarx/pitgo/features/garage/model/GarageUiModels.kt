package com.trustarx.pitgo.features.garage.model

import com.trustarx.pitgo.core.vehicle.model.Vehicle

enum class GarageFailureReason { OFFLINE, TIMEOUT, SESSION_EXPIRED, SERVICE, UNKNOWN }

sealed interface GarageUiState {
    data object Loading : GarageUiState
    data object Empty : GarageUiState
    data class Content(
        val vehicles: List<Vehicle>,
        val changingPrimaryVehicleId: String? = null,
    ) : GarageUiState
    data class Failure(val reason: GarageFailureReason) : GarageUiState
}

package com.trustarx.pitgo.features.providerdashboard.model

import com.trustarx.pitgo.core.account.model.ProviderSnapshot
import com.trustarx.pitgo.features.providertracking.model.ProviderTrackingState

sealed interface ProviderDashboardUiState {
    data object Loading : ProviderDashboardUiState
    data class Content(
        val provider: ProviderSnapshot,
        val updating: Boolean = false,
        val tracking: ProviderTrackingState = ProviderTrackingState.Stopped,
        val incomingAppointmentId: String? = null,
        val availabilityIssue: ProviderAvailabilityIssue? = null,
    ) : ProviderDashboardUiState
    data object Failure : ProviderDashboardUiState
}

enum class ProviderAvailabilityIssue {
    PRECISE_PERMISSION_REQUIRED,
    LOCATION_SERVICES_DISABLED,
    PRECISE_FIX_UNAVAILABLE,
    /** PITTEC only — the offers channel is silenced or notifications are disabled app-wide. */
    NOTIFICATIONS_REQUIRED,
    UPDATE_FAILED,
}

sealed interface ProviderDashboardUpdateResult {
    data class Success(val provider: ProviderSnapshot) : ProviderDashboardUpdateResult
    data object Failure : ProviderDashboardUpdateResult
}

@kotlinx.serialization.Serializable
data class GuinchoFleetVehicle(val id: String, val plate: String, val make: String, val model: String,
    val color: String, val revision: Long,
    @kotlinx.serialization.SerialName("guincho_size_id") val sizeId: String? = null,
    val selected: Boolean = false)
data class GuinchoFleetState(
    val catalog: com.trustarx.pitgo.core.vehicle.model.TransportCatalogState = com.trustarx.pitgo.core.vehicle.model.TransportCatalogState.Loading,
    val vehicles: List<GuinchoFleetVehicle> = emptyList(),
    val newVehicle: com.trustarx.pitgo.features.providerregistration.model.ProviderVehicle = com.trustarx.pitgo.features.providerregistration.model.ProviderVehicle(),
    val operation: GuinchoFleetOperation = GuinchoFleetOperation.IDLE) {
    val busy: Boolean get() = operation in setOf(GuinchoFleetOperation.LOADING,GuinchoFleetOperation.SAVING)
    val failed: Boolean get() = operation in setOf(GuinchoFleetOperation.FAILED,GuinchoFleetOperation.INVALID_INPUT)
    val hasClassifiedDispatchVehicle: Boolean get() {
        val sizes=(catalog as? com.trustarx.pitgo.core.vehicle.model.TransportCatalogState.Ready)?.catalog?.sizes ?: return false
        return vehicles.any { vehicle -> vehicle.selected && sizes.any { it.id==vehicle.sizeId } }
    }
}

enum class GuinchoFleetField { PLATE, MAKE, MODEL, COLOR, SIZE }

enum class GuinchoFleetOperation { IDLE, LOADING, SAVING, INVALID_INPUT, FAILED }

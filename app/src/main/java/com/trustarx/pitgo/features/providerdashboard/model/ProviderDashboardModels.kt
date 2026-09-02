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

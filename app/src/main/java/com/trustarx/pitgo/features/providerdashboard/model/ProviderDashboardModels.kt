package com.trustarx.pitgo.features.providerdashboard.model

import com.trustarx.pitgo.core.account.model.ProviderSnapshot

sealed interface ProviderDashboardUiState {
    data object Loading : ProviderDashboardUiState
    data class Content(val provider: ProviderSnapshot, val updating: Boolean = false) : ProviderDashboardUiState
    data object Failure : ProviderDashboardUiState
}

sealed interface ProviderDashboardUpdateResult {
    data class Success(val provider: ProviderSnapshot) : ProviderDashboardUpdateResult
    data object Failure : ProviderDashboardUpdateResult
}

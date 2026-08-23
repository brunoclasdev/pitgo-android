package com.trustarx.pitgo.features.providerdashboard.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.trustarx.pitgo.core.account.model.ProviderLocationMode
import com.trustarx.pitgo.core.account.model.ProviderSnapshot
import com.trustarx.pitgo.features.providerdashboard.model.ProviderDashboardUiState
import com.trustarx.pitgo.features.providerdashboard.repository.ProviderDashboardRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProviderDashboardViewModel(
    snapshot: ProviderSnapshot,
    private val repository: ProviderDashboardRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<ProviderDashboardUiState>(
        ProviderDashboardUiState.Content(repository.current(snapshot)),
    )
    val state: StateFlow<ProviderDashboardUiState> = _state.asStateFlow()

    fun setAvailability(available: Boolean) = mutate(available = available)
    fun setMode(mode: ProviderLocationMode) = mutate(mode = mode)

    private fun mutate(available: Boolean? = null, mode: ProviderLocationMode? = null) {
        val content = _state.value as? ProviderDashboardUiState.Content ?: return
        if (content.updating) return
        _state.value = content.copy(updating = true)
        viewModelScope.launch {
            when (val result = repository.update(
                content.provider,
                available ?: content.provider.available,
                mode ?: content.provider.locationMode ?: ProviderLocationMode.FIXED,
            )) {
                is com.trustarx.pitgo.features.providerdashboard.model.ProviderDashboardUpdateResult.Success ->
                    _state.value = ProviderDashboardUiState.Content(result.provider)
                com.trustarx.pitgo.features.providerdashboard.model.ProviderDashboardUpdateResult.Failure ->
                    _state.value = content
            }
        }
    }

    companion object {
        fun factory(
            snapshot: ProviderSnapshot,
            repository: ProviderDashboardRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ProviderDashboardViewModel(snapshot, repository) as T
        }
    }
}

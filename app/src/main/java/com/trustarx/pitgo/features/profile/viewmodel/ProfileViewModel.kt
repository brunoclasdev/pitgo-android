package com.trustarx.pitgo.features.profile.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.trustarx.pitgo.core.preferences.AppearanceMode
import com.trustarx.pitgo.core.preferences.AppearancePreferences
import com.trustarx.pitgo.features.clientaddress.model.ClientAddress
import com.trustarx.pitgo.features.clientaddress.model.ClientAddressResult
import com.trustarx.pitgo.features.clientaddress.repository.ClientAddressRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProfileUiState(
    val environmentLabel: String = com.trustarx.pitgo.core.environment.EnvironmentDiagnostics.label,
    val address: ClientAddress? = null,
    val addressLoading: Boolean = true,
    val appearanceMode: AppearanceMode = AppearanceMode.SYSTEM,
)

/**
 * Aggregates what Profile needs beyond the account snapshot it already receives (US6): the
 * client's address (reused from US5's ClientAddressRepository — a single implementation, not
 * duplicated per screen) and the appearance preference. Vehicle padrão is not fetched here — it
 * already comes from AccountSnapshot.primaryVehicle, no new repository call needed for it.
 */
class ProfileViewModel(
    private val clientAddressRepository: ClientAddressRepository,
    private val appearancePreferences: AppearancePreferences,
) : ViewModel() {
    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init {
        refreshAddress()
        viewModelScope.launch {
            appearancePreferences.mode.collect { mode ->
                _state.value = _state.value.copy(appearanceMode = mode)
            }
        }
    }

    fun refreshAddress() {
        viewModelScope.launch {
            _state.value = _state.value.copy(addressLoading = true)
            val address = (clientAddressRepository.current() as? ClientAddressResult.Success)?.address
            _state.value = _state.value.copy(address = address, addressLoading = false)
        }
    }

    fun setAppearanceMode(mode: AppearanceMode) {
        viewModelScope.launch { appearancePreferences.setMode(mode) }
    }

    companion object {
        fun factory(
            clientAddressRepository: ClientAddressRepository,
            appearancePreferences: AppearancePreferences,
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ProfileViewModel(clientAddressRepository, appearancePreferences) as T
        }
    }
}

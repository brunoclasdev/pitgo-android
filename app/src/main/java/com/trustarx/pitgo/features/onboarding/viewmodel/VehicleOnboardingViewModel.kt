package com.trustarx.pitgo.features.onboarding.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.trustarx.pitgo.core.auth.model.AppSessionState
import com.trustarx.pitgo.core.auth.repository.SessionRepository
import com.trustarx.pitgo.core.preferences.VehicleOnboardingRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class OnboardingDestination { VEHICLE_FORM, HOME }

sealed interface VehicleOnboardingUiState {
    data object Ready : VehicleOnboardingUiState
    data object Resolving : VehicleOnboardingUiState
    data class Completed(val destination: OnboardingDestination) : VehicleOnboardingUiState
    data object Failure : VehicleOnboardingUiState
}

class VehicleOnboardingViewModel(
    private val onboardingRepository: VehicleOnboardingRepository,
    private val sessionRepository: SessionRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<VehicleOnboardingUiState>(VehicleOnboardingUiState.Ready)
    val uiState: StateFlow<VehicleOnboardingUiState> = _uiState.asStateFlow()

    fun registerVehicle() = resolve(OnboardingDestination.VEHICLE_FORM)
    fun skipForNow() = resolve(OnboardingDestination.HOME)

    private fun resolve(destination: OnboardingDestination) {
        if (_uiState.value == VehicleOnboardingUiState.Resolving) return
        val account = (sessionRepository.state.value as? AppSessionState.Authenticated.Client)?.account
            ?: return
        _uiState.value = VehicleOnboardingUiState.Resolving
        viewModelScope.launch {
            try {
                onboardingRepository.resolve(account.userId)
                _uiState.value = VehicleOnboardingUiState.Completed(destination)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                _uiState.value = VehicleOnboardingUiState.Failure
            }
        }
    }

    companion object {
        fun factory(
            onboardingRepository: VehicleOnboardingRepository,
            sessionRepository: SessionRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(VehicleOnboardingViewModel::class.java))
                return VehicleOnboardingViewModel(onboardingRepository, sessionRepository) as T
            }
        }
    }
}

package com.trustarx.pitgo.features.garage.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.trustarx.pitgo.core.auth.model.AppSessionState
import com.trustarx.pitgo.core.auth.repository.SessionRepository
import com.trustarx.pitgo.core.vehicle.model.VehicleFailure
import com.trustarx.pitgo.core.vehicle.model.VehicleListResult
import com.trustarx.pitgo.core.vehicle.model.VehicleMutationResult
import com.trustarx.pitgo.core.vehicle.repository.VehicleRepository
import com.trustarx.pitgo.features.garage.model.GarageFailureReason
import com.trustarx.pitgo.features.garage.model.GarageUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GarageViewModel(
    private val vehicleRepository: VehicleRepository,
    private val sessionRepository: SessionRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<GarageUiState>(GarageUiState.Loading)
    val uiState: StateFlow<GarageUiState> = _uiState.asStateFlow()
    private var loadInProgress = false

    init {
        refresh()
    }

    fun refresh() {
        if (loadInProgress) return
        val account = (sessionRepository.state.value as? AppSessionState.Authenticated)?.account
            ?: run {
                _uiState.value = GarageUiState.Failure(GarageFailureReason.SESSION_EXPIRED)
                return
            }
        if ((_uiState.value as? GarageUiState.Content)?.changingPrimaryVehicleId != null) return
        _uiState.value = GarageUiState.Loading
        loadInProgress = true
        viewModelScope.launch {
            _uiState.value = when (val result = vehicleRepository.listActiveVehicles(account.userId)) {
                is VehicleListResult.Success -> if (result.vehicles.isEmpty()) {
                    GarageUiState.Empty
                } else {
                    GarageUiState.Content(result.vehicles)
                }
                is VehicleListResult.Failure -> GarageUiState.Failure(result.reason.toGarageReason())
            }
            loadInProgress = false
        }
    }

    fun setPrimary(vehicleId: String) {
        val current = _uiState.value as? GarageUiState.Content ?: return
        if (current.changingPrimaryVehicleId != null ||
            current.vehicles.firstOrNull { it.id == vehicleId }?.isPrimary == true
        ) return
        _uiState.value = current.copy(changingPrimaryVehicleId = vehicleId)
        viewModelScope.launch {
            when (val result = vehicleRepository.setPrimaryVehicle(vehicleId)) {
                is VehicleMutationResult.Success -> {
                    val updated = current.vehicles.map { vehicle ->
                        vehicle.copy(isPrimary = vehicle.id == result.vehicle.id)
                    }
                    _uiState.value = GarageUiState.Content(updated)
                }
                is VehicleMutationResult.FieldUnavailable ->
                    _uiState.value = GarageUiState.Failure(GarageFailureReason.UNKNOWN)
                is VehicleMutationResult.Failure ->
                    _uiState.value = GarageUiState.Failure(result.reason.toGarageReason())
            }
        }
    }

    private fun VehicleFailure.toGarageReason() = when (this) {
        VehicleFailure.Offline -> GarageFailureReason.OFFLINE
        VehicleFailure.Timeout -> GarageFailureReason.TIMEOUT
        VehicleFailure.SessionExpired -> GarageFailureReason.SESSION_EXPIRED
        VehicleFailure.ServiceUnavailable -> GarageFailureReason.SERVICE
        VehicleFailure.Contract,
        VehicleFailure.VehicleUnavailable,
        VehicleFailure.Unknown,
        -> GarageFailureReason.UNKNOWN
    }

    companion object {
        fun factory(
            vehicleRepository: VehicleRepository,
            sessionRepository: SessionRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(GarageViewModel::class.java))
                return GarageViewModel(vehicleRepository, sessionRepository) as T
            }
        }
    }
}

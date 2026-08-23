package com.trustarx.pitgo.features.garage.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.trustarx.pitgo.core.auth.model.AppSessionState
import com.trustarx.pitgo.core.auth.repository.SessionRepository
import com.trustarx.pitgo.core.vehicle.model.VehicleFailure
import com.trustarx.pitgo.core.vehicle.model.VehicleField
import com.trustarx.pitgo.core.vehicle.model.VehicleFieldError
import com.trustarx.pitgo.core.vehicle.model.VehicleFormOrigin
import com.trustarx.pitgo.core.vehicle.model.VehicleListResult
import com.trustarx.pitgo.core.vehicle.model.VehicleMutationResult
import com.trustarx.pitgo.core.vehicle.repository.VehicleRepository
import com.trustarx.pitgo.core.vehicle.validation.VehicleValidation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class VehicleFormFailureReason {
    OFFLINE, TIMEOUT, SESSION_EXPIRED, SERVICE, CONTRACT, UNKNOWN,
}

sealed interface VehicleFormOperation {
    data object Loading : VehicleFormOperation
    data object Editing : VehicleFormOperation
    data object Submitting : VehicleFormOperation
    data class Failure(val reason: VehicleFormFailureReason) : VehicleFormOperation
    data object Success : VehicleFormOperation
}

data class VehicleFormUiState(
    val origin: VehicleFormOrigin,
    val brand: String = "",
    val model: String = "",
    val version: String = "",
    val manufactureYear: String = "",
    val modelYear: String = "",
    val plate: String = "",
    val renavam: String = "",
    val chassis: String = "",
    val color: String = "",
    val fuelType: String = "",
    val transmission: String = "",
    val engine: String = "",
    val mileage: String = "",
    val makePrimary: Boolean = false,
    val existingVehicleCount: Int = 0,
    val errors: Map<VehicleField, VehicleFieldError> = emptyMap(),
    val focusField: VehicleField? = null,
    val focusRequestId: Long = 0,
    val operation: VehicleFormOperation = VehicleFormOperation.Loading,
) {
    val isProcessing: Boolean
        get() = operation == VehicleFormOperation.Loading ||
            operation == VehicleFormOperation.Submitting
    val hasDraft: Boolean
        get() = listOf(
            brand, model, version, manufactureYear, modelYear, plate, renavam, chassis,
            color, fuelType, transmission, engine, mileage,
        ).any(String::isNotBlank) || makePrimary
}

class VehicleFormViewModel(
    private val origin: VehicleFormOrigin,
    private val vehicleRepository: VehicleRepository,
    private val sessionRepository: SessionRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(VehicleFormUiState(origin = origin))
    val uiState: StateFlow<VehicleFormUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            sessionRepository.state.collectLatest { state ->
                if (state !is AppSessionState.Authenticated.Client) clearForExpiredSession()
            }
        }
        loadVehicleCount()
    }

    fun onFieldChanged(field: VehicleField, value: String) {
        _uiState.update { current ->
            if (current.isProcessing || current.operation == VehicleFormOperation.Success) current
            else current.withField(field, normalizeInput(field, value)).copy(
                errors = current.errors - field,
                operation = VehicleFormOperation.Editing,
            )
        }
    }

    fun onMakePrimaryChanged(value: Boolean) = _uiState.update {
        if (it.isProcessing) it else it.copy(
            makePrimary = value,
            operation = VehicleFormOperation.Editing,
        )
    }

    fun submit() {
        val current = _uiState.value
        if (current.isProcessing || current.operation == VehicleFormOperation.Success) return
        val validation = VehicleValidation.validate(
            current.brand, current.model, current.version, current.manufactureYear,
            current.modelYear, current.plate, current.renavam, current.chassis, current.color,
            current.fuelType, current.transmission, current.engine, current.mileage,
            current.makePrimary && current.existingVehicleCount > 0,
        )
        val draft = validation.draft
        if (draft == null) {
            _uiState.update {
                it.copy(
                    errors = validation.errors,
                    focusField = validation.firstInvalidField,
                    focusRequestId = it.focusRequestId + 1,
                    operation = VehicleFormOperation.Editing,
                )
            }
            return
        }
        _uiState.update { it.copy(errors = emptyMap(), operation = VehicleFormOperation.Submitting) }
        viewModelScope.launch {
            when (val result = vehicleRepository.createVehicle(draft)) {
                is VehicleMutationResult.Success -> _uiState.update {
                    it.copy(operation = VehicleFormOperation.Success)
                }
                is VehicleMutationResult.FieldUnavailable -> _uiState.update {
                    it.copy(
                        errors = mapOf(result.field to VehicleFieldError.UNAVAILABLE),
                        focusField = result.field,
                        focusRequestId = it.focusRequestId + 1,
                        operation = VehicleFormOperation.Editing,
                    )
                }
                is VehicleMutationResult.Failure -> applyFailure(result.reason)
            }
        }
    }

    fun discardDraft() {
        _uiState.value = VehicleFormUiState(
            origin = origin,
            existingVehicleCount = _uiState.value.existingVehicleCount,
            operation = VehicleFormOperation.Editing,
        )
    }

    private fun loadVehicleCount() {
        val account = (sessionRepository.state.value as? AppSessionState.Authenticated.Client)?.account
            ?: return clearForExpiredSession()
        viewModelScope.launch {
            when (val result = vehicleRepository.listActiveVehicles(account.userId)) {
                is VehicleListResult.Success -> _uiState.update {
                    it.copy(
                        existingVehicleCount = result.vehicles.size,
                        operation = VehicleFormOperation.Editing,
                    )
                }
                is VehicleListResult.Failure -> applyFailure(result.reason)
            }
        }
    }

    private fun applyFailure(failure: VehicleFailure) {
        if (failure == VehicleFailure.SessionExpired) {
            clearForExpiredSession()
            return
        }
        _uiState.update { current ->
            current.copy(operation = VehicleFormOperation.Failure(failure.toUiReason()))
        }
    }

    private fun clearForExpiredSession() {
        _uiState.value = VehicleFormUiState(
            origin = origin,
            operation = VehicleFormOperation.Failure(VehicleFormFailureReason.SESSION_EXPIRED),
        )
    }

    private fun normalizeInput(field: VehicleField, value: String): String = when (field) {
        VehicleField.PLATE -> VehicleValidation.normalizePlate(value)
        VehicleField.RENAVAM -> VehicleValidation.normalizeRenavam(value)
        VehicleField.CHASSIS -> VehicleValidation.normalizeChassis(value)
        VehicleField.MANUFACTURE_YEAR,
        VehicleField.MODEL_YEAR,
        VehicleField.MILEAGE,
        -> value.take(10)
        else -> value
    }

    private fun VehicleFormUiState.withField(field: VehicleField, value: String) = when (field) {
        VehicleField.BRAND -> copy(brand = value)
        VehicleField.MODEL -> copy(model = value)
        VehicleField.VERSION -> copy(version = value)
        VehicleField.MANUFACTURE_YEAR -> copy(manufactureYear = value)
        VehicleField.MODEL_YEAR -> copy(modelYear = value)
        VehicleField.PLATE -> copy(plate = value)
        VehicleField.RENAVAM -> copy(renavam = value)
        VehicleField.CHASSIS -> copy(chassis = value)
        VehicleField.COLOR -> copy(color = value)
        VehicleField.FUEL_TYPE -> copy(fuelType = value)
        VehicleField.TRANSMISSION -> copy(transmission = value)
        VehicleField.ENGINE -> copy(engine = value)
        VehicleField.MILEAGE -> copy(mileage = value)
    }

    private fun VehicleFailure.toUiReason() = when (this) {
        VehicleFailure.Offline -> VehicleFormFailureReason.OFFLINE
        VehicleFailure.Timeout -> VehicleFormFailureReason.TIMEOUT
        VehicleFailure.SessionExpired -> VehicleFormFailureReason.SESSION_EXPIRED
        VehicleFailure.ServiceUnavailable -> VehicleFormFailureReason.SERVICE
        VehicleFailure.Contract, VehicleFailure.VehicleUnavailable -> VehicleFormFailureReason.CONTRACT
        VehicleFailure.Unknown -> VehicleFormFailureReason.UNKNOWN
    }

    companion object {
        fun factory(
            origin: VehicleFormOrigin,
            vehicleRepository: VehicleRepository,
            sessionRepository: SessionRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(VehicleFormViewModel::class.java))
                return VehicleFormViewModel(origin, vehicleRepository, sessionRepository) as T
            }
        }
    }
}

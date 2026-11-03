package com.trustarx.pitgo.features.garage.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.trustarx.pitgo.core.auth.model.AppSessionState
import com.trustarx.pitgo.core.auth.repository.SessionRepository
import com.trustarx.pitgo.core.preferences.VehicleOnboardingRepository
import com.trustarx.pitgo.core.vehicle.model.Vehicle
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

enum class VehicleFormFailureReason {
    OFFLINE, TIMEOUT, SESSION_EXPIRED, SERVICE, OPERATION_UNAVAILABLE, CONTRACT, UNKNOWN,
}

sealed interface VehicleFormOperation {
    data object Loading : VehicleFormOperation
    data object Editing : VehicleFormOperation
    data object Submitting : VehicleFormOperation
    data class Failure(val reason: VehicleFormFailureReason) : VehicleFormOperation
    data class Success(val vehicle: Vehicle) : VehicleFormOperation
}

data class VehicleFormUiState(
    val origin: VehicleFormOrigin,
    val transportCatalog: com.trustarx.pitgo.core.vehicle.model.TransportCatalogState = com.trustarx.pitgo.core.vehicle.model.TransportCatalogState.Loading,
    val transportCategoryId: String? = null,
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
        get() = operation is VehicleFormOperation.Loading ||
            operation is VehicleFormOperation.Submitting
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
    private val onboardingRepository: VehicleOnboardingRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(VehicleFormUiState(origin = origin))
    val uiState: StateFlow<VehicleFormUiState> = _uiState.asStateFlow()
    private val expectedUserId =
        (sessionRepository.state.value as? AppSessionState.Authenticated.Client)?.account?.userId
    private var submitJob: Job? = null

    init {
        viewModelScope.launch {
            sessionRepository.state.collectLatest { state ->
                if (state !is AppSessionState.Authenticated.Client || state.account.userId != expectedUserId) {
                    clearForExpiredSession()
                }
            }
        }
        loadVehicleCount()
        retryTransportCatalog()
    }

    fun onFieldChanged(field: VehicleField, value: String) {
        _uiState.update { current ->
            if (current.isProcessing || current.operation is VehicleFormOperation.Success) {
                return@update current
            }
            val normalized = normalizeInput(field, value)
            val updated = current.withField(field, normalized)
            val errors = if (field in current.errors) {
                VehicleValidation.validateField(field, normalized)?.let { error ->
                    current.errors + (field to error)
                } ?: (current.errors - field)
            } else {
                current.errors
            }
            updated.copy(errors = errors, operation = VehicleFormOperation.Editing)
        }
    }

    fun onMakePrimaryChanged(value: Boolean) = _uiState.update {
        if (it.isProcessing || it.operation is VehicleFormOperation.Success) it else it.copy(
            makePrimary = value,
            operation = VehicleFormOperation.Editing,
        )
    }

    fun retryTransportCatalog() { viewModelScope.launch {
        _uiState.update { it.copy(transportCatalog=com.trustarx.pitgo.core.vehicle.model.TransportCatalogState.Loading) }
        val catalog=vehicleRepository.transportCatalog.load()
        _uiState.update { it.copy(transportCatalog=catalog) }
    } }
    fun selectTransportCategory(id: String) { _uiState.update {
        if (it.isProcessing) it else it.copy(transportCategoryId=id)
    } }
    fun submit() {
        val current = _uiState.value
        if (current.isProcessing || current.operation is VehicleFormOperation.Success ||
            submitJob?.isActive == true
        ) return
        val userId = expectedUserId ?: return clearForExpiredSession()
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
        submitJob = viewModelScope.launch {
            try {
                when (val result = vehicleRepository.createVehicle(userId, draft.copy(transportCategoryId=current.transportCategoryId))) {
                    is VehicleMutationResult.Success -> applySuccess(userId, result.vehicle)
                    is VehicleMutationResult.FieldFailure -> applyFieldFailure(userId, result)
                    is VehicleMutationResult.Failure -> if (isExpectedClient(userId)) {
                        applyFailure(result.reason)
                    }
                }
            } finally {
                submitJob = null
            }
        }
    }

    fun discardDraft() {
        _uiState.value = VehicleFormUiState(
            origin = origin,
            existingVehicleCount = _uiState.value.existingVehicleCount,
            transportCatalog = _uiState.value.transportCatalog,
            operation = VehicleFormOperation.Editing,
        )
    }

    private fun loadVehicleCount() {
        val account = (sessionRepository.state.value as? AppSessionState.Authenticated.Client)?.account
            ?: return clearForExpiredSession()
        if (account.userId != expectedUserId) return clearForExpiredSession()
        viewModelScope.launch {
            when (val result = vehicleRepository.listActiveVehicles(account.userId)) {
                is VehicleListResult.Success -> if (isExpectedClient(account.userId)) {
                    _uiState.update {
                        it.copy(
                            existingVehicleCount = result.vehicles.size,
                            operation = VehicleFormOperation.Editing,
                        )
                    }
                }
                is VehicleListResult.Failure -> if (isExpectedClient(account.userId)) {
                    applyFailure(result.reason)
                }
            }
        }
    }

    private suspend fun applySuccess(userId: String, vehicle: Vehicle) {
        if (!isExpectedClient(userId)) return
        if (vehicle.isPrimary) sessionRepository.applyPrimaryVehicle(vehicle)
        if (origin == VehicleFormOrigin.ONBOARDING) {
            try {
                onboardingRepository.resolve(userId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                // The authoritative primary vehicle lets the next session bootstrap recover.
            }
        }
        if (isExpectedClient(userId)) {
            _uiState.update { it.copy(operation = VehicleFormOperation.Success(vehicle)) }
        }
    }

    private fun applyFieldFailure(
        userId: String,
        result: VehicleMutationResult.FieldFailure,
    ) {
        if (!isExpectedClient(userId)) return
        _uiState.update {
            it.copy(
                errors = it.errors + (result.field to result.error),
                focusField = result.field,
                focusRequestId = it.focusRequestId + 1,
                operation = VehicleFormOperation.Editing,
            )
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
        submitJob?.cancel()
        submitJob = null
        _uiState.value = VehicleFormUiState(
            origin = origin,
            operation = VehicleFormOperation.Failure(VehicleFormFailureReason.SESSION_EXPIRED),
        )
    }

    private fun isExpectedClient(userId: String): Boolean =
        (sessionRepository.state.value as? AppSessionState.Authenticated.Client)
            ?.account?.userId == userId && userId == expectedUserId

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
        VehicleFailure.RpcUnavailable -> VehicleFormFailureReason.OPERATION_UNAVAILABLE
        VehicleFailure.Contract, VehicleFailure.VehicleUnavailable -> VehicleFormFailureReason.CONTRACT
        VehicleFailure.Unknown -> VehicleFormFailureReason.UNKNOWN
    }

    companion object {
        fun factory(
            origin: VehicleFormOrigin,
            vehicleRepository: VehicleRepository,
            sessionRepository: SessionRepository,
            onboardingRepository: VehicleOnboardingRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(VehicleFormViewModel::class.java))
                return VehicleFormViewModel(
                    origin,
                    vehicleRepository,
                    sessionRepository,
                    onboardingRepository,
                ) as T
            }
        }
    }
}

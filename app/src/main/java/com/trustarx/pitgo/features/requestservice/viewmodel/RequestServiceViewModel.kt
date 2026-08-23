package com.trustarx.pitgo.features.requestservice.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.trustarx.pitgo.core.catalog.model.CatalogItemResult
import com.trustarx.pitgo.core.catalog.repository.ServiceCatalogRepository
import com.trustarx.pitgo.core.navigation.PitGoRoutes
import com.trustarx.pitgo.core.vehicle.model.Vehicle
import com.trustarx.pitgo.features.requestservice.model.PaymentMethod
import com.trustarx.pitgo.features.requestservice.model.RequestServiceUiState
import com.trustarx.pitgo.features.requestservice.model.SubmissionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RequestServiceViewModel(
    savedStateHandle: SavedStateHandle,
    private val catalogRepository: ServiceCatalogRepository,
) : ViewModel() {
    private val serviceId = savedStateHandle.get<String>(PitGoRoutes.RequestServiceArg).orEmpty()
    private val providerId = savedStateHandle.get<String>(PitGoRoutes.RequestProviderArg).orEmpty()
    private val validArguments = isUuid(serviceId) && isUuid(providerId)
    private var pendingVehicle: Vehicle? = null
    private val _uiState = MutableStateFlow<RequestServiceUiState>(
        if (validArguments) RequestServiceUiState.Loading else RequestServiceUiState.InvalidService,
    )
    val uiState: StateFlow<RequestServiceUiState> = _uiState.asStateFlow()

    init {
        if (validArguments) loadService()
    }

    fun retry() {
        if (validArguments && _uiState.value !is RequestServiceUiState.Content) loadService()
    }

    private fun loadService() {
        _uiState.value = RequestServiceUiState.Loading
        viewModelScope.launch {
            _uiState.value = when (val result = catalogRepository.service(serviceId)) {
                is CatalogItemResult.Success -> RequestServiceUiState.Content(
                    selectedService = result.item,
                    providerId = providerId,
                    vehicle = pendingVehicle,
                )
                CatalogItemResult.NotFound -> RequestServiceUiState.InvalidService
                CatalogItemResult.Offline,
                CatalogItemResult.Timeout,
                CatalogItemResult.SessionExpired,
                CatalogItemResult.ServiceUnavailable,
                CatalogItemResult.Unknown,
                -> RequestServiceUiState.Failure
            }
        }
    }

    fun bindVehicle(vehicle: Vehicle?) {
        pendingVehicle = vehicle
        updateContent { content ->
            if (content.vehicle == vehicle) content else content.copy(vehicle = vehicle)
        }
    }

    fun onProblemDescriptionChanged(description: String) = updateContent {
        it.copy(problemDescription = description)
    }

    fun onPaymentMethodSelected(paymentMethod: PaymentMethod) = updateContent {
        it.copy(paymentMethod = paymentMethod)
    }

    fun onSubmitClicked(): Boolean {
        val content = _uiState.value as? RequestServiceUiState.Content ?: return false
        if (content.submissionStatus != SubmissionStatus.READY) return false
        _uiState.value = content.copy(submissionStatus = SubmissionStatus.TRIGGERED)
        return true
    }

    private fun updateContent(
        transform: (RequestServiceUiState.Content) -> RequestServiceUiState.Content,
    ) {
        _uiState.update { state ->
            if (state is RequestServiceUiState.Content) transform(state) else state
        }
    }

    private fun isUuid(value: String): Boolean = runCatching {
        java.util.UUID.fromString(value).toString().equals(value, ignoreCase = true)
    }.getOrDefault(false)

    companion object {
        fun factory(repository: ServiceCatalogRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { RequestServiceViewModel(createSavedStateHandle(), repository) }
        }
    }
}

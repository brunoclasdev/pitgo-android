package com.trustarx.pitgo.features.requestservice.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.trustarx.pitgo.core.common.model.DemoServiceId
import com.trustarx.pitgo.core.common.model.DemoServices
import com.trustarx.pitgo.core.navigation.PitGoRoutes
import com.trustarx.pitgo.core.vehicle.model.Vehicle
import com.trustarx.pitgo.features.requestservice.model.PaymentMethod
import com.trustarx.pitgo.features.requestservice.model.RequestServiceUiState
import com.trustarx.pitgo.features.requestservice.model.SubmissionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class RequestServiceViewModel(
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        initialState(savedStateHandle[PitGoRoutes.RequestServiceArg]),
    )

    val uiState: StateFlow<RequestServiceUiState> = _uiState.asStateFlow()

    fun bindVehicle(vehicle: Vehicle?) {
        updateContent { content ->
            if (content.vehicle == vehicle) content else content.copy(vehicle = vehicle)
        }
    }

    fun onProblemDescriptionChanged(description: String) {
        updateContent { content ->
            content.copy(problemDescription = description)
        }
    }

    fun onPaymentMethodSelected(paymentMethod: PaymentMethod) {
        updateContent { content ->
            content.copy(paymentMethod = paymentMethod)
        }
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
            when (state) {
                is RequestServiceUiState.Content -> transform(state)
                RequestServiceUiState.InvalidService -> state
            }
        }
    }

    private companion object {
        fun initialState(rawServiceId: String?): RequestServiceUiState {
            val serviceId = DemoServiceId.entries.firstOrNull { id -> id.name == rawServiceId }
                ?: return RequestServiceUiState.InvalidService
            val service = DemoServices.firstOrNull { candidate -> candidate.id == serviceId }
                ?: return RequestServiceUiState.InvalidService

            return RequestServiceUiState.Content(selectedService = service)
        }
    }
}

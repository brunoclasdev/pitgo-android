package com.trustarx.pitgo.features.pittec.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.trustarx.pitgo.core.navigation.PitGoRoutes
import com.trustarx.pitgo.features.payment.repository.PaymentGateway
import com.trustarx.pitgo.features.pittec.model.MockAppointmentStatus
import kotlinx.coroutines.launch
import com.trustarx.pitgo.features.pittec.repository.SimulatedPitTecFlowRepository

class SimulatedPitTecFlowViewModel(private val repository: SimulatedPitTecFlowRepository, private val payments: PaymentGateway, private val requestId: String) : ViewModel() {
    init { repository.reset() }
    val state = repository.state
    private var leaving = false
    fun advance() {
        if (state.value.status == MockAppointmentStatus.COMPLETED || leaving) return
        repository.advance()
        if (state.value.status == MockAppointmentStatus.COMPLETED) viewModelScope.launch {
            payments.settleSimulation(requestId, cancelled = false)
        }
    }
    fun leave(onBack: () -> Unit) {
        if (leaving) return
        leaving = true
        viewModelScope.launch {
            payments.settleSimulation(requestId, cancelled = state.value.status != MockAppointmentStatus.COMPLETED)
            repository.reset()
            onBack()
        }
    }
    companion object {
        fun factory(repository: SimulatedPitTecFlowRepository, payments: PaymentGateway): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SimulatedPitTecFlowViewModel(repository, payments, createSavedStateHandle().get<String>(PitGoRoutes.PaymentRequestIdArg).orEmpty())
            }
        }
    }
}

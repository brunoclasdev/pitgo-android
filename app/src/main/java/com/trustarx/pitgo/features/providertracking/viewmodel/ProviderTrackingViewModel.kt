package com.trustarx.pitgo.features.providertracking.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.trustarx.pitgo.features.providertracking.repository.ProviderTrackingStateRepository

class ProviderTrackingViewModel(
    val appointmentId: String,
    private val repository: ProviderTrackingStateRepository,
) : ViewModel() {
    val state = repository.state
    fun start() = repository.start()
    fun stop() = repository.stop()

    companion object {
        fun factory(appointmentId: String, repository: ProviderTrackingStateRepository) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ProviderTrackingViewModel(appointmentId, repository) as T
            }
    }
}

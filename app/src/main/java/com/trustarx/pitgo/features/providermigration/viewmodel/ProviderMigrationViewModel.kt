package com.trustarx.pitgo.features.providermigration.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.trustarx.pitgo.features.providermigration.model.ProviderMigrationResult
import com.trustarx.pitgo.features.providermigration.model.ProviderMigrationState
import com.trustarx.pitgo.features.providermigration.repository.ProviderMigrationCoordinator
import com.trustarx.pitgo.features.providermigration.repository.ProviderMigrationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProviderMigrationViewModel(
    private val repository: ProviderMigrationRepository,
    private val coordinator: ProviderMigrationCoordinator,
) : ViewModel() {
    private val _state = MutableStateFlow<ProviderMigrationState>(ProviderMigrationState.Idle)
    val state: StateFlow<ProviderMigrationState> = _state.asStateFlow()

    fun begin() {
        if (_state.value == ProviderMigrationState.Starting) return
        _state.value = ProviderMigrationState.Starting
        viewModelScope.launch {
            when (repository.begin(coordinator.createProof())) {
                ProviderMigrationResult.Pending -> {
                    coordinator.markAwaitingProvider(); _state.value = ProviderMigrationState.SwitchAccount
                }
                ProviderMigrationResult.SessionExpired -> _state.value = ProviderMigrationState.Failure(false)
                else -> { coordinator.clear(); _state.value = ProviderMigrationState.Failure(true) }
            }
        }
    }

    fun claim() {
        val proof = coordinator.proofOrNull() ?: run {
            _state.value = ProviderMigrationState.Failure(false); return
        }
        coordinator.markClaiming(); _state.value = ProviderMigrationState.Claiming
        viewModelScope.launch {
            when (repository.claim(proof)) {
                ProviderMigrationResult.Migrated -> {
                    coordinator.clear(); _state.value = ProviderMigrationState.Completed
                }
                ProviderMigrationResult.SessionExpired -> _state.value = ProviderMigrationState.Failure(false)
                else -> _state.value = ProviderMigrationState.Failure(true)
            }
        }
    }

    companion object {
        fun factory(repository: ProviderMigrationRepository, coordinator: ProviderMigrationCoordinator) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ProviderMigrationViewModel(repository, coordinator) as T
            }
    }
}

package com.trustarx.pitgo.features.home.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.trustarx.pitgo.core.auth.model.AppSessionState
import com.trustarx.pitgo.core.auth.repository.SessionRepository
import com.trustarx.pitgo.core.common.model.DemoService
import com.trustarx.pitgo.core.common.model.DemoServices
import com.trustarx.pitgo.features.home.model.HomeUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HomeViewModel(private val sessionRepository: SessionRepository) : ViewModel() {
    private val services = DemoServices.sortedBy(DemoService::displayOrder)
    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            sessionRepository.state.collectLatest { state ->
                _uiState.value = when (state) {
                    is AppSessionState.Authenticated -> state.account.primaryVehicle?.let { vehicle ->
                        HomeUiState.Content(state.account.profile, vehicle, services)
                    } ?: HomeUiState.NoVehicle(state.account.profile, services)
                    AppSessionState.Initializing, AppSessionState.Verifying -> HomeUiState.Loading
                    else -> HomeUiState.Failure
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch { sessionRepository.refresh() }
    }

    companion object {
        fun factory(repository: SessionRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(HomeViewModel::class.java))
                    return HomeViewModel(repository) as T
                }
            }
    }
}

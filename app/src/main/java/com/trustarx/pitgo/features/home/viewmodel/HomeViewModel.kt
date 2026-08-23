package com.trustarx.pitgo.features.home.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.trustarx.pitgo.core.auth.model.AppSessionState
import com.trustarx.pitgo.core.auth.repository.SessionRepository
import com.trustarx.pitgo.core.catalog.model.CatalogLoadResult
import com.trustarx.pitgo.core.catalog.repository.ServiceCatalogRepository
import com.trustarx.pitgo.features.home.model.HomeUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HomeViewModel(
    private val sessionRepository: SessionRepository,
    private val catalogRepository: ServiceCatalogRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            sessionRepository.state.collectLatest { state ->
                _uiState.value = when (state) {
                    is AppSessionState.Authenticated.Client -> when (
                        val catalog = catalogRepository.homeServices()
                    ) {
                        is CatalogLoadResult.Success -> state.account.primaryVehicle?.let { vehicle ->
                            HomeUiState.Content(state.account.profile, vehicle, catalog.items)
                        } ?: HomeUiState.NoVehicle(state.account.profile, catalog.items)
                        else -> HomeUiState.Failure
                    }
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
        fun factory(
            repository: SessionRepository,
            catalogRepository: ServiceCatalogRepository,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(HomeViewModel::class.java))
                    return HomeViewModel(repository, catalogRepository) as T
                }
            }
    }
}

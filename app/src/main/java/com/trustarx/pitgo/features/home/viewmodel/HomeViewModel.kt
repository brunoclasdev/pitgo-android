package com.trustarx.pitgo.features.home.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.trustarx.pitgo.core.auth.model.AppSessionState
import com.trustarx.pitgo.core.auth.repository.SessionRepository
import com.trustarx.pitgo.core.catalog.model.CatalogLoadResult
import com.trustarx.pitgo.core.catalog.repository.ServiceCatalogRepository
import com.trustarx.pitgo.features.clientaddress.model.ClientAddress
import com.trustarx.pitgo.features.clientaddress.model.ClientAddressResult
import com.trustarx.pitgo.features.clientaddress.repository.ClientAddressRepository
import com.trustarx.pitgo.features.home.model.HomeUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HomeViewModel(
    private val sessionRepository: SessionRepository,
    private val catalogRepository: ServiceCatalogRepository,
    private val clientAddressRepository: ClientAddressRepository,
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
                        is CatalogLoadResult.Success -> {
                            val address = currentAddress()
                            state.account.primaryVehicle?.let { vehicle ->
                                HomeUiState.Content(state.account.profile, vehicle, catalog.items, address)
                            } ?: HomeUiState.NoVehicle(state.account.profile, catalog.items, address)
                        }
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

    /**
     * Re-fetches only the address, without a full session refresh — called when returning from
     * ClientAddressScreen (FR-005), since saving/editing there doesn't itself change session
     * state and wouldn't otherwise be picked up by the collectLatest above.
     */
    fun refreshAddress() {
        viewModelScope.launch {
            val address = currentAddress()
            _uiState.value = when (val current = _uiState.value) {
                is HomeUiState.Content -> current.copy(address = address)
                is HomeUiState.NoVehicle -> current.copy(address = address)
                else -> current
            }
        }
    }

    private suspend fun currentAddress(): ClientAddress? =
        (clientAddressRepository.current() as? ClientAddressResult.Success)?.address

    companion object {
        fun factory(
            repository: SessionRepository,
            catalogRepository: ServiceCatalogRepository,
            clientAddressRepository: ClientAddressRepository,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(HomeViewModel::class.java))
                    return HomeViewModel(repository, catalogRepository, clientAddressRepository) as T
                }
            }
    }
}

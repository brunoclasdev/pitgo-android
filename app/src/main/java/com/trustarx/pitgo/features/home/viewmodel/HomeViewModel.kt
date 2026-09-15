package com.trustarx.pitgo.features.home.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.trustarx.pitgo.core.auth.model.AppSessionState
import com.trustarx.pitgo.core.auth.repository.SessionRepository
import com.trustarx.pitgo.core.catalog.model.CatalogLoadResult
import com.trustarx.pitgo.core.catalog.model.HomeService
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
    private val paymentRepository: com.trustarx.pitgo.features.payment.repository.PaymentRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    private val _recoverable = MutableStateFlow<com.trustarx.pitgo.features.payment.model.Payment?>(null)
    val recoverable = _recoverable.asStateFlow()

    /**
     * `sessionRepository.state` reemite a cada mudança de QUALQUER campo da sessão (ex.:
     * `applyPrimaryVehicle`, `applyProviderSnapshot` de outras telas) — não só em login/logout.
     * Sem esse cache, cada reemissão refazia `catalogRepository.homeServices()` e
     * `clientAddressRepository.current()` do zero (achado A3 do relatório de sobrecarga de
     * 2026-09-15). Catálogo e endereço só são recarregados quando o usuário logado muda de
     * verdade (ou em `refresh()` explícito) — as demais reemissões só reconstroem a UI com o
     * `account` novo por cima do catálogo/endereço já em cache.
     */
    private var loadedForUserId: String? = null
    private var cachedCatalog: List<HomeService> = emptyList()
    private var cachedAddress: ClientAddress? = null

    init {
        viewModelScope.launch {
            sessionRepository.state.collectLatest { state ->
                when (state) {
                    is AppSessionState.Authenticated.Client -> {
                        if (loadedForUserId != state.userId) {
                            when (val catalog = catalogRepository.homeServices()) {
                                is CatalogLoadResult.Success -> {
                                    cachedCatalog = catalog.items
                                    cachedAddress = currentAddress()
                                    loadedForUserId = state.userId
                                    refreshPayment()
                                }
                                else -> {
                                    _uiState.value = HomeUiState.Failure
                                    return@collectLatest
                                }
                            }
                        }
                        _uiState.value = state.account.primaryVehicle?.let { vehicle ->
                            HomeUiState.Content(state.account.profile, vehicle, cachedCatalog, cachedAddress)
                        } ?: HomeUiState.NoVehicle(state.account.profile, cachedCatalog, cachedAddress)
                    }
                    AppSessionState.Initializing, AppSessionState.Verifying -> _uiState.value = HomeUiState.Loading
                    else -> {
                        loadedForUserId = null
                        _recoverable.value = null
                        _uiState.value = HomeUiState.Failure
                    }
                }
            }
        }
    }

    fun refresh() {
        loadedForUserId = null
        viewModelScope.launch { sessionRepository.refresh() }
    }

    fun refreshPayment() {
        val userId = loadedForUserId ?: return
        viewModelScope.launch {
            val result = paymentRepository.recoverable()
            if (loadedForUserId == userId) {
                _recoverable.value = (result as? com.trustarx.pitgo.features.payment.model.PaymentEvent.StatusChanged)?.payment
            }
        }
    }

    /**
     * Re-fetches only the address, without a full session refresh — called when returning from
     * ClientAddressScreen (FR-005), since saving/editing there doesn't itself change session
     * state and wouldn't otherwise be picked up by the collectLatest above.
     */
    fun refreshAddress() {
        viewModelScope.launch {
            val address = currentAddress()
            cachedAddress = address
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
            paymentRepository: com.trustarx.pitgo.features.payment.repository.PaymentRepository,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(HomeViewModel::class.java))
                    return HomeViewModel(repository, catalogRepository, clientAddressRepository, paymentRepository) as T
                }
            }
    }
}

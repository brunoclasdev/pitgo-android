package com.trustarx.pitgo.features.pittec.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.trustarx.pitgo.core.auth.model.AppSessionState
import com.trustarx.pitgo.core.auth.repository.SessionRepository
import com.trustarx.pitgo.features.pittec.model.PitTecDispatchResult
import com.trustarx.pitgo.features.pittec.model.PitTecDispatchSnapshot
import com.trustarx.pitgo.features.pittec.repository.PitTecRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

sealed interface PitTecResumeState {
    data object Loading : PitTecResumeState
    data object None : PitTecResumeState
    data class Active(val snapshot: PitTecDispatchSnapshot) : PitTecResumeState
}

/**
 * Client-only counterpart to [com.trustarx.pitgo.features.mobiledispatch.viewmodel.DispatchResumeViewModel],
 * kept fully separate so a PitTec search/appointment is never restored through the legacy
 * MOBILE_SEQUENTIAL contract (see contracts/pittec-rpcs.md `get_my_active_pittec_dispatch`). The
 * provider-side offer resume already goes through the shared, dispatch-kind-agnostic
 * `MobileDispatchRepository.restoreProviderAny()` used by DispatchResumeViewModel.
 */
class PitTecResumeViewModel(
    sessionRepository: SessionRepository,
    private val repository: PitTecRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<PitTecResumeState>(PitTecResumeState.Loading)
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            sessionRepository.state.collectLatest { session ->
                val client = session as? AppSessionState.Authenticated.Client
                if (client == null) {
                    _state.value = PitTecResumeState.None
                    return@collectLatest
                }
                _state.value = when (val result = repository.restore()) {
                    is PitTecDispatchResult.Success -> PitTecResumeState.Active(result.snapshot)
                    else -> PitTecResumeState.None
                }
            }
        }
    }

    companion object {
        fun factory(session: SessionRepository, repository: PitTecRepository) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    PitTecResumeViewModel(session, repository) as T
            }
    }
}

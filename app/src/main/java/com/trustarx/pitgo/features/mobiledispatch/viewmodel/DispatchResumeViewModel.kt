package com.trustarx.pitgo.features.mobiledispatch.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.trustarx.pitgo.core.auth.model.AppSessionState
import com.trustarx.pitgo.core.auth.repository.SessionRepository
import com.trustarx.pitgo.features.mobiledispatch.model.MobileDispatchEvent
import com.trustarx.pitgo.features.mobiledispatch.model.MobileDispatchResult
import com.trustarx.pitgo.features.mobiledispatch.model.MobileDispatchSnapshot
import com.trustarx.pitgo.features.mobiledispatch.repository.MobileDispatchRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

sealed interface DispatchResumeState {
    data object Loading : DispatchResumeState
    data object None : DispatchResumeState
    data class Active(val ownerId: String, val snapshot: MobileDispatchSnapshot) : DispatchResumeState
}

class DispatchResumeViewModel(
    sessionRepository: SessionRepository,
    private val repository: MobileDispatchRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<DispatchResumeState>(DispatchResumeState.Loading)
    val state = _state.asStateFlow()
    init {
        viewModelScope.launch {
            sessionRepository.state.collectLatest { session ->
                val authenticated = session as? AppSessionState.Authenticated
                if (authenticated == null) { _state.value = DispatchResumeState.None; return@collectLatest }
                _state.value = DispatchResumeState.Loading
                // Providers stay on this snapshot for the whole session (dashboard, tracking, etc.),
                // not just the incoming-request screen, so a new PENDING appointment created while
                // they are elsewhere still has to reach them. Keep the subscription open instead of
                // resolving once, otherwise an incoming dispatch is only picked up on next login.
                val providerId = (authenticated as? AppSessionState.Authenticated.ProviderReady)?.account?.providerId
                if (providerId != null) {
                    repository.observeProvider(providerId).collect { event ->
                        if (event is MobileDispatchEvent.Snapshot) {
                            _state.value = event.value?.let { DispatchResumeState.Active(authenticated.userId, it) }
                                ?: DispatchResumeState.None
                        }
                        // Failure events are transient (offline/reconnect); keep the last known state.
                    }
                    return@collectLatest
                }
                _state.value = when (val result = repository.restore()) {
                    is MobileDispatchResult.Success -> DispatchResumeState.Active(authenticated.userId, result.snapshot)
                    else -> DispatchResumeState.None
                }
            }
        }
    }
    companion object {
        fun factory(session: SessionRepository, repository: MobileDispatchRepository) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    DispatchResumeViewModel(session, repository) as T
            }
    }
}

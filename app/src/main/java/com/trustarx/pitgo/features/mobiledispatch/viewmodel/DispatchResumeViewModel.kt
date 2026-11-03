package com.trustarx.pitgo.features.mobiledispatch.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.trustarx.pitgo.core.auth.model.AppSessionState
import com.trustarx.pitgo.core.auth.repository.SessionRepository
import com.trustarx.pitgo.features.mobiledispatch.model.MobileDispatchResult
import com.trustarx.pitgo.features.mobiledispatch.model.MobileDispatchSnapshot
import com.trustarx.pitgo.features.mobiledispatch.repository.MobileDispatchRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

sealed interface DispatchResumeState {
    data object Loading : DispatchResumeState
    data class Search(val ownerId: String, val contextId: String, val rejectedAppointmentId: String) : DispatchResumeState
    data object None : DispatchResumeState
    data class Active(val ownerId: String, val snapshot: MobileDispatchSnapshot) : DispatchResumeState
}

class DispatchResumeViewModel(
    private val sessionRepository: SessionRepository,
    private val repository: MobileDispatchRepository,
    private val guincho: com.trustarx.pitgo.features.guincho.repository.GuinchoRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<DispatchResumeState>(DispatchResumeState.Loading)
    val state = _state.asStateFlow()
    private val handledResumes = mutableSetOf<String>()
    fun consumeSearch(search: DispatchResumeState.Search) {
        handledResumes.add(search.ownerId+":"+search.rejectedAppointmentId)
        if(_state.value==search) _state.value=DispatchResumeState.None
    }
    fun requestGuinchoResume(appointmentId: String) {
        val owner = (sessionRepository.state.value as? AppSessionState.Authenticated.Client)?.userId ?: return
        viewModelScope.launch {
            val result=guincho.resumeSearch() as? com.trustarx.pitgo.features.guincho.model.GuinchoResult.Success ?: return@launch
            val search=result.value as? com.trustarx.pitgo.features.guincho.model.GuinchoSearchResume.Search ?: return@launch
            if(search.rejectedAppointmentId==appointmentId && sessionRepository.state.value.let { (it as? AppSessionState.Authenticated.Client)?.userId }==owner && !handledResumes.contains(owner+":"+appointmentId))
                _state.value=DispatchResumeState.Search(owner,search.contextId,appointmentId)
        }
    }
    init {
        viewModelScope.launch {
            sessionRepository.state.collectLatest { session ->
                val authenticated = session as? AppSessionState.Authenticated
                if ((authenticated as? AppSessionState.Authenticated.ProviderReady)?.account?.locationMode ==
                    com.trustarx.pitgo.core.account.model.ProviderLocationMode.MOBILE) {
                    _state.value = DispatchResumeState.None
                    return@collectLatest
                }
                if (authenticated == null) { handledResumes.clear(); _state.value = DispatchResumeState.None; return@collectLatest }
                if ((_state.value as? DispatchResumeState.Active)?.ownerId != authenticated.userId) {
                    _state.value = DispatchResumeState.Loading
                }
                // Providers stay on this snapshot for the whole session (dashboard, tracking, etc.),
                // not just the incoming-request screen, so a new PENDING appointment created while
                // they are elsewhere still has to reach them. Keep the subscription open instead of
                // resolving once, otherwise an incoming dispatch is only picked up on next login.
                val providerId = (authenticated as? AppSessionState.Authenticated.ProviderReady)?.account?.providerId
                if (providerId != null) {
                    // observeProvider()'s underlying channelFlow sends one Failure event and then
                    // COMPLETES on any transient error (network blip, realtime disconnect while
                    // backgrounded, etc.) — see MobileDispatchRepository.observe(). Without a retry
                    // here, a single hiccup permanently stopped this provider from ever being
                    // notified of new incoming requests again for the rest of the session (reproduced
                    // live: broadcast fired correctly in Postgres, but the provider device never
                    // reacted and the request silently expired). Keep resubscribing forever instead
                    // of letting one failure end the loop.
                    // A realtime channel can also go "zombie" — still reports subscribed, never
                    // errors, just silently stops delivering messages (reproduced live on the
                    // client side: minutes went by with correct server-side broadcasts and no
                    // reaction). observeProvider()'s retry-on-failure loop above does nothing for
                    // that case since the flow never actually fails. Poll restore() on a timer as
                    // an independent fallback so a new PENDING request is picked up regardless of
                    // whatever state the realtime channel silently ended up in.
                    coroutineScope {
                        launch {
                            while (true) {
                                delay(PROVIDER_POLL_INTERVAL_MS)
                                when (val result = repository.restoreAnyDispatchKind()) {
                                    is MobileDispatchResult.Success ->
                                        _state.value = DispatchResumeState.Active(authenticated.userId, result.snapshot)
                                    MobileDispatchResult.None -> _state.value = DispatchResumeState.None
                                    else -> Unit
                                }
                            }
                        }
                        while (true) {
                            try {
                                // observeProvider()'s own restore() result is ignored here — it is
                                // always MOBILE_SEQUENTIAL-only, which would wrongly report `None`
                                // for a provider currently offering/assigned a PitTec appointment.
                                // The topic itself already covers both dispatch kinds (shared
                                // `appointments`/`service_searches` triggers), so any event is just
                                // used as a wake-up to re-derive authoritative state below.
                                repository.observeProvider(providerId).collect {
                                    when (val result = repository.restoreAnyDispatchKind()) {
                                        is MobileDispatchResult.Success ->
                                            _state.value = DispatchResumeState.Active(authenticated.userId, result.snapshot)
                                        MobileDispatchResult.None -> _state.value = DispatchResumeState.None
                                        else -> Unit
                                    }
                                }
                            } catch (cancel: CancellationException) {
                                throw cancel
                            } catch (_: Throwable) {
                                // Swallow — observeProvider() already classifies and emits failures
                                // as events; this only guards against an unexpected throw reaching us.
                            }
                            delay(PROVIDER_RESUBSCRIBE_DELAY_MS)
                        }
                    }
                }
                // A client needs the same periodic recovery as a provider. A transient failure
                // cannot mean that their paid appointment disappeared.
                while (true) {
                    when (val result = repository.restore()) {
                        is MobileDispatchResult.Success -> _state.value = DispatchResumeState.Active(authenticated.userId, result.snapshot)
                        MobileDispatchResult.None -> {
                            when (val resumed = guincho.resumeSearch()) {
                                is com.trustarx.pitgo.features.guincho.model.GuinchoResult.Success -> {
                                    val search = resumed.value as? com.trustarx.pitgo.features.guincho.model.GuinchoSearchResume.Search
                                    _state.value = if (search?.rejectedAppointmentId != null && !handledResumes.contains(authenticated.userId+":"+search.rejectedAppointmentId))
                                        DispatchResumeState.Search(authenticated.userId, search.contextId,search.rejectedAppointmentId) else DispatchResumeState.None
                                }
                                else -> Unit
                            }
                        }
                        is MobileDispatchResult.Failure -> Unit
                    }
                    delay(CLIENT_POLL_INTERVAL_MS)
                }
            }
        }
    }
    companion object {
        private const val CLIENT_POLL_INTERVAL_MS = 5_000L
        private const val PROVIDER_RESUBSCRIBE_DELAY_MS = 3_000L
        private const val PROVIDER_POLL_INTERVAL_MS = 5_000L

        fun factory(session: SessionRepository, repository: MobileDispatchRepository, guincho: com.trustarx.pitgo.features.guincho.repository.GuinchoRepository) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    DispatchResumeViewModel(session, repository, guincho) as T
            }
    }
}

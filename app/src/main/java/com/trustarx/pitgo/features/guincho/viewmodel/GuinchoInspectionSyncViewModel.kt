package com.trustarx.pitgo.features.guincho.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.trustarx.pitgo.core.auth.model.AppSessionState
import com.trustarx.pitgo.core.auth.repository.SessionRepository
import com.trustarx.pitgo.core.network.ConnectivityObserver
import com.trustarx.pitgo.core.network.ConnectivityState
import com.trustarx.pitgo.features.guincho.repository.GuinchoInspectionRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class GuinchoInspectionSyncViewModel(
    private val repository: GuinchoInspectionRepository,
    sessions: SessionRepository,
    connectivity: ConnectivityObserver,
) : ViewModel() {
    private val visible = MutableStateFlow(false)
    private var hideJob: Job? = null
    init {
        viewModelScope.launch {
            var previousAccount: String? = null
            combine(sessions.state, connectivity.state, visible) { session, network, foreground ->
                Triple(session, network == ConnectivityState.ONLINE, foreground)
            }.distinctUntilChanged().collectLatest { (session, online, foreground) ->
                // Verification pauses uploads; it is not an account change. In particular,
                // a camera result can still arrive while the same session is being checked.
                if (session == AppSessionState.Initializing || session == AppSessionState.Verifying ||
                    session == AppSessionState.OfflineVerification) return@collectLatest
                val account = (session as? AppSessionState.Authenticated)?.userId
                if (account != previousAccount) {
                    val leaving = previousAccount
                    repository.files.clear(); repository.pending.hide()
                    previousAccount = account
                    if (leaving != null) repository.files.clearReadCache(leaving)
                    if (account != null) repository.pending.load(account)
                }
                if (account != null && foreground && online) {
                    var wake = true
                    while (isActive) {
                        try {
                            if (wake) { repository.wakePending(account); wake = false }
                            repository.syncPending(account)
                        } catch (cancel: CancellationException) { throw cancel
                        } catch (_: com.trustarx.pitgo.features.guincho.model.InspectionException) { delay(1000) }
                        delay(500)
                    }
                }
            }
        }
    }
    fun onVisible() { hideJob?.cancel(); hideJob = null; visible.value = true }
    fun onHidden() {
        hideJob?.cancel()
        // Brief camera handoffs must not cancel/re-upload the preceding photo repeatedly.
        // Process death still stops the runner immediately; the journal survives it.
        hideJob = viewModelScope.launch { delay(10_000); visible.value = false }
    }
    companion object {
        fun factory(repository: GuinchoInspectionRepository, sessions: SessionRepository, connectivity: ConnectivityObserver) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = GuinchoInspectionSyncViewModel(repository, sessions, connectivity) as T
        }
    }
}

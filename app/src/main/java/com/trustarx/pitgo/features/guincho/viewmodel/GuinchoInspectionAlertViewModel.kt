package com.trustarx.pitgo.features.guincho.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.trustarx.pitgo.core.auth.model.AppSessionState
import com.trustarx.pitgo.core.auth.repository.SessionRepository
import com.trustarx.pitgo.core.network.ConnectivityObserver
import com.trustarx.pitgo.core.network.ConnectivityState
import com.trustarx.pitgo.features.guincho.model.GuinchoInspectionAlert
import com.trustarx.pitgo.features.guincho.repository.GuinchoInspectionAlertRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class InspectionAlertUiState(val owner: String? = null, val alerts: List<GuinchoInspectionAlert> = emptyList(),
    val opening: Boolean = false, val openError: Boolean = false, val destination: Pair<String, Boolean>? = null)
class GuinchoInspectionAlertViewModel(private val repository: GuinchoInspectionAlertRepository,
    sessions: SessionRepository, connectivity: ConnectivityObserver) : ViewModel() {
    private val _state = MutableStateFlow(InspectionAlertUiState())
    val state = _state.asStateFlow()
    private val visible = MutableStateFlow(false)
    private var approvedVersions = emptySet<String>()
    init {
        viewModelScope.launch {
            combine(sessions.state, connectivity.state, visible) { session, network, shown -> Triple(session, network, shown) }
                .distinctUntilChanged().collectLatest { (session, network, shown) ->
                    if (session == AppSessionState.Initializing || session == AppSessionState.Verifying || session == AppSessionState.OfflineVerification) return@collectLatest
                    val account = (session as? AppSessionState.Authenticated.Client)?.userId
                    if (_state.value.owner != account) {
                        approvedVersions = emptySet()
                        _state.value = InspectionAlertUiState(owner = account)
                    }
                    if (account != null && shown && network == ConnectivityState.ONLINE) while (isActive) {
                        try {
                            val alerts = repository.refresh(account)
                            _state.update { if (it.owner == account) it.copy(
                                alerts = alerts.filterNot { alert -> alert.versionId in approvedVersions },
                            ) else it }
                        } catch (cancel: CancellationException) { if (cancel !is TimeoutCancellationException) throw cancel
                        } catch (_: Exception) { /* Keep last known pending indication; never interpret failed reads as approval. */ }
                        delay(5000)
                    }
                }
        }
    }
    fun onVisible() { visible.value = true }
    fun onHidden() { visible.value = false }
    fun approvalConfirmed(account: String, appointmentId: String, versionId: String) {
        if (_state.value.owner != account) return
        // A poll started before approval can still return this version as pending.
        approvedVersions = approvedVersions + versionId
        _state.update { current ->
            if (current.owner == account) current.copy(
                alerts = current.alerts.filterNot { it.appointmentId == appointmentId && it.versionId == versionId },
                openError = false,
            ) else current
        }
    }
    fun open(id: String) {
        val owner = _state.value.owner ?: return
        if (_state.value.opening) return
        _state.update { it.copy(opening = true, openError = false) }
        viewModelScope.launch {
            try {
                val destination = repository.open(owner, id)
                _state.update { if (it.owner == owner) it.copy(destination = destination, openError = destination == null) else it }
            } catch (cancel: CancellationException) { if (cancel !is TimeoutCancellationException) throw cancel; _state.update { it.copy(openError = true) }
            } catch (_: Exception) { _state.update { it.copy(openError = true) }
            } finally { _state.update { it.copy(opening = false) } }
        }
    }
    fun dismiss() { _state.update { it.copy(openError = false) } }
    fun consumed() { _state.update { it.copy(destination = null) } }
    companion object {
        fun factory(repository: GuinchoInspectionAlertRepository, sessions: SessionRepository, connectivity: ConnectivityObserver) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = GuinchoInspectionAlertViewModel(repository, sessions, connectivity) as T
        }
    }
}

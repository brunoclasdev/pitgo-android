package com.trustarx.pitgo.features.mobiledispatch.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.trustarx.pitgo.core.location.model.DeviceLocationResult
import com.trustarx.pitgo.core.location.repository.DeviceLocationRepository
import com.trustarx.pitgo.features.mobiledispatch.model.ClientMobileDispatchUiState
import com.trustarx.pitgo.features.mobiledispatch.model.DispatchPendingAction
import com.trustarx.pitgo.features.mobiledispatch.model.MobileDispatchFailure
import com.trustarx.pitgo.features.mobiledispatch.model.MobileDispatchResult
import com.trustarx.pitgo.features.mobiledispatch.model.MobileDispatchStartCommand
import com.trustarx.pitgo.features.mobiledispatch.model.MobileDispatchStatus
import com.trustarx.pitgo.features.mobiledispatch.model.PendingDispatchAction
import com.trustarx.pitgo.features.mobiledispatch.repository.MobileDispatchRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.ceil

class ClientMobileDispatchViewModel(
    private val serviceId: String,
    private val repository: MobileDispatchRepository,
    private val locationRepository: DeviceLocationRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<ClientMobileDispatchUiState>(ClientMobileDispatchUiState.Restoring)
    val state: StateFlow<ClientMobileDispatchUiState> = _state.asStateFlow()
    private val operation = AtomicLong()
    private var actionJob: Job? = null
    private var streamJob: Job? = null
    private var clockJob: Job? = null
    private var searchId = UUID.randomUUID().toString()

    init { restore() }

    fun restore() = launchAction(PendingDispatchAction.START) { repository.restore() }

    fun startWithPreciseLocation() {
        if (actionJob?.isActive == true) return
        val id = operation.incrementAndGet()
        actionJob = viewModelScope.launch {
            _state.value = ClientMobileDispatchUiState.Locating
            val result = when (val location = locationRepository.currentPreciseFix(30_000)) {
                is DeviceLocationResult.Success -> {
                    _state.value = ClientMobileDispatchUiState.StartingSearch
                    repository.start(MobileDispatchStartCommand(searchId, serviceId, location.fix))
                }
                DeviceLocationResult.PermissionDenied -> MobileDispatchResult.Failure(MobileDispatchFailure.LOCATION_REQUIRED)
                else -> MobileDispatchResult.Failure(MobileDispatchFailure.LOCATION_UNAVAILABLE)
            }
            if (id == operation.get()) apply(result)
        }
    }

    fun next() = withSnapshot(PendingDispatchAction.NEXT) { repository.next(it.searchId, it.revision) }
    fun request() = withSnapshot(PendingDispatchAction.REQUEST) { repository.request(it.searchId, it.revision) }
    fun cancel(reason: String) = withSnapshot(PendingDispatchAction.CANCEL) {
        val appointment = it.appointment
            ?: return@withSnapshot MobileDispatchResult.Failure(MobileDispatchFailure.CONTRACT)
        repository.cancel(appointment.id, reason, appointment.revision)
    }

    fun retry() {
        when (_state.value) {
            is ClientMobileDispatchUiState.LocationRequired -> startWithPreciseLocation()
            is ClientMobileDispatchUiState.Empty,
            is ClientMobileDispatchUiState.Terminal,
            -> {
                searchId = UUID.randomUUID().toString()
                startWithPreciseLocation()
            }
            else -> restore()
        }
    }

    private fun withSnapshot(
        action: PendingDispatchAction,
        block: suspend (com.trustarx.pitgo.features.mobiledispatch.model.MobileDispatchSnapshot) -> MobileDispatchResult,
    ) {
        val content = _state.value as? ClientMobileDispatchUiState.Content ?: return
        if (content.pending != null) return
        launchAction(action) { block(content.snapshot) }
    }

    private fun launchAction(action: PendingDispatchAction, block: suspend () -> MobileDispatchResult) {
        if (actionJob?.isActive == true) return
        val id = operation.incrementAndGet()
        val current = _state.value as? ClientMobileDispatchUiState.Content
        if (current != null) _state.value = current.copy(pending = DispatchPendingAction(action, id))
        actionJob = viewModelScope.launch { if (id == operation.get()) apply(block()) }
    }

    private fun apply(result: MobileDispatchResult) {
        when (result) {
            MobileDispatchResult.None -> _state.value = ClientMobileDispatchUiState.LocationRequired()
            is MobileDispatchResult.Failure -> {
                val current = _state.value as? ClientMobileDispatchUiState.Content
                if (result.reason == MobileDispatchFailure.SESSION_EXPIRED) {
                    _state.value = ClientMobileDispatchUiState.SessionExpired
                } else if (current != null) _state.value = current.copy(pending = null, failure = result.reason)
                else _state.value = if (result.reason in setOf(
                        MobileDispatchFailure.LOCATION_REQUIRED,
                        MobileDispatchFailure.LOCATION_UNAVAILABLE,
                    )) ClientMobileDispatchUiState.LocationRequired(result.reason)
                    else ClientMobileDispatchUiState.Failure(result.reason)
            }
            is MobileDispatchResult.Success -> {
                val snapshot = result.snapshot
                _state.value = when (snapshot.status) {
                    MobileDispatchStatus.EXHAUSTED -> ClientMobileDispatchUiState.Empty(snapshot.serviceName)
                    MobileDispatchStatus.CANCELLED, MobileDispatchStatus.COMPLETED ->
                        ClientMobileDispatchUiState.Terminal(snapshot.status)
                    else -> ClientMobileDispatchUiState.Content(snapshot)
                }
                observe(snapshot.searchId)
                startClock()
            }
        }
    }

    private fun observe(id: String) {
        if (streamJob?.isActive == true) return
        streamJob = viewModelScope.launch {
            repository.observeSearch(id).collect { event ->
                when (event) {
                    is com.trustarx.pitgo.features.mobiledispatch.model.MobileDispatchEvent.Snapshot ->
                        event.value?.let { incoming ->
                            val current = (_state.value as? ClientMobileDispatchUiState.Content)?.snapshot
                            if (current == null || incoming.revision > current.revision) {
                                apply(MobileDispatchResult.Success(incoming))
                            }
                        }
                    is com.trustarx.pitgo.features.mobiledispatch.model.MobileDispatchEvent.Failure ->
                        if (event.reason == MobileDispatchFailure.SESSION_EXPIRED) {
                            _state.value = ClientMobileDispatchUiState.SessionExpired
                        }
                }
            }
        }
    }

    private fun startClock() {
        if (clockJob?.isActive == true) return
        clockJob = viewModelScope.launch {
            while (true) {
                val content = _state.value as? ClientMobileDispatchUiState.Content
                val deadline = content?.snapshot?.appointment?.responseDeadlineEpochMillis
                if (content != null && deadline != null) {
                    val offset = content.snapshot.serverNowEpochMillis - System.currentTimeMillis()
                    val seconds = ceil((deadline - (System.currentTimeMillis() + offset)) / 1000.0)
                        .toLong().coerceAtLeast(0)
                    _state.value = content.copy(remainingSeconds = seconds)
                    if (seconds == 0L) restore()
                }
                delay(1_000)
            }
        }
    }

    companion object {
        fun factory(
            serviceId: String,
            repository: MobileDispatchRepository,
            locationRepository: DeviceLocationRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ClientMobileDispatchViewModel(serviceId, repository, locationRepository) as T
        }
    }
}

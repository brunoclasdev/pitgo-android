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
import com.trustarx.pitgo.features.payment.model.PaymentEvent
import com.trustarx.pitgo.features.payment.model.PaymentStatus
import com.trustarx.pitgo.features.payment.repository.PaymentGateway
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.ceil

class ClientMobileDispatchViewModel(
    private val serviceId: String,
    private val repository: MobileDispatchRepository,
    private val locationRepository: DeviceLocationRepository,
    // Set only when arriving straight from an approved payment (features/payment); the search
    // then starts immediately with that already-paid id instead of waiting for a manual tap, and
    // instead of trying to restore an (impossible, brand new) older session — see spec 018.
    initialRequestId: String? = null,
    private val paymentRepository: PaymentGateway? = null,
) : ViewModel() {
    private val _state = MutableStateFlow<ClientMobileDispatchUiState>(ClientMobileDispatchUiState.Restoring)
    val state: StateFlow<ClientMobileDispatchUiState> = _state.asStateFlow()
    private val operation = AtomicLong()
    private var actionJob: Job? = null
    private var streamJob: Job? = null
    private var pollJob: Job? = null
    private var clockJob: Job? = null
    private var refundWatcherJob: Job? = null
    private var searchId = initialRequestId ?: UUID.randomUUID().toString()

    init {
        if (initialRequestId != null) startWithPreciseLocation() else restore()
        watchForRefund()
    }

    /**
     * A cancellation or an exhausted search moves an approved payment to REFUND_PENDING
     * server-side (FR-015/FR-017); this client-initiated call is what actually performs the
     * Mercado Pago refund (FR-018) — see contracts/payment-rpcs.md.
     */
    private fun watchForRefund() {
        val payments = paymentRepository ?: return
        refundWatcherJob?.cancel()
        refundWatcherJob = viewModelScope.launch {
            payments.observe(searchId).collect { event ->
                if (event !is PaymentEvent.StatusChanged) return@collect
                when (event.status) {
                    PaymentStatus.REFUND_PENDING -> {
                        setRefundNotice(PaymentStatus.REFUND_PENDING)
                        // Cancellation scheduled the refund atomically; only observe the server.
                    }
                    PaymentStatus.REFUNDED, PaymentStatus.REFUND_FAILED -> setRefundNotice(event.status)
                    else -> Unit
                }
            }
        }
    }

    private fun setRefundNotice(status: PaymentStatus) {
        _state.update { current ->
            if (current is ClientMobileDispatchUiState.Content) current.copy(refundNotice = status) else current
        }
    }

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
                watchForRefund()
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
                startPolling()
            }
        }
    }

    /**
     * Realtime is not fully trustworthy as the sole source of truth here: a WebSocket can end up
     * in a "zombie" state (still reports SUBSCRIBED, never errors, just stops delivering
     * messages) after enough backgrounding/network changes — reproduced live: the provider
     * accepted and started the appointment server-side, broadcasts fired correctly, but the
     * client stayed frozen on "Aguardando resposta" for several minutes because nothing ever told
     * it to look again (observe()'s retry-on-failure loop never triggers, since the flow never
     * actually fails). This periodic restore() is a resilience fallback, independent of whatever
     * state the realtime channel thinks it's in — same "poke, then re-fetch authoritative state"
     * philosophy used elsewhere, just on a timer instead of a broadcast.
     */
    private fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (true) {
                delay(POLL_INTERVAL_MS)
                if (_state.value !is ClientMobileDispatchUiState.Content) return@launch
                val result = repository.restore()
                if (result is MobileDispatchResult.Success) {
                    val current = (_state.value as? ClientMobileDispatchUiState.Content)?.snapshot
                    if (current == null || result.snapshot.revision > current.revision) {
                        apply(result)
                    }
                }
                // Transient failures are ignored here — the next poll retries, and observe()'s
                // own retry loop handles realtime-specific failure reporting (e.g. session expiry).
            }
        }
    }

    /**
     * repository.observeSearch()'s underlying channelFlow sends one Failure event and then
     * COMPLETES on any transient error (network blip, realtime disconnect while backgrounded,
     * etc.) — see MobileDispatchRepository.observe(). Without a retry, one hiccup left the client
     * permanently stuck on whatever screen it last rendered (e.g. the search/waiting screen) even
     * after the provider accepted server-side, since nothing was listening anymore to notice.
     * Keeps resubscribing until the search reaches a terminal state or the session expires.
     */
    private fun observe(id: String) {
        if (streamJob?.isActive == true) return
        streamJob = viewModelScope.launch {
            while (true) {
                try {
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
                } catch (cancel: kotlinx.coroutines.CancellationException) {
                    throw cancel
                } catch (_: Throwable) {
                    // observeSearch() already classifies and emits failures as events; this only
                    // guards against an unexpected throw reaching us.
                }
                if (_state.value == ClientMobileDispatchUiState.SessionExpired) return@launch
                if (_state.value !is ClientMobileDispatchUiState.Content) return@launch
                kotlinx.coroutines.delay(SEARCH_RESUBSCRIBE_DELAY_MS)
            }
        }
    }

    private fun startClock() {
        android.util.Log.d("ClientDispatchClockDBG", "startClock called, clockJob active=${clockJob?.isActive}")
        if (clockJob?.isActive == true) return
        clockJob = viewModelScope.launch {
            android.util.Log.d("ClientDispatchClockDBG", "clock loop launched")
            while (true) {
                val content = _state.value as? ClientMobileDispatchUiState.Content
                val deadline = content?.snapshot?.appointment?.responseDeadlineEpochMillis
                android.util.Log.d(
                    "ClientDispatchClockDBG",
                    "tick: state=${_state.value::class.simpleName} status=${content?.snapshot?.status} deadline=$deadline",
                )
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
        private const val SEARCH_RESUBSCRIBE_DELAY_MS = 3_000L
        private const val POLL_INTERVAL_MS = 5_000L

        fun factory(
            serviceId: String,
            repository: MobileDispatchRepository,
            locationRepository: DeviceLocationRepository,
            initialRequestId: String? = null,
            paymentRepository: PaymentGateway? = null,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ClientMobileDispatchViewModel(
                serviceId, repository, locationRepository, initialRequestId, paymentRepository,
            ) as T
        }
    }
}

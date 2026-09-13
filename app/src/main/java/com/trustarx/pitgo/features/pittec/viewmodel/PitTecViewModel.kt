package com.trustarx.pitgo.features.pittec.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.trustarx.pitgo.core.location.model.ClientLocationPurpose
import com.trustarx.pitgo.core.location.model.DeviceLocationFix
import com.trustarx.pitgo.core.location.model.DeviceLocationResult
import com.trustarx.pitgo.core.location.repository.DeviceLocationRepository
import com.trustarx.pitgo.core.map.model.MapCoordinate
import com.trustarx.pitgo.core.map.model.MapViewport
import com.trustarx.pitgo.core.map.model.MapViewportCommand
import com.trustarx.pitgo.core.map.model.MapSurfaceState
import com.trustarx.pitgo.core.network.ConnectivityObserver
import com.trustarx.pitgo.core.network.ConnectivityState
import com.trustarx.pitgo.features.pittec.model.NearbyPitTecMarker
import com.trustarx.pitgo.features.pittec.model.PitTecDispatchEvent
import com.trustarx.pitgo.features.pittec.model.PitTecDispatchPhase
import com.trustarx.pitgo.features.pittec.model.PitTecDispatchResult
import com.trustarx.pitgo.features.pittec.model.PitTecDispatchStatus
import com.trustarx.pitgo.features.pittec.model.PitTecDiscoveryResult
import com.trustarx.pitgo.features.pittec.model.PitTecDiscoveryState
import com.trustarx.pitgo.features.pittec.model.PitTecFailure
import com.trustarx.pitgo.features.pittec.model.PitTecLocationState
import com.trustarx.pitgo.features.pittec.model.PitTecPending
import com.trustarx.pitgo.features.pittec.model.PitTecPendingAction
import com.trustarx.pitgo.features.pittec.model.PitTecStartCommand
import com.trustarx.pitgo.features.pittec.model.PitTecUiState
import com.trustarx.pitgo.features.pittec.repository.PitTecRepository
import com.trustarx.pitgo.features.payment.model.PaymentEvent
import com.trustarx.pitgo.features.payment.model.PaymentStatus
import com.trustarx.pitgo.features.payment.model.RefundResult
import com.trustarx.pitgo.features.payment.repository.PaymentRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.ceil

class PitTecViewModel(
    private val serviceId: String,
    private val repository: PitTecRepository,
    private val locationRepository: DeviceLocationRepository,
    private val connectivityObserver: ConnectivityObserver,
    // Set only when arriving straight from an approved payment (features/payment); the call
    // starts immediately with that already-paid id instead of waiting for a manual "Chamar
    // PitTec" tap, and instead of trying to restore an (impossible, brand new) older session.
    initialRequestId: String? = null,
    private val paymentRepository: PaymentRepository? = null,
) : ViewModel() {
    private val _state = MutableStateFlow<PitTecUiState>(
        if (!isUuid(serviceId)) PitTecUiState.InvalidService
        else PitTecUiState.Content(serviceId = serviceId),
    )
    val state: StateFlow<PitTecUiState> = _state.asStateFlow()

    private val operation = AtomicLong()
    private var discoveryJob: Job? = null
    private var actionJob: Job? = null
    private var streamJob: Job? = null
    private var pollJob: Job? = null
    private var clockJob: Job? = null
    private var requestId = initialRequestId ?: UUID.randomUUID().toString()
    private var cameraRevision = 0L

    private var refundWatcherJob: Job? = null

    init {
        if (_state.value is PitTecUiState.Content) {
            useDeviceLocation()
            if (initialRequestId != null) startDispatch() else restoreDispatch()
            watchForRefund()
        }
        viewModelScope.launch {
            connectivityObserver.state.collect { connectivity ->
                update { current ->
                    if (connectivity == ConnectivityState.OFFLINE) {
                        current.copy(discoveryState = PitTecDiscoveryState.Offline)
                    } else current
                }
            }
        }
    }

    fun onLocationPermissionResult(granted: Boolean) {
        if (!granted) {
            update { it.copy(locationState = PitTecLocationState.Denied, clientCoordinate = null) }
            return
        }
        useDeviceLocation()
    }

    private fun useDeviceLocation() = viewModelScope.launch {
        update { it.copy(locationState = PitTecLocationState.Resolving) }
        locationRepository.beginVisibleUse(ClientLocationPurpose.PROVIDER_DISCOVERY)
        when (val result = locationRepository.currentClientFix()) {
            is DeviceLocationResult.Success -> {
                val coordinate = MapCoordinate(result.fix.latitude, result.fix.longitude)
                if (!coordinate.isValid) {
                    update { it.copy(locationState = PitTecLocationState.Unavailable, clientCoordinate = null) }
                    return@launch
                }
                cameraRevision += 1
                update {
                    val target = it.viewport.copy(center = coordinate, zoom = maxOf(it.viewport.zoom, 13.0))
                    it.copy(
                        viewport = target,
                        cameraCommand = MapViewportCommand(target, cameraRevision),
                        locationState = PitTecLocationState.InUse,
                        clientCoordinate = coordinate,
                    )
                }
                loadDiscovery(result.fix)
            }
            else -> update { it.copy(locationState = PitTecLocationState.Unavailable, clientCoordinate = null) }
        }
    }

    private fun loadDiscovery(fix: DeviceLocationFix) {
        discoveryJob?.cancel()
        update { it.copy(discoveryState = PitTecDiscoveryState.Loading) }
        discoveryJob = viewModelScope.launch {
            when (val result = repository.listNearby(serviceId, fix)) {
                is PitTecDiscoveryResult.Success -> applyMarkers(result.markers)
                PitTecDiscoveryResult.Offline -> update { it.copy(discoveryState = PitTecDiscoveryState.Offline) }
                PitTecDiscoveryResult.SessionExpired -> update { it.copy(discoveryState = PitTecDiscoveryState.SessionExpired) }
                PitTecDiscoveryResult.UnsupportedDispatch -> update { it.copy(discoveryState = PitTecDiscoveryState.UpdateRequired) }
                PitTecDiscoveryResult.InvalidInput -> update { it.copy(discoveryState = PitTecDiscoveryState.Failure) }
                PitTecDiscoveryResult.Timeout,
                PitTecDiscoveryResult.ServiceUnavailable,
                PitTecDiscoveryResult.Failure,
                -> update { it.copy(discoveryState = PitTecDiscoveryState.Failure) }
            }
        }
    }

    private fun applyMarkers(markers: List<NearbyPitTecMarker>) = update {
        it.copy(
            markers = markers,
            discoveryState = if (markers.isEmpty()) PitTecDiscoveryState.Empty else PitTecDiscoveryState.Ready,
        )
    }

    fun retryDiscovery() = useDeviceLocation()

    fun retryStyle() = update { it.copy(styleReloadRevision = it.styleReloadRevision + 1) }

    fun updateSurfaceState(surfaceState: MapSurfaceState) = update { it.copy(surfaceState = surfaceState) }

    fun onViewportIdle(viewport: MapViewport) = update { it.copy(viewport = viewport) }

    fun clearTransientLocation() {
        locationRepository.endVisibleUse(ClientLocationPurpose.PROVIDER_DISCOVERY)
    }

    // -- Dispatch (US2) --------------------------------------------------------------------

    private fun restoreDispatch() {
        setDispatch(PitTecDispatchPhase.Restoring)
        actionJob = viewModelScope.launch { applyDispatch(repository.restore()) }
    }

    fun requestPitTec() {
        val content = _state.value as? PitTecUiState.Content ?: return
        if (content.dispatch !is PitTecDispatchPhase.None &&
            content.dispatch !is PitTecDispatchPhase.NoProvider &&
            content.dispatch !is PitTecDispatchPhase.Terminal &&
            content.dispatch !is PitTecDispatchPhase.Failure
        ) return
        if (actionJob?.isActive == true) return
        requestId = UUID.randomUUID().toString()
        startDispatch()
        watchForRefund()
    }

    private fun startDispatch() {
        if (actionJob?.isActive == true) return
        val id = operation.incrementAndGet()
        actionJob = viewModelScope.launch {
            setDispatch(PitTecDispatchPhase.Locating)
            val result = when (val location = locationRepository.currentPreciseFix(30_000)) {
                is DeviceLocationResult.Success -> {
                    setDispatch(PitTecDispatchPhase.StartingSearch)
                    repository.start(PitTecStartCommand(requestId, serviceId, location.fix))
                }
                DeviceLocationResult.PermissionDenied ->
                    PitTecDispatchResult.Failure(PitTecFailure.LOCATION_REQUIRED)
                else -> PitTecDispatchResult.Failure(PitTecFailure.LOCATION_UNAVAILABLE)
            }
            if (id == operation.get()) applyDispatch(result)
        }
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
            payments.observe(requestId).collect { event ->
                if (event !is PaymentEvent.StatusChanged) return@collect
                when (event.status) {
                    PaymentStatus.REFUND_PENDING -> {
                        setRefundNotice(PaymentStatus.REFUND_PENDING)
                        val paymentId = event.paymentId ?: return@collect
                        when (val result = payments.refund(paymentId)) {
                            is RefundResult.Done -> setRefundNotice(result.status)
                            is RefundResult.Failure -> Unit // stays REFUND_PENDING; safe to retry later
                        }
                    }
                    PaymentStatus.REFUNDED, PaymentStatus.REFUND_FAILED -> setRefundNotice(event.status)
                    else -> Unit
                }
            }
        }
    }

    private fun setRefundNotice(status: PaymentStatus) = update { it.copy(refundNotice = status) }

    fun cancelSearch() {
        val active = dispatchActive() ?: return
        if (active.pending != null) return
        if (actionJob?.isActive == true) return
        val id = operation.incrementAndGet()
        setDispatch(active.copy(pending = PitTecPending(PitTecPendingAction.CANCEL, id)))
        actionJob = viewModelScope.launch {
            val result = repository.cancel(active.snapshot.searchId, active.snapshot.revision)
            if (id == operation.get()) applyDispatch(result)
        }
    }

    /** "Tentar novamente" from a no-provider/terminal/failure card starts a brand-new request. */
    fun retryDispatch() {
        when (dispatchPhase()) {
            is PitTecDispatchPhase.NoProvider,
            is PitTecDispatchPhase.Terminal,
            is PitTecDispatchPhase.Failure,
            -> requestPitTec()
            else -> restoreDispatch()
        }
    }

    private fun dispatchPhase(): PitTecDispatchPhase? = (_state.value as? PitTecUiState.Content)?.dispatch

    private fun dispatchActive(): PitTecDispatchPhase.Active? =
        (_state.value as? PitTecUiState.Content)?.dispatch as? PitTecDispatchPhase.Active

    private fun applyDispatch(result: PitTecDispatchResult) {
        when (result) {
            PitTecDispatchResult.None -> {
                setDispatch(PitTecDispatchPhase.None)
                stopObservers()
            }
            is PitTecDispatchResult.Failure -> {
                if (result.reason == PitTecFailure.SESSION_EXPIRED) {
                    update { it.copy(discoveryState = PitTecDiscoveryState.SessionExpired) }
                    return
                }
                val active = dispatchActive()
                if (active != null) {
                    setDispatch(active.copy(pending = null, failure = result.reason))
                } else {
                    setDispatch(PitTecDispatchPhase.Failure(result.reason))
                }
            }
            is PitTecDispatchResult.Success -> {
                val snapshot = result.snapshot
                setDispatch(
                    when (snapshot.status) {
                        PitTecDispatchStatus.EXHAUSTED -> PitTecDispatchPhase.NoProvider(snapshot.serviceName)
                        PitTecDispatchStatus.CANCELLED, PitTecDispatchStatus.COMPLETED ->
                            PitTecDispatchPhase.Terminal(snapshot.status)
                        else -> PitTecDispatchPhase.Active(snapshot)
                    }
                )
                observeSearch(snapshot.searchId)
                startClock()
                startPolling()
            }
        }
    }

    private fun setDispatch(phase: PitTecDispatchPhase) = update { it.copy(dispatch = phase) }

    /**
     * Same resilience rationale as ClientMobileDispatchViewModel: a realtime channel can report
     * SUBSCRIBED forever while silently stopping delivery, so a periodic authoritative re-fetch
     * runs independently of whatever the channel believes its own state is.
     */
    private fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (true) {
                delay(POLL_INTERVAL_MS)
                val active = dispatchActive() ?: return@launch
                val result = repository.restore()
                if (result is PitTecDispatchResult.Success && result.snapshot.revision > active.snapshot.revision) {
                    applyDispatch(result)
                } else if (result is PitTecDispatchResult.None) {
                    applyDispatch(result)
                }
            }
        }
    }

    private fun observeSearch(searchId: String) {
        if (streamJob?.isActive == true) return
        streamJob = viewModelScope.launch {
            while (true) {
                try {
                    repository.observeSearch(searchId).collect { event ->
                        when (event) {
                            is PitTecDispatchEvent.Snapshot -> event.value?.let { incoming ->
                                val active = dispatchActive()
                                if (active == null || incoming.revision > active.snapshot.revision) {
                                    applyDispatch(PitTecDispatchResult.Success(incoming))
                                }
                            }
                            is PitTecDispatchEvent.Failure ->
                                if (event.reason == PitTecFailure.SESSION_EXPIRED) {
                                    update { it.copy(discoveryState = PitTecDiscoveryState.SessionExpired) }
                                }
                        }
                    }
                } catch (cancel: kotlinx.coroutines.CancellationException) {
                    throw cancel
                } catch (_: Throwable) {
                    // observeSearch() already classifies/emits failures as events.
                }
                if (dispatchActive() == null) return@launch
                delay(SEARCH_RESUBSCRIBE_DELAY_MS)
            }
        }
    }

    private fun startClock() {
        if (clockJob?.isActive == true) return
        clockJob = viewModelScope.launch {
            while (true) {
                val active = dispatchActive()
                val deadline = active?.snapshot?.appointment?.responseDeadlineEpochMillis
                if (active != null && deadline != null) {
                    val offset = active.snapshot.serverNowEpochMillis - System.currentTimeMillis()
                    val seconds = ceil((deadline - (System.currentTimeMillis() + offset)) / 1000.0)
                        .toLong().coerceAtLeast(0)
                    setDispatch(active.copy(remainingSeconds = seconds))
                    if (seconds == 0L) restoreDispatch()
                }
                delay(1_000)
            }
        }
    }

    private fun stopObservers() {
        streamJob?.cancel(); pollJob?.cancel(); clockJob?.cancel()
    }

    override fun onCleared() {
        stopObservers()
        super.onCleared()
    }

    private inline fun update(block: (PitTecUiState.Content) -> PitTecUiState.Content) {
        val current = _state.value as? PitTecUiState.Content ?: return
        _state.value = block(current)
    }

    private fun isUuid(value: String): Boolean = runCatching {
        java.util.UUID.fromString(value).toString().equals(value, ignoreCase = true)
    }.getOrDefault(false)

    companion object {
        private const val SEARCH_RESUBSCRIBE_DELAY_MS = 3_000L
        private const val POLL_INTERVAL_MS = 5_000L

        fun factory(
            serviceId: String,
            repository: PitTecRepository,
            locationRepository: DeviceLocationRepository,
            connectivityObserver: ConnectivityObserver,
            initialRequestId: String? = null,
            paymentRepository: PaymentRepository? = null,
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = PitTecViewModel(
                serviceId, repository, locationRepository, connectivityObserver, initialRequestId, paymentRepository,
            ) as T
        }
    }
}

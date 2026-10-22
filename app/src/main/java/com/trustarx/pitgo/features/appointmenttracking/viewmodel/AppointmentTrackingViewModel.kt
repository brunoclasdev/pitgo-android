package com.trustarx.pitgo.features.appointmenttracking.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.trustarx.pitgo.core.location.model.ClientLocationPurpose
import com.trustarx.pitgo.core.location.model.DeviceLocationResult
import com.trustarx.pitgo.core.location.repository.DeviceLocationRepository
import com.trustarx.pitgo.core.map.model.MapSurfaceState
import com.trustarx.pitgo.core.map.model.MapViewport
import com.trustarx.pitgo.core.map.model.MapViewportCommand
import com.trustarx.pitgo.core.map.model.framingViewport
import com.trustarx.pitgo.features.appointmenttracking.model.AppointmentClientLocation
import com.trustarx.pitgo.features.appointmenttracking.model.AppointmentLocationEvent
import com.trustarx.pitgo.features.appointmenttracking.model.AppointmentProviderLocation
import com.trustarx.pitgo.features.appointmenttracking.model.AppointmentTrackingUiState
import com.trustarx.pitgo.features.appointmenttracking.model.LocationRevision
import com.trustarx.pitgo.features.appointmenttracking.model.TrackingDataState
import com.trustarx.pitgo.features.appointmenttracking.repository.AppointmentTrackingRepository
import com.trustarx.pitgo.core.routing.model.RouteResult
import com.trustarx.pitgo.core.routing.repository.PitTecRouteRepository
import com.trustarx.pitgo.features.mobiledispatch.repository.MobileDispatchRepository
import com.trustarx.pitgo.features.mobiledispatch.model.MobileDispatchResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import com.trustarx.pitgo.features.guincho.model.*
import com.trustarx.pitgo.features.guincho.repository.GuinchoRouteRepository
import com.trustarx.pitgo.features.mobiledispatch.model.MobileAppointmentStatus
import com.trustarx.pitgo.core.map.model.MapRoutePolyline
import com.trustarx.pitgo.core.map.model.MapRouteStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.os.SystemClock

class AppointmentTrackingViewModel(
    private val appointmentId: String,
    private val repository: AppointmentTrackingRepository,
    private val dispatchRepository: MobileDispatchRepository,
    private val routeRepository: PitTecRouteRepository,
    private val deviceLocationRepository: DeviceLocationRepository,
    private val isClient: Boolean,
    private val guinchoRoutes: GuinchoRouteRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(AppointmentTrackingUiState())
    val state: StateFlow<AppointmentTrackingUiState> = _state.asStateFlow()
    private var observeJob: Job? = null
    private var routeJob: Job? = null
    private var latest: AppointmentProviderLocation? = null
    private var acceptedRevision: LocationRevision? = null
    private var lastRoutedRevision: Long = 0L
    private var lastRouteAttemptMillis: Long = 0L
    private var terminal = false
    private var cameraRevision = 0L
    private var guinchoJob: Job? = null
    private val deviation = GuinchoRouteDeviation()
    private var transportActive = false
    private var guinchoGeneration = 0L
    private var routeMeasurement: Long? = null
    private var routeAccessDenied = false

    fun onVisible() {
        if (observeJob != null) return
        terminal = false
        observeJob = viewModelScope.launch {
            // A PitTec appointment is invisible to `restore()` (MOBILE_SEQUENTIAL-only, see
            // contracts/pittec-rpcs.md), so this tries PitTec too — a caller's effective
            // modality/service is exclusive, so at most one ever resolves.
            val direct=dispatchRepository.appointmentSnapshot(appointmentId)
            when (val dispatch = if(direct is MobileDispatchResult.Success) direct else dispatchRepository.restoreAnyDispatchKind()) {
                is MobileDispatchResult.Success -> if(dispatch.snapshot.appointment?.id==appointmentId) {
                    _state.value = _state.value.copy(dispatch = dispatch.snapshot)
                    applyGuinchoPickup()
                }
                else -> Unit
            }
            // Only the client publishes their own position (publish_client_location is a
            // client-only RPC). A provider viewing this same shared screen used to call this
            // unconditionally, which always failed with "Client required" (42501) after wasting
            // a GPS acquisition + up to 3 retries in parallel with the provider's own
            // ProviderLocationTrackingService — a real contributor to inconsistent provider
            // location updates.
            if (isClient) launch {
                while (!terminal) { if(!_state.value.isGuincho) publishOwnLocation(); delay(5_000) }
            }
            launch {
                while (!terminal) {
                    val result = if (_state.value.isPitTec || _state.value.isGuincho) dispatchRepository.appointmentSnapshot(appointmentId)
                        else dispatchRepository.restoreAnyDispatchKind()
                    if (result is MobileDispatchResult.Success && result.snapshot.appointment?.id == appointmentId) {
                        val current = _state.value.dispatch
                        if (current == null || result.snapshot.revision >= current.revision) {
                            _state.value = _state.value.copy(dispatch = result.snapshot)
                            applyGuinchoPickup()
                        }
                        if (result.snapshot.appointment.status in setOf(
                            com.trustarx.pitgo.features.mobiledispatch.model.MobileAppointmentStatus.CANCELLED,
                            com.trustarx.pitgo.features.mobiledispatch.model.MobileAppointmentStatus.COMPLETED,
                        )) {
                            terminal = true
                            latest = null
                            routeJob?.cancel()
                            _state.value = _state.value.copy(data = TrackingDataState.Ended, clientLocation = null, route = null, summaryExpanded = false)
                        }
                    }
                    delay(2_000)
                }
            }
            // repository.observe()'s underlying channelFlow sends one Failure/Offline event and
            // then COMPLETES on any transient error (network blip, realtime disconnect while
            // backgrounded, etc.) — see AppointmentTrackingRepository.observe(). Without a retry,
            // one hiccup permanently froze this screen on its last known state, since nothing was
            // listening anymore for the appointment actually finishing or the provider's position
            // updating. Keeps resubscribing until the appointment reaches a terminal state.
            while (!terminal) {
                if (_state.value.isGuincho && !_state.value.dispatch!!.trackingAuthorized) {
                    delay(OBSERVE_RESUBSCRIBE_DELAY_MS)
                    continue
                }
                try {
                    coroutineScope {
                        launch {
                            while (!terminal) {
                                delay(1_000)
                                if (!terminal && _state.value.data !is TrackingDataState.Offline &&
                                    _state.value.data !is TrackingDataState.Failure
                                ) latest?.let(::publishFreshness)
                            }
                        }
                        repository.observe(appointmentId).collect(::handle)
                    }
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (_: Throwable) {
                    if (!terminal) updateData(TrackingDataState.Failure(latest))
                }
                if (!terminal) delay(OBSERVE_RESUBSCRIBE_DELAY_MS)
            }
        }
    }

    fun onHidden() {
        observeJob?.cancel()
        observeJob = null
        routeJob?.cancel()
        routeJob = null
        guinchoGeneration++; guinchoJob?.cancel(); guinchoJob = null; transportActive = false; routeAccessDenied = false; deviation.reset()
        latest = null
        acceptedRevision = null
        lastRoutedRevision = 0L
        terminal = false
        deviceLocationRepository.endVisibleUse(ClientLocationPurpose.APPOINTMENT_TRACKING)
        _state.value = AppointmentTrackingUiState()
        cameraRevision = 0L
    }

    /** Provider-only "Cheguei" — mirrors the ACCEPTED "Iniciar atendimento" RPC for Mobile. */
    fun arrive() {
        val appointment = _state.value.dispatch?.appointment ?: return
        if (_state.value.arriving || (_state.value.isGuincho && appointment.arrivedAt != null)) return
        _state.value = _state.value.copy(arriving = true, actionFailed = false)
        viewModelScope.launch {
            val result = if ((_state.value.isPitTec || _state.value.isGuincho) && appointment.arrivedAt == null)
                dispatchRepository.arrive(appointment.id, appointment.revision)
            else dispatchRepository.startAppointment(appointment.id, appointment.revision)
            when (result) {
                is MobileDispatchResult.Success -> _state.value = _state.value.copy(
                    dispatch = result.snapshot, arriving = false,
                )
                else -> _state.value = _state.value.copy(arriving = false, actionFailed = true)
            }
        }
    }

    fun startTransport() {
        val snapshot = _state.value.dispatch ?: return
        val appointment = snapshot.appointment ?: return
        val inspection = snapshot.guinchoInspection ?: return
        if (!_state.value.isGuincho || isClient || _state.value.arriving || appointment.arrivedAt == null ||
            (inspection.required && inspection.state != com.trustarx.pitgo.features.guincho.model.InspectionVersionState.APPROVED)) return
        _state.value = _state.value.copy(arriving = true, actionFailed = false)
        viewModelScope.launch {
            when (val result = dispatchRepository.startGuinchoTransport(appointment.id, appointment.revision)) {
                is MobileDispatchResult.Success -> {
                    _state.value = _state.value.copy(dispatch = result.snapshot, arriving = false)
                    applyGuinchoPickup()
                }
                else -> _state.value = _state.value.copy(arriving = false, actionFailed = true)
            }
        }
    }

    fun acknowledgeStart() {
        val appointment = _state.value.dispatch?.appointment ?: return
        if (!isClient || _state.value.arriving) return
        _state.value = _state.value.copy(arriving = true, actionFailed = false)
        viewModelScope.launch {
            when (val result = dispatchRepository.acknowledgeStart(appointment.id, appointment.revision)) {
                is MobileDispatchResult.Success -> _state.value = _state.value.copy(dispatch = result.snapshot, arriving = false)
                else -> _state.value = _state.value.copy(arriving = false, actionFailed = true)
            }
        }
    }

    /** Provider-only completion, valid only once IN_PROGRESS for PitTec. */
    fun complete() {
        val appointment = _state.value.dispatch?.appointment ?: return
        if (_state.value.completing) return
        _state.value = _state.value.copy(completing = true, actionFailed = false)
        viewModelScope.launch {
            when (val result = dispatchRepository.complete(appointment.id, appointment.revision)) {
                is MobileDispatchResult.Success -> _state.value = _state.value.copy(
                    dispatch = result.snapshot, completing = false,
                    actionFailed = result.snapshot.appointment?.status !in setOf(
                        com.trustarx.pitgo.features.mobiledispatch.model.MobileAppointmentStatus.AWAITING_CLIENT_COMPLETION_CONFIRMATION,
                        com.trustarx.pitgo.features.mobiledispatch.model.MobileAppointmentStatus.COMPLETED,
                    ),
                )
                else -> _state.value = _state.value.copy(completing = false, actionFailed = true)
            }
        }
    }

    /** Client confirmation after the PitTec reports that the service is finished. */
    fun confirmCompletion() {
        val appointment = _state.value.dispatch?.appointment ?: return
        if (!isClient || _state.value.completing ||
            appointment.status != com.trustarx.pitgo.features.mobiledispatch.model.MobileAppointmentStatus.AWAITING_CLIENT_COMPLETION_CONFIRMATION
        ) return
        _state.value = _state.value.copy(completing = true, actionFailed = false)
        viewModelScope.launch {
            when (val result = dispatchRepository.confirmCompletion(appointment.id, appointment.revision)) {
                is MobileDispatchResult.Success -> _state.value = _state.value.copy(
                    dispatch = result.snapshot, completing = false,
                    actionFailed = result.snapshot.appointment?.status != com.trustarx.pitgo.features.mobiledispatch.model.MobileAppointmentStatus.COMPLETED,
                )
                else -> _state.value = _state.value.copy(completing = false, actionFailed = true)
            }
        }
    }

    fun dispute(reason: String) {
        val appointment=_state.value.dispatch?.appointment ?: return
        if(!isClient || !_state.value.isGuincho || _state.value.completing || reason.trim().length !in 1..1000) return
        _state.value=_state.value.copy(completing=true,actionFailed=false)
        viewModelScope.launch {
            when(val result=dispatchRepository.dispute(appointment.id,appointment.revision,reason)) {
                is MobileDispatchResult.Success -> _state.value=_state.value.copy(dispatch=result.snapshot,completing=false,
                    actionFailed=result.snapshot.guinchoDispute==null)
                else -> _state.value=_state.value.copy(completing=false,actionFailed=true)
            }
        }
    }

    fun updateSurfaceState(surfaceState: MapSurfaceState) {
        _state.value = _state.value.copy(surfaceState = surfaceState)
    }

    fun retryStyle() {
        _state.value = _state.value.copy(
            styleReloadRevision = _state.value.styleReloadRevision + 1,
        )
    }

    fun onViewportIdle(viewport: MapViewport) {
        _state.value = _state.value.copy(viewport = viewport)
    }

    fun setFollowProvider(enabled: Boolean) {
        _state.value = _state.value.copy(followProvider = enabled)
        if (enabled) latest?.let { moveCameraTo(it, _state.value.clientLocation) }
    }

    fun toggleSummary() {
        _state.value = _state.value.copy(summaryExpanded = !_state.value.summaryExpanded)
    }

    fun cancel(reason: String) {
        if (reason.isBlank() || _state.value.cancelling) return
        val appointment = _state.value.dispatch?.appointment ?: return
        if (_state.value.isPitTec && com.trustarx.pitgo.features.mobiledispatch.model.MobileDispatchAction.CANCEL !in _state.value.dispatch!!.allowedActions) return
        _state.value = _state.value.copy(cancelling = true)
        viewModelScope.launch {
            when (val result = dispatchRepository.cancel(appointment.id, reason, appointment.revision)) {
                is MobileDispatchResult.Success -> _state.value = _state.value.copy(
                    dispatch = result.snapshot,
                    cancelling = false,
                )
                else -> _state.value = _state.value.copy(cancelling = false)
            }
        }
    }

    /**
     * One-shot capture of the client's own position, published to the backend so the provider's
     * map can also see it (data-model.md #3). Fired once per onVisible() — i.e. once per screen
     * open/reopen — never a continuous background stream (decision of /speckit-clarify).
     *
     * A raw-GPS cold fix (no Play Services fused provider, see AndroidLocationDataSource) can
     * easily take longer than a few seconds indoors or with Network provider disabled, so a
     * single short attempt was failing silently and leaving the client marker stuck on whatever
     * was last published (never, on a fresh appointment). Retries with a generous per-attempt
     * timeout instead of giving up after one 5s window, and never lets a failure here take down
     * the sibling coroutine that renders the provider's location.
     */
    private suspend fun publishOwnLocation() {
        deviceLocationRepository.beginVisibleUse(ClientLocationPurpose.APPOINTMENT_TRACKING)
        repeat(OWN_LOCATION_ATTEMPTS) { attempt ->
            try {
                val fix = deviceLocationRepository.currentClientFix(timeoutMillis = OWN_LOCATION_TIMEOUT_MS)
                if (terminal) return
                if (fix is DeviceLocationResult.Success) {
                    applyClientLocation(AppointmentClientLocation(fix.fix.latitude, fix.fix.longitude, fix.fix.capturedAtEpochMillis))
                    repository.publishClientLocation(
                        appointmentId = appointmentId,
                        latitude = fix.fix.latitude,
                        longitude = fix.fix.longitude,
                        accuracyMeters = fix.fix.accuracyMeters.toDouble(),
                        capturedAt = Instant.ofEpochMilli(fix.fix.capturedAtEpochMillis),
                    )
                    return
                }
                Log.w(TAG, "publishOwnLocation attempt ${attempt + 1}/$OWN_LOCATION_ATTEMPTS failed: $fix")
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (t: Throwable) {
                Log.w(TAG, "publishOwnLocation attempt ${attempt + 1}/$OWN_LOCATION_ATTEMPTS threw", t)
            }
            if (attempt < OWN_LOCATION_ATTEMPTS - 1) delay(OWN_LOCATION_RETRY_DELAY_MS)
        }
    }

    /** Updates the client marker and, when the provider isn't known yet, centers on it alone. */
    private fun applyClientLocation(clientLocation: AppointmentClientLocation) {
        _state.value = _state.value.copy(clientLocation = clientLocation)
        val providerLocation = latest
        if (providerLocation != null) {
            if (_state.value.followProvider && !_state.value.isPitTec) moveCameraTo(providerLocation, clientLocation)
        } else {
            if (!_state.value.isPitTec || cameraRevision == 0L) centerOnClientOnly(clientLocation)
        }
    }

    private suspend fun checkGuinchoDeviation(location: AppointmentProviderLocation) {
        val snapshot = _state.value.guinchoRoute.snapshot ?: return
        val generation = guinchoGeneration
        val needsRoute = withContext(Dispatchers.Default) { deviation.accept(location, snapshot.geometry, System.currentTimeMillis()) }
        if (transportActive && generation == guinchoGeneration && needsRoute && guinchoJob?.isActive != true) refreshGuincho(GuinchoRouteReason.DEVIATION)
    }
    fun retryGuinchoRoute() {
        if (_state.value.dispatch?.appointment?.status != MobileAppointmentStatus.IN_PROGRESS || isClient) return
        routeAccessDenied = false; transportActive = true
        refreshGuincho(GuinchoRouteReason.RETRY)
    }
    private fun refreshGuincho(reason: GuinchoRouteReason) {
        if (!transportActive || guinchoJob?.isActive == true || System.currentTimeMillis() < _state.value.guinchoRoute.retryAt) return
        val generation = guinchoGeneration
        routeMeasurement = SystemClock.elapsedRealtimeNanos()
        guinchoJob = viewModelScope.launch {
            try {
                _state.value = _state.value.copy(guinchoRoute = _state.value.guinchoRoute.copy(loading = true, failure = null))
                if (_state.value.guinchoRoute.snapshot == null) guinchoRoutes.snapshot(appointmentId)?.let { applyGuinchoRoute(it) }
                var request = if (reason == GuinchoRouteReason.INITIAL && _state.value.guinchoRoute.snapshot != null) GuinchoRouteReason.RESUME else reason
                while (transportActive && generation == guinchoGeneration) {
                    _state.value = _state.value.copy(guinchoRoute = _state.value.guinchoRoute.copy(loading = true, failure = null))
                    when (val result = guinchoRoutes.refresh(appointmentId, request, _state.value.guinchoRoute.snapshot?.version ?: 0)) {
                        is GuinchoRouteResult.Success -> {
                            if (generation == guinchoGeneration && transportActive) applyGuinchoRoute(result.snapshot)
                            break
                        }
                        is GuinchoRouteResult.Failure -> {
                            if (generation != guinchoGeneration || !transportActive) break
                            if (result.code in setOf("AUTH_REQUIRED", "NOT_PARTICIPANT", "APPOINTMENT_NOT_TRACKABLE")) {
                                routeAccessDenied = true; transportActive = false; deviation.reset()
                                _state.value = _state.value.copy(guinchoRoute = GuinchoRouteState(failure = result.code), guinchoPolyline = null)
                                if (result.code == "AUTH_REQUIRED") updateData(TrackingDataState.SessionExpired)
                                break
                            }
                            _state.value = _state.value.copy(guinchoRoute = _state.value.guinchoRoute.copy(loading = false, failure = result.code,
                                retryAt = System.currentTimeMillis() + result.retryAfterMs))
                            if (!result.retryable) break
                            delay(result.retryAfterMs)
                            request = if (reason == GuinchoRouteReason.DEVIATION) reason else GuinchoRouteReason.RETRY
                        }
                    }
                }
            } finally {
                if (generation == guinchoGeneration) {
                    _state.value = _state.value.copy(guinchoRoute = _state.value.guinchoRoute.copy(loading = false))
                    guinchoJob = null
                    if (transportActive) latest?.let { checkGuinchoDeviation(it) }
                }
            }
        }
    }
    private fun applyGuinchoRoute(snapshot: GuinchoRouteSnapshot) {
        val trip = _state.value.dispatch?.guinchoTrip ?: return
        if (!transportActive || snapshot.appointmentId != appointmentId || snapshot.destination != trip.destination ||
            snapshot.version < (_state.value.guinchoRoute.snapshot?.version ?: 0)) return
        val changed = snapshot.version != _state.value.guinchoRoute.snapshot?.version
        val line = if (changed) MapRoutePolyline("guincho-transport:${snapshot.version}", snapshot.geometry.points.map { it.toMapCoordinate() }, MapRouteStyle.SOLID) else _state.value.guinchoPolyline
        _state.value = _state.value.copy(guinchoRoute = GuinchoRouteState(snapshot), guinchoPolyline = line)
        if (changed) deviation.reset()
        if (changed && _state.value.followProvider) {
            cameraRevision++
            val viewport = framingViewport(snapshot.origin, snapshot.destination)
            _state.value = _state.value.copy(viewport = viewport, cameraCommand = MapViewportCommand(viewport, cameraRevision, line?.points.orEmpty()))
        }
    }
    fun guinchoRouteFrame() {
        val start = routeMeasurement ?: return
        routeMeasurement = null
        if (com.trustarx.pitgo.BuildConfig.DEBUG) Log.d(TAG, "stage=guincho_route_frame duration_ms=${(SystemClock.elapsedRealtimeNanos()-start)/1_000_000}")
    }

    private fun applyGuinchoPickup() {
        val trip = _state.value.dispatch?.guinchoTrip ?: return
        val inTransport = _state.value.dispatch?.appointment?.status == MobileAppointmentStatus.IN_PROGRESS && !isClient && !routeAccessDenied
        if (inTransport && !transportActive) {
            transportActive = true
            refreshGuincho(GuinchoRouteReason.INITIAL)
        } else if (!inTransport && transportActive) {
            transportActive = false; guinchoGeneration++; guinchoJob?.cancel(); guinchoJob = null; deviation.reset()
            _state.value = _state.value.copy(guinchoRoute = GuinchoRouteState(), guinchoPolyline = null)
        }
        if (inTransport) {
            _state.value = _state.value.copy(clientLocation = AppointmentClientLocation(trip.destination.latitude, trip.destination.longitude))
            return
        }
        if (_state.value.clientLocation == null) applyClientLocation(AppointmentClientLocation(
            trip.pickup.latitude, trip.pickup.longitude, _state.value.dispatch!!.serverNowEpochMillis))
    }

    private fun centerOnClientOnly(clientLocation: AppointmentClientLocation) {
        cameraRevision += 1
        val target = MapViewport(clientLocation.coordinate, CLIENT_ONLY_ZOOM)
        _state.value = _state.value.copy(
            viewport = target,
            cameraCommand = MapViewportCommand(target, cameraRevision),
        )
    }

    private fun handle(event: AppointmentLocationEvent) {
        if (terminal) return
        when (event) {
            is AppointmentLocationEvent.Snapshot -> {
                event.clientLocation?.let(::applyClientLocation)
                val location = event.providerLocation
                if (location == null) {
                    if (acceptedRevision == null) updateData(TrackingDataState.Empty)
                } else accept(location)
            }
            is AppointmentLocationEvent.Ended -> {
                // Guincho keeps polling the appointment after location access ends,
                // including client confirmation and support resolution.
                terminal = !_state.value.isGuincho
                latest = null
                routeJob?.cancel()
                guinchoGeneration++; guinchoJob?.cancel(); guinchoJob = null; transportActive = false
                _state.value = _state.value.copy(route = null, clientLocation = null, guinchoRoute = GuinchoRouteState(), guinchoPolyline = null)
                updateData(TrackingDataState.Ended)
            }
            AppointmentLocationEvent.SessionExpired -> {
                terminal = true
                latest = null
                routeJob?.cancel()
                guinchoGeneration++; guinchoJob?.cancel(); guinchoJob = null; transportActive = false
                _state.value = _state.value.copy(route = null, clientLocation = null, guinchoRoute = GuinchoRouteState(), guinchoPolyline = null)
                updateData(TrackingDataState.SessionExpired)
            }
            AppointmentLocationEvent.Offline -> updateData(TrackingDataState.Offline(latest))
            AppointmentLocationEvent.Failure -> updateData(TrackingDataState.Failure(latest))
        }
    }

    private fun accept(location: AppointmentProviderLocation) {
        if (!location.isValid) return
        val accepted = acceptedRevision
        if (accepted != null && location.revision < accepted) return
        if (location.revision == accepted) {
            // An authoritative snapshot after reconnection restores live/stale state even
            // when the truck hasn't moved. Do not recenter or request the same route again.
            publishFreshness(location)
            if (_state.value.isPitTec) refreshRoute(location.version)
            if (transportActive && _state.value.guinchoRoute.snapshot == null) refreshGuincho(GuinchoRouteReason.RETRY)
            return
        }
        acceptedRevision = location.revision
        latest = location
        publishFreshness(location)
        if (!transportActive && _state.value.followProvider && (!_state.value.isPitTec || accepted == null)) {
            moveCameraTo(location, _state.value.clientLocation)
        }
        if (_state.value.isPitTec) refreshRoute(location.version)
        if (transportActive) {
            if (_state.value.guinchoRoute.snapshot == null) refreshGuincho(GuinchoRouteReason.RETRY)
            else viewModelScope.launch { checkGuinchoDeviation(location) }
        }
    }

    /**
     * One in-flight request per appointment (PitTecRouteRepository itself conflates concurrent
     * calls); this only avoids issuing a redundant call for a revision already requested. A
     * failed refresh never clears a previously valid route — see pittec-routing-ui.md `Fallback`.
     */
    private fun refreshRoute(providerLocationRevision: Long) {
        val now = System.currentTimeMillis()
        val current = _state.value.route
        if (routeJob?.isActive == true || now - lastRouteAttemptMillis < 15_000 ||
            providerLocationRevision <= lastRoutedRevision && current != null &&
            current.geometry?.durationSeconds != null && now - current.generatedAtEpochMillis < 60_000) return
        lastRouteAttemptMillis = now
        routeJob = viewModelScope.launch {
            when (val result = routeRepository.refresh(appointmentId, providerLocationRevision)) {
                is RouteResult.Success -> {
                    lastRoutedRevision = result.snapshot.providerLocationRevision
                    _state.value = _state.value.copy(route = result.snapshot)
                }
                is RouteResult.Failure -> Unit
            }
        }
    }

    private fun publishFreshness(location: AppointmentProviderLocation) {
        val age = System.currentTimeMillis() - location.publishedAtEpochMillis
        updateData(
            // Phones can be a few seconds behind the server clock. A just-published
            // position must not flash the stale icon until the phone's clock catches up.
            if (age in -CLOCK_SKEW_TOLERANCE_MS..STALE_AFTER_MS) TrackingDataState.Live(location)
            else TrackingDataState.Stale(location)
        )
    }

    private fun moveCameraTo(location: AppointmentProviderLocation, clientLocation: AppointmentClientLocation?) {
        cameraRevision += 1
        val target = if (clientLocation != null && clientLocation.isValid) {
            framingViewport(location.coordinate, clientLocation.coordinate)
        } else MapViewport(location.coordinate, 16.0)
        _state.value = _state.value.copy(
            viewport = target,
            cameraCommand = MapViewportCommand(target, cameraRevision,
                if (_state.value.isPitTec) _state.value.markers.map { it.coordinate } +
                    _state.value.routePolyline?.points.orEmpty() else emptyList()),
        )
    }

    private fun updateData(data: TrackingDataState) {
        _state.value = _state.value.copy(data = data,
            summaryExpanded = _state.value.summaryExpanded && data != TrackingDataState.Ended && data != TrackingDataState.SessionExpired)
    }

    companion object {
        private const val TAG = "AppointmentTracking"
        private const val STALE_AFTER_MS = 30_000L
        private const val CLOCK_SKEW_TOLERANCE_MS = 5_000L
        private const val CLIENT_ONLY_ZOOM = 15.0
        private const val OWN_LOCATION_ATTEMPTS = 3
        private const val OWN_LOCATION_TIMEOUT_MS = 20_000L
        private const val OWN_LOCATION_RETRY_DELAY_MS = 2_000L
        private const val OBSERVE_RESUBSCRIBE_DELAY_MS = 3_000L

        fun factory(
            appointmentId: String,
            repository: AppointmentTrackingRepository,
            dispatchRepository: MobileDispatchRepository,
            routeRepository: PitTecRouteRepository,
            deviceLocationRepository: DeviceLocationRepository,
            isClient: Boolean,
            guinchoRoutes: GuinchoRouteRepository,
        ) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    AppointmentTrackingViewModel(
                        appointmentId, repository, dispatchRepository, routeRepository,
                        deviceLocationRepository, isClient, guinchoRoutes,
                    ) as T
            }
    }
}

package com.trustarx.pitgo.features.providerdashboard.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.trustarx.pitgo.core.account.model.ProviderLocationMode
import com.trustarx.pitgo.core.account.model.ProviderSnapshot
import com.trustarx.pitgo.features.providerdashboard.model.ProviderDashboardUiState
import com.trustarx.pitgo.features.providerdashboard.model.ProviderAvailabilityIssue
import com.trustarx.pitgo.features.providerdashboard.repository.ProviderDashboardRepository
import com.trustarx.pitgo.core.auth.repository.SessionRepository
import com.trustarx.pitgo.features.providertracking.repository.ProviderTrackingStateRepository
import com.trustarx.pitgo.features.providertracking.notification.ProviderIncomingRequestNotifier
import com.trustarx.pitgo.features.mobiledispatch.repository.MobileDispatchRepository
import com.trustarx.pitgo.features.mobiledispatch.model.MobileDispatchResult
import com.trustarx.pitgo.core.location.model.DeviceLocationResult
import com.trustarx.pitgo.core.location.repository.DeviceLocationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProviderDashboardViewModel(
    snapshot: ProviderSnapshot,
    private val repository: ProviderDashboardRepository,
    private val sessionRepository: SessionRepository,
    private val trackingRepository: ProviderTrackingStateRepository,
    private val dispatchRepository: MobileDispatchRepository,
    private val deviceLocationRepository: DeviceLocationRepository,
    private val incomingRequestNotifier: ProviderIncomingRequestNotifier,
) : ViewModel() {
    private val _state = MutableStateFlow<ProviderDashboardUiState>(
        ProviderDashboardUiState.Content(repository.current(snapshot)),
    )
    val state: StateFlow<ProviderDashboardUiState> = _state.asStateFlow()

    init {
        // The tracking service never survives a process restart (no invisible auto-restart, per
        // contract). If the server still has this provider marked available/MOBILE-or-PITTEC from
        // a previous session, the dashboard reopening is the "visible retomada" that must resume
        // publishing — otherwise availability stays on server-side while discovery goes stale
        // after 30s and the provider is silently excluded from every search.
        if (snapshot.available && snapshot.locationMode in TRACKED_MODES) {
            trackingRepository.start()
        }
        viewModelScope.launch {
            trackingRepository.state.collect { tracking ->
                val content = _state.value as? ProviderDashboardUiState.Content ?: return@collect
                _state.value = content.copy(tracking = tracking)
            }
        }
        viewModelScope.launch {
            // A PitTec offer is invisible to `restore()` alone (see contracts/pittec-rpcs.md).
            val result = dispatchRepository.restoreAnyDispatchKind()
            if (result is MobileDispatchResult.Success) {
                val appointmentId = result.snapshot.appointment?.id
                val content = _state.value as? ProviderDashboardUiState.Content
                if (content != null) _state.value = content.copy(incomingAppointmentId = appointmentId)
            }
        }
    }

    fun setAvailability(available: Boolean) = mutate(available = available)
    fun setMode(mode: ProviderLocationMode) = mutate(mode = mode)

    /**
     * Preflight shared by MOBILE and PITTEC: both require a precise fix before the backend will
     * accept `available = true` (see `set_provider_availability`). PITTEC additionally requires
     * the offers notification channel to be enabled — a provider that can't be alerted has no way
     * to respond to an automatic offer, unlike MOBILE where the app itself surfaces the request.
     */
    fun enableTrackedAvailability(mode: ProviderLocationMode) {
        val content = _state.value as? ProviderDashboardUiState.Content ?: return
        if (content.updating) return
        if (mode == ProviderLocationMode.PITTEC && !incomingRequestNotifier.isReady()) {
            _state.value = content.copy(availabilityIssue = ProviderAvailabilityIssue.NOTIFICATIONS_REQUIRED)
            return
        }
        _state.value = content.copy(updating = true, availabilityIssue = null)
        viewModelScope.launch {
            when (deviceLocationRepository.currentPreciseFix(timeoutMillis = 30_000L)) {
                is DeviceLocationResult.Success -> {
                    _state.value = content.copy(updating = false, availabilityIssue = null)
                    mutate(available = true, mode = mode, preflightCompleted = true)
                }
                DeviceLocationResult.ServicesDisabled -> _state.value = content.copy(
                    availabilityIssue = ProviderAvailabilityIssue.LOCATION_SERVICES_DISABLED,
                )
                DeviceLocationResult.Timeout,
                DeviceLocationResult.Unavailable -> _state.value = content.copy(
                    availabilityIssue = ProviderAvailabilityIssue.PRECISE_FIX_UNAVAILABLE,
                )
                DeviceLocationResult.PermissionDenied -> _state.value = content.copy(
                    availabilityIssue = ProviderAvailabilityIssue.PRECISE_PERMISSION_REQUIRED,
                )
            }
        }
    }

    fun onPrecisePermissionDenied() {
        val content = _state.value as? ProviderDashboardUiState.Content ?: return
        _state.value = content.copy(
            updating = false,
            availabilityIssue = ProviderAvailabilityIssue.PRECISE_PERMISSION_REQUIRED,
        )
    }

    fun clearAvailabilityIssue() {
        val content = _state.value as? ProviderDashboardUiState.Content ?: return
        _state.value = content.copy(availabilityIssue = null)
    }

    private fun mutate(
        available: Boolean? = null,
        mode: ProviderLocationMode? = null,
        preflightCompleted: Boolean = false,
    ) {
        val content = _state.value as? ProviderDashboardUiState.Content ?: return
        if (content.updating) return
        val targetMode = mode ?: content.provider.locationMode
        if (available == true && targetMode in TRACKED_MODES && !preflightCompleted) {
            enableTrackedAvailability(requireNotNull(targetMode))
            return
        }
        _state.value = content.copy(updating = true)
        viewModelScope.launch {
            when (val result = repository.update(
                content.provider,
                available ?: content.provider.available,
                mode ?: content.provider.locationMode ?: ProviderLocationMode.FIXED,
            )) {
                is com.trustarx.pitgo.features.providerdashboard.model.ProviderDashboardUpdateResult.Success ->
                    _state.value = content.copy(
                        provider = result.provider,
                        updating = false,
                        availabilityIssue = null,
                    ).also {
                        sessionRepository.applyProviderSnapshot(result.provider)
                        if (result.provider.available && result.provider.locationMode in TRACKED_MODES) {
                            trackingRepository.start()
                        } else trackingRepository.stop()
                    }
                com.trustarx.pitgo.features.providerdashboard.model.ProviderDashboardUpdateResult.Failure ->
                    _state.value = content.copy(
                        updating = false,
                        availabilityIssue = ProviderAvailabilityIssue.UPDATE_FAILED,
                    )
            }
        }
    }

    companion object {
        /** Both modalities require a live foreground location publisher while available. */
        private val TRACKED_MODES = setOf(ProviderLocationMode.MOBILE, ProviderLocationMode.PITTEC)

        fun factory(
            snapshot: ProviderSnapshot,
            repository: ProviderDashboardRepository,
            sessionRepository: SessionRepository,
            trackingRepository: ProviderTrackingStateRepository,
            dispatchRepository: MobileDispatchRepository,
            deviceLocationRepository: DeviceLocationRepository,
            incomingRequestNotifier: ProviderIncomingRequestNotifier,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ProviderDashboardViewModel(
                    snapshot, repository, sessionRepository, trackingRepository, dispatchRepository,
                    deviceLocationRepository, incomingRequestNotifier,
                ) as T
        }
    }
}

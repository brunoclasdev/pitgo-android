package com.trustarx.pitgo.features.providertracking.model

import com.trustarx.pitgo.core.location.model.DeviceLocationFix

sealed interface ProviderTrackingState {
    data object Stopped : ProviderTrackingState
    data object Starting : ProviderTrackingState
    data class Publishing(val target: ProviderTrackingTarget, val lastPublishedAt: Long?) : ProviderTrackingState
    data class Degraded(
        val target: ProviderTrackingTarget,
        val reason: TrackingStopReason,
        val lastPublishedAt: Long? = null,
    ) : ProviderTrackingState
    data object Stopping : ProviderTrackingState
}

enum class ProviderTrackingTarget { DISCOVERY, APPOINTMENT }

enum class TrackingStopReason {
    OFFLINE, SESSION_EXPIRED, INELIGIBLE, PERMISSION_REVOKED, LOCATION_DISABLED,
    NO_PRECISE_FIX, PUBLISHER_CONFLICT, PLATFORM_START_REJECTED, UNKNOWN,
}

sealed interface PublishLocationResult {
    data class Published(val version: Long, val target: ProviderTrackingTarget) : PublishLocationResult
    data object Ignored : PublishLocationResult
    data object Conflict : PublishLocationResult
    data object Offline : PublishLocationResult
    data object SessionExpired : PublishLocationResult
    data object Ineligible : PublishLocationResult
    data object Failed : PublishLocationResult
}

data class ProviderLocationUpdate(
    val trackerId: String,
    val sequence: Long,
    val fix: DeviceLocationFix,
)

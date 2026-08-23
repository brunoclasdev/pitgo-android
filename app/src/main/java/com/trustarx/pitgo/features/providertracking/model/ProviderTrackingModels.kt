package com.trustarx.pitgo.features.providertracking.model

import com.trustarx.pitgo.core.location.model.DeviceLocationFix

sealed interface ProviderTrackingState {
    data object Stopped : ProviderTrackingState
    data object Starting : ProviderTrackingState
    data class Active(val appointmentId: String, val lastPublishedAt: Long?) : ProviderTrackingState
    data class Degraded(val appointmentId: String, val reason: TrackingStopReason) : ProviderTrackingState
    data object Stopping : ProviderTrackingState
}

enum class TrackingStopReason {
    OFFLINE, SESSION_EXPIRED, INELIGIBLE, PERMISSION_REVOKED, PUBLISHER_CONFLICT, UNKNOWN
}

sealed interface PublishLocationResult {
    data class Published(val version: Long) : PublishLocationResult
    data object Ignored : PublishLocationResult
    data object Conflict : PublishLocationResult
    data object Offline : PublishLocationResult
    data object SessionExpired : PublishLocationResult
    data object Ineligible : PublishLocationResult
    data object Failed : PublishLocationResult
}

data class ProviderLocationUpdate(
    val appointmentId: String,
    val trackerId: String,
    val sequence: Long,
    val fix: DeviceLocationFix,
)

package com.trustarx.pitgo.core.location.model

data class DeviceLocationFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val capturedAtEpochMillis: Long,
) {
    fun isHighQuality(nowEpochMillis: Long): Boolean =
        latitude in -90.0..90.0 && longitude in -180.0..180.0 &&
            accuracyMeters.isFinite() && accuracyMeters > 0f && accuracyMeters <= MAX_PROVIDER_ACCURACY_METERS &&
            capturedAtEpochMillis in (nowEpochMillis - MAX_FIX_AGE_MILLIS)..nowEpochMillis

    companion object {
        const val MAX_PROVIDER_ACCURACY_METERS = 100f
        const val MAX_FIX_AGE_MILLIS = 30_000L
    }
}

sealed interface DeviceLocationAvailability {
    data object NotRequested : DeviceLocationAvailability
    data object Rationale : DeviceLocationAvailability
    data object Requesting : DeviceLocationAvailability
    data object GrantedPrecise : DeviceLocationAvailability
    data object ApproximateOnly : DeviceLocationAvailability
    data object DeniedCanAskAgain : DeviceLocationAvailability
    data object DeniedPermanently : DeviceLocationAvailability
    data object ServicesDisabled : DeviceLocationAvailability
    data object Acquiring : DeviceLocationAvailability
    data object FixUnavailable : DeviceLocationAvailability
    data class Ready(val fix: DeviceLocationFix) : DeviceLocationAvailability
}

sealed interface DeviceLocationResult {
    data class Success(val fix: DeviceLocationFix) : DeviceLocationResult
    data object PermissionDenied : DeviceLocationResult
    data object ServicesDisabled : DeviceLocationResult
    data object Timeout : DeviceLocationResult
    data object Unavailable : DeviceLocationResult
}

package com.trustarx.pitgo.core.location.repository

import com.trustarx.pitgo.core.location.data.AndroidLocationDataSource
import com.trustarx.pitgo.core.location.model.DeviceLocationFix
import com.trustarx.pitgo.core.location.model.DeviceLocationResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.trustarx.pitgo.core.location.model.ClientLocationPurpose
import com.trustarx.pitgo.core.location.model.ClientLocationUsage
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

class DeviceLocationRepository(private val dataSource: AndroidLocationDataSource) {
    private val _clientUsage = MutableStateFlow<ClientLocationUsage>(ClientLocationUsage.Idle)
    val clientUsage: StateFlow<ClientLocationUsage> = _clientUsage.asStateFlow()

    fun beginVisibleUse(purpose: ClientLocationPurpose) {
        _clientUsage.value = ClientLocationUsage.Visible(purpose, System.currentTimeMillis())
    }

    fun endVisibleUse(purpose: ClientLocationPurpose) {
        val current = _clientUsage.value as? ClientLocationUsage.Visible ?: return
        if (current.purpose == purpose) _clientUsage.value = ClientLocationUsage.Idle
    }

    fun clearAllVisibleUses() { _clientUsage.value = ClientLocationUsage.Idle }
    suspend fun currentPreciseFix(timeoutMillis: Long = 30_000L): DeviceLocationResult {
        if (!dataSource.areLocationServicesEnabled()) return DeviceLocationResult.ServicesDisabled
        dataSource.lastKnownLocation()
            ?.takeIf { it.isHighQuality(System.currentTimeMillis()) }
            ?.let { return DeviceLocationResult.Success(it) }
        val fix = withTimeoutOrNull(timeoutMillis) {
            dataSource.preciseUpdates(PRECISE_ACQUISITION_INTERVAL_MILLIS)
                .first { it.isHighQuality(System.currentTimeMillis()) }
        } ?: return DeviceLocationResult.Timeout
        return DeviceLocationResult.Success(fix)
    }

    suspend fun currentClientFix(timeoutMillis: Long = 5_000L): DeviceLocationResult {
        val now = System.currentTimeMillis()
        val cached = dataSource.lastKnownLocation()
        val fallback = cached?.takeIf {
            it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0 &&
                it.accuracyMeters.isFinite() && it.accuracyMeters in 0f..MAX_CLIENT_ACCURACY_METERS &&
                it.capturedAtEpochMillis in (now - MAX_CLIENT_FALLBACK_AGE_MILLIS)..now
        }
        fallback?.takeIf {
            it.capturedAtEpochMillis in (now - MAX_CLIENT_CACHE_AGE_MILLIS)..now
        }?.let { return DeviceLocationResult.Success(it) }
        if (!dataSource.areLocationServicesEnabled()) return DeviceLocationResult.ServicesDisabled
        val fix = withTimeoutOrNull(timeoutMillis) { dataSource.currentPreciseLocation() }
        return when {
            fix != null && fix.latitude in -90.0..90.0 && fix.longitude in -180.0..180.0 &&
                fix.capturedAtEpochMillis >= System.currentTimeMillis() - 60_000 -> {
                DeviceLocationResult.Success(fix)
            }
            fallback != null -> DeviceLocationResult.Success(fallback)
            fix == null -> DeviceLocationResult.Timeout
            else -> DeviceLocationResult.Unavailable
        }
    }

    fun preciseUpdates(): Flow<DeviceLocationFix> = dataSource.preciseUpdates()
        .filter { it.isHighQuality(System.currentTimeMillis()) }

    private companion object {
        const val MAX_CLIENT_CACHE_AGE_MILLIS = 5 * 60_000L
        const val MAX_CLIENT_FALLBACK_AGE_MILLIS = 7 * 24 * 60 * 60_000L
        const val MAX_CLIENT_ACCURACY_METERS = 5_000f
        const val PRECISE_ACQUISITION_INTERVAL_MILLIS = 1_000L
    }
}

package com.trustarx.pitgo.core.location.repository

import com.trustarx.pitgo.core.location.data.FusedLocationDataSource
import com.trustarx.pitgo.core.location.model.DeviceLocationFix
import com.trustarx.pitgo.core.location.model.DeviceLocationResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.trustarx.pitgo.core.location.model.ClientLocationPurpose
import com.trustarx.pitgo.core.location.model.ClientLocationUsage
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.withTimeoutOrNull

class DeviceLocationRepository(private val dataSource: FusedLocationDataSource) {
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
        val fix = withTimeoutOrNull(timeoutMillis) { dataSource.currentPreciseLocation() }
            ?: return DeviceLocationResult.Timeout
        return if (fix.isHighQuality(System.currentTimeMillis())) {
            DeviceLocationResult.Success(fix)
        } else {
            DeviceLocationResult.Unavailable
        }
    }

    suspend fun currentClientFix(timeoutMillis: Long = 15_000L): DeviceLocationResult {
        val fix = withTimeoutOrNull(timeoutMillis) { dataSource.currentPreciseLocation() }
            ?: return DeviceLocationResult.Timeout
        val valid = fix.latitude in -90.0..90.0 && fix.longitude in -180.0..180.0 &&
            fix.capturedAtEpochMillis >= System.currentTimeMillis() - 60_000
        return if (valid) DeviceLocationResult.Success(fix) else DeviceLocationResult.Unavailable
    }

    fun preciseUpdates(): Flow<DeviceLocationFix> = dataSource.preciseUpdates()
        .filter { it.isHighQuality(System.currentTimeMillis()) }
}

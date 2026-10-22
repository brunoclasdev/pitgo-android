package com.trustarx.pitgo.core.location.repository

import com.trustarx.pitgo.core.location.data.AndroidLocationDataSource
import com.trustarx.pitgo.core.location.model.DeviceLocationFix
import com.trustarx.pitgo.core.location.model.DeviceLocationResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.trustarx.pitgo.core.location.model.ClientLocationPurpose
import com.trustarx.pitgo.core.location.model.ClientLocationUsage
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.withTimeoutOrNull

class DeviceLocationRepository(
    private val dataSource: AndroidLocationDataSource,
    applicationScope: CoroutineScope,
) {
    // Home, one-shot requests and the foreground service share one acquisition. GPS stops
    // as soon as nobody needs it; a new subscription never replays an abandoned session.
    private val sharedFixes = flow {
        var previous: DeviceLocationFix? = null
        dataSource.preciseUpdates()
            .onStart { dataSource.lastKnownLocation()?.let { emit(it) } }
            .retryWhen { cause, _ ->
                if (cause is CancellationException) false else {
                    delay(1_000)
                    true
                }
            }
            .collect { fix ->
                val now = System.currentTimeMillis()
                if (!fix.isHighQuality(now)) return@collect
                val last = previous?.takeIf { it.isHighQuality(now) }
                if (last != null) {
                    if (fix.capturedAtEpochMillis <= last.capturedAtEpochMillis) return@collect
                    // Network fixes must not displace a recent, substantially better GPS fix.
                    if (now - last.capturedAtEpochMillis < 5_000 &&
                        fix.accuracyMeters > maxOf(last.accuracyMeters * 1.5f, last.accuracyMeters + 10f)) {
                        return@collect
                    }
                }
                previous = fix
                emit(fix)
            }
    }.conflate().shareIn(
        applicationScope,
        SharingStarted.WhileSubscribed(stopTimeoutMillis = 0, replayExpirationMillis = 0),
        replay = 1,
    )
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
        if (!hasPrecisePermission()) return DeviceLocationResult.PermissionDenied
        if (!dataSource.areLocationServicesEnabled()) return DeviceLocationResult.ServicesDisabled
        dataSource.lastKnownLocation()
            ?.takeIf { it.isHighQuality(System.currentTimeMillis()) }
            ?.let { return DeviceLocationResult.Success(it) }
        val fix = try {
            withTimeoutOrNull(timeoutMillis) {
                preciseUpdates().first()
            }
        } catch (_: IllegalStateException) {
            null
        } catch (_: NoSuchElementException) {
            null
        } catch (_: SecurityException) {
            return DeviceLocationResult.PermissionDenied
        } ?: return when {
            !hasPrecisePermission() -> DeviceLocationResult.PermissionDenied
            !areLocationServicesEnabled() -> DeviceLocationResult.ServicesDisabled
            else -> DeviceLocationResult.Timeout
        }
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
        if (!hasPrecisePermission()) return DeviceLocationResult.PermissionDenied
        val fix = withTimeoutOrNull(timeoutMillis) { preciseUpdates().first() }
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

    fun areLocationServicesEnabled(): Boolean = dataSource.areLocationServicesEnabled()
    fun hasPrecisePermission(): Boolean = dataSource.hasPrecisePermission()

    fun preciseUpdates(): Flow<DeviceLocationFix> = sharedFixes
        .filter { hasPrecisePermission() && areLocationServicesEnabled() && it.isHighQuality(System.currentTimeMillis()) }

    private companion object {
        const val MAX_CLIENT_CACHE_AGE_MILLIS = 5 * 60_000L
        const val MAX_CLIENT_FALLBACK_AGE_MILLIS = 7 * 24 * 60 * 60_000L
        const val MAX_CLIENT_ACCURACY_METERS = 5_000f
    }
}

package com.trustarx.pitgo.core.location.data

import android.annotation.SuppressLint
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationRequest
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.trustarx.pitgo.core.location.model.DeviceLocationFix
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Platform location source that does not require Google Play Services. */
class AndroidLocationDataSource(context: Context) {
    private val appContext = context.applicationContext
    private val manager = appContext
        .getSystemService(Context.LOCATION_SERVICE) as LocationManager

    fun areLocationServicesEnabled(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            manager.isLocationEnabled
        } else {
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }

    fun hasPrecisePermission(): Boolean = ContextCompat.checkSelfPermission(
        appContext, Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    suspend fun lastKnownLocation(): DeviceLocationFix? = availableProviders()
        .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
        .map { it.toFix() }
        .maxWithOrNull(
            compareBy<DeviceLocationFix> { it.isHighQuality(System.currentTimeMillis()) }
                .thenBy { it.capturedAtEpochMillis }
                .thenByDescending { it.accuracyMeters },
        )

    @SuppressLint("MissingPermission")
    fun preciseUpdates(intervalMillis: Long = 2_000L): Flow<DeviceLocationFix> = callbackFlow {
        if (!hasPrecisePermission()) {
            close(SecurityException("Precise location permission required"))
            return@callbackFlow
        }
        if (!areLocationServicesEnabled()) {
            close(IllegalStateException("Location services disabled"))
            return@callbackFlow
        }
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                trySend(location.toFix())
            }

            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit

            @Deprecated("Deprecated by the Android platform")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }
        var registered = false
        // Android's fused provider already combines GPS and network. Older devices need
        // both explicitly; PASSIVE adds duplicate, often older fixes without acquiring GPS.
        val providers = availableProviders().let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && FUSED_PROVIDER in it) {
                listOf(FUSED_PROVIDER)
            } else it.filter { provider -> provider != FUSED_PROVIDER }
        }
        providers.forEach { provider ->
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    manager.requestLocationUpdates(
                        provider,
                        LocationRequest.Builder(intervalMillis)
                            .setQuality(LocationRequest.QUALITY_HIGH_ACCURACY)
                            .setMinUpdateIntervalMillis(intervalMillis)
                            .setMaxUpdateDelayMillis(0)
                            .build(),
                        ContextCompat.getMainExecutor(appContext),
                        listener,
                    )
                } else {
                    manager.requestLocationUpdates(provider, intervalMillis, 0f, listener, Looper.getMainLooper())
                }
                registered = true
            }
        }
        if (!registered) {
            close(IllegalStateException("No enabled Android location provider"))
        }
        awaitClose { manager.removeUpdates(listener) }
    }

    private fun availableProviders(): List<String> {
        val preferred = listOf(
            FUSED_PROVIDER,
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
        )
        // Register even when an individual provider is disabled so switching GPS back on
        // resumes updates without requiring the user to reopen the screen.
        val available = manager.getProviders(false).toSet()
        return preferred.filter(available::contains)
    }

    private fun Location.toFix() = DeviceLocationFix(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = accuracy,
        // The monotonic timestamp preserves the fix's real age across wall-clock changes.
        capturedAtEpochMillis = if (elapsedRealtimeNanos > 0) {
            System.currentTimeMillis() -
                ((SystemClock.elapsedRealtimeNanos() - elapsedRealtimeNanos).coerceAtLeast(0) / 1_000_000)
        } else time,
    )

    private companion object {
        const val FUSED_PROVIDER = "fused"
    }
}

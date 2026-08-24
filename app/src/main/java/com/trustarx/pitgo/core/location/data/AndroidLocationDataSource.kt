package com.trustarx.pitgo.core.location.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import com.trustarx.pitgo.core.location.model.DeviceLocationFix
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first

/** Platform location source that does not require Google Play Services. */
class AndroidLocationDataSource(context: Context) {
    private val manager = context.applicationContext
        .getSystemService(Context.LOCATION_SERVICE) as LocationManager

    fun areLocationServicesEnabled(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            manager.isLocationEnabled
        } else {
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }

    @SuppressLint("MissingPermission")
    suspend fun currentPreciseLocation(): DeviceLocationFix? =
        preciseUpdates(CURRENT_FIX_INTERVAL_MILLIS).first()

    @SuppressLint("MissingPermission")
    suspend fun lastKnownLocation(): DeviceLocationFix? = enabledProviders()
        .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
        .maxWithOrNull(
            compareBy<Location> { it.time }
                .thenByDescending { it.accuracy },
        )
        ?.toFix()

    @SuppressLint("MissingPermission")
    fun preciseUpdates(intervalMillis: Long = 10_000L): Flow<DeviceLocationFix> = callbackFlow {
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                trySend(location.toFix())
            }

            @Deprecated("Deprecated by the Android platform")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }
        var registered = false
        enabledProviders().forEach { provider ->
            runCatching {
                manager.requestLocationUpdates(
                    provider,
                    intervalMillis,
                    0f,
                    listener,
                    Looper.getMainLooper(),
                )
                registered = true
            }
        }
        if (!registered) {
            close(IllegalStateException("No enabled Android location provider"))
        }
        awaitClose { manager.removeUpdates(listener) }
    }

    private fun enabledProviders(): List<String> {
        val preferred = listOf(
            FUSED_PROVIDER,
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )
        val available = manager.getProviders(true).toSet()
        return (preferred + available).distinct().filter(available::contains)
    }

    private fun Location.toFix() = DeviceLocationFix(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = accuracy,
        capturedAtEpochMillis = time,
    )

    private companion object {
        const val FUSED_PROVIDER = "fused"
        const val CURRENT_FIX_INTERVAL_MILLIS = 1_000L
    }
}

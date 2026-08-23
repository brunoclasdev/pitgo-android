package com.trustarx.pitgo.core.location.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.trustarx.pitgo.core.location.model.DeviceLocationFix
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class FusedLocationDataSource(context: Context) {
    private val client: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context.applicationContext)

    @SuppressLint("MissingPermission")
    suspend fun currentPreciseLocation(): DeviceLocationFix? = suspendCancellableCoroutine { cont ->
        val cancellation = CancellationTokenSource()
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setMaxUpdateAgeMillis(DeviceLocationFix.MAX_FIX_AGE_MILLIS)
            .build()
        client.getCurrentLocation(request, cancellation.token)
            .addOnSuccessListener { location -> if (cont.isActive) cont.resume(location?.toFix()) }
            .addOnFailureListener { if (cont.isActive) cont.resume(null) }
        cont.invokeOnCancellation { cancellation.cancel() }
    }

    @SuppressLint("MissingPermission")
    fun preciseUpdates(intervalMillis: Long = 10_000L): Flow<DeviceLocationFix> = callbackFlow {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMillis)
            .setMinUpdateIntervalMillis(intervalMillis)
            .setMaxUpdateAgeMillis(0)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.toFix()?.let(::trySend)
            }
        }
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
            .addOnFailureListener { close(it) }
        awaitClose { client.removeLocationUpdates(callback) }
    }

    private fun Location.toFix() = DeviceLocationFix(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = accuracy,
        capturedAtEpochMillis = time,
    )
}

package com.trustarx.pitgo.features.guincho.repository

import android.content.Context
import android.location.Geocoder
import android.os.Build
import com.trustarx.pitgo.R
import com.trustarx.pitgo.core.map.model.MapCoordinate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

class GuinchoAddressRepository(context: Context) {
    private val application = context.applicationContext

    suspend fun pickupAddress(coordinate: MapCoordinate): String {
        require(coordinate.isValid)
        val address = try {
            withTimeoutOrNull(3_000L) {
                if (!Geocoder.isPresent()) return@withTimeoutOrNull null
                val geocoder = Geocoder(application, Locale.forLanguageTag("pt-BR"))
                if (Build.VERSION.SDK_INT >= 33) {
                    suspendCancellableCoroutine { continuation ->
                        geocoder.getFromLocation(coordinate.latitude, coordinate.longitude, 1) { results ->
                            if (continuation.isActive) continuation.resume(results.firstOrNull()?.getAddressLine(0))
                        }
                    }
                } else {
                    withContext(Dispatchers.IO) {
                        @Suppress("DEPRECATION")
                        geocoder.getFromLocation(coordinate.latitude, coordinate.longitude, 1)
                            ?.firstOrNull()?.getAddressLine(0)
                    }
                }
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            null
        }
        return address?.trim()?.takeIf { it.isNotEmpty() }?.take(500)
            ?: application.getString(R.string.guincho_pickup_coordinates,
                String.format(Locale.US, "%.6f", coordinate.latitude),
                String.format(Locale.US, "%.6f", coordinate.longitude))
    }
}

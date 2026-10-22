package com.trustarx.pitgo.features.guincho.repository

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import com.trustarx.pitgo.R
import com.trustarx.pitgo.core.map.model.MapCoordinate
import com.trustarx.pitgo.features.guincho.model.*
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.exceptions.RestException
import io.ktor.client.call.body
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.text.Normalizer
import kotlin.coroutines.resume

class GuinchoAddressRepository(context: Context, private val client: SupabaseClient) {
    private val application = context.applicationContext

    suspend fun suggestions(query: String, proximity: MapCoordinate): GuinchoResult<List<GuinchoAddressSuggestion>> =
        search(query, proximity, reverse = false)

    suspend fun resolveDestination(address: String, number: String, proximity: MapCoordinate): GuinchoResult<List<GuinchoAddressSuggestion>> {
        val result = search(address, proximity, reverse = false, number = number)
        if (result is GuinchoResult.Success && result.value.isNotEmpty() ||
            result is GuinchoResult.Failure && result.reason == GuinchoFailure.SESSION_EXPIRED) return result
        val platformAddress = addressWithNumber(address, number)
        return if (platformAddress != null) GuinchoResult.Success(listOf(platformAddress)) else result
    }

    private suspend fun addressWithNumber(query: String, number: String): GuinchoAddressSuggestion? = try {
        withTimeoutOrNull(5_000L) {
            val expectedNumber = normalizedNumber(number)
            if (expectedNumber.isEmpty()) return@withTimeoutOrNull null
            if (!Geocoder.isPresent()) return@withTimeoutOrNull null
            val geocoder = Geocoder(application, Locale.forLanguageTag("pt-BR"))
            val results: List<Address> = if (Build.VERSION.SDK_INT >= 33) {
                suspendCancellableCoroutine { continuation ->
                    geocoder.getFromLocationName(query, 5, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            if (continuation.isActive) continuation.resume(addresses)
                        }
                        override fun onError(errorMessage: String?) {
                            if (continuation.isActive) continuation.resume(emptyList())
                        }
                    })
                }
            } else withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                geocoder.getFromLocationName(query, 5).orEmpty()
            }
            results.firstOrNull {
                it.hasLatitude() && it.hasLongitude() && !it.thoroughfare.isNullOrBlank() &&
                    normalizedNumber(it.subThoroughfare.orEmpty()) == expectedNumber &&
                    MapCoordinate(it.latitude,it.longitude).isValid
            }?.let {
                GuinchoAddressSuggestion("android-address",query,MapCoordinate(it.latitude,it.longitude))
            }
        }
    } catch (cancel: CancellationException) { throw cancel }
      catch (_: Exception) { null }

    private fun normalizedNumber(value: String): String = Normalizer.normalize(value,Normalizer.Form.NFKC)
        .filter(Char::isLetterOrDigit).lowercase(Locale.forLanguageTag("pt-BR"))

    suspend fun destinationAddress(coordinate: MapCoordinate): String {
        val result = search("", coordinate, reverse = true)
        if (result is GuinchoResult.Success) result.value.firstOrNull()?.let { return it.label }
        return addressAtCoordinate(coordinate, R.string.guincho_destination_coordinates)
    }

    private suspend fun search(query: String, coordinate: MapCoordinate, reverse: Boolean, number: String? = null): GuinchoResult<List<GuinchoAddressSuggestion>> = try {
        val response = withTimeoutOrNull(10_000L) {
            client.functions.invoke("guincho-address-search", body = buildJsonObject {
                put("query", query); put("latitude", coordinate.latitude); put("longitude", coordinate.longitude)
                put("reverse", reverse)
                number?.let { put("number", it) }
            }).body<AddressSuggestionsDto>()
        }
        if (response == null) GuinchoResult.Failure(GuinchoFailure.TIMEOUT)
        else GuinchoResult.Success(response.suggestions.map {
            require(it.label.isNotBlank() && it.label.length <= 500)
            val point = MapCoordinate(it.latitude, it.longitude)
            require(point.isValid)
            GuinchoAddressSuggestion(it.id, it.label, point)
        })
    } catch (cancel: CancellationException) { throw cancel }
      catch (error: RestException) {
          GuinchoResult.Failure(if (error.statusCode == 401) GuinchoFailure.SESSION_EXPIRED else GuinchoFailure.SERVICE_UNAVAILABLE)
      } catch (_: Exception) { GuinchoResult.Failure(GuinchoFailure.SERVICE_UNAVAILABLE) }

    suspend fun pickupAddress(coordinate: MapCoordinate): String =
        addressAtCoordinate(coordinate, R.string.guincho_pickup_coordinates)

    private suspend fun addressAtCoordinate(coordinate: MapCoordinate, fallbackRes: Int): String {
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
            ?: application.getString(fallbackRes,
                String.format(Locale.US, "%.6f", coordinate.latitude),
                String.format(Locale.US, "%.6f", coordinate.longitude))
    }
}

@Serializable private data class AddressSuggestionsDto(val suggestions: List<AddressSuggestionDto>)
@Serializable private data class AddressSuggestionDto(val id: String, val label: String, val latitude: Double, val longitude: Double)

package com.trustarx.pitgo.features.providertracking.repository

import com.trustarx.pitgo.features.providertracking.data.PublishLocationResponseDto
import com.trustarx.pitgo.features.providertracking.model.ProviderLocationUpdate
import com.trustarx.pitgo.features.providertracking.model.PublishLocationResult
import com.trustarx.pitgo.features.providertracking.model.ProviderTrackingTarget
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.postgrest
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

class ProviderLocationRepository(private val client: SupabaseClient) {
    suspend fun publish(update: ProviderLocationUpdate): PublishLocationResult = try {
        val result = client.postgrest.rpc("publish_mobile_provider_location", buildJsonObject {
            put("p_latitude", update.fix.latitude)
            put("p_longitude", update.fix.longitude)
            put("p_accuracy_m", update.fix.accuracyMeters)
            put("p_captured_at", Instant.ofEpochMilli(update.fix.capturedAtEpochMillis).toString())
            put("p_tracker_instance_id", update.trackerId)
            put("p_tracker_sequence", update.sequence)
        }).decodeAs<PublishLocationResponseDto>()
        when (result.outcome) {
            "PUBLISHED" -> PublishLocationResult.Published(
                result.version ?: 0,
                runCatching { ProviderTrackingTarget.valueOf(result.target.orEmpty()) }
                    .getOrDefault(ProviderTrackingTarget.DISCOVERY),
            )
            "IGNORED" -> PublishLocationResult.Ignored
            "PUBLISHER_CONFLICT" -> PublishLocationResult.Conflict
            else -> PublishLocationResult.Failed
        }
    } catch (cancel: CancellationException) { throw cancel }
      catch (_: HttpRequestTimeoutException) { PublishLocationResult.Offline }
      catch (_: HttpRequestException) { PublishLocationResult.Offline }
      catch (error: PostgrestRestException) {
          if (error.statusCode == 401 || error.statusCode == 403 || error.code == "42501") {
              PublishLocationResult.SessionExpired
          // A fix can age out in transit during a slow connection. Retry with the next
          // fresh fix instead of treating input validation as provider ineligibility.
          } else if (error.code == "22023") PublishLocationResult.Failed
          else PublishLocationResult.Failed
      } catch (_: Throwable) { PublishLocationResult.Failed }

    suspend fun stop(trackerId: String) {
        try {
            client.postgrest.rpc("stop_mobile_provider_location", buildJsonObject {
                put("p_tracker_instance_id", trackerId)
            })
        } catch (cancel: CancellationException) { throw cancel } catch (_: Throwable) { Unit }
    }
}

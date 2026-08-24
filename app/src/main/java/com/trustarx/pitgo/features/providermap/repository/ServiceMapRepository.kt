package com.trustarx.pitgo.features.providermap.repository

import com.trustarx.pitgo.features.providermap.data.PublicProviderDto
import com.trustarx.pitgo.features.providermap.model.MapCenter
import com.trustarx.pitgo.features.providermap.model.ProviderDiscoveryResult
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.exceptions.HttpRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ServiceMapRepository(private val client: SupabaseClient) {
    suspend fun listInitial(serviceId: String): ProviderDiscoveryResult {
        if (!isUuid(serviceId)) return ProviderDiscoveryResult.InvalidInput
        return query("list_service_provider_locations", buildJsonObject {
            put("p_service_id", serviceId)
            put("p_limit", 200)
        })
    }

    suspend fun discover(
        serviceId: String,
        center: MapCenter,
        radiusMeters: Int = 25_000,
    ): ProviderDiscoveryResult {
        if (!center.isValid || !isUuid(serviceId)) return ProviderDiscoveryResult.InvalidInput
        return query("discover_service_providers", buildJsonObject {
            put("p_service_id", serviceId)
            put("p_center_lat", center.latitude)
            put("p_center_long", center.longitude)
            put("p_radius_meters", radiusMeters.coerceIn(1, 200_000))
            put("p_limit", 200)
        })
    }

    private suspend fun query(
        function: String,
        params: kotlinx.serialization.json.JsonObject,
    ): ProviderDiscoveryResult = try {
        val rows = client.postgrest.rpc(function, params).decodeList<PublicProviderDto>()
        ProviderDiscoveryResult.Success(rows.mapNotNull(PublicProviderDto::toDomainOrNull))
      } catch (cancel: CancellationException) { throw cancel }
      catch (_: HttpRequestTimeoutException) { ProviderDiscoveryResult.Timeout }
      catch (_: HttpRequestException) { ProviderDiscoveryResult.Offline }
      catch (error: PostgrestRestException) {
          if (error.statusCode == 401 || error.statusCode == 403 || error.code == "42501") {
              ProviderDiscoveryResult.SessionExpired
          } else if (error.statusCode >= 500) {
              ProviderDiscoveryResult.ServiceUnavailable
          } else ProviderDiscoveryResult.Failure
      } catch (_: Throwable) { ProviderDiscoveryResult.Failure }

    private fun isUuid(value: String): Boolean = runCatching {
        java.util.UUID.fromString(value).toString().equals(value, ignoreCase = true)
    }.getOrDefault(false)
}

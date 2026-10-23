package com.trustarx.pitgo.features.pittec.repository

import com.trustarx.pitgo.core.location.model.DeviceLocationFix
import com.trustarx.pitgo.features.pittec.data.NearbyPitTecMarkerDto
import com.trustarx.pitgo.features.pittec.data.PitTecDispatchEnvelopeDto
import com.trustarx.pitgo.features.pittec.model.PitTecDispatchEvent
import com.trustarx.pitgo.features.pittec.model.PitTecDispatchResult
import com.trustarx.pitgo.features.pittec.model.PitTecDiscoveryResult
import com.trustarx.pitgo.features.pittec.model.PitTecFailure
import com.trustarx.pitgo.features.pittec.model.PitTecStartCommand
import com.trustarx.pitgo.features.pittec.model.PitTecQuoteOutcome
import com.trustarx.pitgo.features.pittec.model.PitTecQuoteResult
import com.trustarx.pitgo.features.pittec.model.isUuid
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.realtime
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.UUID

class PitTecRepository(private val client: SupabaseClient) {
    suspend fun listNearby(serviceId: String, fix: DeviceLocationFix): PitTecDiscoveryResult {
        if (!serviceId.isUuid() || !fix.isFreshAndFinite()) return PitTecDiscoveryResult.InvalidInput
        return try {
            val rows = withTimeout(RPC_TIMEOUT_MILLIS) {
                client.postgrest.rpc("list_nearby_pittecs", buildJsonObject {
                    put("p_service_id", serviceId)
                    put("p_latitude", fix.latitude)
                    put("p_longitude", fix.longitude)
                    put("p_accuracy_meters", fix.accuracyMeters.toDouble())
                    put("p_captured_at", Instant.ofEpochMilli(fix.capturedAtEpochMillis).toString())
                    put("p_limit", 200)
                }).decodeList<NearbyPitTecMarkerDto>()
            }
            PitTecDiscoveryResult.Success(rows.mapNotNull(NearbyPitTecMarkerDto::toDomainOrNull))
        } catch (cancel: CancellationException) { throw cancel }
          catch (_: TimeoutCancellationException) { PitTecDiscoveryResult.Timeout }
          catch (_: HttpRequestTimeoutException) { PitTecDiscoveryResult.Timeout }
          catch (_: HttpRequestException) { PitTecDiscoveryResult.Offline }
          catch (error: PostgrestRestException) {
              when {
                  error.statusCode == 401 || error.statusCode == 403 || error.code == "42501" ->
                      PitTecDiscoveryResult.SessionExpired
                  error.message?.contains("PITTEC_SERVICE_REQUIRED") == true ||
                      error.message?.contains("INVALID_LOCATION") == true -> PitTecDiscoveryResult.InvalidInput
                  error.code in setOf("PGRST202", "PGRST203", "42883", "42P01", "42703") ->
                      PitTecDiscoveryResult.UnsupportedDispatch
                  error.statusCode >= 500 -> PitTecDiscoveryResult.ServiceUnavailable
                  else -> PitTecDiscoveryResult.Failure
              }
          } catch (_: Throwable) { PitTecDiscoveryResult.Failure }
    }

    suspend fun restore(): PitTecDispatchResult = call("get_my_active_pittec_dispatch")

    suspend fun start(command: PitTecStartCommand): PitTecDispatchResult {
        if (!command.requestId.isUuid() || !command.serviceId.isUuid() ||
            !command.fix.isFreshAndFinite()
        ) return PitTecDispatchResult.Failure(PitTecFailure.LOCATION_UNAVAILABLE)
        return call("start_pittec_dispatch", buildJsonObject {
            put("p_request_id", command.requestId)
            put("p_service_id", command.serviceId)
            put("p_latitude", command.fix.latitude)
            put("p_longitude", command.fix.longitude)
            put("p_accuracy_meters", command.fix.accuracyMeters.toDouble())
            put("p_captured_at", Instant.ofEpochMilli(command.fix.capturedAtEpochMillis).toString())
        })
    }

    suspend fun cancel(searchId: String, revision: Long): PitTecDispatchResult {
        if (!searchId.isUuid() || revision <= 0) return contractFailure()
        return call("cancel_pittec_search", buildJsonObject {
            put("p_request_id", UUID.randomUUID().toString())
            put("p_search_id", searchId)
            put("p_expected_revision", revision)
        })
    }

    suspend fun respondToQuote(
        requestId: String,
        appointmentId: String,
        quoteVersion: Long,
        accept: Boolean,
        revision: Long,
    ): PitTecQuoteResult {
        if (!requestId.isUuid() || !appointmentId.isUuid() || quoteVersion <= 0 || revision <= 0) {
            return PitTecQuoteResult.Failure(PitTecFailure.CONTRACT)
        }
        return try {
            val dto = withTimeout(RPC_TIMEOUT_MILLIS) {
                client.postgrest.rpc("respond_to_pittec_quote", buildJsonObject {
                    put("p_request_id", requestId)
                    put("p_appointment_id", appointmentId)
                    put("p_quote_version", quoteVersion)
                    put("p_accept", accept)
                    put("p_expected_revision", revision)
                }).decodeAs<PitTecDispatchEnvelopeDto>()
            }
            val outcome = dto.outcome?.let { runCatching { PitTecQuoteOutcome.valueOf(it) }.getOrNull() }
                ?: return PitTecQuoteResult.Failure(PitTecFailure.CONTRACT)
            PitTecQuoteResult.Success(outcome, dto.domainOrNull())
        } catch (_: TimeoutCancellationException) {
            PitTecQuoteResult.Failure(PitTecFailure.TIMEOUT)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Throwable) {
            PitTecQuoteResult.Failure(classify(error))
        }
    }

    /**
     * Reuses the exact same private-channel mechanics as MobileDispatchRepository but keeps its
     * own class so a PitTec search never gets restored through the legacy Guincho contract by
     * mistake (see contracts/pittec-rpcs.md `get_my_active_pittec_dispatch`). The `service_searches`
     * and `appointments` tables — and their generic dispatch-change triggers/RLS — are shared, so
     * the `mobile-dispatch:<id>` / `provider-dispatch:<id>` topic names already cover PitTec rows.
     */
    fun observeSearch(searchId: String): Flow<PitTecDispatchEvent> = observe(
        topic = "mobile-dispatch:$searchId",
        valid = searchId.isUuid(),
    )

    fun observeProvider(providerId: String): Flow<PitTecDispatchEvent> = observe(
        topic = "provider-dispatch:$providerId",
        valid = providerId.isUuid(),
    )

    private fun observe(topic: String, valid: Boolean): Flow<PitTecDispatchEvent> = channelFlow {
        if (!valid) { send(PitTecDispatchEvent.Failure(PitTecFailure.CONTRACT)); return@channelFlow }
        val channel = client.channel(topic) { isPrivate = true }
        try {
            client.realtime.setAuth()
            val changed = channel.broadcastFlow<JsonObject>("CHANGED")
            launch { changed.collect { send(restore().toEvent()) } }
            launch {
                channel.status.filter { it == RealtimeChannel.Status.SUBSCRIBED }
                    .collect { send(restore().toEvent()) }
            }
            channel.subscribe(blockUntilSubscribed = true)
            send(restore().toEvent())
            awaitCancellation()
        } catch (cancel: CancellationException) { throw cancel }
        catch (error: Throwable) { send(PitTecDispatchEvent.Failure(classify(error))) }
        finally { withContext(NonCancellable) { runCatching { client.realtime.removeChannel(channel) } } }
    }

    private suspend fun call(
        function: String,
        params: JsonObject = buildJsonObject {},
    ): PitTecDispatchResult = try {
        val dto = withTimeout(RPC_TIMEOUT_MILLIS) {
            client.postgrest.rpc(function, params).decodeAs<PitTecDispatchEnvelopeDto>()
        }
        if (dto.outcome == "NONE") PitTecDispatchResult.None
        else dto.domainOrNull()?.let(PitTecDispatchResult::Success) ?: contractFailure()
    } catch (_: TimeoutCancellationException) {
        PitTecDispatchResult.Failure(PitTecFailure.TIMEOUT)
    } catch (cancel: CancellationException) { throw cancel }
      catch (error: Throwable) { PitTecDispatchResult.Failure(classify(error)) }

    private fun classify(error: Throwable): PitTecFailure = when (error) {
        is HttpRequestTimeoutException -> PitTecFailure.TIMEOUT
        is HttpRequestException -> PitTecFailure.OFFLINE
        is PostgrestRestException -> when {
            error.error.contains("PITTEC_PRICING_UNAVAILABLE") -> PitTecFailure.PRICING_UNAVAILABLE
            error.statusCode == 401 || error.statusCode == 403 || error.code == "42501" ->
                PitTecFailure.SESSION_EXPIRED
            error.code in setOf("PGRST202", "PGRST203", "42883", "42P01", "42703") ->
                PitTecFailure.UNSUPPORTED_DISPATCH
            error.statusCode >= 500 -> PitTecFailure.SERVICE_UNAVAILABLE
            else -> PitTecFailure.CONTRACT
        }
        else -> PitTecFailure.UNKNOWN
    }

    private fun PitTecDispatchResult.toEvent() = when (this) {
        is PitTecDispatchResult.Success -> PitTecDispatchEvent.Snapshot(snapshot)
        PitTecDispatchResult.None -> PitTecDispatchEvent.Snapshot(null)
        is PitTecDispatchResult.Failure -> PitTecDispatchEvent.Failure(reason)
    }

    private fun contractFailure() = PitTecDispatchResult.Failure(PitTecFailure.CONTRACT)

    private fun DeviceLocationFix.isFreshAndFinite(): Boolean =
        latitude in -90.0..90.0 && longitude in -180.0..180.0 &&
            accuracyMeters.isFinite() && accuracyMeters in 0f..100f

    private companion object {
        const val RPC_TIMEOUT_MILLIS = 15_000L
    }
}

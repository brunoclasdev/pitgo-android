package com.trustarx.pitgo.features.mobiledispatch.repository

import com.trustarx.pitgo.features.mobiledispatch.data.MobileDispatchEnvelopeDto
import com.trustarx.pitgo.features.mobiledispatch.model.MobileDispatchEvent
import com.trustarx.pitgo.features.mobiledispatch.model.MobileDispatchFailure
import com.trustarx.pitgo.features.mobiledispatch.model.MobileDispatchResult
import com.trustarx.pitgo.features.mobiledispatch.model.MobileDispatchStartCommand
import com.trustarx.pitgo.features.mobiledispatch.model.isUuid
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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

class MobileDispatchRepository(private val client: SupabaseClient) {
    suspend fun restore(): MobileDispatchResult = call("get_my_active_mobile_dispatch")

    /** Dedicated PitTec contract (see contracts/pittec-rpcs.md) so it never mixes with MOBILE_SEQUENTIAL rows. */
    suspend fun restorePitTec(): MobileDispatchResult = call("get_my_active_pittec_dispatch")

    /**
     * A provider's effective modality is exclusive (FIXED/MOBILE/PITTEC), so at most one of the
     * two restores below ever returns an active dispatch — this lets provider-side callers stay
     * agnostic of which dispatch kind is currently offering/assigned to them.
     */
    suspend fun restoreProviderAny(): MobileDispatchResult = when (val mobile = restore()) {
        is MobileDispatchResult.Success -> mobile
        MobileDispatchResult.None -> restorePitTec()
        is MobileDispatchResult.Failure -> mobile
    }

    suspend fun start(command: MobileDispatchStartCommand): MobileDispatchResult {
        if (!command.searchId.isUuid() || !command.serviceId.isUuid() ||
            !command.fix.isHighQuality(System.currentTimeMillis())) {
            return MobileDispatchResult.Failure(MobileDispatchFailure.LOCATION_UNAVAILABLE)
        }
        return call("start_mobile_dispatch", buildJsonObject {
            put("p_search_id", command.searchId)
            put("p_service_id", command.serviceId)
            put("p_latitude", command.fix.latitude)
            put("p_longitude", command.fix.longitude)
            put("p_accuracy_m", command.fix.accuracyMeters)
        })
    }

    suspend fun next(searchId: String, revision: Long) = command(
        "next_mobile_dispatch_candidate", searchId, revision,
    )

    suspend fun request(searchId: String, revision: Long) = command(
        "request_mobile_dispatch_candidate", searchId, revision,
    )

    suspend fun respond(appointmentId: String, accept: Boolean, revision: Long): MobileDispatchResult {
        if (!appointmentId.isUuid() || revision <= 0) return contractFailure()
        return call("respond_to_mobile_dispatch", buildJsonObject {
            put("p_appointment_id", appointmentId); put("p_accept", accept)
            put("p_expected_revision", revision)
        })
    }

    suspend fun startAppointment(appointmentId: String, revision: Long) = appointmentCommand(
        "start_mobile_appointment", appointmentId, revision,
    )

    suspend fun complete(appointmentId: String, revision: Long) = appointmentCommand(
        "complete_mobile_appointment", appointmentId, revision,
    )

    suspend fun cancel(appointmentId: String, reason: String, revision: Long): MobileDispatchResult {
        if (!appointmentId.isUuid() || reason.isBlank() || revision <= 0) return contractFailure()
        return call("cancel_mobile_appointment", buildJsonObject {
            put("p_appointment_id", appointmentId); put("p_reason", reason.trim())
            put("p_expected_revision", revision)
        })
    }

    fun observeSearch(searchId: String): Flow<MobileDispatchEvent> = observe(
        topic = "mobile-dispatch:$searchId",
        valid = searchId.isUuid(),
    )

    fun observeProvider(providerId: String): Flow<MobileDispatchEvent> = observe(
        topic = "provider-dispatch:$providerId",
        valid = providerId.isUuid(),
    )

    private fun observe(topic: String, valid: Boolean): Flow<MobileDispatchEvent> = channelFlow {
        if (!valid) { send(MobileDispatchEvent.Failure(MobileDispatchFailure.CONTRACT)); return@channelFlow }
        val channel = client.channel(topic) { isPrivate = true }
        try {
            client.realtime.setAuth()
            val changed = channel.broadcastFlow<kotlinx.serialization.json.JsonObject>("CHANGED")
            launch { changed.collect { send(restore().toEvent()) } }
            launch {
                channel.status.filter { it == RealtimeChannel.Status.SUBSCRIBED }
                    .collect { send(restore().toEvent()) }
            }
            channel.subscribe(blockUntilSubscribed = true)
            send(restore().toEvent())
            awaitCancellation()
        } catch (cancel: CancellationException) { throw cancel }
        catch (error: Throwable) { send(MobileDispatchEvent.Failure(classify(error))) }
        finally { withContext(NonCancellable) { runCatching { client.realtime.removeChannel(channel) } } }
    }

    private suspend fun command(name: String, searchId: String, revision: Long): MobileDispatchResult {
        if (!searchId.isUuid() || revision <= 0) return contractFailure()
        return call(name, buildJsonObject {
            put("p_search_id", searchId); put("p_expected_revision", revision)
        })
    }

    private suspend fun appointmentCommand(
        name: String, appointmentId: String, revision: Long,
    ): MobileDispatchResult {
        if (!appointmentId.isUuid() || revision <= 0) return contractFailure()
        return call(name, buildJsonObject {
            put("p_appointment_id", appointmentId); put("p_expected_revision", revision)
        })
    }

    private suspend fun call(
        function: String,
        params: kotlinx.serialization.json.JsonObject = buildJsonObject {},
    ): MobileDispatchResult = try {
        val dto = withTimeout(RPC_TIMEOUT_MILLIS) {
            client.postgrest.rpc(function, params).decodeAs<MobileDispatchEnvelopeDto>()
        }
        if (dto.outcome == "NONE") MobileDispatchResult.None
        else dto.domainOrNull()?.let(MobileDispatchResult::Success) ?: contractFailure()
    } catch (_: TimeoutCancellationException) {
        MobileDispatchResult.Failure(MobileDispatchFailure.TIMEOUT)
    } catch (cancel: CancellationException) { throw cancel }
      catch (error: Throwable) { MobileDispatchResult.Failure(classify(error)) }

    private fun classify(error: Throwable): MobileDispatchFailure = when (error) {
        is HttpRequestTimeoutException -> MobileDispatchFailure.TIMEOUT
        is HttpRequestException -> MobileDispatchFailure.OFFLINE
        is PostgrestRestException -> when {
            error.statusCode == 401 || error.statusCode == 403 || error.code == "42501" ->
                MobileDispatchFailure.SESSION_EXPIRED
            error.code in setOf("PGRST202", "PGRST203", "42883", "42P01", "42703") ->
                MobileDispatchFailure.SERVICE_UNAVAILABLE
            error.statusCode >= 500 -> MobileDispatchFailure.SERVICE_UNAVAILABLE
            else -> MobileDispatchFailure.CONTRACT
        }
        else -> MobileDispatchFailure.UNKNOWN
    }

    private fun MobileDispatchResult.toEvent() = when (this) {
        is MobileDispatchResult.Success -> MobileDispatchEvent.Snapshot(snapshot)
        MobileDispatchResult.None -> MobileDispatchEvent.Snapshot(null)
        is MobileDispatchResult.Failure -> MobileDispatchEvent.Failure(reason)
    }

    private fun contractFailure() = MobileDispatchResult.Failure(MobileDispatchFailure.CONTRACT)

    private companion object {
        const val RPC_TIMEOUT_MILLIS = 15_000L
    }
}

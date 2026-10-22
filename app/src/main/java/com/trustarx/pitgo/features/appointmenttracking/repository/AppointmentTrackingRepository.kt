package com.trustarx.pitgo.features.appointmenttracking.repository

import com.trustarx.pitgo.core.common.time.parseSupabaseInstant
import com.trustarx.pitgo.features.appointmenttracking.model.AppointmentClientLocation
import com.trustarx.pitgo.features.appointmenttracking.model.AppointmentLocationEvent
import com.trustarx.pitgo.features.appointmenttracking.model.AppointmentProviderLocation
import com.trustarx.pitgo.features.appointmenttracking.model.TerminalAppointmentStatus
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.realtime.RealtimeChannel
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.time.Instant

class AppointmentTrackingRepository(private val client: SupabaseClient) {
    fun observe(appointmentId: String): Flow<AppointmentLocationEvent> = channelFlow {
        if (!isUuid(appointmentId)) {
            send(AppointmentLocationEvent.Failure)
            return@channelFlow
        }
        val channel = client.channel("appointment-location:$appointmentId") { isPrivate = true }
        // Polling, reconnect and broadcasts can coincide. Keep one RPC snapshot in flight
        // and one pending refresh so slower responses never overwrite newer positions.
        val refreshRequests = Channel<Unit>(Channel.CONFLATED)
        val refreshJob = launch {
            for (request in refreshRequests) send(snapshotSafely(appointmentId))
        }
        launch {
            while (true) {
                refreshRequests.trySend(Unit)
                kotlinx.coroutines.delay(5_000)
            }
        }
        try {
            client.realtime.setAuth()
            val insert = channel.broadcastFlow<JsonObject>("INSERT")
            val update = channel.broadcastFlow<JsonObject>("UPDATE")
            val delete = channel.broadcastFlow<JsonObject>("DELETE")
            val ended = channel.broadcastFlow<JsonObject>("ENDED")

            // Both provider_current_locations and client_current_locations broadcast to this same
            // channel with their own row shape; rather than parsing each shape from the raw
            // payload, any insert/update/delete on either table just re-fetches the authoritative
            // combined snapshot via RPC (same "broadcast wakes the repository up" philosophy used
            // for mobile dispatch — see specs/013 realtime contract).
            launch { insert.collect { refreshRequests.trySend(Unit) } }
            launch { update.collect { refreshRequests.trySend(Unit) } }
            launch { delete.collect { refreshRequests.trySend(Unit) } }
            launch { ended.collect { payload ->
                parseEnded(payload)?.let {
                    refreshJob.cancelAndJoin()
                    send(it)
                }
            } }
            launch {
                channel.status
                    .filter { it == RealtimeChannel.Status.SUBSCRIBED }
                    .collect { refreshRequests.trySend(Unit) }
            }

            channel.subscribe(blockUntilSubscribed = true)
            // This explicit first snapshot also covers implementations that update the channel
            // status before the status collector is scheduled. Later SUBSCRIBED transitions
            // resnapshot after every reconnect.
            refreshRequests.trySend(Unit)
            awaitCancellation()
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: HttpRequestTimeoutException) {
            send(AppointmentLocationEvent.Offline)
        } catch (_: HttpRequestException) {
            send(AppointmentLocationEvent.Offline)
        } catch (error: PostgrestRestException) {
            send(
                if (error.statusCode == 401 || error.statusCode == 403 || error.code == "42501") {
                    AppointmentLocationEvent.SessionExpired
                } else AppointmentLocationEvent.Failure
            )
        } catch (_: Throwable) {
            send(AppointmentLocationEvent.Failure)
            awaitCancellation()
        } finally {
            withContext(NonCancellable) { runCatching { client.realtime.removeChannel(channel) } }
        }
    }

    /**
     * Intentionally lets failures propagate (no runCatching here): the caller
     * (AppointmentTrackingViewModel.publishOwnLocation) retries on exception, so swallowing errors
     * here used to mean a failed publish looked identical to a successful one — the client marker
     * moved in the local UI (updated before this call) while the backend, and therefore the
     * provider's map, silently kept stale/empty data forever.
     */
    suspend fun publishClientLocation(
        appointmentId: String,
        latitude: Double,
        longitude: Double,
        accuracyMeters: Double,
        capturedAt: Instant,
    ) {
        if (!isUuid(appointmentId)) return
        client.postgrest.rpc(
            "publish_client_location",
            buildJsonObject {
                put("p_appointment_id", appointmentId)
                put("p_latitude", latitude)
                put("p_longitude", longitude)
                put("p_accuracy_m", accuracyMeters)
                put("p_captured_at", capturedAt.toString())
            },
        )
    }

    private suspend fun snapshotSafely(appointmentId: String): AppointmentLocationEvent = try {
        snapshot(appointmentId)
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (_: HttpRequestTimeoutException) {
        AppointmentLocationEvent.Offline
    } catch (_: HttpRequestException) {
        AppointmentLocationEvent.Offline
    } catch (error: PostgrestRestException) {
        if (error.statusCode == 401 || error.statusCode == 403 || error.code == "42501") {
            AppointmentLocationEvent.SessionExpired
        } else AppointmentLocationEvent.Failure
    } catch (_: Throwable) {
        AppointmentLocationEvent.Failure
    }

    private suspend fun snapshot(appointmentId: String): AppointmentLocationEvent {
        val dto = client.postgrest.rpc(
            "get_appointment_provider_location",
            buildJsonObject { put("p_appointment_id", appointmentId) },
        ).decodeAs<LocationSnapshotDto>()
        return when (dto.state) {
            "EMPTY" -> AppointmentLocationEvent.Snapshot(null, fetchClientLocation(appointmentId))
            "ENDED" -> AppointmentLocationEvent.Ended(
                TerminalAppointmentStatus.valueOf(requireNotNull(dto.status))
            )
            "LIVE", "STALE" -> AppointmentLocationEvent.Snapshot(
                dto.toDomain(),
                fetchClientLocation(appointmentId),
            )
            else -> AppointmentLocationEvent.Failure
        }
    }

    private suspend fun fetchClientLocation(appointmentId: String): AppointmentClientLocation? {
        val dto = try {
            client.postgrest.rpc(
                "get_appointment_client_location",
                buildJsonObject { put("p_appointment_id", appointmentId) },
            ).decodeAs<ClientLocationSnapshotDto>()
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { return null }
        if (dto.outcome != "FOUND" || dto.location == null) return null
        val location = AppointmentClientLocation(dto.location.latitude, dto.location.longitude, dto.location.capturedAt?.let { parseSupabaseInstant(it).toEpochMilli() })
        return location.takeIf(AppointmentClientLocation::isValid)
    }

    private fun parseEnded(payload: JsonObject): AppointmentLocationEvent.Ended? {
        val status = sequenceOf(payload, payload["payload"] as? JsonObject)
            .filterNotNull()
            .mapNotNull { it["status"]?.jsonPrimitive?.content }
            .firstOrNull()
            ?: return null
        return runCatching { TerminalAppointmentStatus.valueOf(status) }
            .getOrNull()
            ?.let(AppointmentLocationEvent::Ended)
    }

    private fun isUuid(value: String): Boolean = runCatching {
        java.util.UUID.fromString(value).toString().equals(value, ignoreCase = true)
    }.getOrDefault(false)
}

@Serializable
private data class LocationSnapshotDto(
    val state: String,
    val status: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerialName("accuracy_m") val accuracyMeters: Double? = null,
    @SerialName("captured_at") val capturedAt: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
    val version: Long? = null,
) {
    fun toDomain() = AppointmentProviderLocation(
        latitude = requireNotNull(latitude),
        longitude = requireNotNull(longitude),
        accuracyMeters = requireNotNull(accuracyMeters),
        capturedAtEpochMillis = parseSupabaseInstant(requireNotNull(capturedAt)).toEpochMilli(),
        publishedAtEpochMillis = parseSupabaseInstant(requireNotNull(publishedAt)).toEpochMilli(),
        version = requireNotNull(version),
    ).also { require(it.isValid) }
}

@Serializable
private data class ClientLocationSnapshotDto(
    val outcome: String,
    val location: ClientLocationDto? = null,
)

@Serializable
private data class ClientLocationDto(
    @SerialName("captured_at") val capturedAt: String? = null,
    val latitude: Double,
    val longitude: Double,
)

package com.trustarx.pitgo.features.appointmenttracking.repository

import com.trustarx.pitgo.features.appointmenttracking.model.AppointmentLocationEvent
import com.trustarx.pitgo.features.appointmenttracking.model.AppointmentProviderLocation
import com.trustarx.pitgo.features.appointmenttracking.model.LocationRevision
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
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
        try {
            client.realtime.setAuth()
            val insert = channel.broadcastFlow<JsonObject>("INSERT")
            val update = channel.broadcastFlow<JsonObject>("UPDATE")
            val delete = channel.broadcastFlow<JsonObject>("DELETE")
            val ended = channel.broadcastFlow<JsonObject>("ENDED")

            launch { insert.collect { payload -> parseUpsert(payload)?.let { send(it) } } }
            launch { update.collect { payload -> parseUpsert(payload)?.let { send(it) } } }
            launch { delete.collect { payload -> parseRemoved(payload)?.let { send(it) } } }
            launch { ended.collect { payload -> parseEnded(payload)?.let { send(it) } } }
            launch {
                channel.status
                    .filter { it == RealtimeChannel.Status.SUBSCRIBED }
                    .collect { send(snapshotSafely(appointmentId)) }
            }

            channel.subscribe(blockUntilSubscribed = true)
            // This explicit first snapshot also covers implementations that update the channel
            // status before the status collector is scheduled. Later SUBSCRIBED transitions
            // resnapshot after every reconnect.
            send(snapshotSafely(appointmentId))
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
        } finally {
            withContext(NonCancellable) { runCatching { client.realtime.removeChannel(channel) } }
        }
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
            "EMPTY" -> AppointmentLocationEvent.Snapshot(null)
            "ENDED" -> AppointmentLocationEvent.Ended(
                TerminalAppointmentStatus.valueOf(requireNotNull(dto.status))
            )
            "LIVE", "STALE" -> AppointmentLocationEvent.Snapshot(dto.toDomain())
            else -> AppointmentLocationEvent.Failure
        }
    }

    private fun parseUpsert(payload: JsonObject): AppointmentLocationEvent.Upsert? =
        record(payload, preferOld = false)?.toLocationOrNull()?.let(AppointmentLocationEvent::Upsert)

    private fun parseRemoved(payload: JsonObject): AppointmentLocationEvent.Removed? =
        record(payload, preferOld = true)?.toRevisionOrNull()?.let(AppointmentLocationEvent::Removed)

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

    private fun record(payload: JsonObject, preferOld: Boolean): JsonObject? {
        val keys = if (preferOld) listOf("old", "record", "payload")
        else listOf("record", "new", "payload")
        return keys.firstNotNullOfOrNull { key ->
            runCatching { payload[key]?.jsonObject }.getOrNull()
        }
    }

    private fun JsonObject.toLocationOrNull(): AppointmentProviderLocation? = runCatching {
        AppointmentProviderLocation(
            latitude = getValue("latitude").jsonPrimitive.content.toDouble(),
            longitude = getValue("longitude").jsonPrimitive.content.toDouble(),
            accuracyMeters = getValue("accuracy_m").jsonPrimitive.content.toDouble(),
            capturedAtEpochMillis = Instant.parse(
                getValue("captured_at").jsonPrimitive.content,
            ).toEpochMilli(),
            publishedAtEpochMillis = Instant.parse(
                getValue("published_at").jsonPrimitive.content,
            ).toEpochMilli(),
            version = getValue("version").jsonPrimitive.content.toLong(),
        ).takeIf(AppointmentProviderLocation::isValid)
    }.getOrNull()

    private fun JsonObject.toRevisionOrNull(): LocationRevision? = runCatching {
        LocationRevision(
            publishedAtEpochMillis = Instant.parse(
                getValue("published_at").jsonPrimitive.content,
            ).toEpochMilli(),
            version = getValue("version").jsonPrimitive.content.toLong(),
        )
    }.getOrNull()

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
        capturedAtEpochMillis = Instant.parse(requireNotNull(capturedAt)).toEpochMilli(),
        publishedAtEpochMillis = Instant.parse(requireNotNull(publishedAt)).toEpochMilli(),
        version = requireNotNull(version),
    ).also { require(it.isValid) }
}

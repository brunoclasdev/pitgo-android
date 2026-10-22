package com.trustarx.pitgo.features.guincho.repository

import com.trustarx.pitgo.core.environment.EnvironmentConfig
import com.trustarx.pitgo.features.guincho.data.*
import com.trustarx.pitgo.features.guincho.model.*
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.exceptions.RestException
import io.ktor.client.call.body
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.SerializationException
import java.time.DateTimeException

class GuinchoRouteRepository(private val client: SupabaseClient, private val files: GuinchoInspectionCaptureFiles) {
    private val lock = Mutex()
    private val flights = mutableMapOf<String, CompletableDeferred<GuinchoRouteResult>>()
    suspend fun snapshot(appointment: String): GuinchoRouteSnapshot? {
        return try {
            val user = client.auth.currentUserOrNull()?.id ?: return null
            val gen = files.generation
            val dto = client.postgrest.rpc("get_guincho_route_snapshot", buildJsonObject { put("p_appointment_id", appointment) }).decodeAs<GuinchoRouteDto>()
            if (user != client.auth.currentUserOrNull()?.id || gen != files.generation) null else if (dto.outcome == "FOUND") dto.snapshot(appointment) else null
        } catch (cancel: CancellationException) { throw cancel } catch (_: Exception) { null }
    }
    suspend fun refresh(appointment: String, reason: GuinchoRouteReason, version: Long): GuinchoRouteResult {
        val user = client.auth.currentUserOrNull()?.id ?: return GuinchoRouteResult.Failure("AUTH_REQUIRED", false)
        val gen = files.generation
        val key = "${EnvironmentConfig.fingerprint}:$user:$appointment"
        val (result, owner) = lock.withLock {
            flights[key]?.takeIf { !it.isCompleted }?.let { it to false } ?: (CompletableDeferred<GuinchoRouteResult>().also { flights[key] = it } to true)
        }
        if (!owner) return result.await()
        try {
            val dto = client.functions.invoke("guincho-route", GuinchoRouteRequestDto(appointment, reason.name, version)).body<GuinchoRouteDto>()
            val outcome = if (user != client.auth.currentUserOrNull()?.id || gen != files.generation) GuinchoRouteResult.Failure("AUTH_REQUIRED", false)
                else if (dto.status == "ROUTE") GuinchoRouteResult.Success(dto.snapshot(appointment))
                else GuinchoRouteResult.Failure(dto.code ?: "SERVICE_UNAVAILABLE", dto.retryable, dto.retryAfterMs.coerceIn(500, 30_000))
            result.complete(outcome); return outcome
        } catch (cancel: CancellationException) { result.cancel(cancel); throw cancel
        } catch (error: Exception) {
            if (error is IllegalArgumentException || error is DateTimeException || error is SerializationException) {
                val failure = GuinchoRouteResult.Failure("CONTRACT", false)
                result.complete(failure); return failure
            }
            val dto = (error as? RestException)?.let { runCatching { it.response.body<GuinchoRouteDto>() }.getOrNull() }
            val code = when ((error as? RestException)?.statusCode) {
                401 -> "AUTH_REQUIRED"
                403 -> "NOT_PARTICIPANT"
                400 -> "INVALID_REQUEST"
                409 -> dto?.code ?: "APPOINTMENT_NOT_TRACKABLE"
                429 -> "RATE_LIMITED"
                504 -> "ROUTER_TIMEOUT"
                else -> dto?.code ?: "SERVICE_UNAVAILABLE"
            }
            val failure = GuinchoRouteResult.Failure(code, code !in setOf("AUTH_REQUIRED", "NOT_PARTICIPANT", "INVALID_REQUEST", "APPOINTMENT_NOT_TRACKABLE") && (dto?.retryable ?: true), (dto?.retryAfterMs ?: 1000).coerceIn(500, 30_000))
            result.complete(failure); return failure
        } finally {
            withContext(NonCancellable) { lock.withLock { if (flights[key] === result) flights.remove(key) }; if (!result.isCompleted) result.cancel() }
        }
    }
}

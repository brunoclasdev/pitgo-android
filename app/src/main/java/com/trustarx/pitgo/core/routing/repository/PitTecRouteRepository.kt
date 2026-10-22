package com.trustarx.pitgo.core.routing.repository

import com.trustarx.pitgo.core.routing.data.RouteErrorDto
import com.trustarx.pitgo.core.routing.data.RouteRequestDto
import com.trustarx.pitgo.core.routing.data.RouteSuccessDto
import com.trustarx.pitgo.core.routing.model.RouteFailure
import com.trustarx.pitgo.core.routing.model.RouteResult
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.functions.functions
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One in-flight request per appointment: a newer call while one is running attaches to the same
 * result instead of firing a second upstream request (see refresh policy in
 * contracts/pittec-routing-ui.md). A caller that arrives after the in-flight one already reflects
 * a since-updated revision can call [refresh] again once it completes — the Edge Function itself
 * always returns whatever is authoritative at the moment it runs.
 */
class PitTecRouteRepository(private val client: SupabaseClient) {
    private val mutex = Mutex()
    private val inFlight = mutableMapOf<String, CompletableDeferred<RouteResult>>()

    suspend fun refresh(appointmentId: String, providerLocationRevision: Long): RouteResult {
        if (!isUuid(appointmentId) || providerLocationRevision <= 0) {
            return RouteResult.Failure(RouteFailure.CONTRACT, retryable = false)
        }
        val deferred = mutex.withLock {
            inFlight[appointmentId]?.takeIf { !it.isCompleted }?.let { return@withLock it to false }
            CompletableDeferred<RouteResult>().also { inFlight[appointmentId] = it } to true
        }
        val (result, owner) = deferred
        if (!owner) return result.await()
        try {
            val outcome = call(appointmentId, providerLocationRevision)
            result.complete(outcome)
            return outcome
        } catch (cancel: CancellationException) {
            result.cancel(cancel)
            throw cancel
        } finally {
            withContext(NonCancellable) {
                mutex.withLock { if (inFlight[appointmentId] === result) inFlight.remove(appointmentId) }
            }
        }
    }

    private suspend fun call(appointmentId: String, providerLocationRevision: Long): RouteResult = try {
        val response = client.functions.invoke(
            function = "pittec-route",
            body = RouteRequestDto(appointmentId, providerLocationRevision),
        )
        val dto = response.body<RouteSuccessDto>()
        dto.toDomainOrNull()?.let(RouteResult::Success)
            ?: RouteResult.Failure(RouteFailure.CONTRACT, retryable = false)
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (_: HttpRequestTimeoutException) {
        RouteResult.Failure(RouteFailure.ROUTER_TIMEOUT, retryable = true)
    } catch (_: HttpRequestException) {
        RouteResult.Failure(RouteFailure.OFFLINE, retryable = true)
    } catch (error: RestException) {
        val body = runCatching { error.response.body<RouteErrorDto>() }.getOrNull()
        val reason = when {
            error.statusCode == 401 -> RouteFailure.AUTH_REQUIRED
            error.statusCode == 403 -> RouteFailure.NOT_PARTICIPANT
            body?.code == "LOCATION_NOT_READY" -> RouteFailure.LOCATION_NOT_READY
            error.statusCode == 409 -> RouteFailure.APPOINTMENT_NOT_TRACKABLE
            error.statusCode == 429 -> RouteFailure.RATE_LIMITED
            error.statusCode == 502 -> RouteFailure.ROUTER_FAILURE
            error.statusCode == 504 -> RouteFailure.ROUTER_TIMEOUT
            error.statusCode >= 500 -> RouteFailure.SERVICE_UNAVAILABLE
            else -> RouteFailure.CONTRACT
        }
        RouteResult.Failure(reason, retryable = body?.retryable ?: (error.statusCode >= 500))
    } catch (_: Throwable) {
        RouteResult.Failure(RouteFailure.UNKNOWN, retryable = true)
    }

    private fun isUuid(value: String): Boolean = runCatching {
        java.util.UUID.fromString(value).toString().equals(value, ignoreCase = true)
    }.getOrDefault(false)
}

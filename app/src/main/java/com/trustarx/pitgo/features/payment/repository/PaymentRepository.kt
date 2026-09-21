package com.trustarx.pitgo.features.payment.repository

import android.util.Log
import com.trustarx.pitgo.features.mobiledispatch.model.isUuid
import com.trustarx.pitgo.features.payment.data.CreatePaymentRequestDto
import com.trustarx.pitgo.features.payment.data.CreatePaymentResponseDto
import com.trustarx.pitgo.features.payment.data.EdgeFunctionErrorDto
import com.trustarx.pitgo.features.payment.data.PaymentRealtimeEventDto
import com.trustarx.pitgo.features.payment.data.PaymentStatusProbeDto
import com.trustarx.pitgo.features.payment.data.RefundPaymentRequestDto
import com.trustarx.pitgo.features.payment.data.RefundPaymentResponseDto
import com.trustarx.pitgo.features.payment.model.CreatePaymentCommand
import com.trustarx.pitgo.features.payment.model.CreatePaymentResult
import com.trustarx.pitgo.features.payment.model.PaymentEvent
import com.trustarx.pitgo.features.payment.model.PaymentFailure
import com.trustarx.pitgo.features.payment.model.RefundResult
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.realtime
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.trustarx.pitgo.features.payment.model.Payment
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Charges and status for the checkout step (features/payment). Mirrors the Edge-Function-call
 * pattern from [com.trustarx.pitgo.core.routing.repository.PitTecRouteRepository] for
 * `createPayment`, and the private-Realtime-channel pattern from
 * [com.trustarx.pitgo.features.mobiledispatch.repository.MobileDispatchRepository] for [observe].
 */
class PaymentRepository(private val client: SupabaseClient) {
    suspend fun recoverable(): PaymentEvent = try {
        val dto = withTimeout(RPC_TIMEOUT_MILLIS) {
            client.postgrest.rpc("get_recoverable_payment").decodeAs<PaymentStatusProbeDto>()
        }
        if (dto.outcome == "NOT_FOUND") PaymentEvent.NotFound else dto.toDomainOrNull()?.let {
            PaymentEvent.StatusChanged(it.status, it.id, it)
        } ?: PaymentEvent.Failure(PaymentFailure.CONTRACT)
    } catch (_: TimeoutCancellationException) { PaymentEvent.Failure(PaymentFailure.TIMEOUT)
    } catch (cancel: CancellationException) { throw cancel
    } catch (error: Throwable) { PaymentEvent.Failure(classifyRpcError(error)) }

    suspend fun history(before: Payment? = null): List<Payment> = withTimeout(RPC_TIMEOUT_MILLIS) {
        val rows = client.postgrest.rpc("get_payment_history", buildJsonObject {
            put("p_limit", 20)
            before?.createdAt?.let { put("p_before", it); put("p_before_id", before.id) }
        }).decodeList<PaymentStatusProbeDto>()
        rows.map { requireNotNull(it.toDomainOrNull()) }
    }
    suspend fun createPayment(command: CreatePaymentCommand): CreatePaymentResult {
        val pitTecContextValid = command.dispatchKind.name != "PITTEC_AUTO_SEQUENTIAL" ||
            (command.appointmentId?.isUuid() == true && (command.quoteVersion ?: 0) > 0)
        if (!command.requestId.isUuid() || command.amount.signum() <= 0 || !pitTecContextValid) {
            return CreatePaymentResult.Failure(PaymentFailure.CONTRACT)
        }
        return try {
            val response = withTimeout(RPC_TIMEOUT_MILLIS) {
                client.functions.invoke(
                    function = "mp-create-payment",
                    body = CreatePaymentRequestDto(
                        requestId = command.requestId,
                        dispatchKind = command.dispatchKind.name,
                        serviceId = command.serviceId,
                        amount = command.amount.toPlainString().takeUnless {
                            command.dispatchKind.name == "PITTEC_AUTO_SEQUENTIAL"
                        },
                        appointmentId = command.appointmentId,
                        quoteVersion = command.quoteVersion,
                        method = command.method.name,
                        savedCardId = command.savedCardId,
                        cardSecurityCodeToken = command.cardSecurityCodeToken,
                    ),
                )
            }
            val dto = response.body<CreatePaymentResponseDto>()
            dto.toDomainOrNull(command)?.let(CreatePaymentResult::Success)
                ?: CreatePaymentResult.Failure(PaymentFailure.CONTRACT)
        } catch (_: TimeoutCancellationException) {
            CreatePaymentResult.Failure(PaymentFailure.TIMEOUT)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: HttpRequestTimeoutException) {
            CreatePaymentResult.Failure(PaymentFailure.TIMEOUT)
        } catch (_: HttpRequestException) {
            CreatePaymentResult.Failure(PaymentFailure.OFFLINE)
        } catch (error: RestException) {
            CreatePaymentResult.Failure(classifyEdgeFunctionError(error))
        } catch (_: Throwable) {
            CreatePaymentResult.Failure(PaymentFailure.UNKNOWN)
        }
    }

    suspend fun cancel(paymentId: String): CreatePaymentResult {
        if (!paymentId.isUuid()) return CreatePaymentResult.Failure(PaymentFailure.CONTRACT)
        return try {
            val response = withTimeout(RPC_TIMEOUT_MILLIS) {
                client.functions.invoke("mp-cancel-payment", body = RefundPaymentRequestDto(paymentId))
            }
            response.body<PaymentStatusProbeDto>().toDomainOrNull()?.let(CreatePaymentResult::Success)
                ?: CreatePaymentResult.Failure(PaymentFailure.CONTRACT)
        } catch (_: TimeoutCancellationException) { CreatePaymentResult.Failure(PaymentFailure.TIMEOUT)
        } catch (cancel: CancellationException) { throw cancel
        } catch (error: RestException) { CreatePaymentResult.Failure(classifyEdgeFunctionError(error))
        } catch (_: Exception) { CreatePaymentResult.Failure(PaymentFailure.UNKNOWN) }
    }

    /** Optional scheduling command; backend cancellation already schedules autonomous refunds. */
    suspend fun refund(paymentId: String): RefundResult {
        if (!paymentId.isUuid()) return RefundResult.Failure(PaymentFailure.CONTRACT)
        return try {
            val response = withTimeout(REFUND_TIMEOUT_MILLIS) {
                client.functions.invoke(function = "mp-refund-payment", body = RefundPaymentRequestDto(paymentId))
            }
            response.body<RefundPaymentResponseDto>().toStatusOrNull()?.let(RefundResult::Done)
                ?: RefundResult.Failure(PaymentFailure.CONTRACT)
        } catch (_: TimeoutCancellationException) {
            RefundResult.Failure(PaymentFailure.TIMEOUT)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: HttpRequestTimeoutException) {
            RefundResult.Failure(PaymentFailure.TIMEOUT)
        } catch (_: HttpRequestException) {
            RefundResult.Failure(PaymentFailure.OFFLINE)
        } catch (error: RestException) {
            RefundResult.Failure(classifyEdgeFunctionError(error))
        } catch (_: Throwable) {
            RefundResult.Failure(PaymentFailure.UNKNOWN)
        }
    }

    /** One-shot fallback used when the Realtime channel drops or hasn't emitted yet (10 s rule). */
    suspend fun getStatus(requestId: String): PaymentEvent {
        if (!requestId.isUuid()) return PaymentEvent.Failure(PaymentFailure.CONTRACT)
        return try {
            val dto = withTimeout(RPC_TIMEOUT_MILLIS) {
                client.postgrest.rpc("get_payment_status", buildJsonObject {
                    put("p_request_id", requestId)
                }).decodeAs<PaymentStatusProbeDto>()
            }
            if (dto.outcome == "NOT_FOUND") PaymentEvent.NotFound
            else dto.toDomainOrNull()?.let { PaymentEvent.StatusChanged(it.status, it.id, it) }
                ?: PaymentEvent.Failure(PaymentFailure.CONTRACT)
        } catch (_: TimeoutCancellationException) {
            PaymentEvent.Failure(PaymentFailure.TIMEOUT)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Throwable) {
            PaymentEvent.Failure(classifyRpcError(error))
        }
    }

    /** Private `payment:<requestId>` broadcast; emits on every status change, self-heals via [getStatus]. */
    fun observe(requestId: String): Flow<PaymentEvent> = channelFlow {
        if (!requestId.isUuid()) {
            send(PaymentEvent.Failure(PaymentFailure.CONTRACT))
            return@channelFlow
        }
        val topic = "payment:$requestId"
        val channel = client.channel(topic) { isPrivate = true }
        val lock = Mutex()
        var latestId: String? = null
        var latestRevision = -1L
        suspend fun refresh() = lock.withLock {
            val event = getStatus(requestId)
            val payment = (event as? PaymentEvent.StatusChanged)?.payment
            if (payment != null) {
                if (payment.id == latestId && payment.revision <= latestRevision) return@withLock
                latestId = payment.id; latestRevision = payment.revision
            }
            send(event)
        }
        launch { while (true) { refresh(); delay(10_000L) } }
        try {
            client.realtime.setAuth()
            val changed = channel.broadcastFlow<PaymentRealtimeEventDto>("CHANGED")
            launch {
                changed.collect { event ->
                    if (event.requestId == requestId) refresh()
                }
            }
            launch {
                channel.status.filter { it == RealtimeChannel.Status.SUBSCRIBED }
                    .collect { refresh() }
            }
            channel.subscribe(blockUntilSubscribed = true)
            refresh()
            awaitCancellation()
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Throwable) {
            // Realtime is an optimization. A private-channel authorization failure must not
            // turn a valid payment into a generic service-unavailable screen: the polling job
            // above continues calling get_payment_status every 10 seconds.
            Log.w("PaymentRepository", "payment Realtime unavailable; using status polling", error)
            awaitCancellation()
        } finally {
            withContext(NonCancellable) { runCatching { client.realtime.removeChannel(channel) } }
        }
    }

    private suspend fun classifyEdgeFunctionError(error: RestException): PaymentFailure {
        val body = runCatching { error.response.body<EdgeFunctionErrorDto>() }.getOrNull()
        return when {
            error.statusCode == 401 -> PaymentFailure.SESSION_EXPIRED
            body?.code == "CARD_REJECTED" -> PaymentFailure.CARD_REJECTED
            body?.code == "CARD_NOT_FOUND" -> PaymentFailure.CARD_NOT_FOUND
            body?.code == "PAYER_DOCUMENT_REQUIRED" -> PaymentFailure.PAYER_DOCUMENT_REQUIRED
            body?.code == "QUOTE_EXPIRED" -> PaymentFailure.QUOTE_EXPIRED
            body?.code == "QUOTE_CONFLICT" -> PaymentFailure.QUOTE_CONFLICT
            body?.code == "INVALID_PAYMENT_CONTEXT" -> PaymentFailure.INVALID_PAYMENT_CONTEXT
            error.statusCode == 400 -> PaymentFailure.CONTRACT
            error.statusCode >= 500 -> PaymentFailure.SERVICE_UNAVAILABLE
            else -> PaymentFailure.CONTRACT
        }
    }

    private fun classifyRpcError(error: Throwable): PaymentFailure = when (error) {
        is HttpRequestTimeoutException -> PaymentFailure.TIMEOUT
        is HttpRequestException -> PaymentFailure.OFFLINE
        is PostgrestRestException -> when {
            error.statusCode == 401 || error.statusCode == 403 || error.code == "42501" ->
                PaymentFailure.SESSION_EXPIRED
            error.statusCode >= 500 -> PaymentFailure.SERVICE_UNAVAILABLE
            else -> PaymentFailure.CONTRACT
        }
        else -> PaymentFailure.UNKNOWN
    }

    private companion object {
        const val RPC_TIMEOUT_MILLIS = 15_000L

        const val REFUND_TIMEOUT_MILLIS = 15_000L
    }
}

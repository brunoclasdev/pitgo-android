package com.trustarx.pitgo.features.payment.repository

import com.trustarx.pitgo.features.payment.model.*
import com.trustarx.pitgo.features.mobiledispatch.model.isUuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.math.BigDecimal
import java.util.UUID
import com.trustarx.pitgo.features.mobiledispatch.model.MobileDispatchKind
import com.trustarx.pitgo.features.payment.data.CreatePaymentResponseDto
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.CancellationException

class SimulatedPaymentGateway(private val client: SupabaseClient? = null) : PaymentGateway {
    private val persisted = client?.let(::PaymentRepository)
    override val simulated = true
    private val payments = MutableStateFlow<Map<String, Payment>>(emptyMap())
    private val mutex = Mutex()
    private val finishedRequests = mutableSetOf<String>()
    private fun event(requestId: String): PaymentEvent = payments.value[requestId]?.let {
        PaymentEvent.StatusChanged(it.status, it.id, it)
    } ?: PaymentEvent.NotFound
    override suspend fun recoverable(): PaymentEvent {
        val remote = persisted?.recoverable()
        if (remote is PaymentEvent.StatusChanged && remote.payment?.dispatchKind == MobileDispatchKind.GUINCHO_DIRECT) return rememberRemote(remote)
        return mutex.withLock { payments.value.values.lastOrNull { it.dispatchKind != MobileDispatchKind.GUINCHO_DIRECT && it.blocksNewAttempt && it.requestId !in finishedRequests }?.let {
        PaymentEvent.StatusChanged(it.status, it.id, it)
        } ?: PaymentEvent.NotFound }
    }
    override suspend fun history(before: Payment?): List<Payment> {
        val remote = persisted?.history(before).orEmpty().filter { it.dispatchKind == MobileDispatchKind.GUINCHO_DIRECT }
        val local = payments.value.values.filter { it.dispatchKind != MobileDispatchKind.GUINCHO_DIRECT }
        return (remote + local).distinctBy { it.id }.sortedByDescending { it.createdAt }
            .filter { before == null || it.createdAt.orEmpty() < before.createdAt.orEmpty() || (it.createdAt == before.createdAt && it.id < before.id) }.take(20)
    }
    private fun rememberRemote(remote: PaymentEvent.StatusChanged): PaymentEvent.StatusChanged {
        val payment = requireNotNull(remote.payment)
        val pix = payment.pix ?: payments.value[payment.requestId]?.pix ?: if (payment.method == PaymentMethod.PIX && payment.status == PaymentStatus.PENDING) {
            PixCharge("SIMULACAO-SEM-VALOR", "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=", System.currentTimeMillis() + 300_000)
        } else null
        val restored = payment.copy(pix = pix)
        payments.value = payments.value + (payment.requestId to restored)
        return PaymentEvent.StatusChanged(restored.status, restored.id, restored)
    }
    override suspend fun createPayment(command: CreatePaymentCommand): CreatePaymentResult = mutex.withLock {
        payments.value[command.requestId]?.takeIf { it.blocksNewAttempt }?.let {
            if (it.dispatchKind != command.dispatchKind || it.serviceId != command.serviceId || it.appointmentId != command.appointmentId || it.quoteVersion != command.quoteVersion || it.amount.compareTo(command.amount) != 0) {
                return@withLock CreatePaymentResult.Failure(PaymentFailure.INVALID_PAYMENT_CONTEXT)
            }
            return@withLock CreatePaymentResult.Success(it)
        }
        if (command.amount.signum() <= 0 || !command.requestId.isUuid()) return@withLock CreatePaymentResult.Failure(PaymentFailure.CONTRACT)
        var payment = Payment(UUID.randomUUID().toString(), command.requestId, command.dispatchKind,
            command.method, command.amount, command.appointmentId, command.quoteVersion,
            PaymentStatus.PENDING,
            pix = if (command.method == PaymentMethod.PIX) PixCharge("SIMULACAO-SEM-VALOR", "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=", System.currentTimeMillis() + 300_000) else null,
            serviceId = command.serviceId ?: "simulated", createdAt = java.time.Instant.now().toString())
        if (command.dispatchKind == MobileDispatchKind.GUINCHO_DIRECT) {
            payment = try {
                val dto = requireNotNull(client).postgrest.rpc("simulate_guincho_payment", buildJsonObject {
                    put("p_request_id", command.requestId); put("p_status", "PENDING"); put("p_method", command.method.name)
                }).decodeAs<CreatePaymentResponseDto>()
                requireNotNull(dto.toDomainOrNull(command)).copy(pix = payment.pix)
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: RestException) {
                val reason = when {
                    error.message?.contains("PRICE_CHANGED") == true -> PaymentFailure.QUOTE_CONFLICT
                    error.message?.contains("INVALID_TRANSITION") == true -> PaymentFailure.INVALID_PAYMENT_CONTEXT
                    else -> PaymentFailure.SERVICE_UNAVAILABLE
                }
                return@withLock CreatePaymentResult.Failure(reason)
            }
            catch (_: Exception) { return@withLock CreatePaymentResult.Failure(PaymentFailure.SERVICE_UNAVAILABLE) }
        }
        payments.value = payments.value + (command.requestId to payment)
        CreatePaymentResult.Success(payment)
    }
    override suspend fun simulate(requestId: String, status: PaymentStatus): PaymentEvent = mutex.withLock {
        val payment = payments.value[requestId] ?: return@withLock PaymentEvent.NotFound
        if (payment.status != PaymentStatus.PENDING || status !in setOf(PaymentStatus.APPROVED, PaymentStatus.REJECTED, PaymentStatus.EXPIRED, PaymentStatus.CANCELLED)) return@withLock event(requestId)
        if (payment.dispatchKind == MobileDispatchKind.GUINCHO_DIRECT) {
            try { requireNotNull(client).postgrest.rpc("simulate_guincho_payment", buildJsonObject {
                put("p_request_id", requestId); put("p_status", status.name); put("p_method", payment.method.name)
            }) } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { return@withLock PaymentEvent.Failure(PaymentFailure.SERVICE_UNAVAILABLE) }
        }
        payments.value = payments.value + (requestId to payment.copy(status = status, revision = payment.revision + 1))
        event(requestId)
    }
    override suspend fun cancel(paymentId: String): CreatePaymentResult = mutex.withLock {
        val payment = payments.value.values.find { it.id == paymentId } ?: return@withLock CreatePaymentResult.Failure(PaymentFailure.CONTRACT)
        if (payment.dispatchKind == MobileDispatchKind.GUINCHO_DIRECT) {
            try { requireNotNull(client).postgrest.rpc("request_payment_cancellation", buildJsonObject { put("p_payment_id",paymentId) }) }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { return@withLock CreatePaymentResult.Failure(PaymentFailure.SERVICE_UNAVAILABLE) }
        }
        val updated = payment.copy(status = if (payment.status == PaymentStatus.APPROVED) PaymentStatus.REFUNDED else PaymentStatus.CANCELLED, cancelRequested = true, revision = payment.revision + 1)
        payments.value = payments.value + (payment.requestId to updated)
        CreatePaymentResult.Success(updated)
    }
    override suspend fun refund(paymentId: String): RefundResult = when (val result = cancel(paymentId)) {
        is CreatePaymentResult.Success -> RefundResult.Done(result.payment.status)
        is CreatePaymentResult.Failure -> RefundResult.Failure(result.reason)
    }
    override suspend fun settleSimulation(requestId: String, cancelled: Boolean): PaymentEvent = mutex.withLock {
        val payment = payments.value[requestId] ?: return@withLock PaymentEvent.NotFound
        val status = when {
            cancelled && payment.status == PaymentStatus.APPROVED -> PaymentStatus.REFUNDED
            cancelled && payment.status == PaymentStatus.PENDING -> PaymentStatus.CANCELLED
            else -> payment.status
        }
        payments.value = payments.value + (requestId to payment.copy(status = status, cancelRequested = cancelled, revision = payment.revision + 1))
        finishedRequests += requestId
        event(requestId)
    }
    override suspend fun quoteTotal(requestId: String, appointmentId: String?, quoteVersion: Long?) = BigDecimal("1.00")
    override suspend fun getStatus(requestId: String): PaymentEvent {
        val local=payments.value[requestId]
        if (local==null || local.dispatchKind==MobileDispatchKind.GUINCHO_DIRECT) {
            val remote=persisted?.getStatus(requestId)
            if (remote is PaymentEvent.StatusChanged && remote.payment?.dispatchKind==MobileDispatchKind.GUINCHO_DIRECT) {
                return rememberRemote(remote)
            }
            if (local?.dispatchKind==MobileDispatchKind.GUINCHO_DIRECT && remote is PaymentEvent.Failure) return remote
        }
        return event(requestId)
    }
    override suspend fun cancelUnpaidGuincho(requestId: String) = persisted?.cancelUnpaidGuincho(requestId) ?: false
    override fun observe(requestId: String) = payments.map { event(requestId) }
}

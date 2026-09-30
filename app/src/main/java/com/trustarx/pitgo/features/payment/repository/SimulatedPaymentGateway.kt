package com.trustarx.pitgo.features.payment.repository

import com.trustarx.pitgo.features.payment.model.*
import com.trustarx.pitgo.features.mobiledispatch.model.isUuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.math.BigDecimal
import java.util.UUID

class SimulatedPaymentGateway : PaymentGateway {
    override val simulated = true
    private val payments = MutableStateFlow<Map<String, Payment>>(emptyMap())
    private val mutex = Mutex()
    private val finishedRequests = mutableSetOf<String>()
    private fun event(requestId: String): PaymentEvent = payments.value[requestId]?.let {
        PaymentEvent.StatusChanged(it.status, it.id, it)
    } ?: PaymentEvent.NotFound
    override suspend fun recoverable() = mutex.withLock { payments.value.values.lastOrNull { it.blocksNewAttempt && it.requestId !in finishedRequests }?.let {
        PaymentEvent.StatusChanged(it.status, it.id, it)
    } ?: PaymentEvent.NotFound }
    override suspend fun history(before: Payment?) = payments.value.values.toList().asReversed().let {
        if (before == null) it else it.dropWhile { payment -> payment.id != before.id }.drop(1)
    }.take(20)
    override suspend fun createPayment(command: CreatePaymentCommand): CreatePaymentResult = mutex.withLock {
        payments.value[command.requestId]?.takeIf { it.blocksNewAttempt }?.let {
            if (it.dispatchKind != command.dispatchKind || it.serviceId != command.serviceId || it.appointmentId != command.appointmentId || it.quoteVersion != command.quoteVersion || it.amount.compareTo(command.amount) != 0) {
                return@withLock CreatePaymentResult.Failure(PaymentFailure.INVALID_PAYMENT_CONTEXT)
            }
            return@withLock CreatePaymentResult.Success(it)
        }
        if (command.amount.signum() <= 0 || !command.requestId.isUuid()) return@withLock CreatePaymentResult.Failure(PaymentFailure.CONTRACT)
        val payment = Payment(UUID.randomUUID().toString(), command.requestId, command.dispatchKind,
            command.method, command.amount, command.appointmentId, command.quoteVersion,
            PaymentStatus.PENDING,
            pix = if (command.method == PaymentMethod.PIX) PixCharge("SIMULACAO-SEM-VALOR", "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=", System.currentTimeMillis() + 300_000) else null,
            serviceId = command.serviceId ?: "simulated", createdAt = java.time.Instant.now().toString())
        payments.value = payments.value + (command.requestId to payment)
        CreatePaymentResult.Success(payment)
    }
    override suspend fun simulate(requestId: String, status: PaymentStatus): PaymentEvent = mutex.withLock {
        val payment = payments.value[requestId] ?: return@withLock PaymentEvent.NotFound
        if (payment.status != PaymentStatus.PENDING || status !in setOf(PaymentStatus.APPROVED, PaymentStatus.REJECTED, PaymentStatus.EXPIRED, PaymentStatus.CANCELLED)) return@withLock event(requestId)
        payments.value = payments.value + (requestId to payment.copy(status = status, revision = payment.revision + 1))
        event(requestId)
    }
    override suspend fun cancel(paymentId: String): CreatePaymentResult = mutex.withLock {
        val payment = payments.value.values.find { it.id == paymentId } ?: return@withLock CreatePaymentResult.Failure(PaymentFailure.CONTRACT)
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
    override suspend fun getStatus(requestId: String) = event(requestId)
    override fun observe(requestId: String) = payments.map { event(requestId) }
}

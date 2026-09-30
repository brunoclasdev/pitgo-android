package com.trustarx.pitgo.features.payment.repository

import com.trustarx.pitgo.features.payment.model.*
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

interface PaymentGateway {
    val simulated: Boolean get() = false
    suspend fun recoverable(): PaymentEvent
    suspend fun history(before: Payment? = null): List<Payment>
    suspend fun createPayment(command: CreatePaymentCommand): CreatePaymentResult
    suspend fun cancel(paymentId: String): CreatePaymentResult
    suspend fun refund(paymentId: String): RefundResult
    suspend fun quoteTotal(requestId: String, appointmentId: String?, quoteVersion: Long?): BigDecimal?
    suspend fun getStatus(requestId: String): PaymentEvent
    fun observe(requestId: String): Flow<PaymentEvent>
    suspend fun simulate(requestId: String, status: PaymentStatus): PaymentEvent = PaymentEvent.Failure(PaymentFailure.CONTRACT)
    suspend fun settleSimulation(requestId: String, cancelled: Boolean): PaymentEvent = PaymentEvent.Failure(PaymentFailure.CONTRACT)
}

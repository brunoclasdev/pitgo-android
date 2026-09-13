package com.trustarx.pitgo.features.payment.model

import com.trustarx.pitgo.features.mobiledispatch.model.MobileDispatchKind
import java.math.BigDecimal

data class CreatePaymentCommand(
    val requestId: String,
    val dispatchKind: MobileDispatchKind,
    val amount: BigDecimal,
    val method: PaymentMethod,
    val savedCardId: String? = null,
    val cardSecurityCodeToken: String? = null,
)

enum class PaymentFailure {
    OFFLINE, TIMEOUT, SESSION_EXPIRED,
    CARD_REJECTED, CARD_NOT_FOUND, PAYER_DOCUMENT_REQUIRED,
    SERVICE_UNAVAILABLE, CONTRACT, UNKNOWN,
}

sealed interface CreatePaymentResult {
    data class Success(val payment: Payment) : CreatePaymentResult
    data class Failure(val reason: PaymentFailure) : CreatePaymentResult
}

sealed interface PaymentEvent {
    data class StatusChanged(val status: PaymentStatus, val paymentId: String? = null) : PaymentEvent
    data class Failure(val reason: PaymentFailure) : PaymentEvent
}

sealed interface RefundResult {
    data class Done(val status: PaymentStatus) : RefundResult
    data class Failure(val reason: PaymentFailure) : RefundResult
}

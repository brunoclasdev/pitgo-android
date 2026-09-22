package com.trustarx.pitgo.features.payment.model

import com.trustarx.pitgo.features.mobiledispatch.model.MobileDispatchKind
import com.trustarx.pitgo.features.mobiledispatch.model.isUuid
import java.math.BigDecimal

/**
 * How the client pays a solicitation. `SAVED_CARD` always references a card cadastrado
 * beforehand in features/paymentmethods — the checkout step never registers a card inline
 * (FR-019).
 */
enum class PaymentMethod { PIX, SAVED_CARD }
enum class PaymentSyncState { CREATING, VERIFYING, CURRENT }

/**
 * Mirrors the `payments.status` state machine in
 * specs/018-mercadopago-payments/data-model.md. `REFUND_PENDING` is transient: the backend sets
 * it the instant a paid solicitation is cancelled/exhausted with nothing delivered, and the app
 * observes the autonomous backend until `REFUNDED` or `REFUND_FAILED`.
 */
enum class PaymentStatus {
    PENDING, APPROVED, REJECTED, EXPIRED, CANCELLED,
    REFUND_PENDING, REFUNDED, REFUND_FAILED,
}

/** Pix charge details returned by `mp-create-payment` while `status == PENDING`. */
data class PixCharge(
    val qrCode: String,
    val qrCodeBase64: String,
    val expiresAtEpochMillis: Long,
) {
    val isComplete get() = qrCode.isNotBlank() && qrCodeBase64.isNotBlank() && expiresAtEpochMillis > 0L
}

data class Payment(
    val id: String,
    val requestId: String,
    val dispatchKind: MobileDispatchKind,
    val method: PaymentMethod,
    val amount: BigDecimal,
    val appointmentId: String? = null,
    val quoteVersion: Long? = null,
    val status: PaymentStatus,
    val pix: PixCharge? = null,
    val serviceId: String? = null,
    val syncState: PaymentSyncState = PaymentSyncState.CURRENT,
    val revision: Long = 0,
    val cancelRequested: Boolean = false,
    val supportRequired: Boolean = false,
    val createdAt: String? = null,
    val cardBrand: String? = null,
    val cardLastFourDigits: String? = null,
) {
    val canProceed get() = status == PaymentStatus.APPROVED && syncState == PaymentSyncState.CURRENT && !cancelRequested
    val blocksNewAttempt get() = syncState != PaymentSyncState.CURRENT || status in setOf(
        PaymentStatus.PENDING, PaymentStatus.APPROVED, PaymentStatus.REFUND_PENDING, PaymentStatus.REFUND_FAILED,
    )
    fun isValid() = id.isUuid() && requestId.isUuid() && amount.signum() > 0 &&
        (appointmentId == null || appointmentId.isUuid()) &&
        (quoteVersion == null || quoteVersion > 0)
}

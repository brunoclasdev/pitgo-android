package com.trustarx.pitgo.features.payment.data

import com.trustarx.pitgo.core.common.time.parseSupabaseInstant
import com.trustarx.pitgo.features.payment.model.CreatePaymentCommand
import com.trustarx.pitgo.features.payment.model.Payment
import com.trustarx.pitgo.features.payment.model.PaymentStatus
import com.trustarx.pitgo.features.payment.model.PixCharge
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Body sent to the `mp-create-payment` Edge Function (contracts/mercadopago-edge-functions.md). */
@Serializable
internal data class CreatePaymentRequestDto(
    @SerialName("request_id") val requestId: String,
    @SerialName("dispatch_kind") val dispatchKind: String,
    val amount: Double,
    val method: String,
    @SerialName("saved_card_id") val savedCardId: String? = null,
    @SerialName("card_security_code_token") val cardSecurityCodeToken: String? = null,
)

@Serializable
internal data class PixChargeDto(
    @SerialName("qr_code") val qrCode: String,
    @SerialName("qr_code_base64") val qrCodeBase64: String,
    @SerialName("expires_at") val expiresAt: String? = null,
) {
    fun toDomain() = PixCharge(
        qrCode = qrCode,
        qrCodeBase64 = qrCodeBase64,
        expiresAtEpochMillis = expiresAt?.let { runCatching { parseSupabaseInstant(it).toEpochMilli() }.getOrNull() } ?: 0L,
    )
}

@Serializable
internal data class CreatePaymentResponseDto(
    @SerialName("payment_id") val paymentId: String,
    val status: String,
    val pix: PixChargeDto? = null,
) {
    fun toDomainOrNull(command: CreatePaymentCommand): Payment? = runCatching {
        Payment(
            id = paymentId,
            requestId = command.requestId,
            dispatchKind = command.dispatchKind,
            method = command.method,
            amount = command.amount,
            status = PaymentStatus.valueOf(status),
            pix = pix?.toDomain(),
        ).takeIf(Payment::isValid)
    }.getOrNull()
}

/** Error body shape returned by every `mp-*` Edge Function on non-2xx (see their contracts). */
@Serializable
internal data class EdgeFunctionErrorDto(val code: String? = null)

/** Body sent to `mp-refund-payment`. */
@Serializable
internal data class RefundPaymentRequestDto(@SerialName("payment_id") val paymentId: String)

/** Response from `mp-refund-payment`. */
@Serializable
internal data class RefundPaymentResponseDto(val status: String) {
    fun toStatusOrNull(): PaymentStatus? = runCatching { PaymentStatus.valueOf(status) }.getOrNull()
}

/** Decodes `get_payment_status(p_request_id)` — the Realtime fallback (contracts/payment-realtime-ui.md). */
@Serializable
internal data class PaymentStatusProbeDto(
    val outcome: String,
    @SerialName("payment_id") val paymentId: String? = null,
    val status: String? = null,
    val method: String? = null,
) {
    fun toStatusOrNull(): PaymentStatus? =
        status?.let { runCatching { PaymentStatus.valueOf(it) }.getOrNull() }
}

/** Decodes the Realtime broadcast payload published by `private.broadcast_payment_change()`. */
@Serializable
internal data class PaymentRealtimeEventDto(
    @SerialName("request_id") val requestId: String? = null,
    @SerialName("payment_id") val paymentId: String? = null,
    val status: String? = null,
) {
    fun toStatusOrNull(): PaymentStatus? =
        status?.let { runCatching { PaymentStatus.valueOf(it) }.getOrNull() }
}

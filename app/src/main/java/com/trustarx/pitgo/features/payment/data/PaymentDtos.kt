package com.trustarx.pitgo.features.payment.data

import com.trustarx.pitgo.core.common.time.parseSupabaseInstant
import com.trustarx.pitgo.features.payment.model.CreatePaymentCommand
import com.trustarx.pitgo.features.payment.model.Payment
import com.trustarx.pitgo.features.payment.model.PaymentStatus
import com.trustarx.pitgo.features.payment.model.PaymentSyncState
import com.trustarx.pitgo.features.payment.model.PaymentMethod
import com.trustarx.pitgo.features.mobiledispatch.model.MobileDispatchKind
import java.math.BigDecimal
import com.trustarx.pitgo.features.payment.model.PixCharge
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Body sent to the `mp-create-payment` Edge Function (contracts/mercadopago-edge-functions.md). */
@Serializable
internal data class CreatePaymentRequestDto(
    @SerialName("request_id") val requestId: String,
    @SerialName("dispatch_kind") val dispatchKind: String,
    val amount: String? = null,
    @SerialName("service_id") val serviceId: String? = null,
    @SerialName("appointment_id") val appointmentId: String? = null,
    @SerialName("quote_version") val quoteVersion: Long? = null,
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
    val amount: String? = null,
    val currency: String = "BRL",
    val method: String? = null,
    @SerialName("service_id") val serviceId: String? = null,
    @SerialName("sync_state") val syncState: String = "CURRENT",
    val revision: Long = 0,
    @SerialName("cancel_requested") val cancelRequested: Boolean = false,
    @SerialName("support_required") val supportRequired: Boolean = false,
) {
    fun toDomainOrNull(command: CreatePaymentCommand): Payment? = runCatching {
        Payment(
            id = paymentId,
            requestId = command.requestId,
            dispatchKind = command.dispatchKind,
            method = method?.let(PaymentMethod::valueOf) ?: command.method,
            amount = amount?.let(::BigDecimal) ?: command.amount,
            appointmentId = command.appointmentId,
            quoteVersion = command.quoteVersion,
            status = PaymentStatus.valueOf(status),
            pix = pix?.toDomain(),
            serviceId = serviceId ?: command.serviceId,
            syncState = PaymentSyncState.valueOf(syncState), revision = revision,
            cancelRequested = cancelRequested, supportRequired = supportRequired,
        ).takeIf { it.isValid() && currency == "BRL" }
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
    @SerialName("request_id") val requestId: String? = null,
    @SerialName("service_id") val serviceId: String? = null,
    @SerialName("dispatch_kind") val dispatchKind: String? = null,
    @SerialName("appointment_id") val appointmentId: String? = null,
    @SerialName("quote_version") val quoteVersion: Long? = null,
    @SerialName("sync_state") val syncState: String = "CURRENT",
    val revision: Long = 0,
    val amount: String? = null,
    val currency: String = "BRL",
    val pix: PixChargeDto? = null,
    @SerialName("cancel_requested") val cancelRequested: Boolean = false,
    @SerialName("support_required") val supportRequired: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("card_brand") val cardBrand: String? = null,
    @SerialName("card_last_four_digits") val cardLastFourDigits: String? = null,
) {
    fun toDomainOrNull(): Payment? = runCatching {
        Payment(id = requireNotNull(paymentId), requestId = requireNotNull(requestId),
            dispatchKind = MobileDispatchKind.valueOf(requireNotNull(dispatchKind)),
            method = PaymentMethod.valueOf(requireNotNull(method)), amount = BigDecimal(requireNotNull(amount)),
            status = PaymentStatus.valueOf(requireNotNull(status)), appointmentId = appointmentId, quoteVersion = quoteVersion,
            pix = pix?.toDomain(), serviceId = serviceId, syncState = PaymentSyncState.valueOf(syncState),
            revision = revision, cancelRequested = cancelRequested, supportRequired = supportRequired,
            createdAt = createdAt, cardBrand = cardBrand, cardLastFourDigits = cardLastFourDigits,
        ).takeIf { it.isValid() && currency == "BRL" }
    }.getOrNull()
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

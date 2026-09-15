package com.trustarx.pitgo.features.payment.model

import com.trustarx.pitgo.features.paymentmethods.model.SavedCard

/** See specs/018-mercadopago-payments/contracts/payment-realtime-ui.md. */
sealed interface PaymentCheckoutUiState {
    data object Loading : PaymentCheckoutUiState
    data object InvalidService : PaymentCheckoutUiState

    data class SelectingMethod(
        val amount: java.math.BigDecimal,
        // Populated once features/paymentmethods exists to select from (US3); Pix is always
        // offered regardless (FR-005/FR-019 — cadastro nunca é oferecido aqui).
        val savedCards: List<SavedCard> = emptyList(),
    ) : PaymentCheckoutUiState

    data object CreatingPayment : PaymentCheckoutUiState
    data object Verifying : PaymentCheckoutUiState
    data class FinancialStatus(val payment: Payment) : PaymentCheckoutUiState
    data class ConfirmingCard(val context: com.trustarx.pitgo.features.paymentmethods.model.SavedCardTokenizationContext) : PaymentCheckoutUiState
    data class AwaitingPix(val pix: PixCharge) : PaymentCheckoutUiState
    data object AwaitingCardConfirmation : PaymentCheckoutUiState
    data object Approved : PaymentCheckoutUiState
    data object Declined : PaymentCheckoutUiState
    data object Expired : PaymentCheckoutUiState
    data object Failure : PaymentCheckoutUiState
}

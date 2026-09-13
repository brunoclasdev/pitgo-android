package com.trustarx.pitgo.features.paymentmethods.model

/** See specs/018-mercadopago-payments/contracts/payment-realtime-ui.md. */
sealed interface PaymentMethodsUiState {
    data object Loading : PaymentMethodsUiState

    /** Empty state is just `cards.isEmpty()` — same screen, no separate state needed (FR-005). */
    data class Listing(val cards: List<SavedCard>) : PaymentMethodsUiState

    data object Registering : PaymentMethodsUiState
    data class RegisterFailed(val cards: List<SavedCard>) : PaymentMethodsUiState
    data class Removing(val cards: List<SavedCard>, val removingId: String) : PaymentMethodsUiState
    data object Failure : PaymentMethodsUiState
}

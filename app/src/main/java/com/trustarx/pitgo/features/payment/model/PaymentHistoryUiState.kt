package com.trustarx.pitgo.features.payment.model

data class PaymentHistoryUiState(
    val payments: List<Payment> = emptyList(), val loading: Boolean = false,
    val failed: Boolean = false, val hasMore: Boolean = true,
)

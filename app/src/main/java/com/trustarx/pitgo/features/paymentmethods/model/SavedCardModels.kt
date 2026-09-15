package com.trustarx.pitgo.features.paymentmethods.model

/**
 * A card the client cadastrou opcionalmente (FR-004). Only what's needed to show/select it in
 * the UI — no PAN, CVV or expiration ever reaches the app (FR-006).
 */
data class SavedCard(
    val id: String,
    val brand: String,
    val lastFourDigits: String,
) {
    fun isValid() = id.isNotBlank() && brand.isNotBlank() && lastFourDigits.length == 4
}

sealed interface SavedCardsResult {
    data class Success(val cards: List<SavedCard>) : SavedCardsResult
    data class Failure(val reason: SavedCardFailure) : SavedCardsResult
}

sealed interface SaveCardResult {
    data class Success(val card: SavedCard) : SaveCardResult
    data class Failure(val reason: SavedCardFailure) : SaveCardResult
}

sealed interface RemoveCardResult {
    data object Success : RemoveCardResult
    data class Failure(val reason: SavedCardFailure) : RemoveCardResult
}

enum class SavedCardFailure {
    OFFLINE, TIMEOUT, SESSION_EXPIRED, CARD_REJECTED, CARD_NOT_FOUND,
    SERVICE_UNAVAILABLE, CONTRACT, UNKNOWN,
}

data class SavedCardTokenizationContext(
    val savedCardId: String, val cardId: String, val holderName: String,
    val documentNumber: String?, val documentType: String,
)
sealed interface CardContextResult {
    data class Success(val context: SavedCardTokenizationContext) : CardContextResult
    data class Failure(val reason: SavedCardFailure) : CardContextResult
}

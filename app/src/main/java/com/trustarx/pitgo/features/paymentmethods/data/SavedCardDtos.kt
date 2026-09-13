package com.trustarx.pitgo.features.paymentmethods.data

import com.trustarx.pitgo.features.paymentmethods.model.SavedCard
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Decodes one row of `list_saved_cards()`. */
@Serializable
internal data class SavedCardDto(
    val id: String,
    val brand: String,
    @SerialName("last_four_digits") val lastFourDigits: String,
) {
    fun toDomainOrNull(): SavedCard? =
        SavedCard(id, brand, lastFourDigits).takeIf(SavedCard::isValid)
}

/** Body sent to `mp-save-card`. */
@Serializable
internal data class SaveCardRequestDto(@SerialName("card_token") val cardToken: String)

/** Response from `mp-save-card`. */
@Serializable
internal data class SaveCardResponseDto(
    @SerialName("saved_card_id") val savedCardId: String,
    val brand: String,
    @SerialName("last_four_digits") val lastFourDigits: String,
) {
    fun toDomainOrNull(): SavedCard? =
        SavedCard(savedCardId, brand, lastFourDigits).takeIf(SavedCard::isValid)
}

/** Body sent to `mp-remove-card`. */
@Serializable
internal data class RemoveCardRequestDto(@SerialName("saved_card_id") val savedCardId: String)

package com.trustarx.pitgo.features.paymentmethods.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.trustarx.pitgo.features.paymentmethods.model.PaymentMethodsUiState
import com.trustarx.pitgo.features.paymentmethods.model.RemoveCardResult
import com.trustarx.pitgo.features.paymentmethods.model.SaveCardResult
import com.trustarx.pitgo.features.paymentmethods.model.SavedCard
import com.trustarx.pitgo.features.paymentmethods.model.SavedCardsResult
import com.trustarx.pitgo.features.paymentmethods.repository.SavedCardRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Área dedicada de formas de pagamento (US2) — cadastro sempre opcional, nunca inline no checkout. */
class PaymentMethodsViewModel(private val repository: SavedCardRepository) : ViewModel() {
    private var cards: List<SavedCard> = emptyList()
    private val _uiState = MutableStateFlow<PaymentMethodsUiState>(PaymentMethodsUiState.Loading)
    val uiState: StateFlow<PaymentMethodsUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        _uiState.value = PaymentMethodsUiState.Loading
        viewModelScope.launch {
            _uiState.value = when (val result = repository.list()) {
                is SavedCardsResult.Success -> {
                    cards = result.cards
                    PaymentMethodsUiState.Listing(cards)
                }
                is SavedCardsResult.Failure -> PaymentMethodsUiState.Failure
            }
        }
    }

    fun registerCard(cardToken: String) {
        if (_uiState.value == PaymentMethodsUiState.Registering) return
        _uiState.value = PaymentMethodsUiState.Registering
        viewModelScope.launch {
            _uiState.value = when (val result = repository.save(cardToken)) {
                is SaveCardResult.Success -> {
                    cards = cards.filterNot { it.id == result.card.id } + result.card
                    PaymentMethodsUiState.Listing(cards)
                }
                is SaveCardResult.Failure -> PaymentMethodsUiState.RegisterFailed(cards)
            }
        }
    }

    /** US2 cenário 2: cliente pode tentar de novo ou seguir sem cadastrar. */
    fun dismissRegisterFailure() {
        _uiState.value = PaymentMethodsUiState.Listing(cards)
    }

    fun removeCard(cardId: String) {
        if (_uiState.value is PaymentMethodsUiState.Removing) return
        _uiState.value = PaymentMethodsUiState.Removing(cards, cardId)
        viewModelScope.launch {
            _uiState.value = when (repository.remove(cardId)) {
                RemoveCardResult.Success -> {
                    cards = cards.filterNot { it.id == cardId }
                    PaymentMethodsUiState.Listing(cards)
                }
                is RemoveCardResult.Failure -> PaymentMethodsUiState.Listing(cards, removalFailed = true)
            }
        }
    }

    companion object {
        fun factory(repository: SavedCardRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                PaymentMethodsViewModel(repository) as T
        }
    }
}

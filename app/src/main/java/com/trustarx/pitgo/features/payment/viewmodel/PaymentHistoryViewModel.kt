package com.trustarx.pitgo.features.payment.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.trustarx.pitgo.features.payment.model.PaymentHistoryUiState
import com.trustarx.pitgo.features.payment.repository.PaymentGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PaymentHistoryViewModel(private val repository: PaymentGateway) : ViewModel() {
    private val _state = MutableStateFlow(PaymentHistoryUiState())
    val state = _state.asStateFlow()
    fun load(refresh: Boolean = false) {
        if (_state.value.loading || (!refresh && !_state.value.hasMore)) return
        val previous = if (refresh) PaymentHistoryUiState() else _state.value
        _state.value = previous.copy(loading = true, failed = false)
        viewModelScope.launch {
            try {
                val page = repository.history(previous.payments.lastOrNull())
                _state.value = previous.copy(payments = (previous.payments + page).distinctBy { it.id },
                    loading = false, failed = false, hasMore = page.size == 20)
            } catch (_: TimeoutCancellationException) { _state.value = previous.copy(failed = true)
            } catch (cancel: CancellationException) { throw cancel
            } catch (_: Exception) { _state.value = previous.copy(failed = true) }
        }
    }
    companion object {
        fun factory(repository: PaymentGateway) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = PaymentHistoryViewModel(repository) as T
        }
    }
}

package com.trustarx.pitgo.features.payment.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.trustarx.pitgo.core.catalog.model.CatalogItemResult
import com.trustarx.pitgo.core.catalog.repository.ServiceCatalogRepository
import com.trustarx.pitgo.core.navigation.PitGoRoutes
import com.trustarx.pitgo.features.mobiledispatch.model.MobileDispatchKind
import com.trustarx.pitgo.features.mobiledispatch.model.isUuid
import com.trustarx.pitgo.features.payment.model.CreatePaymentCommand
import com.trustarx.pitgo.features.payment.model.CreatePaymentResult
import com.trustarx.pitgo.features.payment.model.Payment
import com.trustarx.pitgo.features.payment.model.PaymentCheckoutUiState
import com.trustarx.pitgo.features.payment.model.PaymentEvent
import com.trustarx.pitgo.features.payment.model.PaymentMethod
import com.trustarx.pitgo.features.payment.model.PaymentStatus
import com.trustarx.pitgo.features.payment.repository.PaymentRepository
import com.trustarx.pitgo.features.paymentmethods.model.SavedCard
import com.trustarx.pitgo.features.paymentmethods.model.SavedCardsResult
import com.trustarx.pitgo.features.paymentmethods.repository.SavedCardRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.UUID

/**
 * Checkout shared by the MOBILE_SEQUENTIAL and PitTec flows (spec 018): reached from Home instead
 * of the dispatch screen itself, generates the `request_id` those flows will later start with,
 * and only lets the caller proceed once a payment is `APPROVED` (FR-009/FR-014).
 */
class PaymentCheckoutViewModel(
    savedStateHandle: SavedStateHandle,
    private val catalogRepository: ServiceCatalogRepository,
    private val paymentRepository: PaymentRepository,
    private val savedCardRepository: SavedCardRepository,
) : ViewModel() {
    private val serviceId = savedStateHandle.get<String>(PitGoRoutes.PaymentServiceArg).orEmpty()
    private val dispatchKind = runCatching {
        MobileDispatchKind.valueOf(savedStateHandle.get<String>(PitGoRoutes.PaymentDispatchKindArg).orEmpty())
    }.getOrDefault(MobileDispatchKind.MOBILE_SEQUENTIAL)

    /**
     * The id the dispatch screen will reuse to start once this payment is approved. Normally a
     * fresh id (guincho móvel, paid before dispatch); `019-pittec-request-flow` instead supplies
     * the PitTec search's own id here, since that search/appointment already exists and the
     * payment must be tied to it (`arrive_pittec_appointment` checks
     * `payments.request_id = dispatch_search_id`).
     */
    val requestId: String = savedStateHandle.get<String>(PitGoRoutes.PaymentRequestIdArg)
        ?.takeIf(String::isUuid)
        ?: UUID.randomUUID().toString()

    private var amount: BigDecimal = BigDecimal.ZERO
    private var savedCards: List<SavedCard> = emptyList()
    private var observeJob: Job? = null

    private val _uiState = MutableStateFlow<PaymentCheckoutUiState>(PaymentCheckoutUiState.Loading)
    val uiState: StateFlow<PaymentCheckoutUiState> = _uiState.asStateFlow()

    init { loadService() }

    private fun loadService() {
        _uiState.value = PaymentCheckoutUiState.Loading
        viewModelScope.launch {
            val service = (catalogRepository.service(serviceId) as? CatalogItemResult.Success)?.item
            val parsedAmount = service?.minimumPrice?.let { runCatching { BigDecimal(it) }.getOrNull() }
            if (service == null || parsedAmount == null || parsedAmount.signum() <= 0) {
                _uiState.value = PaymentCheckoutUiState.InvalidService
                return@launch
            }
            amount = parsedAmount
            savedCards = (savedCardRepository.list() as? SavedCardsResult.Success)?.cards.orEmpty()
            _uiState.value = PaymentCheckoutUiState.SelectingMethod(amount, savedCards)
        }
    }

    fun payWithPix() = createPayment(PaymentMethod.PIX, savedCardId = null)

    fun payWithSavedCard(savedCardId: String) = createPayment(PaymentMethod.SAVED_CARD, savedCardId)

    private fun createPayment(method: PaymentMethod, savedCardId: String?) {
        if (_uiState.value == PaymentCheckoutUiState.CreatingPayment || amount.signum() <= 0) return
        _uiState.value = PaymentCheckoutUiState.CreatingPayment
        viewModelScope.launch {
            when (
                val result = paymentRepository.createPayment(
                    CreatePaymentCommand(requestId, dispatchKind, amount, method, savedCardId),
                )
            ) {
                is CreatePaymentResult.Success -> applyPayment(result.payment)
                is CreatePaymentResult.Failure -> _uiState.value = PaymentCheckoutUiState.Failure
            }
        }
    }

    private fun applyPayment(payment: Payment) {
        when (payment.status) {
            PaymentStatus.APPROVED -> _uiState.value = PaymentCheckoutUiState.Approved
            PaymentStatus.REJECTED -> _uiState.value = PaymentCheckoutUiState.Declined
            PaymentStatus.EXPIRED -> _uiState.value = PaymentCheckoutUiState.Expired
            PaymentStatus.PENDING -> {
                _uiState.value = payment.pix?.let(PaymentCheckoutUiState::AwaitingPix)
                    ?: PaymentCheckoutUiState.AwaitingCardConfirmation
                observeApproval()
            }
            PaymentStatus.CANCELLED, PaymentStatus.REFUND_PENDING,
            PaymentStatus.REFUNDED, PaymentStatus.REFUND_FAILED,
            -> _uiState.value = PaymentCheckoutUiState.Failure
        }
    }

    private fun observeApproval() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            paymentRepository.observe(requestId).collect { event ->
                if (event !is PaymentEvent.StatusChanged) return@collect
                when (event.status) {
                    PaymentStatus.APPROVED -> _uiState.value = PaymentCheckoutUiState.Approved
                    PaymentStatus.REJECTED -> _uiState.value = PaymentCheckoutUiState.Declined
                    PaymentStatus.EXPIRED -> _uiState.value = PaymentCheckoutUiState.Expired
                    else -> Unit // still pending, or a post-dispatch refund state that can't occur here
                }
            }
        }
    }

    /** After an expired Pix code or a failure — generates a fresh charge, same [requestId]. */
    fun retry() = createPayment(PaymentMethod.PIX, savedCardId = null)

    fun chooseAnotherMethod() {
        observeJob?.cancel()
        _uiState.value = PaymentCheckoutUiState.SelectingMethod(amount, savedCards)
    }

    override fun onCleared() {
        observeJob?.cancel()
    }

    companion object {
        fun factory(
            catalogRepository: ServiceCatalogRepository,
            paymentRepository: PaymentRepository,
            savedCardRepository: SavedCardRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                PaymentCheckoutViewModel(
                    createSavedStateHandle(), catalogRepository, paymentRepository, savedCardRepository,
                )
            }
        }
    }
}

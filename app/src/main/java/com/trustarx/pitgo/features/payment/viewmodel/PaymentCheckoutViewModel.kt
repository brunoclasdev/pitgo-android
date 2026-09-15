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
    private val savedStateHandle: SavedStateHandle,
    private val catalogRepository: ServiceCatalogRepository,
    private val paymentRepository: PaymentRepository,
    private val savedCardRepository: SavedCardRepository,
) : ViewModel() {
    private val serviceId = savedStateHandle.get<String>(PitGoRoutes.PaymentServiceArg).orEmpty()
    private val dispatchKind = runCatching {
        MobileDispatchKind.valueOf(savedStateHandle.get<String>(PitGoRoutes.PaymentDispatchKindArg).orEmpty())
    }.getOrDefault(MobileDispatchKind.MOBILE_SEQUENTIAL)
    private val appointmentId: String? = savedStateHandle.get<String>(PitGoRoutes.PaymentAppointmentIdArg)
    private val quoteVersion: Long? = savedStateHandle.get<String>(PitGoRoutes.PaymentQuoteVersionArg)
        ?.toLongOrNull()
    private val quotedAmount: BigDecimal? = savedStateHandle.get<String>(PitGoRoutes.PaymentAmountArg)
        ?.let { runCatching { BigDecimal(it) }.getOrNull() }

    /**
     * The id the dispatch screen will reuse to start once this payment is approved. Normally a
     * fresh id (guincho móvel, paid before dispatch); `019-pittec-request-flow` instead supplies
     * the PitTec search's own id here, since that search/appointment already exists and the
     * payment must be tied to it (`arrive_pittec_appointment` checks
     * `payments.request_id = dispatch_search_id`).
     */
    val requestId: String = savedStateHandle.get<String>(PitGoRoutes.PaymentRequestIdArg)
        ?.takeIf(String::isUuid)
        ?: savedStateHandle.get<String>("payment_request_id")
        ?: UUID.randomUUID().toString().also { savedStateHandle["payment_request_id"] = it }

    private var amount: BigDecimal = BigDecimal.ZERO
    private var savedCards: List<SavedCard> = emptyList()
    private var observeJob: Job? = null
    private var currentPayment: Payment? = null
    private var screenActive = false
    private var cancelling = false

    private val _uiState = MutableStateFlow<PaymentCheckoutUiState>(PaymentCheckoutUiState.Loading)
    val uiState: StateFlow<PaymentCheckoutUiState> = _uiState.asStateFlow()

    init { loadService() }

    private fun loadService() {
        _uiState.value = PaymentCheckoutUiState.Loading
        viewModelScope.launch {
            when (val existing = paymentRepository.getStatus(requestId)) {
                is PaymentEvent.StatusChanged -> {
                    existing.payment?.let { amount = it.amount; applyPayment(it); observeApproval(); return@launch }
                }
                is PaymentEvent.Failure -> { _uiState.value = PaymentCheckoutUiState.Failure; return@launch }
                PaymentEvent.NotFound -> Unit
            }
            val service = if (dispatchKind == MobileDispatchKind.PITTEC_AUTO_SEQUENTIAL) null
                else (catalogRepository.service(serviceId) as? CatalogItemResult.Success)?.item
            val parsedAmount = quotedAmount ?: service?.minimumPrice?.let { runCatching { BigDecimal(it) }.getOrNull() }
            if ((dispatchKind == MobileDispatchKind.PITTEC_AUTO_SEQUENTIAL &&
                    (appointmentId == null || quoteVersion == null)) ||
                parsedAmount == null || parsedAmount.signum() <= 0
            ) {
                _uiState.value = PaymentCheckoutUiState.InvalidService
                return@launch
            }
            amount = parsedAmount
            savedCards = (savedCardRepository.list() as? SavedCardsResult.Success)?.cards.orEmpty()
            _uiState.value = PaymentCheckoutUiState.SelectingMethod(amount, savedCards)
        }
    }

    fun payWithPix() = createPayment(PaymentMethod.PIX, savedCardId = null)

    fun payWithSavedCard(savedCardId: String) {
        if (_uiState.value !is PaymentCheckoutUiState.SelectingMethod) return
        _uiState.value = PaymentCheckoutUiState.Loading
        viewModelScope.launch {
            when (val result = savedCardRepository.tokenizationContext(savedCardId)) {
                is com.trustarx.pitgo.features.paymentmethods.model.CardContextResult.Success ->
                    _uiState.value = PaymentCheckoutUiState.ConfirmingCard(result.context)
                is com.trustarx.pitgo.features.paymentmethods.model.CardContextResult.Failure ->
                    _uiState.value = PaymentCheckoutUiState.Failure
            }
        }
    }
    fun confirmCard(token: String) {
        val state = _uiState.value as? PaymentCheckoutUiState.ConfirmingCard ?: return
        _uiState.value = PaymentCheckoutUiState.SelectingMethod(amount, savedCards)
        createPayment(PaymentMethod.SAVED_CARD, state.context.savedCardId, token)
    }

    private fun createPayment(method: PaymentMethod, savedCardId: String?, token: String? = null) {
        if (_uiState.value !is PaymentCheckoutUiState.SelectingMethod || amount.signum() <= 0) return
        _uiState.value = PaymentCheckoutUiState.CreatingPayment
        viewModelScope.launch {
            when (
                val result = paymentRepository.createPayment(
                    CreatePaymentCommand(
                        requestId = requestId,
                        serviceId = serviceId,
                        dispatchKind = dispatchKind,
                        amount = amount,
                        method = method,
                        appointmentId = appointmentId,
                        quoteVersion = quoteVersion,
                        savedCardId = savedCardId,
                        cardSecurityCodeToken = token,
                    ),
                )
            ) {
                is CreatePaymentResult.Success -> { applyPayment(result.payment); observeApproval() }
                is CreatePaymentResult.Failure -> {
                    _uiState.value = PaymentCheckoutUiState.Verifying
                    observeApproval()
                }
            }
        }
    }

    private fun applyPayment(payment: Payment) {
        if (currentPayment?.id == payment.id && payment.revision < (currentPayment?.revision ?: 0)) return
        currentPayment = payment
        amount = payment.amount
        if (payment.cancelRequested || (payment.status == PaymentStatus.APPROVED && payment.serviceId == null)
            || payment.status in setOf(PaymentStatus.REFUND_PENDING, PaymentStatus.REFUNDED, PaymentStatus.REFUND_FAILED)) {
            _uiState.value = PaymentCheckoutUiState.FinancialStatus(payment)
            return
        }
        if (payment.syncState != com.trustarx.pitgo.features.payment.model.PaymentSyncState.CURRENT) {
            _uiState.value = PaymentCheckoutUiState.Verifying
            return
        }
        when (payment.status) {
            PaymentStatus.APPROVED -> _uiState.value = if (payment.canProceed) PaymentCheckoutUiState.Approved else PaymentCheckoutUiState.Verifying
            PaymentStatus.REJECTED -> _uiState.value = PaymentCheckoutUiState.Declined
            PaymentStatus.EXPIRED -> _uiState.value = PaymentCheckoutUiState.Expired
            PaymentStatus.PENDING -> {
                _uiState.value = payment.pix?.let(PaymentCheckoutUiState::AwaitingPix)
                    ?: PaymentCheckoutUiState.AwaitingCardConfirmation
            }
            PaymentStatus.CANCELLED, PaymentStatus.REFUND_PENDING,
            PaymentStatus.REFUNDED, PaymentStatus.REFUND_FAILED,
            -> _uiState.value = PaymentCheckoutUiState.FinancialStatus(payment)
        }
    }

    private fun observeApproval() {
        if (!screenActive) return
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            paymentRepository.observe(requestId).collect { event ->
                if (event is PaymentEvent.StatusChanged) event.payment?.let(::applyPayment)
                else if (event is PaymentEvent.NotFound && _uiState.value == PaymentCheckoutUiState.Verifying)
                    _uiState.value = PaymentCheckoutUiState.Failure
            }
        }
    }

    fun onActive() { screenActive = true; observeApproval() }
    fun onInactive() { screenActive = false; observeJob?.cancel(); observeJob = null }

    fun cancelPayment(onNoPayment: () -> Unit) {
        if (cancelling || _uiState.value == PaymentCheckoutUiState.CreatingPayment) return
        cancelling = true
        viewModelScope.launch {
            try {
                when (val existing = paymentRepository.getStatus(requestId)) {
                    PaymentEvent.NotFound -> onNoPayment()
                    is PaymentEvent.Failure -> _uiState.value = PaymentCheckoutUiState.Failure
                    is PaymentEvent.StatusChanged -> {
                        val id = existing.paymentId ?: return@launch
                        when (val result = paymentRepository.cancel(id)) {
                            is CreatePaymentResult.Success -> { applyPayment(result.payment); observeApproval() }
                            is CreatePaymentResult.Failure -> { _uiState.value = PaymentCheckoutUiState.Verifying; observeApproval() }
                        }
                    }
                }
            } finally { cancelling = false }
        }
    }

    /** After an expired Pix code or a failure — generates a fresh charge, same [requestId]. */
    fun retry() = chooseAnotherMethod()

    fun chooseAnotherMethod() {
        if (_uiState.value == PaymentCheckoutUiState.CreatingPayment) return
        viewModelScope.launch {
            when (val result = paymentRepository.getStatus(requestId)) {
                is PaymentEvent.StatusChanged -> result.payment?.let {
                    if (it.blocksNewAttempt) { applyPayment(it); observeApproval() }
                    else { observeJob?.cancel()
                        savedCards = (savedCardRepository.list() as? SavedCardsResult.Success)?.cards.orEmpty()
                        _uiState.value = PaymentCheckoutUiState.SelectingMethod(amount, savedCards) }
                }
                PaymentEvent.NotFound -> loadService()
                is PaymentEvent.Failure -> _uiState.value = PaymentCheckoutUiState.Failure
            }
        }
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

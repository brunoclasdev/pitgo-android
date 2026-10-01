package com.trustarx.pitgo.features.payment.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.trustarx.pitgo.BuildConfig
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
import com.trustarx.pitgo.features.payment.model.PaymentFailure
import com.trustarx.pitgo.features.payment.model.PaymentMethod
import com.trustarx.pitgo.features.payment.model.PaymentStatus
import com.trustarx.pitgo.features.payment.repository.PaymentGateway
import com.trustarx.pitgo.features.paymentmethods.model.SavedCard
import com.trustarx.pitgo.features.paymentmethods.model.SavedCardsResult
import com.trustarx.pitgo.features.paymentmethods.repository.SavedCardRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.math.BigDecimal
import java.util.UUID
import kotlin.random.Random

/**
 * Checkout shared by the MOBILE_SEQUENTIAL and PitTec flows (spec 018): reached from Home instead
 * of the dispatch screen itself, generates the `request_id` those flows will later start with,
 * and only lets the caller proceed once a payment is `APPROVED` (FR-009/FR-014).
 */
class PaymentCheckoutViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val catalogRepository: ServiceCatalogRepository,
    private val paymentRepository: PaymentGateway,
    private val savedCardRepository: SavedCardRepository,
) : ViewModel() {
    private val serviceId = savedStateHandle.get<String>(PitGoRoutes.PaymentServiceArg).orEmpty()
    private val dispatchKind = runCatching {
        MobileDispatchKind.valueOf(savedStateHandle.get<String>(PitGoRoutes.PaymentDispatchKindArg).orEmpty())
    }.getOrDefault(MobileDispatchKind.MOBILE_SEQUENTIAL)
    val directed get() = dispatchKind==MobileDispatchKind.GUINCHO_DIRECT
    private val homol = BuildConfig.ENVIRONMENT_ID == "homol"
    private fun homolAmount(): BigDecimal = savedStateHandle.get<String>("homol_payment_amount")
        ?.toBigDecimalOrNull()
        ?: BigDecimal.valueOf(Random.nextLong(100, 201), 2).also {
            savedStateHandle["homol_payment_amount"] = it.toPlainString()
        }
    private val appointmentId: String? = savedStateHandle.get<String>(PitGoRoutes.PaymentAppointmentIdArg)
    private val quoteVersion: Long? = savedStateHandle.get<String>(PitGoRoutes.PaymentQuoteVersionArg)
        ?.toLongOrNull()
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
    private var paymentOrderAmount: BigDecimal = BigDecimal.ZERO
    private var savedCards: List<SavedCard> = emptyList()
    private var observeJob: Job? = null
    private var currentPayment: Payment? = null
    private var screenActive = false
    private var cancelling = false

    private val _uiState = MutableStateFlow<PaymentCheckoutUiState>(PaymentCheckoutUiState.Loading)
    val uiState: StateFlow<PaymentCheckoutUiState> = _uiState.asStateFlow()

    val simulated = paymentRepository.simulated
    fun simulate(status: PaymentStatus) {
        viewModelScope.launch {
            val event = paymentRepository.simulate(requestId, status)
            (event as? PaymentEvent.StatusChanged)?.payment?.let(::applyPayment)
        }
    }
    fun simulateFailure() { _uiState.value = PaymentCheckoutUiState.Failure }

    init { loadService() }

    private fun loadService() {
        observeJob?.cancel()
        observeJob = null
        currentPayment = null
        _uiState.value = PaymentCheckoutUiState.Loading
        viewModelScope.launch {
            if (directed && (appointmentId?.isUuid() != true || quoteVersion == null || quoteVersion <= 0)) {
                _uiState.value = PaymentCheckoutUiState.InvalidService; return@launch
            }
            when (val existing = paymentRepository.getStatus(requestId)) {
                is PaymentEvent.StatusChanged -> {
                    existing.payment?.let {
                        paymentOrderAmount = it.amount
                        amount = if (!homol && dispatchKind == MobileDispatchKind.PITTEC_AUTO_SEQUENTIAL) {
                            paymentRepository.quoteTotal(requestId, appointmentId, quoteVersion) ?: it.amount
                        } else it.amount
                        applyPayment(it)
                        observeApproval()
                        return@launch
                    }
                }
                is PaymentEvent.Failure -> { _uiState.value = PaymentCheckoutUiState.Failure; return@launch }
                PaymentEvent.NotFound -> Unit
            }
            if (simulated) {
                amount = if(dispatchKind == MobileDispatchKind.GUINCHO_DIRECT)
                    savedStateHandle.get<String>(PitGoRoutes.PaymentAmountArg)?.toBigDecimalOrNull() ?: BigDecimal.ZERO
                    else TEST_PAYMENT_AMOUNT
                if(amount.signum()<=0) { _uiState.value=PaymentCheckoutUiState.InvalidService; return@launch }
                paymentOrderAmount = amount
                savedCards = (savedCardRepository.list() as? SavedCardsResult.Success)?.cards.orEmpty()
                _uiState.value = PaymentCheckoutUiState.SelectingMethod(amount, savedCards)
                return@launch
            }
            val service = if (dispatchKind != MobileDispatchKind.MOBILE_SEQUENTIAL) null
                else (catalogRepository.service(serviceId) as? CatalogItemResult.Success)?.item
            val parsedAmount = if (dispatchKind != MobileDispatchKind.MOBILE_SEQUENTIAL) {
                paymentRepository.quoteTotal(requestId, appointmentId, quoteVersion)
            } else TEST_PAYMENT_AMOUNT.takeIf { service != null }
            if ((dispatchKind == MobileDispatchKind.PITTEC_AUTO_SEQUENTIAL &&
                    (appointmentId == null || quoteVersion == null)) ||
                parsedAmount == null || parsedAmount.signum() <= 0
            ) {
                _uiState.value = PaymentCheckoutUiState.InvalidService
                return@launch
            }
            amount = if (homol) homolAmount() else parsedAmount
            paymentOrderAmount = if (homol) amount else if (dispatchKind == MobileDispatchKind.PITTEC_AUTO_SEQUENTIAL) {
                TEST_PAYMENT_AMOUNT
            } else parsedAmount
            savedCards = (savedCardRepository.list() as? SavedCardsResult.Success)?.cards.orEmpty()
            _uiState.value = PaymentCheckoutUiState.SelectingMethod(amount, savedCards)
        }
    }

    fun payWithPix() = createPayment(PaymentMethod.PIX, savedCardId = null)

    fun payWithSavedCard(savedCardId: String) {
        if (simulated) { createPayment(PaymentMethod.SAVED_CARD, savedCardId); return }
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
            // The Edge Function has its own bounded provider call, but keep a UI
            // deadline as well so a stalled network/function request cannot leave
            // the checkout showing an endless spinner.
            val result = withTimeoutOrNull(CREATE_PAYMENT_UI_TIMEOUT_MILLIS) {
                paymentRepository.createPayment(
                    CreatePaymentCommand(
                        requestId = requestId,
                        serviceId = serviceId,
                        dispatchKind = dispatchKind,
                        amount = paymentOrderAmount,
                        method = method,
                        appointmentId = appointmentId,
                        quoteVersion = quoteVersion,
                        savedCardId = savedCardId,
                        cardSecurityCodeToken = token,
                    ),
                )
            } ?: CreatePaymentResult.Failure(com.trustarx.pitgo.features.payment.model.PaymentFailure.TIMEOUT)
            when (result) {
                is CreatePaymentResult.Success -> { applyPayment(result.payment); observeApproval() }
                is CreatePaymentResult.Failure -> {
                    // Only an ambiguous provider/network result can have created a payment
                    // without returning it. Deterministic failures must be actionable and must
                    // not be presented as an indefinitely pending payment.
                    if (directed && result.reason in setOf(
                        com.trustarx.pitgo.features.payment.model.PaymentFailure.QUOTE_EXPIRED,
                        com.trustarx.pitgo.features.payment.model.PaymentFailure.QUOTE_CONFLICT,
                        com.trustarx.pitgo.features.payment.model.PaymentFailure.INVALID_PAYMENT_CONTEXT,
                    )) {
                        _uiState.value = PaymentCheckoutUiState.QuoteChanged
                    } else if (result.reason in setOf(
                            com.trustarx.pitgo.features.payment.model.PaymentFailure.TIMEOUT,
                            com.trustarx.pitgo.features.payment.model.PaymentFailure.UNKNOWN,
                        )) {
                        _uiState.value = PaymentCheckoutUiState.Verifying
                        observeApproval()
                    } else {
                        _uiState.value = PaymentCheckoutUiState.Failure
                    }
                }
            }
        }
    }

    private fun applyPayment(payment: Payment) {
        if (currentPayment?.id == payment.id && payment.revision < (currentPayment?.revision ?: 0)) return
        currentPayment = payment
        if (homol || dispatchKind != MobileDispatchKind.PITTEC_AUTO_SEQUENTIAL) amount = payment.amount
        if (payment.cancelRequested || (payment.status == PaymentStatus.APPROVED && payment.serviceId == null)
            || payment.status in setOf(PaymentStatus.REFUND_PENDING, PaymentStatus.REFUNDED, PaymentStatus.REFUND_FAILED)) {
            _uiState.value = PaymentCheckoutUiState.FinancialStatus(payment)
            return
        }
        val pixReadyWhileProviderSyncing = payment.status == PaymentStatus.PENDING &&
            payment.method == PaymentMethod.PIX && payment.pix?.isComplete == true
        if (payment.syncState != com.trustarx.pitgo.features.payment.model.PaymentSyncState.CURRENT &&
            !pixReadyWhileProviderSyncing) {
            _uiState.value = PaymentCheckoutUiState.Verifying
            return
        }
        when (payment.status) {
            PaymentStatus.APPROVED -> _uiState.value = if (payment.canProceed) PaymentCheckoutUiState.Approved else PaymentCheckoutUiState.Verifying
            PaymentStatus.REJECTED -> _uiState.value = PaymentCheckoutUiState.Declined
            PaymentStatus.EXPIRED -> _uiState.value = PaymentCheckoutUiState.Expired
            PaymentStatus.PENDING -> {
                _uiState.value = when {
                    payment.pix != null && payment.pix.isComplete ->
                        PaymentCheckoutUiState.AwaitingPix(payment.amount, payment.pix)
                    payment.method == PaymentMethod.PIX -> PaymentCheckoutUiState.Verifying
                    else -> PaymentCheckoutUiState.AwaitingCardConfirmation
                }
            }
            PaymentStatus.CANCELLED, PaymentStatus.REFUND_PENDING,
            PaymentStatus.REFUNDED, PaymentStatus.REFUND_FAILED,
            -> _uiState.value = PaymentCheckoutUiState.FinancialStatus(payment)
        }
    }

    private fun observeApproval() {
        // Checkout status must not depend on private Realtime authorization. A delayed provider
        // response is recovered through the authenticated status RPC, which is also safe after
        // process recreation and does not create another payment attempt.
        if (!screenActive || (currentPayment == null && _uiState.value != PaymentCheckoutUiState.Verifying)) return
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            paymentRepository.observe(requestId).takeWhile { event ->
                when (event) {
                    is PaymentEvent.StatusChanged -> event.payment?.let(::applyPayment)
                    PaymentEvent.NotFound -> Unit
                    is PaymentEvent.Failure -> if (event.reason in setOf(PaymentFailure.SESSION_EXPIRED, PaymentFailure.CONTRACT)) {
                        _uiState.value = PaymentCheckoutUiState.Failure
                    } // Keep waiting through transient connectivity/read failures.
                }
                screenActive && (_uiState.value is PaymentCheckoutUiState.Verifying ||
                    _uiState.value is PaymentCheckoutUiState.AwaitingPix ||
                    _uiState.value is PaymentCheckoutUiState.AwaitingCardConfirmation)
            }.collect()
        }
    }

    fun onActive() { screenActive = true; observeApproval() }
    fun onInactive() { screenActive = false; observeJob?.cancel(); observeJob = null }

    fun cancelPayment(onNoPayment: () -> Unit) {
        if (cancelling || _uiState.value == PaymentCheckoutUiState.CreatingPayment) return
        cancelling = true
        _uiState.value = PaymentCheckoutUiState.Cancelling
        viewModelScope.launch {
            try {
                if (directed && (appointmentId?.isUuid() != true || quoteVersion == null || quoteVersion <= 0)) {
                _uiState.value = PaymentCheckoutUiState.InvalidService; return@launch
            }
            when (val existing = paymentRepository.getStatus(requestId)) {
                    PaymentEvent.NotFound -> if(!directed || paymentRepository.cancelUnpaidGuincho(requestId)) onNoPayment()
                        else _uiState.value=PaymentCheckoutUiState.Failure
                    is PaymentEvent.Failure -> _uiState.value = PaymentCheckoutUiState.Failure
                    is PaymentEvent.StatusChanged -> {
                        val id = existing.paymentId ?: run { onNoPayment(); return@launch }
                        when (val result = paymentRepository.cancel(id)) {
                            is CreatePaymentResult.Success -> {
                                applyPayment(result.payment)
                                observeApproval()
                                if (result.payment.cancelRequested || result.payment.status in setOf(
                                        PaymentStatus.CANCELLED,
                                        PaymentStatus.REFUND_PENDING,
                                        PaymentStatus.REFUNDED,
                                        PaymentStatus.REFUND_FAILED,
                                    )) onNoPayment()
                            }
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
        private val TEST_PAYMENT_AMOUNT = BigDecimal("1.00")
        private const val CREATE_PAYMENT_UI_TIMEOUT_MILLIS = 20_000L

        fun factory(
            catalogRepository: ServiceCatalogRepository,
            paymentRepository: PaymentGateway,
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

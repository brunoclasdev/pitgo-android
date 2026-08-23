package com.trustarx.pitgo.features.providerregistration.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.trustarx.pitgo.core.account.model.ProviderOnboardingSession
import com.trustarx.pitgo.core.auth.model.AppSessionState
import com.trustarx.pitgo.core.auth.repository.SessionRepository
import com.trustarx.pitgo.features.providerregistration.model.AddressVerificationState
import com.trustarx.pitgo.features.providerregistration.model.AddressDraft
import com.trustarx.pitgo.features.providerregistration.model.CompanyDraft
import com.trustarx.pitgo.features.providerregistration.model.LoadState
import com.trustarx.pitgo.features.providerregistration.model.OfferDraft
import com.trustarx.pitgo.features.providerregistration.model.OfferField
import com.trustarx.pitgo.features.providerregistration.model.ProviderCatalogResult
import com.trustarx.pitgo.features.providerregistration.model.ProviderEditingContent
import com.trustarx.pitgo.features.providerregistration.model.ProviderEntryResult
import com.trustarx.pitgo.features.providerregistration.model.ProviderFailure
import com.trustarx.pitgo.features.providerregistration.model.ProviderField
import com.trustarx.pitgo.features.providerregistration.model.ProviderFieldError
import com.trustarx.pitgo.features.providerregistration.model.ProviderFocusRequest
import com.trustarx.pitgo.features.providerregistration.model.ProviderRegistration
import com.trustarx.pitgo.features.providerregistration.model.ProviderRegistrationDraft
import com.trustarx.pitgo.features.providerregistration.model.ProviderRegistrationResult
import com.trustarx.pitgo.features.providerregistration.model.ProviderRegistrationUiState
import com.trustarx.pitgo.features.providerregistration.model.ProviderLocationMode
import com.trustarx.pitgo.features.providerregistration.model.ProviderSubmissionState
import com.trustarx.pitgo.features.providerregistration.repository.ProviderRegistrationRepository
import com.trustarx.pitgo.features.providerregistration.repository.AddressVerificationResult
import com.trustarx.pitgo.features.providerregistration.repository.ProviderAddressVerificationRepository
import com.trustarx.pitgo.features.providerregistration.validation.ProviderRegistrationValidation
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ProviderRegistrationViewModel(
    private val expectedUserId: String,
    private val initialEmail: String,
    private val repository: ProviderRegistrationRepository,
    private val addressVerificationRepository: ProviderAddressVerificationRepository,
    private val sessionRepository: SessionRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<ProviderRegistrationUiState>(
        ProviderRegistrationUiState.Loading,
    )
    val uiState: StateFlow<ProviderRegistrationUiState> = _uiState.asStateFlow()

    private var nextOfferId = 1L
    private var focusRequestId = 0L
    private var bootstrapJob: Job? = null
    private var submitJob: Job? = null
    private var verificationJob: Job? = null

    init {
        viewModelScope.launch {
            sessionRepository.state.collectLatest { state ->
                val currentUserId = (state as? AppSessionState.Authenticated)?.userId
                if (currentUserId != expectedUserId) expireSession()
            }
        }
        bootstrap()
    }

    fun retryStartup() = bootstrap()

    fun onFieldChanged(field: ProviderField, value: String) = updateEditing { content ->
        val normalized = when (field) {
            ProviderField.Phone -> ProviderRegistrationValidation.normalizePhone(value)
            ProviderField.Cnpj -> ProviderRegistrationValidation.normalizeCnpj(value)
            ProviderField.PostalCode -> ProviderRegistrationValidation.normalizePostalCode(value)
            else -> value
        }
        val addressChanged = field in ADDRESS_FIELDS
        content.copy(
            draft = content.draft.withField(field, normalized),
            addressVerification = if (addressChanged) AddressVerificationState.Unverified
                else content.addressVerification,
            errors = content.errors - field,
            submission = ProviderSubmissionState.Idle,
        )
    }

    fun onLocationModeSelected(mode: ProviderLocationMode) = updateEditing { content ->
        content.copy(
            draft = content.draft.copy(locationMode = mode),
            errors = content.errors - ProviderField.LocationMode,
        )
    }

    fun verifyAddress() {
        if (verificationJob != null) return
        val content = (_uiState.value as? ProviderRegistrationUiState.Editing)?.content ?: return
        verificationJob = viewModelScope.launch {
            try {
                _uiState.updateEditing { it.copy(addressVerification = AddressVerificationState.Verifying) }
                when (val result = addressVerificationRepository.verify(content.draft.address)) {
                    is AddressVerificationResult.Verified -> _uiState.updateEditing {
                        it.copy(
                            addressVerification = result.state,
                            errors = it.errors - ProviderField.LocationVerification,
                        )
                    }
                    is AddressVerificationResult.Diverged -> Unit
                    is AddressVerificationResult.Failure -> _uiState.updateEditing {
                        it.copy(addressVerification = AddressVerificationState.Failure(result.reason))
                    }
                }
            } finally { verificationJob = null }
        }
    }

    fun onOfferFieldChanged(localId: Long, field: OfferField, value: String) = updateEditing { content ->
        content.copy(
            draft = content.draft.updateOffer(localId) { offer ->
                when (field) {
                    OfferField.PRICE -> offer.copy(priceInput = value.take(18))
                    OfferField.DURATION -> offer.copy(
                        durationInput = value.filter(Char::isDigit).take(5),
                    )
                    OfferField.DESCRIPTION -> offer.copy(description = value)
                    OfferField.CATEGORY, OfferField.SERVICE -> offer
                }
            },
            errors = content.errors - ProviderField.Offer(localId, field),
            submission = ProviderSubmissionState.Idle,
        )
    }

    fun onCategorySelected(localId: Long, categoryId: String) {
        updateEditing { content ->
            content.copy(
                draft = content.draft.updateOffer(localId) {
                    it.copy(categoryId = categoryId, serviceId = null)
                },
                errors = content.errors - ProviderField.Offer(localId, OfferField.CATEGORY) -
                    ProviderField.Offer(localId, OfferField.SERVICE),
                submission = ProviderSubmissionState.Idle,
            )
        }
        loadServices(categoryId)
    }

    fun onServiceSelected(localId: Long, serviceId: String) = updateEditing { content ->
        content.copy(
            draft = content.draft.updateOffer(localId) { it.copy(serviceId = serviceId) },
            errors = content.errors - ProviderField.Offer(localId, OfferField.SERVICE),
            submission = ProviderSubmissionState.Idle,
        )
    }

    fun onFreeChanged(localId: Long, isFree: Boolean) = updateEditing { content ->
        content.copy(
            draft = content.draft.updateOffer(localId) {
                it.copy(isFree = isFree, priceInput = if (isFree) "" else it.priceInput)
            },
            errors = content.errors - ProviderField.Offer(localId, OfferField.PRICE),
            submission = ProviderSubmissionState.Idle,
        )
    }

    fun addOffer() = updateEditing { content ->
        content.copy(
            draft = content.draft.copy(
                offers = content.draft.offers + OfferDraft(localId = nextOfferId++),
            ),
            errors = content.errors - ProviderField.Offers,
            submission = ProviderSubmissionState.Idle,
        )
    }

    fun removeOffer(localId: Long) = updateEditing { content ->
        content.copy(
            draft = content.draft.copy(
                offers = content.draft.offers.filterNot { it.localId == localId },
            ),
            errors = content.errors.filterKeys {
                it !is ProviderField.Offer || it.localId != localId
            },
            submission = ProviderSubmissionState.Idle,
        )
    }

    fun retryCatalog(categoryId: String? = null) {
        if (categoryId != null) loadServices(categoryId) else bootstrap()
    }

    fun submit() {
        val editing = (_uiState.value as? ProviderRegistrationUiState.Editing)?.content ?: return
        if (editing.submission == ProviderSubmissionState.Submitting || submitJob != null) return
        val services = editing.servicesByCategory.values.flatMap { state ->
            (state as? LoadState.Content)?.value.orEmpty()
        }
        val validation = ProviderRegistrationValidation.validate(
            editing.draft, services, editing.addressVerification,
        )
        val registration = validation.registration
        if (registration == null) {
            val formErrors = validation.errors - ProviderField.LocationVerification
            if (formErrors.isNotEmpty()) {
                requestFocus(formErrors.keys.first(), formErrors)
            } else {
                geocodeAndSubmit(editing)
            }
            return
        }
        startRegistration(registration)
    }

    private fun geocodeAndSubmit(editing: ProviderEditingContent) {
        _uiState.updateEditing {
            it.copy(
                errors = emptyMap(),
                addressVerification = AddressVerificationState.Verifying,
                submission = ProviderSubmissionState.Submitting,
            )
        }
        submitJob = viewModelScope.launch {
            try {
                when (val geocode = addressVerificationRepository.verify(editing.draft.address)) {
                    is AddressVerificationResult.Verified -> {
                        _uiState.updateEditing {
                            it.copy(addressVerification = geocode.state)
                        }
                        val current = (_uiState.value as? ProviderRegistrationUiState.Editing)
                            ?.content ?: return@launch
                        val services = current.servicesByCategory.values.flatMap { state ->
                            (state as? LoadState.Content)?.value.orEmpty()
                        }
                        val validation = ProviderRegistrationValidation.validate(
                            current.draft, services, current.addressVerification,
                        )
                        val registration = validation.registration
                        if (registration == null) {
                            requestFocus(validation.firstInvalidField, validation.errors)
                            _uiState.updateEditing {
                                it.copy(submission = ProviderSubmissionState.Idle)
                            }
                            return@launch
                        }
                        performRegistration(registration)
                    }
                    is AddressVerificationResult.Failure -> _uiState.updateEditing {
                        it.copy(
                            addressVerification = AddressVerificationState.Failure(geocode.reason),
                            submission = ProviderSubmissionState.Failure(geocode.reason),
                        )
                    }
                    is AddressVerificationResult.Diverged -> Unit
                }
            } finally {
                submitJob = null
            }
        }
    }

    private fun startRegistration(registration: ProviderRegistration) {
        _uiState.updateEditing {
            it.copy(errors = emptyMap(), submission = ProviderSubmissionState.Submitting)
        }
        submitJob = viewModelScope.launch {
            try {
                performRegistration(registration)
            } finally {
                submitJob = null
            }
        }
    }

    private suspend fun performRegistration(registration: ProviderRegistration) {
        when (val result = repository.submit(expectedUserId, registration)) {
            is ProviderRegistrationResult.Success -> ifSessionCurrent {
                _uiState.value = ProviderRegistrationUiState.Success(result.summary)
            }
            is ProviderRegistrationResult.AlreadyRegistered -> ifSessionCurrent {
                _uiState.value = ProviderRegistrationUiState.AlreadyRegistered(result.summary)
            }
            ProviderRegistrationResult.EmailUnavailable -> remoteFieldError(ProviderField.Email)
            ProviderRegistrationResult.CnpjUnavailable -> remoteFieldError(ProviderField.Cnpj)
            ProviderRegistrationResult.DuplicateService -> remoteFieldError(ProviderField.Offers)
            is ProviderRegistrationResult.Failure -> applyFailure(result.reason)
        }
    }

    fun discardDraft() {
        val current = (_uiState.value as? ProviderRegistrationUiState.Editing)?.content ?: return
        _uiState.value = ProviderRegistrationUiState.Editing(
            current.copy(
                draft = current.baselineDraft,
                errors = emptyMap(),
                submission = ProviderSubmissionState.Idle,
            ),
        )
    }

    private fun bootstrap() {
        if (bootstrapJob != null || !isSessionCurrent()) return
        _uiState.value = ProviderRegistrationUiState.Loading
        bootstrapJob = viewModelScope.launch {
            try {
                when (val result = repository.loadEntry(expectedUserId)) {
                    is ProviderEntryResult.AlreadyRegistered -> ifSessionCurrent {
                        _uiState.value = ProviderRegistrationUiState.AlreadyRegistered(result.summary)
                    }
                    is ProviderEntryResult.Ready -> ifSessionCurrent {
                        val initialDraft = ProviderRegistrationDraft(
                            company = CompanyDraft(email = initialEmail),
                        )
                        _uiState.value = ProviderRegistrationUiState.Editing(
                            ProviderEditingContent(
                                draft = initialDraft,
                                baselineDraft = initialDraft,
                                categories = if (result.categories.isEmpty()) LoadState.Empty
                                else LoadState.Content(result.categories),
                            ),
                        )
                    }
                    is ProviderEntryResult.Failure -> applyStartupFailure(result.reason)
                }
            } finally {
                bootstrapJob = null
            }
        }
    }

    private fun loadServices(categoryId: String) {
        val editing = (_uiState.value as? ProviderRegistrationUiState.Editing)?.content ?: return
        if (editing.servicesByCategory[categoryId] is LoadState.Content ||
            editing.servicesByCategory[categoryId] == LoadState.Loading
        ) return
        _uiState.updateEditing {
            it.copy(servicesByCategory = it.servicesByCategory + (categoryId to LoadState.Loading))
        }
        viewModelScope.launch {
            when (val result = repository.loadServices(categoryId)) {
                is ProviderCatalogResult.Success -> ifSessionCurrent {
                    _uiState.updateEditing { content ->
                        val state = if (result.items.isEmpty()) LoadState.Empty
                        else LoadState.Content(result.items)
                        val validIds = result.items.mapTo(mutableSetOf()) { it.id }
                        content.copy(
                            draft = content.draft.copy(
                                offers = content.draft.offers.map { offer ->
                                    if (offer.categoryId == categoryId &&
                                        offer.serviceId != null && offer.serviceId !in validIds
                                    ) offer.copy(serviceId = null) else offer
                                },
                            ),
                            servicesByCategory = content.servicesByCategory + (categoryId to state),
                        )
                    }
                }
                is ProviderCatalogResult.Failure -> ifSessionCurrent {
                    if (result.reason == ProviderFailure.SessionExpired) expireSession()
                    else _uiState.updateEditing {
                        it.copy(
                            servicesByCategory = it.servicesByCategory + (
                                categoryId to LoadState.Failure(result.reason)
                                ),
                        )
                    }
                }
            }
        }
    }

    private fun requestFocus(
        field: ProviderField?,
        errors: Map<ProviderField, ProviderFieldError>,
    ) {
        _uiState.updateEditing {
            focusRequestId += 1
            it.copy(
                errors = errors,
                focusRequest = field?.let { target -> ProviderFocusRequest(focusRequestId, target) },
                submission = ProviderSubmissionState.Idle,
            )
        }
    }

    private fun remoteFieldError(field: ProviderField) {
        if (!isSessionCurrent()) return
        requestFocus(field, mapOf(field to ProviderFieldError.UNAVAILABLE))
    }

    private fun applyStartupFailure(reason: ProviderFailure) {
        if (!isSessionCurrent() || reason == ProviderFailure.SessionExpired) {
            expireSession()
        } else {
            _uiState.value = ProviderRegistrationUiState.StartupFailure(reason)
        }
    }

    private fun applyFailure(reason: ProviderFailure) {
        if (!isSessionCurrent() || reason == ProviderFailure.SessionExpired) {
            expireSession()
        } else {
            _uiState.updateEditing {
                it.copy(submission = ProviderSubmissionState.Failure(reason))
            }
        }
    }

    private fun expireSession() {
        bootstrapJob?.cancel()
        submitJob?.cancel()
        bootstrapJob = null
        submitJob = null
        _uiState.value = ProviderRegistrationUiState.SessionExpired
    }

    private fun isSessionCurrent(): Boolean =
        (sessionRepository.state.value as? AppSessionState.Authenticated)?.userId ==
            expectedUserId

    private inline fun ifSessionCurrent(block: () -> Unit) {
        if (isSessionCurrent()) block() else expireSession()
    }

    private inline fun updateEditing(
        transform: (ProviderEditingContent) -> ProviderEditingContent,
    ) = _uiState.updateEditing(transform)

    private fun ProviderRegistrationDraft.withField(
        field: ProviderField,
        value: String,
    ): ProviderRegistrationDraft = when (field) {
        ProviderField.Name -> copy(company = company.copy(name = value))
        ProviderField.Description -> copy(company = company.copy(description = value))
        ProviderField.Email -> copy(company = company.copy(email = value))
        ProviderField.Phone -> copy(company = company.copy(phone = value))
        ProviderField.Cnpj -> copy(company = company.copy(cnpj = value))
        ProviderField.PostalCode -> copy(address = address.copy(postalCode = value))
        ProviderField.Street -> copy(address = address.copy(street = value))
        ProviderField.Number -> copy(address = address.copy(number = value))
        ProviderField.Complement -> copy(address = address.copy(complement = value))
        ProviderField.Neighborhood -> copy(address = address.copy(neighborhood = value))
        ProviderField.City -> copy(address = address.copy(city = value))
        ProviderField.State -> copy(address = address.copy(state = value.uppercase().take(2)))
        ProviderField.Latitude, ProviderField.Longitude, ProviderField.LocationMode,
        ProviderField.LocationVerification, ProviderField.Offers, is ProviderField.Offer -> this
    }

    private fun ProviderRegistrationDraft.updateOffer(
        localId: Long,
        transform: (OfferDraft) -> OfferDraft,
    ) = copy(offers = offers.map { if (it.localId == localId) transform(it) else it })

    private inline fun MutableStateFlow<ProviderRegistrationUiState>.updateEditing(
        transform: (ProviderEditingContent) -> ProviderEditingContent,
    ) = update { state ->
        val content = (state as? ProviderRegistrationUiState.Editing)?.content ?: return@update state
        ProviderRegistrationUiState.Editing(transform(content))
    }

    companion object {
        fun factory(
            account: ProviderOnboardingSession,
            repository: ProviderRegistrationRepository,
            addressVerificationRepository: ProviderAddressVerificationRepository,
            sessionRepository: SessionRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(ProviderRegistrationViewModel::class.java))
                return ProviderRegistrationViewModel(
                    expectedUserId = account.userId,
                    initialEmail = account.authEmail,
                    repository = repository,
                    addressVerificationRepository = addressVerificationRepository,
                    sessionRepository = sessionRepository,
                ) as T
            }
        }

        private val ADDRESS_FIELDS = setOf(
            ProviderField.PostalCode, ProviderField.Street, ProviderField.Number,
            ProviderField.Complement, ProviderField.Neighborhood, ProviderField.City,
            ProviderField.State,
        )
    }
}

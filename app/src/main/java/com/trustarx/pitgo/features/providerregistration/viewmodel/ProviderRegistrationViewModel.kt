package com.trustarx.pitgo.features.providerregistration.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.trustarx.pitgo.core.account.model.ProviderOnboardingSession
import com.trustarx.pitgo.core.address.model.AddressDraft
import com.trustarx.pitgo.core.address.model.CepAddressField
import com.trustarx.pitgo.core.address.model.CepAutofillProvenance
import com.trustarx.pitgo.core.address.model.PostalCodeLookupResult
import com.trustarx.pitgo.core.address.model.PostalCodeLookupState
import com.trustarx.pitgo.core.address.repository.ViaCepAddressLookupRepository
import com.trustarx.pitgo.core.auth.model.AppSessionState
import com.trustarx.pitgo.core.auth.repository.SessionRepository
import com.trustarx.pitgo.core.catalog.model.ServiceDispatchKind
import com.trustarx.pitgo.features.providerregistration.model.AddressVerificationState
import com.trustarx.pitgo.features.providerregistration.model.CatalogService
import com.trustarx.pitgo.features.providerregistration.model.CompanyDraft
import com.trustarx.pitgo.features.providerregistration.model.ImplicitServiceResult
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
import java.util.Locale

class ProviderRegistrationViewModel(
    private val expectedUserId: String,
    private val initialEmail: String,
    private val repository: ProviderRegistrationRepository,
    private val addressVerificationRepository: ProviderAddressVerificationRepository,
    private val viaCepAddressLookupRepository: ViaCepAddressLookupRepository,
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
    private var modeResolutionJob: Job? = null
    private var verificationJob: Job? = null
    private var postalCodeLookupJob: Job? = null
    private var postalCodeLookupAttemptId = 0L

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

    fun onFieldChanged(field: ProviderField, value: String) {
        val normalized = when (field) {
            ProviderField.Phone -> ProviderRegistrationValidation.normalizePhone(value)
            ProviderField.Cnpj -> ProviderRegistrationValidation.normalizeCnpj(value)
            ProviderField.PostalCode -> ProviderRegistrationValidation.normalizePostalCode(value)
            else -> value
        }
        val current = (_uiState.value as? ProviderRegistrationUiState.Editing)?.content ?: return
        val postalCodeChanged = field == ProviderField.PostalCode &&
            normalized != current.draft.address.postalCode
        val addressChanged = field in ADDRESS_FIELDS
        if (addressChanged) cancelAddressVerification()
        if (postalCodeChanged || (field in CEP_DERIVED_FIELDS &&
                current.postalCodeLookup is PostalCodeLookupState.Loading)
        ) {
            cancelPostalCodeLookup()
        }

        updateEditing { content ->
            var draft = content.draft
            var provenance = content.cepAutofillProvenance
            var lookup = content.postalCodeLookup
            if (postalCodeChanged) {
                draft = draft.clearCepDerivedFields(provenance?.fields.orEmpty())
                provenance = null
                lookup = PostalCodeLookupState.Idle
            }
            draft = draft.withField(field, normalized)
            val verification = if (addressChanged) AddressVerificationState.Unverified
                else content.addressVerification
            val errors = revalidatePresentedField(
                content = content,
                draft = draft,
                verification = verification,
                field = field,
                clearPostalNotFound = postalCodeChanged,
            )
            content.copy(
                draft = draft,
                postalCodeLookup = lookup,
                cepAutofillProvenance = provenance,
                addressVerification = verification,
                errors = errors,
                submission = ProviderSubmissionState.Idle,
            )
        }
    }

    fun lookupPostalCode() {
        val content = (_uiState.value as? ProviderRegistrationUiState.Editing)?.content ?: return
        if (content.submission == ProviderSubmissionState.Submitting ||
            content.postalCodeLookup is PostalCodeLookupState.Loading ||
            postalCodeLookupJob != null
        ) return

        val postalCode = ProviderRegistrationValidation.normalizePostalCode(
            content.draft.address.postalCode,
        )
        if (postalCode.length != POSTAL_CODE_LENGTH) {
            requestFocus(
                ProviderField.PostalCode,
                content.errors + (
                    ProviderField.PostalCode to ProviderFieldError.INVALID_POSTAL_CODE
                    ),
            )
            return
        }

        cancelAddressVerification()
        val attemptId = ++postalCodeLookupAttemptId
        _uiState.updateEditing {
            it.copy(
                postalCodeLookup = PostalCodeLookupState.Loading(attemptId, postalCode),
                addressVerification = AddressVerificationState.Unverified,
                errors = it.errors - ProviderField.PostalCode,
                submission = ProviderSubmissionState.Idle,
            )
        }
        postalCodeLookupJob = viewModelScope.launch {
            try {
                when (val result = viaCepAddressLookupRepository.lookup(postalCode)) {
                    is PostalCodeLookupResult.Found -> applyPostalCodeResult(
                        attemptId,
                        postalCode,
                        result,
                    )
                    PostalCodeLookupResult.NotFound -> applyPostalCodeNotFound(
                        attemptId,
                        postalCode,
                    )
                    is PostalCodeLookupResult.Failure -> applyPostalCodeFailure(
                        attemptId,
                        postalCode,
                        result,
                    )
                }
            } finally {
                if (postalCodeLookupAttemptId == attemptId) postalCodeLookupJob = null
            }
        }
    }

    fun onLocationModeSelected(mode: ProviderLocationMode) {
        val current = (_uiState.value as? ProviderRegistrationUiState.Editing)?.content ?: return
        if (current.draft.locationMode == mode) {
            updateEditing { it.copy(errors = it.errors - ProviderField.LocationMode) }
            return
        }
        modeResolutionJob?.cancel()
        updateEditing { content ->
            content.copy(
                draft = content.draft.copy(locationMode = mode, primaryCategoryId = null),
                errors = content.errors.filterKeys {
                    it != ProviderField.LocationMode && it != ProviderField.PrimaryCategory && it != ProviderField.Offers && it !is ProviderField.Offer
                },
                implicitServiceIssue = null,
            )
        }
        when (mode) {
            ProviderLocationMode.FIXED -> updateEditing { content ->
                content.copy(draft = content.draft.copy(offers = listOf(OfferDraft(localId = nextOfferId++))))
            }
            ProviderLocationMode.MOBILE -> resolveImplicitOffer(mode, ServiceDispatchKind.MOBILE_SEQUENTIAL)
            ProviderLocationMode.PITTEC -> resolveImplicitOffer(mode, ServiceDispatchKind.PITTEC_AUTO_SEQUENTIAL)
        }
    }

    /**
     * Móvel e PitTec não têm etapa de escolha de serviço (spec 016): o serviço implícito é
     * localizado pelo `dispatchKind` (nunca pelo nome, ver research.md #1) e vira a única oferta
     * do rascunho. Reaproveita `servicesByCategory` para que a validação existente enxergue esse
     * serviço sem precisar de nenhuma mudança em `ProviderRegistrationValidation`.
     */
    private fun resolveImplicitOffer(mode: ProviderLocationMode, dispatchKind: ServiceDispatchKind) {
        modeResolutionJob = viewModelScope.launch {
            when (val result = repository.resolveImplicitService(dispatchKind)) {
                is ImplicitServiceResult.Found -> {
                    if (!isSessionCurrent()) {
                        expireSession()
                        return@launch
                    }
                    updateEditing { content ->
                        if (content.draft.locationMode != mode) return@updateEditing content
                        content.withImplicitOffer(mode, result.service, OfferDraft(localId = nextOfferId++))
                    }
                }
                ImplicitServiceResult.Absent, ImplicitServiceResult.Duplicated -> updateEditing { content ->
                    if (content.draft.locationMode != mode) return@updateEditing content
                    content.copy(
                        draft = content.draft.copy(offers = emptyList()),
                        implicitServiceIssue = ProviderFailure.CatalogChanged,
                    )
                }
                is ImplicitServiceResult.Failure -> if (result.reason == ProviderFailure.SessionExpired) {
                    expireSession()
                } else {
                    updateEditing { content ->
                        if (content.draft.locationMode != mode) return@updateEditing content
                        content.copy(
                            draft = content.draft.copy(offers = emptyList()),
                            implicitServiceIssue = result.reason,
                        )
                    }
                }
            }
        }
    }

    fun verifyAddress() {
        if (verificationJob != null) return
        val content = (_uiState.value as? ProviderRegistrationUiState.Editing)?.content ?: return
        if (content.postalCodeLookup is PostalCodeLookupState.Loading ||
            content.submission == ProviderSubmissionState.Submitting
        ) return
        val fingerprint = content.draft.address.fingerprint()
        verificationJob = viewModelScope.launch {
            try {
                _uiState.updateEditing { it.copy(addressVerification = AddressVerificationState.Verifying) }
                when (val result = addressVerificationRepository.verify(content.draft.address)) {
                    is AddressVerificationResult.Verified -> _uiState.updateEditing {
                        if (isSessionCurrent() && it.draft.address.fingerprint() == fingerprint) {
                            it.copy(
                                addressVerification = result.state,
                                errors = it.errors - ProviderField.LocationVerification,
                            )
                        } else it
                    }
                    is AddressVerificationResult.Diverged -> _uiState.updateEditing {
                        if (isSessionCurrent() && it.draft.address.fingerprint() == fingerprint) {
                            it.copy(
                                addressVerification = AddressVerificationState.Diverged(
                                    result.distanceMeters,
                                ),
                            )
                        } else it
                    }
                    is AddressVerificationResult.Failure -> _uiState.updateEditing {
                        if (isSessionCurrent() && it.draft.address.fingerprint() == fingerprint) {
                            it.copy(
                                addressVerification = AddressVerificationState.Failure(result.reason),
                            )
                        } else it
                    }
                }
            } finally { verificationJob = null }
        }
    }

    private fun applyPostalCodeResult(
        attemptId: Long,
        postalCode: String,
        result: PostalCodeLookupResult.Found,
    ) {
        _uiState.updateEditing { content ->
            if (!content.matchesPostalCodeAttempt(attemptId, postalCode) || !isSessionCurrent()) {
                return@updateEditing content
            }

            val response = result.address
            val applied = buildSet {
                if (response.street != null) add(CepAddressField.STREET)
                if (response.complement != null) add(CepAddressField.COMPLEMENT)
                if (response.neighborhood != null) add(CepAddressField.NEIGHBORHOOD)
                add(CepAddressField.CITY)
                add(CepAddressField.STATE)
            }
            val updatedAddress = content.draft.address.copy(
                street = response.street ?: content.draft.address.street,
                complement = response.complement ?: content.draft.address.complement,
                neighborhood = response.neighborhood ?: content.draft.address.neighborhood,
                city = response.city,
                state = response.state,
            )
            val updatedDraft = content.draft.copy(address = updatedAddress)
            val incompleteField = listOf(
                ProviderField.Street to updatedAddress.street,
                ProviderField.Neighborhood to updatedAddress.neighborhood,
                ProviderField.City to updatedAddress.city,
                ProviderField.State to updatedAddress.state,
            ).firstOrNull { (_, value) -> value.isBlank() }?.first
            val focusField = incompleteField ?: ProviderField.Number
            val validation = validate(updatedDraft, AddressVerificationState.Unverified, content)
            val fieldsToRefresh = CEP_DERIVED_FIELDS + ProviderField.PostalCode
            val errors = content.errors.toMutableMap().apply {
                fieldsToRefresh.forEach { field ->
                    val error = validation.errors[field]
                    if (error == null) remove(field) else put(field, error)
                }
            }
            focusRequestId += 1
            content.copy(
                draft = updatedDraft,
                postalCodeLookup = PostalCodeLookupState.Found(
                    attemptId,
                    postalCode,
                    applied,
                ),
                cepAutofillProvenance = CepAutofillProvenance(postalCode, applied),
                addressVerification = AddressVerificationState.Unverified,
                errors = errors,
                focusRequest = ProviderFocusRequest(focusRequestId, focusField),
            )
        }
    }

    private fun applyPostalCodeNotFound(attemptId: Long, postalCode: String) {
        _uiState.updateEditing { content ->
            if (!content.matchesPostalCodeAttempt(attemptId, postalCode) || !isSessionCurrent()) {
                return@updateEditing content
            }
            focusRequestId += 1
            content.copy(
                postalCodeLookup = PostalCodeLookupState.NotFound(attemptId, postalCode),
                errors = content.errors + (
                    ProviderField.PostalCode to ProviderFieldError.POSTAL_CODE_NOT_FOUND
                    ),
                focusRequest = ProviderFocusRequest(focusRequestId, ProviderField.PostalCode),
            )
        }
    }

    private fun applyPostalCodeFailure(
        attemptId: Long,
        postalCode: String,
        result: PostalCodeLookupResult.Failure,
    ) {
        _uiState.updateEditing { content ->
            if (!content.matchesPostalCodeAttempt(attemptId, postalCode) || !isSessionCurrent()) {
                return@updateEditing content
            }
            content.copy(
                postalCodeLookup = PostalCodeLookupState.Failure(
                    attemptId,
                    postalCode,
                    result.reason,
                ),
            )
        }
    }

    fun onOfferFieldChanged(localId: Long, field: OfferField, value: String) = updateEditing { content ->
        val target = ProviderField.Offer(localId, field)
        val updatedDraft = content.draft.updateOffer(localId) { offer ->
            when (field) {
                OfferField.PRICE -> offer.copy(priceInput = value.take(18))
                OfferField.DURATION -> offer.copy(
                    durationInput = value.filter(Char::isDigit).take(5),
                )
                OfferField.DESCRIPTION -> offer.copy(description = value)
                OfferField.CATEGORY, OfferField.SERVICE -> offer
            }
        }
        content.copy(
            draft = updatedDraft,
            errors = revalidatePresentedField(
                content,
                updatedDraft,
                content.addressVerification,
                target,
            ),
            submission = ProviderSubmissionState.Idle,
        )
    }

    fun onCategorySelected(localId: Long, categoryId: String) {
        updateEditing { content ->
            if (content.draft.primaryCategoryId == categoryId) return@updateEditing content
            val updatedDraft = content.draft.copy(
                primaryCategoryId = categoryId,
                offers = listOf(OfferDraft(localId = nextOfferId++, categoryId = categoryId)),
            )
            val errors = content.errors.filterKeys { it !is ProviderField.Offer } - ProviderField.Offers - ProviderField.PrimaryCategory
            content.copy(
                draft = updatedDraft,
                errors = errors,
                submission = ProviderSubmissionState.Idle,
            )
        }
        loadServices(categoryId)
    }

    fun onServiceSelected(localId: Long, serviceId: String) = updateEditing { content ->
        val updatedDraft = content.draft.updateOffer(localId) { it.copy(serviceId = serviceId) }
        val target = ProviderField.Offer(localId, OfferField.SERVICE)
        content.copy(
            draft = updatedDraft,
            errors = revalidatePresentedField(
                content,
                updatedDraft,
                content.addressVerification,
                target,
            ),
            submission = ProviderSubmissionState.Idle,
        )
    }

    fun onFreeChanged(localId: Long, isFree: Boolean) = updateEditing { content ->
        val updatedDraft = content.draft.updateOffer(localId) {
            it.copy(isFree = isFree, priceInput = if (isFree) "" else it.priceInput)
        }
        val target = ProviderField.Offer(localId, OfferField.PRICE)
        content.copy(
            draft = updatedDraft,
            errors = revalidatePresentedField(
                content,
                updatedDraft,
                content.addressVerification,
                target,
            ),
            submission = ProviderSubmissionState.Idle,
        )
    }

    fun addOffer() = updateEditing { content ->
        content.copy(
            draft = content.draft.copy(
                offers = content.draft.offers + OfferDraft(localId = nextOfferId++, categoryId = content.draft.primaryCategoryId),
            ),
            errors = content.errors - ProviderField.Offers,
            submission = ProviderSubmissionState.Idle,
        )
    }

    fun removeOffer(localId: Long) = updateEditing { content ->
        val updatedDraft = content.draft.copy(
            offers = content.draft.offers.filterNot { it.localId == localId },
        )
        var errors = content.errors.filterKeys {
            it !is ProviderField.Offer || it.localId != localId
        }
        if (ProviderField.Offers in errors) {
            val offersError = ProviderRegistrationValidation.validateField(
                updatedDraft,
                content.availableServices(),
                content.addressVerification,
                ProviderField.Offers,
            )
            errors = if (offersError == null) errors - ProviderField.Offers
            else errors + (ProviderField.Offers to offersError)
        }
        content.copy(
            draft = updatedDraft,
            errors = errors,
            submission = ProviderSubmissionState.Idle,
        )
    }

    fun retryCatalog(categoryId: String? = null) {
        if (categoryId != null) loadServices(categoryId) else bootstrap()
    }

    /** Tenta de novo a resolução do serviço implícito depois de uma falha (T006). */
    fun retryImplicitService() {
        val content = (_uiState.value as? ProviderRegistrationUiState.Editing)?.content ?: return
        when (val mode = content.draft.locationMode) {
            ProviderLocationMode.MOBILE -> resolveImplicitOffer(mode, ServiceDispatchKind.MOBILE_SEQUENTIAL)
            ProviderLocationMode.PITTEC -> resolveImplicitOffer(mode, ServiceDispatchKind.PITTEC_AUTO_SEQUENTIAL)
            ProviderLocationMode.FIXED, null -> Unit
        }
    }

    fun submit() {
        val editing = (_uiState.value as? ProviderRegistrationUiState.Editing)?.content ?: return
        if (editing.submission == ProviderSubmissionState.Submitting || submitJob != null) return
        if (editing.postalCodeLookup is PostalCodeLookupState.Loading) return
        submitJob = viewModelScope.launch {
            try {
                val validated = when (editing.draft.locationMode) {
                    ProviderLocationMode.MOBILE, ProviderLocationMode.PITTEC -> recheckImplicitService(editing)
                    ProviderLocationMode.FIXED -> recheckFixedServices(editing)
                    else -> editing
                } ?: return@launch
                proceedWithValidation(validated)
            } finally {
                submitJob = null
            }
        }
    }

    private suspend fun recheckFixedServices(editing: ProviderEditingContent): ProviderEditingContent? {
        val categoryId = editing.draft.primaryCategoryId ?: return editing
        _uiState.updateEditing { it.copy(submission = ProviderSubmissionState.Submitting) }
        return when (val result = repository.loadServices(categoryId)) {
            is ProviderCatalogResult.Success -> {
                val refreshed = editing.copy(
                    servicesByCategory = editing.servicesByCategory + (categoryId to
                        if (result.items.isEmpty()) LoadState.Empty else LoadState.Content(result.items)),
                    submission = ProviderSubmissionState.Idle,
                )
                if (!isSessionCurrent()) null else {
                    _uiState.updateEditing { refreshed }
                    refreshed
                }
            }
            is ProviderCatalogResult.Failure -> {
                if (result.reason == ProviderFailure.SessionExpired) expireSession()
                else _uiState.updateEditing { it.copy(submission = ProviderSubmissionState.Failure(result.reason)) }
                null
            }
        }
    }

    /**
     * Confere de novo, imediatamente antes do envio, que o serviço implícito de Móvel/PitTec
     * ainda existe e é único (spec 016, FR-009) — o catálogo pode ter mudado entre a escolha da
     * modalidade e o clique em enviar. Preserva o preço/duração já digitados para Móvel ao
     * reaproveitar a oferta atual como base. Retorna `null` quando a checagem já tratou um
     * desfecho terminal (falha exibida ou sessão expirada) e o envio não deve continuar.
     */
    private suspend fun recheckImplicitService(editing: ProviderEditingContent): ProviderEditingContent? {
        val mode = requireNotNull(editing.draft.locationMode)
        val dispatchKind = if (mode == ProviderLocationMode.PITTEC) {
            ServiceDispatchKind.PITTEC_AUTO_SEQUENTIAL
        } else ServiceDispatchKind.MOBILE_SEQUENTIAL
        _uiState.updateEditing { it.copy(submission = ProviderSubmissionState.Submitting) }
        return when (val result = repository.resolveImplicitService(dispatchKind)) {
            is ImplicitServiceResult.Found -> {
                if (!isSessionCurrent()) {
                    expireSession()
                    return null
                }
                var refreshed: ProviderEditingContent? = null
                _uiState.updateEditing { content ->
                    if (content.draft.locationMode != mode) return@updateEditing content
                    val base = content.draft.offers.singleOrNull() ?: OfferDraft(localId = nextOfferId++)
                    content.withImplicitOffer(mode, result.service, base).also { refreshed = it }
                }
                refreshed
            }
            ImplicitServiceResult.Absent, ImplicitServiceResult.Duplicated -> {
                _uiState.updateEditing {
                    it.copy(
                        submission = ProviderSubmissionState.Idle,
                        implicitServiceIssue = ProviderFailure.CatalogChanged,
                    )
                }
                null
            }
            is ImplicitServiceResult.Failure -> {
                if (result.reason == ProviderFailure.SessionExpired) {
                    expireSession()
                } else {
                    _uiState.updateEditing {
                        it.copy(
                            submission = ProviderSubmissionState.Idle,
                            implicitServiceIssue = result.reason,
                        )
                    }
                }
                null
            }
        }
    }

    private suspend fun proceedWithValidation(editing: ProviderEditingContent) {
        val services = editing.availableServices()
        val validation = ProviderRegistrationValidation.validate(
            editing.draft, services, editing.addressVerification,
        )
        val matchingNotFound = (editing.postalCodeLookup as? PostalCodeLookupState.NotFound)
            ?.postalCode == editing.draft.address.postalCode
        val allErrors = if (matchingNotFound) {
            validation.errors + (
                ProviderField.PostalCode to ProviderFieldError.POSTAL_CODE_NOT_FOUND
                )
        } else validation.errors
        val registration = validation.registration?.takeIf { !matchingNotFound }
        if (registration == null) {
            val formErrors = allErrors - ProviderField.LocationVerification
            if (formErrors.isNotEmpty()) {
                requestFocus(formErrors.keys.first(), formErrors)
            } else {
                geocodeAndSubmit(editing)
            }
            return
        }
        startRegistration(registration)
    }

    private suspend fun geocodeAndSubmit(editing: ProviderEditingContent) {
        val fingerprint = editing.draft.address.fingerprint()
        _uiState.updateEditing {
            it.copy(
                errors = emptyMap(),
                addressVerification = AddressVerificationState.Verifying,
                submission = ProviderSubmissionState.Submitting,
            )
        }
        when (val geocode = addressVerificationRepository.verify(editing.draft.address)) {
            is AddressVerificationResult.Verified -> {
                _uiState.updateEditing {
                    if (isSessionCurrent() && it.draft.address.fingerprint() == fingerprint) {
                        it.copy(addressVerification = geocode.state)
                    } else it
                }
                val current = (_uiState.value as? ProviderRegistrationUiState.Editing)
                    ?.content ?: return
                if (!isSessionCurrent() || current.draft.address.fingerprint() != fingerprint) {
                    return
                }
                val services = current.availableServices()
                val validation = ProviderRegistrationValidation.validate(
                    current.draft, services, current.addressVerification,
                )
                val registration = validation.registration
                if (registration == null) {
                    requestFocus(validation.firstInvalidField, validation.errors)
                    _uiState.updateEditing {
                        it.copy(submission = ProviderSubmissionState.Idle)
                    }
                    return
                }
                performRegistration(registration)
            }
            is AddressVerificationResult.Failure -> _uiState.updateEditing {
                it.copy(
                    addressVerification = AddressVerificationState.Failure(geocode.reason),
                    submission = ProviderSubmissionState.Failure(geocode.reason),
                )
            }
            is AddressVerificationResult.Diverged -> _uiState.updateEditing {
                if (isSessionCurrent() && it.draft.address.fingerprint() == fingerprint) {
                    it.copy(
                        addressVerification = AddressVerificationState.Diverged(
                            geocode.distanceMeters,
                        ),
                        submission = ProviderSubmissionState.Idle,
                    )
                } else it
            }
        }
    }

    private suspend fun startRegistration(registration: ProviderRegistration) {
        _uiState.updateEditing {
            it.copy(errors = emptyMap(), submission = ProviderSubmissionState.Submitting)
        }
        performRegistration(registration)
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
        cancelPostalCodeLookup()
        cancelAddressVerification()
        _uiState.value = ProviderRegistrationUiState.Editing(
            current.copy(
                draft = current.baselineDraft,
                postalCodeLookup = PostalCodeLookupState.Idle,
                cepAutofillProvenance = null,
                addressVerification = AddressVerificationState.Unverified,
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
        cancelPostalCodeLookup()
        cancelAddressVerification()
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

    private fun validate(
        draft: ProviderRegistrationDraft,
        verification: AddressVerificationState,
        content: ProviderEditingContent,
    ) = ProviderRegistrationValidation.validate(
        draft = draft,
        availableServices = content.availableServices(),
        addressVerification = verification,
    )

    private fun revalidatePresentedField(
        content: ProviderEditingContent,
        draft: ProviderRegistrationDraft,
        verification: AddressVerificationState,
        field: ProviderField,
        clearPostalNotFound: Boolean = false,
    ): Map<ProviderField, ProviderFieldError> {
        if (field !in content.errors) return content.errors
        if (clearPostalNotFound && field == ProviderField.PostalCode) {
            return content.errors - field
        }
        if (field == ProviderField.PostalCode &&
            content.errors[field] == ProviderFieldError.POSTAL_CODE_NOT_FOUND
        ) {
            return content.errors
        }
        val currentError = ProviderRegistrationValidation.validateField(
            draft,
            content.availableServices(),
            verification,
            field,
        )
        return if (currentError == null) content.errors - field
        else content.errors + (field to currentError)
    }

    private fun ProviderEditingContent.availableServices() = servicesByCategory.values.flatMap {
        state -> (state as? LoadState.Content)?.value.orEmpty()
    }

    /**
     * Aplica o serviço implícito de Móvel/PitTec resolvido como a única oferta do rascunho,
     * partindo de [base] — uma oferta nova (troca de modalidade) ou a oferta atual (nova
     * checagem antes do envio, preservando o preço/duração já digitados para Móvel).
     */
    private fun ProviderEditingContent.withImplicitOffer(
        mode: ProviderLocationMode,
        service: CatalogService,
        base: OfferDraft,
    ): ProviderEditingContent {
        val offer = base.copy(
            categoryId = service.categoryId,
            serviceId = service.id,
            isFree = mode == ProviderLocationMode.PITTEC,
            durationInput = if (mode == ProviderLocationMode.PITTEC) {
                DEFAULT_PITTEC_DURATION_MINUTES.toString()
            } else base.durationInput,
        )
        return copy(
            draft = draft.copy(offers = listOf(offer)),
            servicesByCategory = servicesByCategory + (service.categoryId to LoadState.Content(listOf(service))),
            implicitServiceIssue = null,
        )
    }

    private fun ProviderEditingContent.matchesPostalCodeAttempt(
        attemptId: Long,
        postalCode: String,
    ): Boolean {
        val loading = postalCodeLookup as? PostalCodeLookupState.Loading ?: return false
        return loading.attemptId == attemptId && loading.postalCode == postalCode &&
            draft.address.postalCode == postalCode
    }

    private fun cancelPostalCodeLookup() {
        postalCodeLookupJob?.cancel()
        postalCodeLookupJob = null
    }

    private fun cancelAddressVerification() {
        verificationJob?.cancel()
        verificationJob = null
    }

    private fun ProviderRegistrationDraft.clearCepDerivedFields(
        fields: Set<CepAddressField>,
    ): ProviderRegistrationDraft = copy(
        address = address.copy(
            street = if (CepAddressField.STREET in fields) "" else address.street,
            complement = if (CepAddressField.COMPLEMENT in fields) "" else address.complement,
            neighborhood = if (CepAddressField.NEIGHBORHOOD in fields) "" else address.neighborhood,
            city = if (CepAddressField.CITY in fields) "" else address.city,
            state = if (CepAddressField.STATE in fields) "" else address.state,
        ),
    )

    private fun AddressDraft.fingerprint() = AddressFingerprint(
        street = street.trim(),
        number = number.trim(),
        neighborhood = neighborhood.trim(),
        postalCode = ProviderRegistrationValidation.normalizePostalCode(postalCode),
        city = city.trim(),
        state = state.trim().uppercase(Locale.ROOT),
        complement = complement.trim(),
    )

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
        ProviderField.State -> copy(
            address = address.copy(state = value.uppercase(Locale.ROOT).take(2)),
        )
        ProviderField.Latitude, ProviderField.Longitude, ProviderField.LocationMode,
        ProviderField.LocationVerification, ProviderField.PrimaryCategory, ProviderField.Offers, is ProviderField.Offer -> this
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
            viaCepAddressLookupRepository: ViaCepAddressLookupRepository,
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
                    viaCepAddressLookupRepository = viaCepAddressLookupRepository,
                    sessionRepository = sessionRepository,
                ) as T
            }
        }

        private val ADDRESS_FIELDS = setOf(
            ProviderField.PostalCode, ProviderField.Street, ProviderField.Number,
            ProviderField.Complement, ProviderField.Neighborhood, ProviderField.City,
            ProviderField.State,
        )
        private val CEP_DERIVED_FIELDS = setOf(
            ProviderField.Street,
            ProviderField.Complement,
            ProviderField.Neighborhood,
            ProviderField.City,
            ProviderField.State,
        )
        private const val POSTAL_CODE_LENGTH = 8

        /**
         * Duração interna sem significado de cobrança para a oferta implícita do PitTec — o
         * valor real (fixo + variável por distância) é definido pela plataforma, não pelo
         * prestador (spec 016, Clarifications).
         */
        private const val DEFAULT_PITTEC_DURATION_MINUTES = 60
    }
}

private data class AddressFingerprint(
    val street: String,
    val number: String,
    val neighborhood: String,
    val postalCode: String,
    val city: String,
    val state: String,
    val complement: String,
)

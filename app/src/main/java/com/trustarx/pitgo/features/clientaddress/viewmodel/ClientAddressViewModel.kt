package com.trustarx.pitgo.features.clientaddress.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.trustarx.pitgo.core.address.model.AddressDraft
import com.trustarx.pitgo.core.address.model.CepAddressField
import com.trustarx.pitgo.core.address.model.CepAutofillProvenance
import com.trustarx.pitgo.core.address.model.PostalCodeLookupResult
import com.trustarx.pitgo.core.address.model.PostalCodeLookupState
import com.trustarx.pitgo.core.address.repository.ViaCepAddressLookupRepository
import com.trustarx.pitgo.core.common.validation.BrazilianInputValidation
import com.trustarx.pitgo.features.clientaddress.model.ClientAddress
import com.trustarx.pitgo.features.clientaddress.model.ClientAddressFailure
import com.trustarx.pitgo.features.clientaddress.model.ClientAddressResult
import com.trustarx.pitgo.features.clientaddress.model.ClientAddressUiState
import com.trustarx.pitgo.features.clientaddress.repository.ClientAddressRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Client's own contact address (US5/US6): a lean form (autofill via CEP, no geocoding
 * verification) — deliberately not modeled after ProviderRegistrationViewModel's wizard-grade
 * validation/focus machinery, which exists for a public, fraud-sensitive business address, not a
 * private client contact address.
 */
class ClientAddressViewModel(
    private val repository: ClientAddressRepository,
    private val viaCepAddressLookupRepository: ViaCepAddressLookupRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<ClientAddressUiState>(ClientAddressUiState.Loading)
    val state: StateFlow<ClientAddressUiState> = _state.asStateFlow()
    private var postalCodeLookupJob: Job? = null
    private var postalCodeLookupAttemptId = 0L

    init {
        viewModelScope.launch {
            when (val result = repository.current()) {
                is ClientAddressResult.Success -> _state.value = ClientAddressUiState.Content(
                    draft = result.address?.toDraft() ?: AddressDraft(),
                )
                is ClientAddressResult.Failure -> _state.value = result.reason.toUiState()
            }
        }
    }

    fun updateStreet(value: String) = updateContent { it.copy(draft = it.draft.copy(street = value)) }
    fun updateNumber(value: String) = updateContent { it.copy(draft = it.draft.copy(number = value)) }
    fun updateNeighborhood(value: String) = updateContent { it.copy(draft = it.draft.copy(neighborhood = value)) }
    fun updatePostalCode(value: String) = updateContent {
        it.copy(draft = it.draft.copy(postalCode = value), postalCodeLookup = PostalCodeLookupState.Idle)
    }
    fun updateComplement(value: String) = updateContent { it.copy(draft = it.draft.copy(complement = value)) }
    fun updateCity(value: String) = updateContent { it.copy(draft = it.draft.copy(city = value)) }
    fun updateState(value: String) = updateContent { it.copy(draft = it.draft.copy(state = value)) }

    fun lookupPostalCode() {
        val content = _state.value as? ClientAddressUiState.Content ?: return
        if (content.postalCodeLookup is PostalCodeLookupState.Loading || postalCodeLookupJob != null) return
        val postalCode = BrazilianInputValidation.normalizePostalCode(content.draft.postalCode)
        if (!BrazilianInputValidation.isValidPostalCode(postalCode)) return

        val attemptId = ++postalCodeLookupAttemptId
        updateContent { it.copy(postalCodeLookup = PostalCodeLookupState.Loading(attemptId, postalCode)) }
        postalCodeLookupJob = viewModelScope.launch {
            try {
                when (val result = viaCepAddressLookupRepository.lookup(postalCode)) {
                    is PostalCodeLookupResult.Found -> applyFound(attemptId, postalCode, result)
                    PostalCodeLookupResult.NotFound -> updateContent {
                        it.copy(postalCodeLookup = PostalCodeLookupState.NotFound(attemptId, postalCode))
                    }
                    is PostalCodeLookupResult.Failure -> updateContent {
                        it.copy(postalCodeLookup = PostalCodeLookupState.Failure(attemptId, postalCode, result.reason))
                    }
                }
            } finally {
                if (postalCodeLookupAttemptId == attemptId) postalCodeLookupJob = null
            }
        }
    }

    fun save() {
        val content = _state.value as? ClientAddressUiState.Content ?: return
        if (content.saving) return
        val draft = content.draft
        val postalCode = BrazilianInputValidation.normalizePostalCode(draft.postalCode)
        val valid = draft.street.isNotBlank() && draft.number.isNotBlank() &&
            draft.neighborhood.isNotBlank() && draft.city.isNotBlank() &&
            BrazilianInputValidation.isValidPostalCode(postalCode) &&
            BrazilianInputValidation.isValidState(draft.state)
        if (!valid) return
        _state.value = content.copy(saving = true, failure = null)
        viewModelScope.launch {
            val address = ClientAddress(
                street = draft.street.trim(),
                number = draft.number.trim(),
                neighborhood = draft.neighborhood.trim(),
                zipCode = postalCode,
                city = draft.city.trim(),
                state = draft.state.trim().uppercase(),
                complement = draft.complement.trim().takeIf(String::isNotBlank),
                latitude = draft.latitude.toDoubleOrNull(),
                longitude = draft.longitude.toDoubleOrNull(),
            )
            when (val result = repository.save(address)) {
                is ClientAddressResult.Success -> _state.value = ClientAddressUiState.Saved
                is ClientAddressResult.Failure -> _state.value = if (result.reason == ClientAddressFailure.SESSION_EXPIRED) {
                    ClientAddressUiState.SessionExpired
                } else content.copy(saving = false, failure = result.reason)
            }
        }
    }

    private fun applyFound(attemptId: Long, postalCode: String, result: PostalCodeLookupResult.Found) {
        updateContent { content ->
            if ((content.postalCodeLookup as? PostalCodeLookupState.Loading)?.attemptId != attemptId) return@updateContent content
            val response = result.address
            val applied = buildSet {
                if (response.street != null) add(CepAddressField.STREET)
                if (response.complement != null) add(CepAddressField.COMPLEMENT)
                if (response.neighborhood != null) add(CepAddressField.NEIGHBORHOOD)
                add(CepAddressField.CITY)
                add(CepAddressField.STATE)
            }
            content.copy(
                draft = content.draft.copy(
                    street = response.street ?: content.draft.street,
                    complement = response.complement ?: content.draft.complement,
                    neighborhood = response.neighborhood ?: content.draft.neighborhood,
                    city = response.city,
                    state = response.state,
                ),
                postalCodeLookup = PostalCodeLookupState.Found(attemptId, postalCode, applied),
                cepAutofillProvenance = CepAutofillProvenance(postalCode, applied),
            )
        }
    }

    private inline fun updateContent(block: (ClientAddressUiState.Content) -> ClientAddressUiState.Content) {
        val content = _state.value as? ClientAddressUiState.Content ?: return
        _state.value = block(content)
    }

    private fun ClientAddress.toDraft() = AddressDraft(
        street = street, number = number, neighborhood = neighborhood, postalCode = zipCode,
        city = city, state = state, complement = complement.orEmpty(),
        latitude = latitude?.toString().orEmpty(), longitude = longitude?.toString().orEmpty(),
    )

    private fun ClientAddressFailure.toUiState(): ClientAddressUiState = when (this) {
        ClientAddressFailure.SESSION_EXPIRED -> ClientAddressUiState.SessionExpired
        else -> ClientAddressUiState.Failure(this)
    }

    companion object {
        fun factory(repository: ClientAddressRepository, viaCepAddressLookupRepository: ViaCepAddressLookupRepository) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ClientAddressViewModel(repository, viaCepAddressLookupRepository) as T
            }
    }
}

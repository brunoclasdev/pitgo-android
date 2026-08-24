package com.trustarx.pitgo.features.clientaddress.model

import com.trustarx.pitgo.core.address.model.AddressDraft
import com.trustarx.pitgo.core.address.model.CepAutofillProvenance
import com.trustarx.pitgo.core.address.model.PostalCodeLookupState

data class ClientAddress(
    val street: String,
    val number: String,
    val neighborhood: String,
    val zipCode: String,
    val city: String,
    val state: String,
    val complement: String?,
    val latitude: Double?,
    val longitude: Double?,
)

enum class ClientAddressFailure { OFFLINE, TIMEOUT, SESSION_EXPIRED, SERVICE_UNAVAILABLE, CONTRACT, UNKNOWN }

sealed interface ClientAddressResult {
    /** [address] is null when the client has no address saved yet. */
    data class Success(val address: ClientAddress?) : ClientAddressResult
    data class Failure(val reason: ClientAddressFailure) : ClientAddressResult
}

sealed interface ClientAddressUiState {
    data object Loading : ClientAddressUiState
    data class Content(
        val draft: AddressDraft = AddressDraft(),
        val postalCodeLookup: PostalCodeLookupState = PostalCodeLookupState.Idle,
        val cepAutofillProvenance: CepAutofillProvenance? = null,
        val saving: Boolean = false,
        val failure: ClientAddressFailure? = null,
    ) : ClientAddressUiState
    data class Failure(val reason: ClientAddressFailure) : ClientAddressUiState
    data object Saved : ClientAddressUiState
    data object SessionExpired : ClientAddressUiState
}

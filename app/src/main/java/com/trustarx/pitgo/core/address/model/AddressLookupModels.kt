package com.trustarx.pitgo.core.address.model

data class AddressDraft(
    val street: String = "",
    val number: String = "",
    val neighborhood: String = "",
    val postalCode: String = "",
    val city: String = "",
    val state: String = "",
    val complement: String = "",
    val latitude: String = "",
    val longitude: String = "",
)

data class PostalAddressResult(
    val postalCode: String,
    val street: String?,
    val complement: String?,
    val neighborhood: String?,
    val city: String,
    val state: String,
)

enum class PostalCodeLookupFailure {
    OFFLINE,
    TIMEOUT,
    SERVICE_UNAVAILABLE,
    UNEXPECTED_RESPONSE,
}

sealed interface PostalCodeLookupResult {
    data class Found(val address: PostalAddressResult) : PostalCodeLookupResult
    data object NotFound : PostalCodeLookupResult
    data class Failure(val reason: PostalCodeLookupFailure) : PostalCodeLookupResult
}

enum class CepAddressField {
    STREET,
    COMPLEMENT,
    NEIGHBORHOOD,
    CITY,
    STATE,
}

data class CepAutofillProvenance(
    val postalCode: String,
    val fields: Set<CepAddressField>,
)

sealed interface PostalCodeLookupState {
    data object Idle : PostalCodeLookupState
    data class Loading(val attemptId: Long, val postalCode: String) : PostalCodeLookupState
    data class Found(
        val attemptId: Long,
        val postalCode: String,
        val appliedFields: Set<CepAddressField>,
    ) : PostalCodeLookupState
    data class NotFound(val attemptId: Long, val postalCode: String) : PostalCodeLookupState
    data class Failure(
        val attemptId: Long,
        val postalCode: String,
        val reason: PostalCodeLookupFailure,
    ) : PostalCodeLookupState
}

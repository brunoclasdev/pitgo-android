package com.trustarx.pitgo.features.providerregistration.model

import com.trustarx.pitgo.core.address.model.AddressDraft
import com.trustarx.pitgo.core.address.model.CepAutofillProvenance
import com.trustarx.pitgo.core.address.model.PostalCodeLookupState
import com.trustarx.pitgo.core.catalog.model.ServiceDispatchKind
import java.math.BigDecimal

data class ServiceCategory(
    val id: String,
    val name: String,
    val description: String?,
)

data class CatalogService(
    val id: String,
    val categoryId: String,
    val name: String,
    val description: String?,
    /** Identifica o serviço implícito de Móvel/PitTec sem depender do nome (ver research.md #1). */
    val dispatchKind: ServiceDispatchKind = ServiceDispatchKind.BROWSE,
)

data class CompanyDraft(
    val name: String = "",
    val description: String = "",
    val email: String = "",
    val phone: String = "",
    val cnpj: String = "",
)

data class OfferDraft(
    val localId: Long,
    val categoryId: String? = null,
    val serviceId: String? = null,
    val priceInput: String = "",
    val durationInput: String = "",
    val description: String = "",
    val isFree: Boolean = false,
)

data class ProviderRegistrationDraft(
    val locationMode: ProviderLocationMode? = null,
    val company: CompanyDraft = CompanyDraft(),
    val address: AddressDraft = AddressDraft(),
    val offers: List<OfferDraft> = listOf(OfferDraft(localId = 0L)),
)

data class CompanyRegistration(
    val name: String,
    val description: String?,
    val email: String,
    val phone: String,
    val cnpj: String,
)

data class AddressRegistration(
    val street: String,
    val number: String,
    val neighborhood: String,
    val postalCode: String,
    val city: String,
    val state: String,
    val country: String = "BR",
    val complement: String?,
    val verificationToken: String,
)

data class OfferRegistration(
    val serviceId: String,
    val price: BigDecimal,
    val durationMinutes: Int,
    val description: String?,
    val isFree: Boolean,
)

data class ProviderRegistration(
    val locationMode: ProviderLocationMode,
    val company: CompanyRegistration,
    val address: AddressRegistration,
    val offers: List<OfferRegistration>,
)

enum class ProviderLocationMode { FIXED, MOBILE, PITTEC }

sealed interface AddressVerificationState {
    data object Unverified : AddressVerificationState
    data object PermissionRequired : AddressVerificationState
    data object Locating : AddressVerificationState
    data object Verifying : AddressVerificationState
    data class Diverged(val distanceMeters: Int) : AddressVerificationState
    data class Verified(
        val token: String,
        val latitude: Double,
        val longitude: Double,
        val distanceMeters: Int,
        val mismatchConfirmed: Boolean,
        val expiresAtEpochMillis: Long,
    ) : AddressVerificationState
    data class Failure(val reason: ProviderFailure) : AddressVerificationState
}

data class ProviderSummary(
    val name: String,
    val email: String,
    val phone: String,
    val maskedCnpj: String,
    val address: AddressSummary,
    val offers: List<OfferSummary>,
)

data class AddressSummary(
    val street: String,
    val number: String,
    val neighborhood: String,
    val city: String,
    val state: String,
)

data class OfferSummary(
    val categoryName: String,
    val serviceName: String,
    val price: BigDecimal,
    val durationMinutes: Int,
    val description: String?,
)

enum class OfferField {
    CATEGORY,
    SERVICE,
    PRICE,
    DURATION,
    DESCRIPTION,
}

sealed interface ProviderField {
    data object LocationMode : ProviderField
    data object LocationVerification : ProviderField
    data object Name : ProviderField
    data object Description : ProviderField
    data object Email : ProviderField
    data object Phone : ProviderField
    data object Cnpj : ProviderField
    data object PostalCode : ProviderField
    data object Street : ProviderField
    data object Number : ProviderField
    data object Complement : ProviderField
    data object Neighborhood : ProviderField
    data object City : ProviderField
    data object State : ProviderField
    data object Latitude : ProviderField
    data object Longitude : ProviderField
    data object Offers : ProviderField
    data class Offer(val localId: Long, val field: OfferField) : ProviderField
}

enum class ProviderFieldError {
    REQUIRED,
    INVALID_EMAIL,
    INVALID_PHONE,
    INVALID_CNPJ,
    INVALID_POSTAL_CODE,
    POSTAL_CODE_NOT_FOUND,
    INVALID_STATE,
    COORDINATE_PAIR_REQUIRED,
    COORDINATE_OUT_OF_RANGE,
    SERVICE_UNAVAILABLE,
    DUPLICATE_SERVICE,
    INVALID_PRICE,
    PRICE_MUST_BE_POSITIVE,
    INVALID_DURATION,
    TEXT_TOO_LONG,
    UNAVAILABLE,
}

data class ProviderFocusRequest(
    val requestId: Long,
    val field: ProviderField,
)

sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data object Empty : LoadState<Nothing>
    data class Content<T>(val value: T) : LoadState<T>
    data class Failure(val reason: ProviderFailure) : LoadState<Nothing>
}

sealed interface ProviderFailure {
    data object Offline : ProviderFailure
    data object Timeout : ProviderFailure
    data object SessionExpired : ProviderFailure
    data object ServiceUnavailable : ProviderFailure
    data object Contract : ProviderFailure
    data object LocationUnavailable : ProviderFailure
    data object CatalogChanged : ProviderFailure
    data object Unknown : ProviderFailure
}

sealed interface ProviderSubmissionState {
    data object Idle : ProviderSubmissionState
    data object Submitting : ProviderSubmissionState
    data class Failure(val reason: ProviderFailure) : ProviderSubmissionState
}

data class ProviderEditingContent(
    val draft: ProviderRegistrationDraft,
    val baselineDraft: ProviderRegistrationDraft,
    val categories: LoadState<List<ServiceCategory>> = LoadState.Loading,
    val servicesByCategory: Map<String, LoadState<List<CatalogService>>> = emptyMap(),
    val postalCodeLookup: PostalCodeLookupState = PostalCodeLookupState.Idle,
    val cepAutofillProvenance: CepAutofillProvenance? = null,
    val addressVerification: AddressVerificationState = AddressVerificationState.Unverified,
    val errors: Map<ProviderField, ProviderFieldError> = emptyMap(),
    val focusRequest: ProviderFocusRequest? = null,
    val submission: ProviderSubmissionState = ProviderSubmissionState.Idle,
    /**
     * Serviço implícito de Móvel/PitTec ausente ou duplicado no catálogo (ver
     * contracts/provider-registration-service-rule.md). Independente de [submission]: é
     * verificado ao trocar de modalidade e de novo antes do envio, não apenas como resultado de
     * uma tentativa de envio já feita.
     */
    val implicitServiceIssue: ProviderFailure? = null,
) {
    val hasDraft: Boolean get() = draft != baselineDraft
}

sealed interface ProviderRegistrationUiState {
    data object Loading : ProviderRegistrationUiState
    data class StartupFailure(val reason: ProviderFailure) : ProviderRegistrationUiState
    data class AlreadyRegistered(val summary: ProviderSummary) : ProviderRegistrationUiState
    data class Editing(val content: ProviderEditingContent) : ProviderRegistrationUiState
    data class Success(val summary: ProviderSummary) : ProviderRegistrationUiState
    data object SessionExpired : ProviderRegistrationUiState
}

sealed interface ProviderEntryResult {
    data class Ready(val categories: List<ServiceCategory>) : ProviderEntryResult
    data class AlreadyRegistered(val summary: ProviderSummary) : ProviderEntryResult
    data class Failure(val reason: ProviderFailure) : ProviderEntryResult
}

sealed interface ProviderCatalogResult<out T> {
    data class Success<T>(val items: List<T>) : ProviderCatalogResult<T>
    data class Failure(val reason: ProviderFailure) : ProviderCatalogResult<Nothing>
}

/** Resultado de localizar o serviço implícito de Móvel/PitTec pelo `dispatchKind` (data-model.md). */
sealed interface ImplicitServiceResult {
    data class Found(val service: CatalogService) : ImplicitServiceResult
    data object Absent : ImplicitServiceResult
    data object Duplicated : ImplicitServiceResult
    data class Failure(val reason: ProviderFailure) : ImplicitServiceResult
}

sealed interface ProviderRegistrationResult {
    data class Success(val summary: ProviderSummary) : ProviderRegistrationResult
    data class AlreadyRegistered(val summary: ProviderSummary) : ProviderRegistrationResult
    data object EmailUnavailable : ProviderRegistrationResult
    data object CnpjUnavailable : ProviderRegistrationResult
    data object DuplicateService : ProviderRegistrationResult
    data class Failure(val reason: ProviderFailure) : ProviderRegistrationResult
}

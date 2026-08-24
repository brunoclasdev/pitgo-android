package com.trustarx.pitgo.features.providerregistration.validation

import com.trustarx.pitgo.core.common.validation.BrazilianInputValidation
import com.trustarx.pitgo.features.providerregistration.model.AddressRegistration
import com.trustarx.pitgo.features.providerregistration.model.AddressVerificationState
import com.trustarx.pitgo.features.providerregistration.model.CatalogService
import com.trustarx.pitgo.features.providerregistration.model.CompanyRegistration
import com.trustarx.pitgo.features.providerregistration.model.OfferField
import com.trustarx.pitgo.features.providerregistration.model.OfferRegistration
import com.trustarx.pitgo.features.providerregistration.model.ProviderField
import com.trustarx.pitgo.features.providerregistration.model.ProviderFieldError
import com.trustarx.pitgo.features.providerregistration.model.ProviderRegistration
import com.trustarx.pitgo.features.providerregistration.model.ProviderRegistrationDraft
import java.math.BigDecimal
import java.util.Locale

data class ProviderValidationResult(
    val registration: ProviderRegistration?,
    val errors: Map<ProviderField, ProviderFieldError>,
    val firstInvalidField: ProviderField?,
)

object ProviderRegistrationValidation {
    private val pricePattern = Regex("^[0-9]+([.,][0-9]{1,2})?$")

    fun normalizePhone(value: String) = BrazilianInputValidation.normalizePhone(value)
    fun normalizeCnpj(value: String) = BrazilianInputValidation.normalizeCnpj(value)
    fun normalizePostalCode(value: String) = BrazilianInputValidation.normalizePostalCode(value)

    fun validate(
        draft: ProviderRegistrationDraft,
        availableServices: Collection<CatalogService>,
        addressVerification: AddressVerificationState,
    ): ProviderValidationResult {
        val errors = linkedMapOf<ProviderField, ProviderFieldError>()
        val company = draft.company
        val address = draft.address
        val normalizedName = company.name.trim()
        val normalizedDescription = company.description.trim().ifBlank { null }
        val normalizedEmail = BrazilianInputValidation.normalizeEmail(company.email)
        val normalizedPhone = normalizePhone(company.phone)
        val normalizedCnpj = normalizeCnpj(company.cnpj)

        if (draft.locationMode == null) {
            errors[ProviderField.LocationMode] = ProviderFieldError.REQUIRED
        }
        requiredOrLength(normalizedName, 150, ProviderField.Name, errors)
        optionalLength(normalizedDescription, 2000, ProviderField.Description, errors)
        when {
            normalizedEmail.isBlank() -> errors[ProviderField.Email] = ProviderFieldError.REQUIRED
            normalizedEmail.length > 254 || !BrazilianInputValidation.isValidEmail(normalizedEmail) ->
                errors[ProviderField.Email] = ProviderFieldError.INVALID_EMAIL
        }
        when {
            normalizedPhone.isBlank() -> errors[ProviderField.Phone] = ProviderFieldError.REQUIRED
            !BrazilianInputValidation.isValidPhone(normalizedPhone) ->
                errors[ProviderField.Phone] = ProviderFieldError.INVALID_PHONE
        }
        when {
            normalizedCnpj.isBlank() -> errors[ProviderField.Cnpj] = ProviderFieldError.REQUIRED
            !BrazilianInputValidation.isValidCnpj(normalizedCnpj) ->
                errors[ProviderField.Cnpj] = ProviderFieldError.INVALID_CNPJ
        }

        val normalizedStreet = address.street.trim()
        val normalizedNumber = address.number.trim()
        val normalizedNeighborhood = address.neighborhood.trim()
        val normalizedPostalCode = normalizePostalCode(address.postalCode)
        val normalizedCity = address.city.trim()
        val normalizedState = address.state.trim().uppercase(Locale.ROOT)
        val normalizedComplement = address.complement.trim().ifBlank { null }

        when {
            normalizedPostalCode.isBlank() -> errors[ProviderField.PostalCode] = ProviderFieldError.REQUIRED
            !BrazilianInputValidation.isValidPostalCode(normalizedPostalCode) ->
                errors[ProviderField.PostalCode] = ProviderFieldError.INVALID_POSTAL_CODE
        }
        requiredOrLength(normalizedStreet, 160, ProviderField.Street, errors)
        requiredOrLength(normalizedNumber, 40, ProviderField.Number, errors)
        optionalLength(normalizedComplement, 160, ProviderField.Complement, errors)
        requiredOrLength(normalizedNeighborhood, 100, ProviderField.Neighborhood, errors)
        requiredOrLength(normalizedCity, 100, ProviderField.City, errors)
        when {
            normalizedState.isBlank() -> errors[ProviderField.State] = ProviderFieldError.REQUIRED
            !BrazilianInputValidation.isValidState(normalizedState) ->
                errors[ProviderField.State] = ProviderFieldError.INVALID_STATE
        }

        val verifiedAddress = addressVerification as? AddressVerificationState.Verified
        if (verifiedAddress == null || verifiedAddress.expiresAtEpochMillis <= System.currentTimeMillis()) {
            errors[ProviderField.LocationVerification] = ProviderFieldError.REQUIRED
        }

        val availableById = availableServices.associateBy(CatalogService::id)
        if (draft.offers.isEmpty()) errors[ProviderField.Offers] = ProviderFieldError.REQUIRED
        val seenServices = mutableSetOf<String>()
        val normalizedOffers = draft.offers.mapNotNull { offer ->
            val categoryField = ProviderField.Offer(offer.localId, OfferField.CATEGORY)
            val serviceField = ProviderField.Offer(offer.localId, OfferField.SERVICE)
            val priceField = ProviderField.Offer(offer.localId, OfferField.PRICE)
            val durationField = ProviderField.Offer(offer.localId, OfferField.DURATION)
            val descriptionField = ProviderField.Offer(offer.localId, OfferField.DESCRIPTION)
            if (offer.categoryId == null) errors[categoryField] = ProviderFieldError.REQUIRED

            val selectedService = offer.serviceId?.let(availableById::get)
            when {
                offer.serviceId == null -> errors[serviceField] = ProviderFieldError.REQUIRED
                selectedService == null || selectedService.categoryId != offer.categoryId ->
                    errors[serviceField] = ProviderFieldError.SERVICE_UNAVAILABLE
                !seenServices.add(offer.serviceId) ->
                    errors[serviceField] = ProviderFieldError.DUPLICATE_SERVICE
            }

            val price = if (offer.isFree) BigDecimal.ZERO else parsePrice(offer.priceInput)
            if (!offer.isFree) {
                when {
                    offer.priceInput.isBlank() -> errors[priceField] = ProviderFieldError.REQUIRED
                    price == null -> errors[priceField] = ProviderFieldError.INVALID_PRICE
                    price <= BigDecimal.ZERO -> errors[priceField] = ProviderFieldError.PRICE_MUST_BE_POSITIVE
                }
            }
            val duration = offer.durationInput.toIntOrNull()
            when {
                offer.durationInput.isBlank() -> errors[durationField] = ProviderFieldError.REQUIRED
                duration == null -> errors[durationField] = ProviderFieldError.INVALID_DURATION
                duration !in 1..10080 -> errors[durationField] = ProviderFieldError.INVALID_DURATION
            }
            val description = offer.description.trim().ifBlank { null }
            optionalLength(description, 1000, descriptionField, errors)

            if (selectedService == null || price == null || duration == null || duration !in 1..10080) {
                null
            } else {
                OfferRegistration(
                    serviceId = selectedService.id,
                    price = price,
                    durationMinutes = duration,
                    description = description,
                    isFree = offer.isFree,
                )
            }
        }

        if (errors.isNotEmpty()) {
            return ProviderValidationResult(null, errors, errors.keys.first())
        }

        return ProviderValidationResult(
            registration = ProviderRegistration(
                locationMode = requireNotNull(draft.locationMode),
                company = CompanyRegistration(
                    name = normalizedName,
                    description = normalizedDescription,
                    email = normalizedEmail,
                    phone = normalizedPhone,
                    cnpj = normalizedCnpj,
                ),
                address = AddressRegistration(
                    street = normalizedStreet,
                    number = normalizedNumber,
                    neighborhood = normalizedNeighborhood,
                    postalCode = normalizedPostalCode,
                    city = normalizedCity,
                    state = normalizedState,
                    complement = normalizedComplement,
                    verificationToken = requireNotNull(verifiedAddress).token,
                ),
                offers = normalizedOffers,
            ),
            errors = emptyMap(),
            firstInvalidField = null,
        )
    }

    fun validateField(
        draft: ProviderRegistrationDraft,
        availableServices: Collection<CatalogService>,
        addressVerification: AddressVerificationState,
        field: ProviderField,
    ): ProviderFieldError? = validate(draft, availableServices, addressVerification).errors[field]

    fun maximumLength(field: ProviderField): Int? = when (field) {
        ProviderField.Name -> 150
        ProviderField.Description -> 2_000
        ProviderField.Email -> 254
        ProviderField.Street, ProviderField.Complement -> 160
        ProviderField.Number -> 40
        ProviderField.Neighborhood, ProviderField.City -> 100
        is ProviderField.Offer -> if (field.field == OfferField.DESCRIPTION) 1_000 else null
        else -> null
    }

    private fun requiredOrLength(
        value: String,
        maximum: Int,
        field: ProviderField,
        errors: MutableMap<ProviderField, ProviderFieldError>,
    ) {
        when {
            value.isBlank() -> errors[field] = ProviderFieldError.REQUIRED
            value.length > maximum -> errors[field] = ProviderFieldError.TEXT_TOO_LONG
        }
    }

    private fun optionalLength(
        value: String?,
        maximum: Int,
        field: ProviderField,
        errors: MutableMap<ProviderField, ProviderFieldError>,
    ) {
        if (value != null && value.length > maximum) errors[field] = ProviderFieldError.TEXT_TOO_LONG
    }

    private fun parsePrice(value: String): BigDecimal? {
        val normalized = value.trim()
        if (!pricePattern.matches(normalized)) return null
        return normalized.replace(',', '.').toBigDecimalOrNull()
            ?.takeIf { it.scale().coerceAtLeast(0) <= 2 }
    }

}

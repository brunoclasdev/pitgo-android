package com.trustarx.pitgo.features.providerregistration.data

import com.trustarx.pitgo.core.catalog.model.ServiceDispatchKind
import com.trustarx.pitgo.features.providerregistration.model.AddressRegistration
import com.trustarx.pitgo.features.providerregistration.model.CatalogService
import com.trustarx.pitgo.features.providerregistration.model.CompanyRegistration
import com.trustarx.pitgo.features.providerregistration.model.OfferRegistration
import com.trustarx.pitgo.features.providerregistration.model.ServiceCategory
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive

@Serializable
internal data class ServiceCategoryDto(
    val id: String,
    val name: String,
    val description: String? = null,
)

@Serializable
internal data class CatalogServiceDto(
    val id: String,
    @SerialName("category_id") val categoryId: String,
    val name: String,
    val description: String? = null,
    @SerialName("dispatch_kind") val dispatchKind: String? = null,
)

@Serializable
internal data class ProviderDto(
    val id: String,
    @SerialName("account_id") val accountId: String,
    val name: String,
    val description: String? = null,
    val email: String,
    val phone: String,
    val cnpj: String,
)

@Serializable
internal data class ProviderAddressDto(
    val street: String,
    val number: String,
    val neighborhood: String,
    val city: String,
    val state: String,
)

@Serializable
internal data class ProviderOfferDto(
    @SerialName("service_id") val serviceId: String,
    val price: JsonElement,
    @SerialName("duration_minutes") val durationMinutes: Int,
    val description: String? = null,
)

@Serializable
internal data class ProviderRegistrationRpcParams(
    @SerialName("p_provider") val provider: ProviderInputDto,
    @SerialName("p_address") val address: AddressInputDto,
    @SerialName("p_offers") val offers: List<ProviderOfferInputDto>,
)

@Serializable
internal data class ProviderInputDto(
    val name: String,
    val description: String? = null,
    val email: String,
    val phone: String,
    val cnpj: String,
    @SerialName("location_mode") val locationMode: String,
)

@Serializable
internal data class AddressInputDto(
    val street: String,
    val number: String,
    val neighborhood: String,
    @SerialName("zip_code") val postalCode: String,
    val city: String,
    val state: String,
    val country: String,
    val complement: String? = null,
    @SerialName("verification_token") val verificationToken: String,
)

@Serializable
internal data class ProviderOfferInputDto(
    @SerialName("service_id") val serviceId: String,
    val price: String,
    @SerialName("duration_minutes") val durationMinutes: Int,
    val description: String? = null,
    @SerialName("is_free") val isFree: Boolean,
)

@Serializable
internal data class ProviderRegistrationRpcResultDto(
    val outcome: String,
    @SerialName("offer_count") val offerCount: Int,
)

internal fun ServiceCategoryDto.toDomain() = ServiceCategory(
    id = id,
    name = name,
    description = description,
)

internal fun CatalogServiceDto.toDomain() = CatalogService(
    id = id,
    categoryId = categoryId,
    name = name,
    description = description,
    dispatchKind = dispatchKind
        ?.let { runCatching { ServiceDispatchKind.valueOf(it) }.getOrNull() }
        ?: ServiceDispatchKind.BROWSE,
)

internal fun CompanyRegistration.toDto(locationMode: String) = ProviderInputDto(
    name = name,
    description = description,
    email = email,
    phone = phone,
    cnpj = cnpj,
    locationMode = locationMode,
)

internal fun AddressRegistration.toDto() = AddressInputDto(
    street = street,
    number = number,
    neighborhood = neighborhood,
    postalCode = postalCode,
    city = city,
    state = state,
    country = country,
    complement = complement,
    verificationToken = verificationToken,
)

internal fun OfferRegistration.toDto() = ProviderOfferInputDto(
    serviceId = serviceId,
    price = price.toPlainString(),
    durationMinutes = durationMinutes,
    description = description,
    isFree = isFree,
)

internal fun ProviderOfferDto.decimalPrice() = price.jsonPrimitive.content.toBigDecimal()

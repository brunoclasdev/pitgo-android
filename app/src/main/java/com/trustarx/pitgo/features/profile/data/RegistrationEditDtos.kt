package com.trustarx.pitgo.features.profile.data

import com.trustarx.pitgo.core.account.ProviderKind
import com.trustarx.pitgo.core.address.model.AddressDraft
import com.trustarx.pitgo.features.profile.model.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class RegistrationAddressDto(
    val street: String, val number: String, val neighborhood: String,
    @SerialName("zip_code") val postalCode: String, val city: String, val state: String,
    val complement: String? = null, @SerialName("updated_at") val updatedAt: String,
) {
    fun domain() = AddressDraft(street, number, neighborhood, postalCode, city, state, complement.orEmpty())
}
@Serializable
internal data class RegistrationSnapshotDto(
    @SerialName("owner_id") val ownerId: String, @SerialName("account_type") val type: String,
    val name: String, val phone: String, val email: String, val document: String,
    val description: String? = null, @SerialName("provider_kind") val providerKind: String? = null,
    @SerialName("updated_at") val updatedAt: String, val address: RegistrationAddressDto? = null,
) {
    fun domain() = RegistrationSnapshot(ownerId,RegistrationAccountType.valueOf(type),name,phone,email,document,
        description.orEmpty(),providerKind?.let(ProviderKind::valueOf),updatedAt,address?.domain(),address?.updatedAt).also {
        java.util.UUID.fromString(ownerId)
        require(name.isNotBlank() && email.isNotBlank() && updatedAt.isNotBlank())
    }
}

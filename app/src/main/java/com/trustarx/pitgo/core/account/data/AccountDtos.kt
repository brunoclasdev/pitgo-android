package com.trustarx.pitgo.core.account.data

import com.trustarx.pitgo.core.account.model.Profile
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class AccountProfileDto(
    val id: String,
    val name: String? = null,
    val email: String,
    val phone: String? = null,
    val cpf: String? = null,
    val cnpj: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val active: Boolean,
)

internal fun AccountProfileDto.toDomainOrNull(): Profile? {
    val normalizedName = name?.trim().orEmpty()
    val normalizedPhone = phone?.trim().orEmpty()
    val normalizedEmail = email.trim().lowercase()
    val normalizedAvatar = avatarUrl?.trim()?.takeIf(String::isNotEmpty)

    if (id.isBlank() || normalizedName.isBlank() || normalizedEmail.isBlank() ||
        normalizedPhone.isBlank() || !((cpf == null) xor (cnpj == null))
    ) {
        return null
    }

    return Profile(
        id = id,
        name = normalizedName,
        email = normalizedEmail,
        phone = normalizedPhone,
        cpf = cpf,
        cnpj = cnpj,
        avatarUrl = normalizedAvatar,
        active = active,
    )
}

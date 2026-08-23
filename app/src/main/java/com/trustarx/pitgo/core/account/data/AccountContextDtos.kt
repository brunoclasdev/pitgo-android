package com.trustarx.pitgo.core.account.data

import com.trustarx.pitgo.core.account.model.AccountContext
import com.trustarx.pitgo.core.account.model.AccountType
import com.trustarx.pitgo.core.account.model.ClientAccountState
import com.trustarx.pitgo.core.account.model.ProviderAccountState
import com.trustarx.pitgo.core.account.model.ProviderLocationMode
import com.trustarx.pitgo.core.account.model.ProviderSnapshot
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class AccountContextDto(
    @SerialName("account_type") val accountType: String,
    @SerialName("client_state") val clientState: String? = null,
    @SerialName("provider_state") val providerState: String? = null,
    @SerialName("provider_id") val providerId: String? = null,
    @SerialName("legacy_provider_migration_available")
    val legacyProviderMigrationAvailable: Boolean = false,
)

@Serializable
internal data class ProviderAccountDto(
    val id: String,
    @SerialName("account_id") val accountId: String,
    val name: String,
    val email: String,
    val phone: String,
    @SerialName("location_mode") val locationMode: String? = null,
    val active: Boolean = true,
    val available: Boolean = false,
)

internal fun AccountContextDto.toDomainOrNull(): AccountContext? {
    val type = enumValueOrNull<AccountType>(accountType) ?: return null
    val client = clientState?.let { enumValueOrNull<ClientAccountState>(it) }
    val provider = providerState?.let { enumValueOrNull<ProviderAccountState>(it) }
    if (type == AccountType.CLIENT && client == null) return null
    if (type == AccountType.PROVIDER && provider == null) return null
    return AccountContext(type, client, provider, providerId, legacyProviderMigrationAvailable)
}

internal fun ProviderAccountDto.toDomainOrNull(authEmail: String): ProviderSnapshot? {
    val normalizedName = name.trim()
    val normalizedEmail = email.trim().lowercase()
    val normalizedPhone = phone.trim()
    val mode = locationMode?.let { enumValueOrNull<ProviderLocationMode>(it) }
    if (id.isBlank() || accountId.isBlank() || normalizedName.isBlank() ||
        normalizedEmail.isBlank() || normalizedPhone.isBlank()
    ) return null
    return ProviderSnapshot(
        userId = accountId,
        authEmail = authEmail,
        providerId = id,
        name = normalizedName,
        email = normalizedEmail,
        phone = normalizedPhone,
        locationMode = mode,
        active = active,
        available = available,
    )
}

private inline fun <reified T : Enum<T>> enumValueOrNull(raw: String): T? =
    enumValues<T>().firstOrNull { it.name == raw }

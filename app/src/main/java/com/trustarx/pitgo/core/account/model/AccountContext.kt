package com.trustarx.pitgo.core.account.model

enum class AccountType { CLIENT, PROVIDER }

enum class ClientAccountState { READY, MISSING, INACTIVE, INCONSISTENT }

enum class ProviderAccountState { ONBOARDING, READY, INCOMPLETE }

data class AccountContext(
    val accountType: AccountType,
    val clientState: ClientAccountState?,
    val providerState: ProviderAccountState?,
    val providerId: String?,
    val legacyProviderMigrationAvailable: Boolean,
)

data class ProviderSnapshot(
    val userId: String,
    val authEmail: String,
    val providerId: String,
    val name: String,
    val email: String,
    val phone: String,
    val locationMode: ProviderLocationMode?,
    val active: Boolean,
    val available: Boolean,
    val pendingLocationMode: ProviderLocationMode? = null,
) {
    fun isConsistent(): Boolean = userId.isNotBlank() && providerId.isNotBlank() &&
        name.isNotBlank() && email.equals(authEmail, ignoreCase = true) && phone.isNotBlank() &&
        active
}

enum class ProviderLocationMode { FIXED, MOBILE, PITTEC }

data class ProviderOnboardingSession(
    val userId: String,
    val authEmail: String,
)

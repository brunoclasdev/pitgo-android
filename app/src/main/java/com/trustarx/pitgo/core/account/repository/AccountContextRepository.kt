package com.trustarx.pitgo.core.account.repository

import com.trustarx.pitgo.core.account.data.AccountContextDto
import com.trustarx.pitgo.core.account.data.ProviderAccountDto
import com.trustarx.pitgo.core.account.data.toDomainOrNull
import com.trustarx.pitgo.core.account.model.AccountType
import com.trustarx.pitgo.core.account.model.ClientAccountState
import com.trustarx.pitgo.core.account.model.ProviderAccountState
import com.trustarx.pitgo.core.account.model.ProviderOnboardingSession
import com.trustarx.pitgo.core.account.model.ProviderSnapshot
import com.trustarx.pitgo.core.auth.model.ProfileBlockedReason
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException

sealed interface AccountContextLoadResult {
    data class Restricted(val userId: String, val email: String, val mode: String) : AccountContextLoadResult
    data class Client(
        val account: com.trustarx.pitgo.core.account.model.AccountSnapshot,
        val legacyProviderMigrationAvailable: Boolean,
    ) :
        AccountContextLoadResult
    data class ProviderOnboarding(val session: ProviderOnboardingSession) : AccountContextLoadResult
    data class ProviderReady(val account: ProviderSnapshot) : AccountContextLoadResult
    data class Blocked(val reason: ProfileBlockedReason) : AccountContextLoadResult
    data object Offline : AccountContextLoadResult
    data object Timeout : AccountContextLoadResult
    data object Unauthorized : AccountContextLoadResult
    data object ServiceUnavailable : AccountContextLoadResult
    data object Unknown : AccountContextLoadResult
}

class AccountContextRepository(
    private val client: SupabaseClient,
    private val clientAccountRepository: AccountRepository,
) {
    var selectedMode: String? = null
    var memberships: Set<String> = emptySet()
        private set
    suspend fun load(userId: String, authEmail: String): AccountContextLoadResult { return try {
        val dto = client.postgrest.rpc(GET_CONTEXT, kotlinx.serialization.json.buildJsonObject {
            selectedMode?.let { put("p_account_type", kotlinx.serialization.json.JsonPrimitive(it)) }
        }).decodeAs<AccountContextDto>()
        memberships = dto.memberships.map { it.accountType }.toSet()
        val membership = dto.memberships.firstOrNull { it.accountType == dto.accountType }
        if (membership != null && !membership.allowed && !membership.legacy && membership.status != com.trustarx.pitgo.features.registrationverification.model.RegistrationStatus.APPROVED) {
            return AccountContextLoadResult.Restricted(userId, authEmail, dto.accountType)
        }
        val context = dto.toDomainOrNull() ?: return AccountContextLoadResult.Blocked(
            ProfileBlockedReason.INCONSISTENT,
        )
        when (context.accountType) {
            AccountType.CLIENT -> when (context.clientState) {
                ClientAccountState.READY -> when (
                    val result = clientAccountRepository.loadAccount(userId, authEmail)
                ) {
                    is AccountLoadResult.Success -> AccountContextLoadResult.Client(
                        result.account, context.legacyProviderMigrationAvailable,
                    )
                    is AccountLoadResult.Blocked -> AccountContextLoadResult.Blocked(result.reason)
                    AccountLoadResult.Offline -> AccountContextLoadResult.Offline
                    AccountLoadResult.Timeout -> AccountContextLoadResult.Timeout
                    AccountLoadResult.Unauthorized -> AccountContextLoadResult.Unauthorized
                    AccountLoadResult.ServiceUnavailable -> AccountContextLoadResult.ServiceUnavailable
                    AccountLoadResult.Unknown -> AccountContextLoadResult.Unknown
                }
                ClientAccountState.MISSING -> AccountContextLoadResult.Blocked(ProfileBlockedReason.MISSING)
                ClientAccountState.INACTIVE -> AccountContextLoadResult.Blocked(ProfileBlockedReason.INACTIVE)
                ClientAccountState.INCONSISTENT, null -> AccountContextLoadResult.Blocked(
                    ProfileBlockedReason.INCONSISTENT,
                )
            }
            AccountType.PROVIDER -> when (context.providerState) {
                ProviderAccountState.ONBOARDING -> AccountContextLoadResult.ProviderOnboarding(
                    ProviderOnboardingSession(userId, authEmail),
                )
                ProviderAccountState.READY -> loadProvider(userId, authEmail)
                ProviderAccountState.INCOMPLETE, null -> AccountContextLoadResult.Blocked(
                    ProfileBlockedReason.INCONSISTENT,
                )
            }
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: HttpRequestTimeoutException) {
        AccountContextLoadResult.Timeout
    } catch (_: HttpRequestException) {
        AccountContextLoadResult.Offline
    } catch (error: PostgrestRestException) {
        when {
            error.statusCode == 401 || error.statusCode == 403 || error.code == "42501" ->
                AccountContextLoadResult.Unauthorized
            error.statusCode >= 500 -> AccountContextLoadResult.ServiceUnavailable
            else -> AccountContextLoadResult.Unknown
        }
    } catch (_: Throwable) {
        AccountContextLoadResult.Unknown
    }

    }

    private suspend fun loadProvider(
        userId: String,
        authEmail: String,
    ): AccountContextLoadResult {
        val dto = client.from(PROVIDERS).select {
            filter { eq("account_id", userId) }
            limit(1)
        }.decodeSingleOrNull<ProviderAccountDto>()
            ?: return AccountContextLoadResult.Blocked(ProfileBlockedReason.MISSING)
        val provider = dto.toDomainOrNull(authEmail)
            ?: return AccountContextLoadResult.Blocked(ProfileBlockedReason.INCONSISTENT)
        return if (provider.isConsistent()) AccountContextLoadResult.ProviderReady(provider)
        else AccountContextLoadResult.Blocked(ProfileBlockedReason.INCONSISTENT)
    }

    private companion object {
        const val GET_CONTEXT = "get_my_account_context_v2"
        const val PROVIDERS = "providers"
    }
}

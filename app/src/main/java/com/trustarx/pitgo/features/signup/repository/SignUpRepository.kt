package com.trustarx.pitgo.features.signup.repository

import com.trustarx.pitgo.core.account.repository.AccountLoadResult
import com.trustarx.pitgo.core.account.repository.AccountRepository
import com.trustarx.pitgo.core.preferences.VehicleOnboardingRepository
import com.trustarx.pitgo.features.signup.model.AccountRegistration
import com.trustarx.pitgo.features.signup.model.SignUpResult
import com.trustarx.pitgo.features.signup.model.SignUpStage
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

interface SignUpRepository {
    suspend fun createAccount(
        registration: AccountRegistration,
        onStageChanged: (SignUpStage) -> Unit,
    ): SignUpResult

    suspend fun recoverProfile(onStageChanged: (SignUpStage) -> Unit): SignUpResult
}

class SupabaseSignUpRepository(
    private val client: SupabaseClient,
    private val accountRepository: AccountRepository,
    private val onboardingRepository: VehicleOnboardingRepository,
) : SignUpRepository {
    override suspend fun createAccount(
        registration: AccountRegistration,
        onStageChanged: (SignUpStage) -> Unit,
    ): SignUpResult = try {
        client.auth.awaitInitialization()
        val existing = client.auth.currentUserOrNull()
        if (!existing?.email.equals(registration.email, ignoreCase = true)) {
            onStageChanged(SignUpStage.CREATING_ACCOUNT)
            client.auth.signUpWith(Email) {
                email = registration.email
                password = registration.password
                data = buildJsonObject {
                    put("name", registration.name)
                    put("phone", registration.phone)
                    registration.cpf?.let { put("cpf", it) }
                    registration.cnpj?.let { put("cnpj", it) }
                }
            }
        }
        confirmTriggeredProfile(onStageChanged)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Throwable) {
        classifyFailure(error)
    }

    override suspend fun recoverProfile(onStageChanged: (SignUpStage) -> Unit): SignUpResult = try {
        client.auth.awaitInitialization()
        confirmTriggeredProfile(onStageChanged, recovering = true)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Throwable) {
        classifyFailure(error)
    }

    private suspend fun confirmTriggeredProfile(
        onStageChanged: (SignUpStage) -> Unit,
        recovering: Boolean = false,
    ): SignUpResult {
        onStageChanged(
            if (recovering) SignUpStage.RECOVERING_PROFILE else SignUpStage.ENSURING_PROFILE,
        )
        val user = client.auth.currentUserOrNull() ?: return SignUpResult.EnvironmentMisconfigured
        val email = user.email?.trim()?.lowercase().orEmpty()
        if (email.isBlank()) return SignUpResult.EnvironmentMisconfigured
        return when (accountRepository.loadAccount(user.id, email)) {
            is AccountLoadResult.Success -> {
                onboardingRepository.markPending(user.id)
                SignUpResult.Success(user.id)
            }
            is AccountLoadResult.Blocked -> SignUpResult.ProfileRecoveryRequired
            AccountLoadResult.Offline,
            AccountLoadResult.Timeout,
            AccountLoadResult.ServiceUnavailable,
            AccountLoadResult.Unknown,
            -> SignUpResult.ProfileRecoveryRequired
            AccountLoadResult.Unauthorized -> SignUpResult.EnvironmentMisconfigured
        }
    }

    private fun classifyFailure(error: Throwable): SignUpResult = when (error) {
        is AuthRestException -> when (error.errorCode) {
            AuthErrorCode.EmailExists,
            AuthErrorCode.UserAlreadyExists,
            -> SignUpResult.EmailUnavailable
            AuthErrorCode.ValidationFailed,
            AuthErrorCode.EmailAddressInvalid,
            AuthErrorCode.WeakPassword,
            -> SignUpResult.InvalidServerInput
            AuthErrorCode.OverRequestRateLimit,
            AuthErrorCode.OverEmailSendRateLimit,
            -> SignUpResult.RateLimited
            AuthErrorCode.UnexpectedFailure -> when {
                error.error.contains(CPF_UNIQUE) || error.error.contains(CNPJ_UNIQUE) ->
                    SignUpResult.DocumentUnavailable
                error.error.contains(EMAIL_UNIQUE) -> SignUpResult.EmailUnavailable
                else -> SignUpResult.ServiceUnavailable
            }
            else -> if (error.statusCode >= 500) SignUpResult.ServiceUnavailable
            else SignUpResult.Unknown
        }
        is HttpRequestTimeoutException -> SignUpResult.ServiceUnavailable
        is HttpRequestException -> SignUpResult.Offline
        is RestException -> if (error.statusCode >= 500) {
            SignUpResult.ServiceUnavailable
        } else {
            SignUpResult.Unknown
        }
        else -> SignUpResult.Unknown
    }

    private companion object {
        const val EMAIL_UNIQUE = "profiles_email_unique"
        const val CPF_UNIQUE = "profiles_cpf_unique"
        const val CNPJ_UNIQUE = "profiles_cnpj_unique"
    }
}

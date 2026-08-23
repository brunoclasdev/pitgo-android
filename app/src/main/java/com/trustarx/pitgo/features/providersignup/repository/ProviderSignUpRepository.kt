package com.trustarx.pitgo.features.providersignup.repository

import com.trustarx.pitgo.features.providersignup.model.ProviderCredentials
import com.trustarx.pitgo.features.providersignup.model.ProviderSignUpFailure
import com.trustarx.pitgo.features.providersignup.model.ProviderSignUpResult
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.exceptions.HttpRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ProviderSignUpRepository(private val client: SupabaseClient) {
    suspend fun create(credentials: ProviderCredentials): ProviderSignUpResult = try {
        client.auth.awaitInitialization()
        client.auth.signUpWith(Email) {
            email = credentials.email
            password = credentials.password
            data = buildJsonObject { put("account_type", "PROVIDER") }
        }
        val current = client.auth.currentUserOrNull()
        if (current?.email.equals(credentials.email, ignoreCase = true)) {
            ProviderSignUpResult.Success
        } else {
            ProviderSignUpResult.Failure(ProviderSignUpFailure.CONFIRMATION_REQUIRED)
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: HttpRequestTimeoutException) {
        ProviderSignUpResult.Failure(ProviderSignUpFailure.SERVICE_UNAVAILABLE)
    } catch (_: HttpRequestException) {
        ProviderSignUpResult.Failure(ProviderSignUpFailure.OFFLINE)
    } catch (error: AuthRestException) {
        val reason = when (error.errorCode) {
            AuthErrorCode.EmailExists, AuthErrorCode.UserAlreadyExists ->
                ProviderSignUpFailure.EMAIL_UNAVAILABLE
            AuthErrorCode.OverRequestRateLimit, AuthErrorCode.OverEmailSendRateLimit ->
                ProviderSignUpFailure.RATE_LIMITED
            AuthErrorCode.UnexpectedFailure -> ProviderSignUpFailure.SERVICE_UNAVAILABLE
            else -> if (error.statusCode >= 500) ProviderSignUpFailure.SERVICE_UNAVAILABLE
            else ProviderSignUpFailure.UNKNOWN
        }
        ProviderSignUpResult.Failure(reason)
    } catch (_: Throwable) {
        ProviderSignUpResult.Failure(ProviderSignUpFailure.UNKNOWN)
    }
}

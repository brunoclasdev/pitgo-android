package com.trustarx.pitgo.core.auth.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.SignOutScope
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow

data class AuthIdentity(val id: String, val email: String)

enum class AuthFailure {
    INVALID_CREDENTIALS, INVALID_SESSION, RATE_LIMITED, OFFLINE, TIMEOUT,
    SERVICE_UNAVAILABLE, UNKNOWN,
}

sealed interface AuthOperationResult {
    data class Success(val identity: AuthIdentity) : AuthOperationResult
    data class Failure(val reason: AuthFailure) : AuthOperationResult
}

class SupabaseAuthDataSource(private val client: SupabaseClient) {
    val sessionStatus: StateFlow<SessionStatus> get() = client.auth.sessionStatus

    suspend fun awaitInitialization() = client.auth.awaitInitialization()

    fun currentIdentityOrNull(): AuthIdentity? = client.auth.currentUserOrNull()?.let { user ->
        val email = user.email?.trim()?.lowercase().orEmpty()
        if (user.id.isBlank() || email.isBlank()) null else AuthIdentity(user.id, email)
    }

    suspend fun validateCurrentIdentity(): AuthOperationResult = safely {
        client.auth.retrieveUserForCurrentSession(updateSession = true).let { user ->
            val email = user.email?.trim()?.lowercase().orEmpty()
            if (user.id.isBlank() || email.isBlank()) {
                AuthOperationResult.Failure(AuthFailure.INVALID_SESSION)
            } else {
                AuthOperationResult.Success(AuthIdentity(user.id, email))
            }
        }
    }

    suspend fun signIn(email: String, password: String): AuthOperationResult = safely {
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
        currentIdentityOrNull()?.let(AuthOperationResult::Success)
            ?: AuthOperationResult.Failure(AuthFailure.INVALID_SESSION)
    }

    suspend fun clearLocalSession() {
        runCatching { client.auth.signOut(SignOutScope.LOCAL) }
            .onFailure { if (it is CancellationException) throw it }
        if (client.auth.currentSessionOrNull() != null) client.auth.clearSession()
    }

    private suspend fun safely(block: suspend () -> AuthOperationResult): AuthOperationResult = try {
        block()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: HttpRequestTimeoutException) {
        AuthOperationResult.Failure(AuthFailure.TIMEOUT)
    } catch (_: HttpRequestException) {
        AuthOperationResult.Failure(AuthFailure.OFFLINE)
    } catch (error: AuthRestException) {
        AuthOperationResult.Failure(classify(error))
    } catch (error: RestException) {
        AuthOperationResult.Failure(
            when {
                error.statusCode == 401 || error.statusCode == 403 -> AuthFailure.INVALID_SESSION
                error.statusCode == 429 -> AuthFailure.RATE_LIMITED
                error.statusCode >= 500 -> AuthFailure.SERVICE_UNAVAILABLE
                else -> AuthFailure.UNKNOWN
            },
        )
    } catch (_: Throwable) {
        AuthOperationResult.Failure(AuthFailure.UNKNOWN)
    }

    private fun classify(error: AuthRestException): AuthFailure = when (error.errorCode) {
        AuthErrorCode.InvalidCredentials -> AuthFailure.INVALID_CREDENTIALS
        AuthErrorCode.SessionExpired, AuthErrorCode.SessionNotFound,
        AuthErrorCode.RefreshTokenNotFound, AuthErrorCode.RefreshTokenAlreadyUsed,
        AuthErrorCode.BadJwt, AuthErrorCode.UserNotFound -> AuthFailure.INVALID_SESSION
        AuthErrorCode.OverRequestRateLimit,
        AuthErrorCode.OverEmailSendRateLimit -> AuthFailure.RATE_LIMITED
        AuthErrorCode.RequestTimeout -> AuthFailure.TIMEOUT
        AuthErrorCode.UnexpectedFailure -> AuthFailure.SERVICE_UNAVAILABLE
        else -> when {
            error.statusCode == 401 || error.statusCode == 403 -> AuthFailure.INVALID_SESSION
            error.statusCode == 429 -> AuthFailure.RATE_LIMITED
            error.statusCode >= 500 -> AuthFailure.SERVICE_UNAVAILABLE
            else -> AuthFailure.UNKNOWN
        }
    }
}

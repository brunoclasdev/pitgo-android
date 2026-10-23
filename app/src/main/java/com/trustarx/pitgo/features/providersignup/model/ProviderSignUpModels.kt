package com.trustarx.pitgo.features.providersignup.model

data class ProviderSignUpUiState(
    val email: String = "",
    val password: String = "",
    val passwordConfirmation: String = "",
    val emailError: ProviderSignUpFieldError? = null,
    val passwordError: ProviderSignUpFieldError? = null,
    val confirmationError: ProviderSignUpFieldError? = null,
    val submission: ProviderSignUpSubmission = ProviderSignUpSubmission.Editing,
    val providerKind: com.trustarx.pitgo.core.account.ProviderKind = com.trustarx.pitgo.core.account.ProviderKind.PITTEC,
) {
    val isProcessing get() = submission == ProviderSignUpSubmission.Submitting
    val hasDraft get() = email.isNotBlank() || password.isNotBlank() || passwordConfirmation.isNotBlank()
}

enum class ProviderSignUpFieldError { REQUIRED, INVALID_EMAIL, WEAK_PASSWORD, PASSWORD_MISMATCH }

sealed interface ProviderSignUpSubmission {
    data object Editing : ProviderSignUpSubmission
    data object Submitting : ProviderSignUpSubmission
    data object Success : ProviderSignUpSubmission
    data class Failure(val reason: ProviderSignUpFailure) : ProviderSignUpSubmission
}

enum class ProviderSignUpFailure {
    EMAIL_UNAVAILABLE, RATE_LIMITED, OFFLINE, SERVICE_UNAVAILABLE,
    CONFIRMATION_REQUIRED, INVALID_FORM, UNKNOWN,
}

sealed interface ProviderSignUpResult {
    data object Success : ProviderSignUpResult
    data class Failure(val reason: ProviderSignUpFailure) : ProviderSignUpResult
}

data class ProviderCredentials(val email: String, val password: String)

object ProviderSignUpValidation {
    fun validate(state: ProviderSignUpUiState): Pair<ProviderCredentials?, ProviderSignUpUiState> {
        val email = state.email.trim().lowercase()
        val emailError = when {
            email.isBlank() -> ProviderSignUpFieldError.REQUIRED
            !EMAIL.matches(email) -> ProviderSignUpFieldError.INVALID_EMAIL
            else -> null
        }
        val passwordError = when {
            state.password.isBlank() -> ProviderSignUpFieldError.REQUIRED
            state.password.length < 8 -> ProviderSignUpFieldError.WEAK_PASSWORD
            else -> null
        }
        val confirmationError = when {
            state.passwordConfirmation.isBlank() -> ProviderSignUpFieldError.REQUIRED
            state.passwordConfirmation != state.password -> ProviderSignUpFieldError.PASSWORD_MISMATCH
            else -> null
        }
        val updated = state.copy(
            emailError = emailError,
            passwordError = passwordError,
            confirmationError = confirmationError,
        )
        val credentials = if (emailError == null && passwordError == null && confirmationError == null) {
            ProviderCredentials(email, state.password)
        } else null
        return credentials to updated
    }

    private val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
}

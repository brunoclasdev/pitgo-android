package com.trustarx.pitgo.features.login.model

enum class LoginField { EMAIL, PASSWORD }

enum class LoginFieldError { REQUIRED, INVALID_EMAIL }

enum class LoginFailureReason {
    INVALID_CREDENTIALS, RATE_LIMITED, OFFLINE, TIMEOUT, PROFILE_BLOCKED,
    SERVICE_UNAVAILABLE, UNKNOWN,
}

sealed interface LoginOperation {
    data object Editing : LoginOperation
    data object Submitting : LoginOperation
    data class Failure(val reason: LoginFailureReason) : LoginOperation
    data object Success : LoginOperation
}

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val emailError: LoginFieldError? = null,
    val passwordError: LoginFieldError? = null,
    val focusField: LoginField? = null,
    val focusRequestId: Long = 0,
    val operation: LoginOperation = LoginOperation.Editing,
) {
    val isSubmitting: Boolean get() = operation is LoginOperation.Submitting
}

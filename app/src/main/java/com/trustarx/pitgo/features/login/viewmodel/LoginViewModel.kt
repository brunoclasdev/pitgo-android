package com.trustarx.pitgo.features.login.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.trustarx.pitgo.core.auth.repository.SessionLoginFailure
import com.trustarx.pitgo.core.auth.repository.SessionLoginResult
import com.trustarx.pitgo.core.auth.repository.SessionRepository
import com.trustarx.pitgo.features.login.model.LoginFailureReason
import com.trustarx.pitgo.features.login.model.LoginField
import com.trustarx.pitgo.features.login.model.LoginFieldError
import com.trustarx.pitgo.features.login.model.LoginOperation
import com.trustarx.pitgo.features.login.model.LoginUiState
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LoginViewModel(private val sessionRepository: SessionRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onEmailChanged(value: String) = _uiState.update {
        if (it.isSubmitting) it else it.copy(
            email = value,
            emailError = null,
            operation = LoginOperation.Editing,
        )
    }

    fun onPasswordChanged(value: String) = _uiState.update {
        if (it.isSubmitting) it else it.copy(
            password = value,
            passwordError = null,
            operation = LoginOperation.Editing,
        )
    }

    fun onSubmit() {
        val current = _uiState.value
        if (current.isSubmitting) return
        val email = current.email.trim().lowercase(Locale.ROOT)
        val emailError = when {
            email.isBlank() -> LoginFieldError.REQUIRED
            !EMAIL_PATTERN.matches(email) -> LoginFieldError.INVALID_EMAIL
            else -> null
        }
        val passwordError = if (current.password.isEmpty()) LoginFieldError.REQUIRED else null
        if (emailError != null || passwordError != null) {
            requestFocus(if (emailError != null) LoginField.EMAIL else LoginField.PASSWORD) {
                copy(emailError = emailError, passwordError = passwordError)
            }
            return
        }

        _uiState.update { it.copy(email = email, operation = LoginOperation.Submitting) }
        viewModelScope.launch {
            when (val result = sessionRepository.signIn(email, current.password)) {
                SessionLoginResult.Success -> _uiState.update {
                    it.copy(password = "", operation = LoginOperation.Success)
                }
                is SessionLoginResult.Failure -> applyFailure(result.reason)
            }
        }
    }

    fun clearPassword() = _uiState.update { it.copy(password = "") }

    private fun applyFailure(reason: SessionLoginFailure) {
        val mapped = when (reason) {
            SessionLoginFailure.INVALID_CREDENTIALS -> LoginFailureReason.INVALID_CREDENTIALS
            SessionLoginFailure.RATE_LIMITED -> LoginFailureReason.RATE_LIMITED
            SessionLoginFailure.OFFLINE -> LoginFailureReason.OFFLINE
            SessionLoginFailure.TIMEOUT -> LoginFailureReason.TIMEOUT
            SessionLoginFailure.PROFILE_BLOCKED -> LoginFailureReason.PROFILE_BLOCKED
            SessionLoginFailure.SERVICE_UNAVAILABLE -> LoginFailureReason.SERVICE_UNAVAILABLE
            SessionLoginFailure.UNKNOWN -> LoginFailureReason.UNKNOWN
        }
        requestFocus(LoginField.PASSWORD) {
            copy(password = "", operation = LoginOperation.Failure(mapped))
        }
    }

    private fun requestFocus(
        field: LoginField,
        transform: LoginUiState.() -> LoginUiState,
    ) = _uiState.update { current ->
        current.transform().copy(
            focusField = field,
            focusRequestId = current.focusRequestId + 1,
        )
    }

    companion object {
        private val EMAIL_PATTERN = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

        fun factory(repository: SessionRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(LoginViewModel::class.java))
                    return LoginViewModel(repository) as T
                }
            }
    }
}

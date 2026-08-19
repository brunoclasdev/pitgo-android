package com.trustarx.pitgo.features.signup.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.trustarx.pitgo.features.signup.model.FocusRequest
import com.trustarx.pitgo.features.signup.model.PersonType
import com.trustarx.pitgo.features.signup.model.SignUpFailureReason
import com.trustarx.pitgo.features.signup.model.SignUpField
import com.trustarx.pitgo.features.signup.model.SignUpFieldError
import com.trustarx.pitgo.features.signup.model.SignUpResult
import com.trustarx.pitgo.features.signup.model.SignUpStage
import com.trustarx.pitgo.features.signup.model.SignUpSubmissionState
import com.trustarx.pitgo.features.signup.model.SignUpUiState
import com.trustarx.pitgo.features.signup.model.SignUpValidation
import com.trustarx.pitgo.features.signup.repository.SignUpRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SignUpViewModel(
    private val repository: SignUpRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SignUpUiState())
    val uiState: StateFlow<SignUpUiState> = _uiState.asStateFlow()

    private var focusRequestId = 0L

    fun onPersonTypeChanged(personType: PersonType) = edit { state ->
        state.copy(
            personType = personType,
            document = "",
            errors = state.errors.copy(personType = null, document = null),
        )
    }

    fun onNameChanged(value: String) = edit { state ->
        state.copy(name = value, errors = state.errors.copy(name = null))
    }

    fun onEmailChanged(value: String) = edit { state ->
        state.copy(email = value, errors = state.errors.copy(email = null))
    }

    fun onPhoneChanged(value: String) = edit { state ->
        state.copy(
            phone = SignUpValidation.normalizePhone(value),
            errors = state.errors.copy(phone = null),
        )
    }

    fun onDocumentChanged(value: String) = edit { state ->
        state.copy(
            document = SignUpValidation.normalizeDocument(value, state.personType),
            errors = state.errors.copy(document = null),
        )
    }

    fun onPasswordChanged(value: String) = edit { state ->
        state.copy(password = value, errors = state.errors.copy(password = null))
    }

    fun onPasswordConfirmationChanged(value: String) = edit { state ->
        state.copy(
            passwordConfirmation = value,
            errors = state.errors.copy(passwordConfirmation = null),
        )
    }

    fun onSubmit() {
        val current = _uiState.value
        if (current.isProcessing || current.isProfilePending ||
            current.submissionState is SignUpSubmissionState.Success
        ) {
            return
        }

        val validation = SignUpValidation.validate(
            personType = current.personType,
            name = current.name,
            email = current.email,
            phone = current.phone,
            document = current.document,
            password = current.password,
            passwordConfirmation = current.passwordConfirmation,
        )

        val registration = validation.registration
        if (registration == null) {
            _uiState.update {
                it.copy(
                    errors = validation.errors,
                    focusRequest = validation.errors.firstInvalidField()?.let(::newFocusRequest),
                    submissionState = SignUpSubmissionState.Failure(
                        reason = SignUpFailureReason.INVALID_FORM,
                    ),
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                errors = validation.errors,
                submissionState = SignUpSubmissionState.Submitting(SignUpStage.CREATING_ACCOUNT),
            )
        }
        viewModelScope.launch {
            val result = repository.createAccount(registration) { stage ->
                _uiState.update { state ->
                    state.copy(submissionState = SignUpSubmissionState.Submitting(stage))
                }
            }
            applyRemoteResult(result)
        }
    }

    fun onRetryProfile() {
        val current = _uiState.value
        if (!current.isProfilePending || current.isProcessing) return

        _uiState.update {
            it.copy(
                submissionState = SignUpSubmissionState.Submitting(SignUpStage.RECOVERING_PROFILE),
            )
        }
        viewModelScope.launch {
            val result = repository.recoverProfile { stage ->
                _uiState.update { state ->
                    state.copy(submissionState = SignUpSubmissionState.Submitting(stage))
                }
            }
            applyRemoteResult(result)
        }
    }

    private fun edit(transform: (SignUpUiState) -> SignUpUiState) {
        _uiState.update { current ->
            if (!current.isInputEnabled) current else transform(current).copy(
                submissionState = SignUpSubmissionState.Editing,
            )
        }
    }

    private fun applyRemoteResult(result: SignUpResult) {
        _uiState.update { current ->
            val cleared = current.copy(password = "", passwordConfirmation = "")
            when (result) {
                is SignUpResult.Success -> cleared.copy(
                    errors = com.trustarx.pitgo.features.signup.model.SignUpFieldErrors(),
                    submissionState = SignUpSubmissionState.Success,
                )
                SignUpResult.EmailUnavailable -> cleared.copy(
                    errors = cleared.errors.copy(email = SignUpFieldError.UNAVAILABLE),
                    focusRequest = newFocusRequest(SignUpField.EMAIL),
                    submissionState = SignUpSubmissionState.Failure(
                        SignUpFailureReason.EMAIL_UNAVAILABLE,
                    ),
                )
                SignUpResult.DocumentUnavailable -> cleared.copy(
                    errors = cleared.errors.copy(document = SignUpFieldError.UNAVAILABLE),
                    focusRequest = newFocusRequest(SignUpField.DOCUMENT),
                    submissionState = SignUpSubmissionState.Failure(
                        SignUpFailureReason.DOCUMENT_UNAVAILABLE,
                    ),
                )
                SignUpResult.InvalidServerInput -> cleared.copy(
                    focusRequest = newFocusRequest(SignUpField.PASSWORD),
                    submissionState = SignUpSubmissionState.Failure(
                        SignUpFailureReason.INVALID_SERVER_INPUT,
                    ),
                )
                SignUpResult.RateLimited -> cleared.failure(SignUpFailureReason.RATE_LIMITED)
                SignUpResult.Offline -> cleared.failure(SignUpFailureReason.OFFLINE)
                SignUpResult.ServiceUnavailable ->
                    cleared.failure(SignUpFailureReason.SERVICE_UNAVAILABLE)
                SignUpResult.ProfileRecoveryRequired -> cleared.copy(
                    submissionState = SignUpSubmissionState.Failure(
                        reason = SignUpFailureReason.PROFILE_RECOVERY_REQUIRED,
                        profilePending = true,
                    ),
                )
                SignUpResult.EnvironmentMisconfigured ->
                    cleared.failure(SignUpFailureReason.ENVIRONMENT_MISCONFIGURED)
                SignUpResult.Unknown -> cleared.failure(SignUpFailureReason.UNKNOWN)
            }
        }
    }

    private fun SignUpUiState.failure(reason: SignUpFailureReason): SignUpUiState = copy(
        submissionState = SignUpSubmissionState.Failure(reason),
    )

    private fun newFocusRequest(field: SignUpField): FocusRequest = FocusRequest(
        field = field,
        requestId = ++focusRequestId,
    )

    companion object {
        fun factory(repository: SignUpRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(SignUpViewModel::class.java))
                    return SignUpViewModel(repository) as T
                }
            }
    }
}

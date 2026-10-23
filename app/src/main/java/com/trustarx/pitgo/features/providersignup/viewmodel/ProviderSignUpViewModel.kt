package com.trustarx.pitgo.features.providersignup.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.trustarx.pitgo.features.providersignup.model.ProviderSignUpFailure
import com.trustarx.pitgo.features.providersignup.model.ProviderSignUpResult
import com.trustarx.pitgo.features.providersignup.model.ProviderSignUpSubmission
import com.trustarx.pitgo.features.providersignup.model.ProviderSignUpUiState
import com.trustarx.pitgo.features.providersignup.model.ProviderSignUpValidation
import com.trustarx.pitgo.features.providersignup.repository.ProviderSignUpRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ProviderSignUpViewModel(private val repository: ProviderSignUpRepository, kind: com.trustarx.pitgo.core.account.ProviderKind) : ViewModel() {
    private val _state = MutableStateFlow(ProviderSignUpUiState(providerKind = kind))
    val state: StateFlow<ProviderSignUpUiState> = _state.asStateFlow()

    fun onEmailChanged(value: String) = edit { it.copy(email = value, emailError = null) }
    fun onPasswordChanged(value: String) = edit { it.copy(password = value, passwordError = null) }
    fun onConfirmationChanged(value: String) = edit {
        it.copy(passwordConfirmation = value, confirmationError = null)
    }

    fun submit() {
        if (_state.value.isProcessing) return
        val (credentials, validated) = ProviderSignUpValidation.validate(_state.value)
        if (credentials == null) {
            _state.value = validated.copy(
                submission = ProviderSignUpSubmission.Failure(ProviderSignUpFailure.INVALID_FORM),
            )
            return
        }
        _state.value = validated.copy(submission = ProviderSignUpSubmission.Submitting)
        viewModelScope.launch {
            _state.value = when (val result = repository.create(credentials, validated.providerKind)) {
                ProviderSignUpResult.Success -> validated.copy(
                    password = "",
                    passwordConfirmation = "",
                    submission = ProviderSignUpSubmission.Success,
                )
                is ProviderSignUpResult.Failure -> validated.copy(
                    password = "",
                    passwordConfirmation = "",
                    submission = ProviderSignUpSubmission.Failure(result.reason),
                )
            }
        }
    }

    private fun edit(transform: (ProviderSignUpUiState) -> ProviderSignUpUiState) {
        if (!_state.value.isProcessing) _state.update {
            transform(it).copy(submission = ProviderSignUpSubmission.Editing)
        }
    }

    companion object {
        fun factory(repository: ProviderSignUpRepository, kind: com.trustarx.pitgo.core.account.ProviderKind = com.trustarx.pitgo.core.account.ProviderKind.PITTEC): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ProviderSignUpViewModel(repository, kind) as T
            }
    }
}

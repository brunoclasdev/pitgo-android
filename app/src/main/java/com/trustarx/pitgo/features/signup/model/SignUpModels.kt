package com.trustarx.pitgo.features.signup.model

enum class PersonType {
    INDIVIDUAL,
    COMPANY,
}

enum class SignUpField {
    PERSON_TYPE,
    NAME,
    EMAIL,
    PHONE,
    DOCUMENT,
    PASSWORD,
    PASSWORD_CONFIRMATION,
}

enum class SignUpFieldError {
    REQUIRED,
    INVALID_EMAIL,
    INVALID_PHONE,
    INVALID_CPF,
    INVALID_CNPJ,
    PASSWORD_TOO_SHORT,
    PASSWORDS_DO_NOT_MATCH,
    UNAVAILABLE,
}

data class SignUpFieldErrors(
    val personType: SignUpFieldError? = null,
    val name: SignUpFieldError? = null,
    val email: SignUpFieldError? = null,
    val phone: SignUpFieldError? = null,
    val document: SignUpFieldError? = null,
    val password: SignUpFieldError? = null,
    val passwordConfirmation: SignUpFieldError? = null,
) {
    val hasErrors: Boolean
        get() = personType != null || name != null || email != null || phone != null ||
            document != null || password != null || passwordConfirmation != null

    fun firstInvalidField(): SignUpField? = when {
        personType != null -> SignUpField.PERSON_TYPE
        name != null -> SignUpField.NAME
        email != null -> SignUpField.EMAIL
        phone != null -> SignUpField.PHONE
        document != null -> SignUpField.DOCUMENT
        password != null -> SignUpField.PASSWORD
        passwordConfirmation != null -> SignUpField.PASSWORD_CONFIRMATION
        else -> null
    }
}

data class FocusRequest(
    val field: SignUpField,
    val requestId: Long,
)

data class AccountRegistration(
    val personType: PersonType,
    val name: String,
    val email: String,
    val phone: String,
    val cpf: String?,
    val cnpj: String?,
    val password: String,
) {
    init {
        require((cpf == null) xor (cnpj == null)) {
            "A registration must contain exactly one document."
        }
    }
}

enum class SignUpStage {
    CREATING_ACCOUNT,
    ENSURING_PROFILE,
    RECOVERING_PROFILE,
}

enum class SignUpFailureReason {
    INVALID_FORM,
    EMAIL_UNAVAILABLE,
    DOCUMENT_UNAVAILABLE,
    INVALID_SERVER_INPUT,
    RATE_LIMITED,
    OFFLINE,
    SERVICE_UNAVAILABLE,
    PROFILE_RECOVERY_REQUIRED,
    ENVIRONMENT_MISCONFIGURED,
    UNKNOWN,
}

sealed interface SignUpSubmissionState {
    data object Editing : SignUpSubmissionState
    data class Submitting(val stage: SignUpStage) : SignUpSubmissionState
    data class Failure(
        val reason: SignUpFailureReason,
        val profilePending: Boolean = false,
    ) : SignUpSubmissionState
    data object Success : SignUpSubmissionState
}

data class SignUpUiState(
    val personType: PersonType? = null,
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val document: String = "",
    val password: String = "",
    val passwordConfirmation: String = "",
    val errors: SignUpFieldErrors = SignUpFieldErrors(),
    val focusRequest: FocusRequest? = null,
    val submissionState: SignUpSubmissionState = SignUpSubmissionState.Editing,
) {
    val hasDraft: Boolean
        get() = personType != null || name.isNotBlank() || email.isNotBlank() ||
            phone.isNotBlank() || document.isNotBlank() || password.isNotEmpty() ||
            passwordConfirmation.isNotEmpty()

    val isProcessing: Boolean
        get() = submissionState is SignUpSubmissionState.Submitting

    val isProfilePending: Boolean
        get() = (submissionState as? SignUpSubmissionState.Failure)?.profilePending == true

    val isInputEnabled: Boolean
        get() = !isProcessing && !isProfilePending && submissionState !is SignUpSubmissionState.Success
}

sealed interface SignUpResult {
    data class Success(val userId: String) : SignUpResult
    data object EmailUnavailable : SignUpResult
    data object DocumentUnavailable : SignUpResult
    data object InvalidServerInput : SignUpResult
    data object RateLimited : SignUpResult
    data object Offline : SignUpResult
    data object ServiceUnavailable : SignUpResult
    data object ProfileRecoveryRequired : SignUpResult
    data object EnvironmentMisconfigured : SignUpResult
    data object Unknown : SignUpResult
}

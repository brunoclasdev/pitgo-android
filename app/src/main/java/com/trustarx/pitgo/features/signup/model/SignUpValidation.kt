package com.trustarx.pitgo.features.signup.model

import com.trustarx.pitgo.core.common.validation.BrazilianInputValidation

data class SignUpValidationResult(
    val registration: AccountRegistration?,
    val errors: SignUpFieldErrors,
)

object SignUpValidation {
    fun normalizePhone(value: String): String = BrazilianInputValidation.normalizePhone(value)

    fun normalizeDocument(value: String, personType: PersonType?): String = when (personType) {
        PersonType.INDIVIDUAL -> BrazilianInputValidation.normalizeCpf(value)
        PersonType.COMPANY -> BrazilianInputValidation.normalizeCnpj(value)
        null -> ""
    }

    fun validate(
        personType: PersonType?,
        name: String,
        email: String,
        phone: String,
        document: String,
        password: String,
        passwordConfirmation: String,
    ): SignUpValidationResult {
        val normalizedName = name.trim()
        val normalizedEmail = BrazilianInputValidation.normalizeEmail(email)
        val normalizedPhone = normalizePhone(phone)
        val normalizedDocument = normalizeDocument(document, personType)

        val errors = SignUpFieldErrors(
            personType = if (personType == null) SignUpFieldError.REQUIRED else null,
            name = if (normalizedName.isBlank()) SignUpFieldError.REQUIRED else null,
            email = when {
                normalizedEmail.isBlank() -> SignUpFieldError.REQUIRED
                !BrazilianInputValidation.isValidEmail(normalizedEmail) -> SignUpFieldError.INVALID_EMAIL
                else -> null
            },
            phone = when {
                normalizedPhone.isBlank() -> SignUpFieldError.REQUIRED
                !isValidPhone(normalizedPhone) -> SignUpFieldError.INVALID_PHONE
                else -> null
            },
            document = when {
                personType == null -> null
                normalizedDocument.isBlank() -> SignUpFieldError.REQUIRED
                personType == PersonType.INDIVIDUAL && !isValidCpf(normalizedDocument) ->
                    SignUpFieldError.INVALID_CPF
                personType == PersonType.COMPANY && !isValidCnpj(normalizedDocument) ->
                    SignUpFieldError.INVALID_CNPJ
                else -> null
            },
            password = when {
                password.isEmpty() -> SignUpFieldError.REQUIRED
                password.length < 8 -> SignUpFieldError.PASSWORD_TOO_SHORT
                else -> null
            },
            passwordConfirmation = when {
                passwordConfirmation.isEmpty() -> SignUpFieldError.REQUIRED
                passwordConfirmation != password -> SignUpFieldError.PASSWORDS_DO_NOT_MATCH
                else -> null
            },
        )

        if (errors.hasErrors || personType == null) {
            return SignUpValidationResult(registration = null, errors = errors)
        }

        return SignUpValidationResult(
            registration = AccountRegistration(
                personType = personType,
                name = normalizedName,
                email = normalizedEmail,
                phone = normalizedPhone,
                cpf = normalizedDocument.takeIf { personType == PersonType.INDIVIDUAL },
                cnpj = normalizedDocument.takeIf { personType == PersonType.COMPANY },
                password = password,
            ),
            errors = errors,
        )
    }

    fun isValidPhone(value: String): Boolean = BrazilianInputValidation.isValidPhone(value)

    fun isValidCpf(value: String): Boolean = BrazilianInputValidation.isValidCpf(value)

    fun isValidCnpj(value: String): Boolean = BrazilianInputValidation.isValidCnpj(value)
}

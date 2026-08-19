package com.trustarx.pitgo.features.signup.model

import java.util.Locale

data class SignUpValidationResult(
    val registration: AccountRegistration?,
    val errors: SignUpFieldErrors,
)

object SignUpValidation {
    private val emailPattern = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    private val validBrazilianAreaCodes = setOf(
        11, 12, 13, 14, 15, 16, 17, 18, 19,
        21, 22, 24, 27, 28,
        31, 32, 33, 34, 35, 37, 38,
        41, 42, 43, 44, 45, 46, 47, 48, 49,
        51, 53, 54, 55,
        61, 62, 63, 64, 65, 66, 67, 68, 69,
        71, 73, 74, 75, 77, 79,
        81, 82, 83, 84, 85, 86, 87, 88, 89,
        91, 92, 93, 94, 95, 96, 97, 98, 99,
    )

    fun normalizePhone(value: String): String = value.filter(Char::isDigit).take(11)

    fun normalizeDocument(value: String, personType: PersonType?): String = when (personType) {
        PersonType.INDIVIDUAL -> value.filter(Char::isDigit).take(11)
        PersonType.COMPANY -> value
            .uppercase(Locale.ROOT)
            .filter { it.isDigit() || it in 'A'..'Z' }
            .take(14)
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
        val normalizedEmail = email.trim().lowercase(Locale.ROOT)
        val normalizedPhone = normalizePhone(phone)
        val normalizedDocument = normalizeDocument(document, personType)

        val errors = SignUpFieldErrors(
            personType = if (personType == null) SignUpFieldError.REQUIRED else null,
            name = if (normalizedName.isBlank()) SignUpFieldError.REQUIRED else null,
            email = when {
                normalizedEmail.isBlank() -> SignUpFieldError.REQUIRED
                !emailPattern.matches(normalizedEmail) -> SignUpFieldError.INVALID_EMAIL
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

    fun isValidPhone(value: String): Boolean =
        value.length in 10..11 && value.take(2).toIntOrNull() in validBrazilianAreaCodes

    fun isValidCpf(value: String): Boolean {
        if (value.length != 11 || !value.all(Char::isDigit) || value.toSet().size == 1) return false
        val first = modulo11Digit(value.take(9), 10)
        val second = modulo11Digit(value.take(9) + first, 11)
        return value.takeLast(2) == "$first$second"
    }

    fun isValidCnpj(value: String): Boolean {
        if (!Regex("^[0-9A-Z]{12}[0-9]{2}$").matches(value)) return false
        if (value.all(Char::isDigit) && value.toSet().size == 1) return false

        val firstWeights = intArrayOf(5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2)
        val secondWeights = intArrayOf(6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2)
        val bodyValues = value.take(12).map { it.code - '0'.code }
        val first = cnpjDigit(bodyValues, firstWeights)
        val second = cnpjDigit(bodyValues + first, secondWeights)
        return value.takeLast(2) == "$first$second"
    }

    private fun modulo11Digit(value: String, initialWeight: Int): Int {
        val total = value.mapIndexed { index, character ->
            character.digitToInt() * (initialWeight - index)
        }.sum()
        val remainder = total % 11
        return if (remainder < 2) 0 else 11 - remainder
    }

    private fun cnpjDigit(values: List<Int>, weights: IntArray): Int {
        val remainder = values.indices.sumOf { values[it] * weights[it] } % 11
        return if (remainder < 2) 0 else 11 - remainder
    }
}

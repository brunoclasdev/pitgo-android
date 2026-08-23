package com.trustarx.pitgo.core.common.validation

import java.util.Locale

object BrazilianInputValidation {
    private val emailPattern = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    private val validAreaCodes = setOf(
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

    val states = listOf(
        "AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO", "MA", "MT", "MS",
        "MG", "PA", "PB", "PR", "PE", "PI", "RJ", "RN", "RS", "RO", "RR", "SC",
        "SP", "SE", "TO",
    )

    fun normalizeEmail(value: String): String = value.trim().lowercase(Locale.ROOT)

    fun isValidEmail(value: String): Boolean = emailPattern.matches(normalizeEmail(value))

    fun normalizePhone(value: String): String = value.filter(Char::isDigit).take(11)

    fun isValidPhone(value: String): Boolean {
        val normalized = normalizePhone(value)
        return normalized.length in 10..11 && normalized.take(2).toIntOrNull() in validAreaCodes
    }

    fun normalizeCpf(value: String): String = value.filter(Char::isDigit).take(11)

    fun normalizeCnpj(value: String): String = value
        .uppercase(Locale.ROOT)
        .filter { it.isDigit() || it in 'A'..'Z' }
        .take(14)

    fun normalizePostalCode(value: String): String = value.filter(Char::isDigit).take(8)

    fun isValidPostalCode(value: String): Boolean = normalizePostalCode(value).length == 8

    fun isValidState(value: String): Boolean = value.uppercase(Locale.ROOT) in states

    fun isValidCpf(value: String): Boolean {
        val normalized = normalizeCpf(value)
        if (normalized.length != 11 || normalized.toSet().size == 1) return false
        val first = modulo11Digit(normalized.take(9), 10)
        val second = modulo11Digit(normalized.take(9) + first, 11)
        return normalized.takeLast(2) == "$first$second"
    }

    fun isValidCnpj(value: String): Boolean {
        val normalized = normalizeCnpj(value)
        if (!Regex("^[0-9A-Z]{12}[0-9]{2}$").matches(normalized)) return false
        if (normalized.all(Char::isDigit) && normalized.toSet().size == 1) return false

        val firstWeights = intArrayOf(5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2)
        val secondWeights = intArrayOf(6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2)
        val bodyValues = normalized.take(12).map { it.code - '0'.code }
        val first = cnpjDigit(bodyValues, firstWeights)
        val second = cnpjDigit(bodyValues + first, secondWeights)
        return normalized.takeLast(2) == "$first$second"
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

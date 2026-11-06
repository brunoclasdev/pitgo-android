package com.trustarx.pitgo.core.common.validation

import com.google.i18n.phonenumbers.PhoneNumberUtil

object InternationalPhone {
    private val util = PhoneNumberUtil.getInstance()
    val regions: Set<String> = util.supportedRegions

    fun callingCode(region: String): Int = util.getCountryCodeForRegion(region)

    fun format(digits: String, region: String): String {
        val formatter = util.getAsYouTypeFormatter(region)
        return digits.fold("") { _, digit -> formatter.inputDigit(digit) }
    }

    fun toE164(digits: String, region: String): String? = runCatching {
        val number = util.parse(digits, region)
        if (util.isValidNumberForRegion(number, region)) {
            util.format(number, PhoneNumberUtil.PhoneNumberFormat.E164)
        } else null
    }.getOrNull()
}

package com.trustarx.pitgo.core.ui

import androidx.compose.ui.text.AnnotatedString
import com.trustarx.pitgo.core.common.validation.InternationalPhone
import org.junit.Assert.*
import org.junit.Test

class InternationalPhoneTest {
    @Test fun validatesAndPreservesCountryCode() {
        assertEquals("+5511912345678", InternationalPhone.toE164("11912345678", "BR"))
        assertEquals("+12025550123", InternationalPhone.toE164("2025550123", "US"))
        assertEquals("+351912345678", InternationalPhone.toE164("912345678", "PT"))
        assertNull(InternationalPhone.toE164("123", "BR"))
        assertNull(InternationalPhone.toE164("11912345678", "US"))
    }

    @Test fun formatsForSelectedCountryAndMapsEveryCursorPosition() {
        assertEquals("11 91234-5678", InternationalPhone.format("11912345678", "BR"))
        assertEquals("(202) 555-0123", InternationalPhone.format("2025550123", "US"))
        for ((region, digits) in listOf("BR" to "11912345678", "US" to "2025550123", "PT" to "912345678", "GB" to "02079460018")) {
            for (length in 0..digits.length) {
                val original = digits.take(length)
                val result = CountryPhoneTransformation(region).filter(AnnotatedString(original))
                for (offset in 0..original.length) {
                    val mapped = result.offsetMapping.originalToTransformed(offset)
                    assertTrue(mapped in 0..result.text.length)
                    assertEquals(offset, result.offsetMapping.transformedToOriginal(mapped))
                }
                for (offset in 0..result.text.length) {
                    assertTrue(result.offsetMapping.transformedToOriginal(offset) in 0..original.length)
                }
            }
        }
    }
}

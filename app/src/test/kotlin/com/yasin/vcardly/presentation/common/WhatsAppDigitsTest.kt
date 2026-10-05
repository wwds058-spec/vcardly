package com.yasin.vcardly.presentation.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WhatsAppDigitsTest {
    @Test fun internationalNumber_keepsCountryCode() = assertEquals("919876543210", whatsAppDigits("+91 98765 43210"))
    @Test fun punctuationIsStripped() = assertEquals("971501234567", whatsAppDigits("+971 (50) 123-4567"))
    @Test fun leadingZerosDropped() = assertEquals("4420794600", whatsAppDigits("0044 2079 4600"))
    @Test fun tooShort_isNull() = assertNull(whatsAppDigits("12345"))
    @Test fun tooLong_isNull() = assertNull(whatsAppDigits("1234567890123456"))
    @Test fun blank_isNull() = assertNull(whatsAppDigits("  "))
}

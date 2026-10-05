package com.yasin.vcardly.presentation.common

import org.junit.Assert.assertEquals
import org.junit.Test

class InitialsTest {
    @Test fun twoWords() = assertEquals("AS", initialsOf("asha  Singh"))
    @Test fun threeWords_usesFirstAndLast() = assertEquals("JS", initialsOf("John Quincy Smith"))
    @Test fun oneWord() = assertEquals("B", initialsOf("bilal"))
    @Test fun blank() = assertEquals("", initialsOf("   "))
    @Test fun supplementaryCodePoint_notSplit() = assertEquals("😀", initialsOf("😀 Smile").take(2))
    @Test fun nonLatin() = assertEquals("హర", initialsOf("హరి రమ"))
}

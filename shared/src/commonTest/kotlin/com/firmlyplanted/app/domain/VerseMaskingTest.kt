package com.firmlyplanted.app.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class VerseMaskingTest {

    @Test
    fun firstWordKeepsLeadingPunctuation() {
        assertEquals("“In", VerseMasking.firstWords("“In the beginning God", count = 1))
    }

    @Test
    fun firstLetterMasksRestOfFirstWord() {
        assertEquals("B-------,", VerseMasking.firstLetter("Brothers, I do not consider"))
        assertEquals("“I-", VerseMasking.firstLetter("“In the beginning"))
    }

    @Test
    fun firstLetterOfEmptyTextIsEmpty() {
        assertEquals("", VerseMasking.firstLetter("   "))
    }
}

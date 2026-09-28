package com.firmlyplanted.app.data.remote

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UsxVerseParserTest {

    private val usx = """
        <?xml version="1.0" encoding="utf-8"?>
        <!-- fetch.bible style milestones -->
        <usx version="3.0">
          <book code="JHN" style="id">John</book>
          <chapter number="1" style="c" sid="JHN 1" />
          <para style="p">
            <verse number="1" style="v" sid="JHN 1:1" />In the <char style="w" strong="G746">beginning</char>
            was the Word<note caller="+" style="f"><char style="ft">Or &quot;Logos&quot;</char></note>,
            &amp; the Word was with God.<verse eid="JHN 1:1" />
            <verse number="2" style="v" sid="JHN 1:2"/>He was &#x201C;in&#8221; the beginning.<verse eid="JHN 1:2" />
          </para>
          <chapter eid="JHN 1" />
          <chapter number="2" style="c" sid="JHN 2" />
          <para style="p"><verse number="1" style="v" sid="JHN 2:1" /><![CDATA[On the third day]]><verse eid="JHN 2:1" /></para>
        </usx>
    """.trimIndent()

    @Test
    fun extractsVerseTextSkippingNotesAndResolvingEntities() {
        assertEquals(
            listOf(
                ParsedVerse(1, 1, "In the beginning was the Word , & the Word was with God."),
                ParsedVerse(1, 2, "He was “in” the beginning."),
                ParsedVerse(2, 1, "On the third day"),
            ),
            UsxVerseParser.parse(usx),
        )
    }

    @Test
    fun parseRangeFiltersAcrossChapters() {
        assertEquals(
            listOf(1 to 2, 2 to 1),
            UsxVerseParser.parseRange(usx, 1, 2, 2, 1).map { it.chapter to it.verse },
        )
    }

    @Test
    fun malformedXmlThrows() {
        assertFailsWith<IllegalArgumentException> { UsxVerseParser.parse("<usx><verse number=\"1\"") }
    }
}

package com.firmlyplanted.app.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Texts are from the World English Bible (public domain), plus a few invented verses for edge cases. */
class PastedTextParserTest {

    private val lro = '‭' // YouVersion wraps reference parts in these direction marks
    private val pdf = '‬'

    private fun parse(raw: String) = PastedTextParser.parse(raw).getOrThrow()

    @Test
    fun youVersionMultiVerseCopyWithReferenceAndLink() {
        val passage = parse(
            "16 For God so loved the world, that he gave his one and only Son, that whoever believes in him " +
                "should not perish, but have eternal life. 17 For God didn't send his Son into the world to judge " +
                "the world, but that the world should be saved through him.\n\n" +
                "$lro${lro}John$pdf $lro$lro${"3"}$pdf:$lro${"16"}$pdf-17$pdf $lro${"WEB"}$pdf$pdf\n" +
                "https://bible.com/bible/206/jhn.3.16-17.WEB",
        )

        assertEquals("John", passage.reference.book?.name)
        assertEquals("WEB", passage.reference.translationAbbrev)
        assertEquals("https://bible.com/bible/206/jhn.3.16-17.WEB", passage.reference.link)
        assertEquals(listOf(3 to 16, 3 to 17), passage.verses.map { it.chapter to it.verse })
        assertTrue(passage.verses[0].text.startsWith("For God so loved the world"))
        assertTrue(passage.verses[1].text.endsWith("saved through him."))
        assertNull(passage.skippedPrefix)
    }

    @Test
    fun oneVersePerLineWorksToo() {
        val passage = parse("1 In the beginning was the Word,\n2 The same was in the beginning with God.\nJohn 1:1-2 WEB")

        assertEquals(listOf(1, 2), passage.verses.map { it.verse })
        assertEquals("In the beginning was the Word,", passage.verses[0].text)
    }

    @Test
    fun numbersInsideVersesAreNotTakenAsVerseNumbers() {
        val passage = parse(
            "10 He said, bring the fish. 11 The net was full of 153 great fish, and 7,000 men saw it, as in 3:16. " +
                "12 Come and eat breakfast.\nJohn 21:10-12",
        )

        assertEquals(listOf(10, 11, 12), passage.verses.map { it.verse })
        assertEquals("The net was full of 153 great fish, and 7,000 men saw it, as in 3:16.", passage.verses[1].text)
    }

    @Test
    fun verseNumberingRestartingAtOneStartsANewChapter() {
        val passage = parse("22 So Naomi returned. 1 Naomi had a relative. 2 Ruth said to Naomi, let me go.\nRuth 1:22-2:2 WEB")

        assertEquals(listOf(1 to 22, 2 to 1, 2 to 2), passage.verses.map { it.chapter to it.verse })
        assertEquals(2, passage.reference.endChapter)
        assertEquals(2, passage.reference.endVerse)
    }

    @Test
    fun singleSharedVerseInQuotesWithTrailingReference() {
        val passage = parse("“Jesus wept.” John 11:35 WEB")

        assertEquals(listOf(PastedVerse(11, 35, "Jesus wept.")), passage.verses)
        assertEquals("John", passage.reference.book?.name)
    }

    @Test
    fun firstVerseCopiedWithoutItsNumber() {
        val passage = parse("For God so loved the world. 17 For God didn't send his Son to judge the world.\nJohn 3:16-17")

        assertEquals(listOf(16, 17), passage.verses.map { it.verse })
        assertEquals("For God so loved the world.", passage.verses[0].text)
    }

    @Test
    fun referenceOnTheFirstLine() {
        val passage = parse("Psalm 23:1-2 WEB\n1 Yahweh is my shepherd: I shall lack nothing. 2 He makes me lie down in green pastures.")

        assertEquals("Psalms", passage.reference.book?.name)
        assertEquals(listOf(23 to 1, 23 to 2), passage.verses.map { it.chapter to it.verse })
    }

    @Test
    fun linkIdentifiesTheBookWhenTheReferenceIsInAnotherLanguage() {
        val passage = parse(
            "16 Vì Ðức Chúa Trời yêu thương thế gian. 17 Vả, Ðức Chúa Trời đã sai Con Ngài.\n" +
                "Giăng 3:16-17 VIE1925\nhttps://bible.com/bible/193/JHN.3.16-17.VIE1925",
        )

        assertEquals("John", passage.reference.book?.name)
        assertEquals("VIE1925", passage.reference.translationAbbrev)
        assertEquals(listOf(16, 17), passage.verses.map { it.verse })
        assertTrue(passage.verses.none { "Giăng" in it.text })
    }

    @Test
    fun noReferenceStartsFromTheFirstVerseNumber() {
        val passage = parse("5 Blessed are the gentle. 6 Blessed are those who hunger.")

        assertNull(passage.reference.book)
        assertEquals(listOf(1 to 5, 1 to 6), passage.verses.map { it.chapter to it.verse })
    }

    @Test
    fun textBeforeTheFirstVerseNumberIsReportedNotLost() {
        val passage = parse("The Word Became Flesh\n1 In the beginning was the Word. 2 The same was with God.\nJohn 1:1-2")

        assertEquals("The Word Became Flesh", passage.skippedPrefix)
        assertEquals(listOf(1, 2), passage.verses.map { it.verse })
    }

    // --- Headings -------------------------------------------------------------------------------

    @Test
    fun sectionHeadingBetweenVersesIsLeftOut() {
        val passage = parse(
            "13 who were born not of blood, but of God.\n\nThe Word Became Flesh\n\n" +
                "14 The Word became flesh, and lived among us.\nJohn 1:13-14",
        )

        assertEquals("who were born not of blood, but of God.", passage.verses[0].text)
        assertEquals("The Word became flesh, and lived among us.", passage.verses[1].text)
        assertEquals(listOf("The Word Became Flesh"), passage.headings)
    }

    @Test
    fun headingWithoutBlankLinesAndStackedParallelReference() {
        val passage = parse(
            "12 He will burn up the chaff with unquenchable fire.\nThe Baptism of Jesus\n(Mark 1:9-11; Luke 3:21-22)\n" +
                "13 Then Jesus came from Galilee to the Jordan.\nMatthew 3:12-13",
        )

        assertEquals(listOf(12, 13), passage.verses.map { it.verse })
        assertEquals("He will burn up the chaff with unquenchable fire.", passage.verses[0].text)
        assertEquals(listOf("The Baptism of Jesus", "(Mark 1:9-11; Luke 3:21-22)"), passage.headings)
    }

    @Test
    fun poetryLineBreaksStayInTheVerse() {
        val passage = parse(
            "1 Blessed is the man\nwho walks not in the counsel of the wicked,\nnor stands in the way of sinners,\n" +
                "nor sits in the seat of scoffers;\n2 but his delight is in the law of the LORD,\n" +
                "and on his law he meditates day and night.\nPsalm 1:1-2",
        )

        assertTrue(passage.verses[0].text.endsWith("nor sits in the seat of scoffers;"))
        assertTrue(passage.verses[0].text.startsWith("Blessed is the man who walks not"))
        assertEquals(emptyList(), passage.headings)
    }

    @Test
    fun lastLineWithoutPunctuationIsNotMistakenForAHeading() {
        val passage = parse("3 And God said to them\n4 Go into the city.\n5 And they went into the city of David\nGenesis 9:3-5")

        assertEquals("And God said to them", passage.verses[0].text)
        assertEquals("And they went into the city of David", passage.verses[2].text)
        assertEquals(emptyList(), passage.headings)
    }

    @Test
    fun chapterTitlesAndPsalmSuperscriptionsAreLeftOut() {
        val passage = parse(
            "6 For the LORD knows the way of the righteous, but the way of the wicked shall perish.\n\nPsalm 2\n\n" +
                "1 Why do the nations rage and the peoples plot in vain?\n" +
                "2 The kings of the earth set themselves.\n\nPsalm 3\n" +
                "A Psalm of David, when he fled from Absalom his son.\n" +
                "1 O LORD, how many are my foes!\nPsalm 1:6-3:1",
        )

        assertEquals(listOf(1 to 6, 2 to 1, 2 to 2, 3 to 1), passage.verses.map { it.chapter to it.verse })
        assertTrue(passage.verses[0].text.endsWith("shall perish."))
        assertEquals("The kings of the earth set themselves.", passage.verses[2].text)
        assertEquals(listOf("Psalm 2", "Psalm 3", "A Psalm of David, when he fled from Absalom his son."), passage.headings)
    }

    @Test
    fun uncasedScriptHeadingIsDetectedWithABlankLine() {
        val passage = parse("1 בְּרֵאשִׁית בָּרָא אֱלֹהִים׃\n\nבריאת העולם\n\n2 וְהָאָרֶץ הָיְתָה תֹהוּ׃\nGenesis 1:1-2")

        assertEquals(listOf("בריאת העולם"), passage.headings)
        assertEquals("בְּרֵאשִׁית בָּרָא אֱלֹהִים׃", passage.verses[0].text)
    }

    @Test
    fun headingBeforeAnUnnumberedFirstVerse() {
        val passage = parse("God's Love for the World\nFor God so loved the world. 17 For God didn't send his Son.\nJohn 3:16-17")

        assertEquals(listOf("God's Love for the World"), passage.headings)
        assertEquals("For God so loved the world.", passage.verses[0].text)
    }

    @Test
    fun headingDetectionCanBeTurnedOff() {
        val passage = PastedTextParser.parse(
            "13 born of God.\n\nThe Word Became Flesh\n\n14 The Word became flesh.\nJohn 1:13-14",
            detectHeadings = false,
        ).getOrThrow()

        assertEquals("born of God. The Word Became Flesh", passage.verses[0].text)
        assertEquals(emptyList(), passage.headings)
    }

    @Test
    fun textWithNoNumbersAndNoReferenceIsRejected() {
        assertTrue(PastedTextParser.parse("For God so loved the world.").isFailure)
        assertTrue(PastedTextParser.parse("   ").isFailure)
    }

    @Test
    fun detectsHebrewAndGreekScripts() {
        assertEquals("Hebrew", PastedTextParser.detectLanguage("בְּרֵאשִׁית בָּרָא אֱלֹהִים"))
        assertEquals("Greek", PastedTextParser.detectLanguage("Ἐν ἀρχῇ ἦν ὁ λόγος"))
        assertEquals("Other", PastedTextParser.detectLanguage("In the beginning"))
    }
}

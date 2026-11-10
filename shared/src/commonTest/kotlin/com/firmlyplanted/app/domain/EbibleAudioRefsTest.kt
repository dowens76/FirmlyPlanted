package com.firmlyplanted.app.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** App verse references -> eBible.org audio clips: WLC (English-style) vs Masoretic, and SBLGNT vs Nestle 1904. */
class EbibleAudioRefsTest {

    private val hebrew = EbibleAudioRefs.HEBREW
    private val greek = EbibleAudioRefs.GREEK

    private fun resolve(book: String, chapter: Int, verse: Int) = hebrew.resolve(book, chapter, verse)

    @Test
    fun sameNumberingWhereTheyAgree() {
        assertEquals(listOf(EbibleClipRef(chapter = 1, verse = 1)), resolve("Genesis", 1, 1))
        assertEquals(listOf(EbibleClipRef(chapter = 2, verse = 1)), resolve("Ruth", 2, 1))
    }

    @Test
    fun psalmTitlesShiftVerses() {
        // Hebrew Ps 3:1 is the title, so the app's 3:1 is read as Hebrew 3:2.
        assertEquals(listOf(EbibleClipRef(chapter = 3, verse = 2)), resolve("Psalms", 3, 1))
        assertEquals(listOf(EbibleClipRef(chapter = 3, verse = 9)), resolve("Psalms", 3, 8))
    }

    @Test
    fun chapterBoundariesThatDiffer() {
        assertEquals(listOf(EbibleClipRef(chapter = 3, verse = 19)), resolve("Malachi", 4, 1))
        assertEquals(listOf(EbibleClipRef(chapter = 4, verse = 1)), resolve("Joel", 3, 1))
        assertEquals(listOf(EbibleClipRef(chapter = 3, verse = 1)), resolve("Joel", 2, 28))
        // English Gen 31:55 is Hebrew 32:1, so the rest of chapter 32 shifts by one.
        assertEquals(listOf(EbibleClipRef(chapter = 32, verse = 1)), resolve("Genesis", 31, 55))
        assertEquals(listOf(EbibleClipRef(chapter = 32, verse = 2)), resolve("Genesis", 32, 1))
        assertEquals(listOf(EbibleClipRef(chapter = 32, verse = 33)), resolve("Genesis", 32, 32))
    }

    @Test
    fun mislabelledClipsUseExactIds() {
        assertEquals(listOf(EbibleClipRef(chapter = 119, clipId = "117c")), resolve("Psalms", 119, 118))
        assertEquals(
            listOf(EbibleClipRef(chapter = 119, clipId = "117a"), EbibleClipRef(chapter = 119, clipId = "117b")),
            resolve("Psalms", 119, 117),
        )
    }

    @Test
    fun aVerseCanSpanTwoAudioChapters() {
        val refs = resolve("1 Samuel", 20, 42)
        assertEquals(setOf(20, 21), refs.map { it.chapter }.toSet())
    }

    @Test
    fun noAudioForUnmatchedVersesOrOtherBooks() {
        assertTrue(resolve("1 Chronicles", 1, 41).isEmpty())
        assertTrue(resolve("John", 3, 16).isEmpty())
    }

    @Test
    fun clipRefMatching() {
        val whole = EbibleClipRef(chapter = 1, verse = 12)
        assertTrue(whole.matches("12"))
        assertTrue(whole.matches("12a"))
        assertTrue(whole.matches("12c"))
        assertFalse(whole.matches("1"))
        assertFalse(whole.matches("120a"))
        assertFalse(whole.matches("a"))
        assertTrue(EbibleClipRef(chapter = 1, clipId = "b").matches("b"))
    }

    @Test
    fun epubFileNames() {
        assertEquals("Tanakh fluent 08-Ruth רות.epub", hebrew.fileName(hebrew.bookFile("Ruth")!!, AudioSpeed.FLUENT))
        assertEquals(
            "Tanakh slower 09-First Samuel שמואל א.epub",
            hebrew.fileName(hebrew.bookFile("1 Samuel")!!, AudioSpeed.SLOWER),
        )
        assertEquals(39, hebrew.bookFiles.size)
        // Greek has one recording; a stray SLOWER setting still names the only file.
        assertEquals("56_Philippians_ΠΡΟΣ ΦΙΛΙΠΠΗΣΙΟΥΣ.epub", greek.fileName(greek.bookFile("Philippians")!!, AudioSpeed.SLOWER))
        assertEquals("68_1 John_ΙΩΑΝΟΥ Α.epub", greek.fileName(greek.bookFile("1 John")!!, AudioSpeed.FLUENT))
    }

    @Test
    fun editionsByTranslation() {
        assertEquals(hebrew, EbibleAudioRefs.forTranslation(DefaultTranslations.WLC_ID))
        assertEquals(greek, EbibleAudioRefs.forTranslation(DefaultTranslations.SBLGNT_ID))
        assertEquals(null, EbibleAudioRefs.forTranslation(DefaultTranslations.ESV_ID))
        assertEquals(listOf(AudioSpeed.FLUENT), greek.speeds)
    }

    @Test
    fun greekVersesMapToTheSameNumbers() {
        assertEquals(listOf(EbibleClipRef(chapter = 1, verse = 1)), greek.resolve("Philippians", 1, 1))
        assertEquals(listOf(EbibleClipRef(chapter = 3, verse = 16)), greek.resolve("John", 3, 16))
        // Books eBible.org hasn't recorded yet have no audio.
        assertTrue(greek.resolve("Acts", 1, 1).isEmpty())
        assertTrue(greek.resolve("Genesis", 1, 1).isEmpty())
    }

    @Test
    fun greekVerseDivisionsThatDiffer() {
        // Nestle 1904 ends Eph 5:13 earlier than SBLGNT; the rest of its 5:13 is read with 5:14.
        assertEquals(listOf(EbibleClipRef(chapter = 5, clipId = "13a")), greek.resolve("Ephesians", 5, 13))
        assertEquals("13b", greek.resolve("Ephesians", 5, 14).first().clipId)
    }

    @Test
    fun greekWordingDifferencesAreFlagged() {
        // John 1:34: Nestle 1904 "ὁ υἱὸς τοῦ θεοῦ", SBLGNT "ὁ ἐκλεκτὸς τοῦ θεοῦ".
        assertTrue(greek.wordingDiffers("John", 1, 34))
        // John 1:15 differs only in spelling (Ἰωάνης / Ἰωάννης), which isn't flagged.
        assertFalse(greek.wordingDiffers("John", 1, 15))
        assertFalse(greek.wordingDiffers("Philippians", 1, 1))
        assertFalse(hebrew.wordingDiffers("Genesis", 1, 1))
    }
}

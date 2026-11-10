package com.firmlyplanted.app.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** App (English-style) WLC references -> eBible.org (Masoretic) audio clips. */
class EbibleAudioRefsTest {

    private fun resolve(book: String, chapter: Int, verse: Int) = EbibleAudioRefs.resolve(book, chapter, verse)

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
        val ruth = EbibleAudioRefs.bookFile("Ruth")!!
        assertEquals("Tanakh fluent 08-Ruth רות.epub", ruth.fileName(AudioSpeed.FLUENT))
        assertEquals(
            "Tanakh slower 09-First Samuel שמואל א.epub",
            EbibleAudioRefs.bookFile("1 Samuel")!!.fileName(AudioSpeed.SLOWER),
        )
        assertEquals(39, EbibleAudioIndex.bookFiles.size)
    }
}

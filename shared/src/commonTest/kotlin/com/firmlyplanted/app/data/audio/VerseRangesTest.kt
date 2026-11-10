package com.firmlyplanted.app.data.audio

import com.firmlyplanted.app.domain.EbibleClipRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VerseRangesTest {

    private val chapter20 = listOf(SmilClip("41", 100.0, 110.0), SmilClip("42a", 110.0, 115.5), SmilClip("42b", 115.5, 121.0))
    private val chapter21 = listOf(SmilClip("1", 0.4, 6.2), SmilClip("2a", 6.2, 9.0))
    private val clips = mapOf(20 to chapter20, 21 to chapter21)

    private fun segments(refs: List<EbibleClipRef>, available: Map<Int, List<SmilClip>> = clips) =
        VerseRanges.segments(refs, clipsFor = { available[it] }, pathFor = { "/audio/$it.mp3" })

    @Test
    fun spansAllPartsOfAVerse() {
        assertEquals(
            listOf(AudioSegment("/audio/20.mp3", 110.0, 121.0)),
            segments(listOf(EbibleClipRef(chapter = 20, verse = 42))),
        )
    }

    @Test
    fun aVerseAcrossTwoChaptersPlaysBothInOrder() {
        // 1 Sam 20:42 (English) = Hebrew 20:42 + 21:1.
        assertEquals(
            listOf(AudioSegment("/audio/20.mp3", 110.0, 121.0), AudioSegment("/audio/21.mp3", 0.4, 6.2)),
            segments(listOf(EbibleClipRef(chapter = 20, verse = 42), EbibleClipRef(chapter = 21, verse = 1))),
        )
    }

    @Test
    fun exactClipIdsForMislabelledClips() {
        assertEquals(
            listOf(AudioSegment("/audio/20.mp3", 115.5, 121.0)),
            segments(listOf(EbibleClipRef(chapter = 20, clipId = "42b"))),
        )
    }

    @Test
    fun nothingUntilEveryChapterIsDownloaded() {
        val refs = listOf(EbibleClipRef(chapter = 20, verse = 42), EbibleClipRef(chapter = 21, verse = 1))
        assertNull(segments(refs, available = mapOf(20 to chapter20)))
    }

    @Test
    fun nothingWhenNoClipMatches() {
        assertNull(segments(listOf(EbibleClipRef(chapter = 21, verse = 9))))
        assertNull(segments(emptyList()))
    }
}

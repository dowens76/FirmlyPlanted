package com.firmlyplanted.app.data.audio

import com.firmlyplanted.app.domain.EbibleClipRef

/** Seconds [start]..[end] of the unaltered chapter recording at [path]. */
data class AudioSegment(val path: String, val start: Double, val end: Double)

/**
 * Where one app verse is in the chapter recordings: usually one segment; two when the verse
 * spans two ePub chapters (e.g. 1 Sam 20:42 = Hebrew 20:42 + 21:1). Players play the segments in
 * order and stop at each segment's end. [key] identifies the verse for "now playing" state.
 */
data class VerseAudio(val key: String, val segments: List<AudioSegment>)

object VerseRanges {
    /**
     * The verse's time ranges: for each ePub chapter its [refs] touch (in order), the span from the
     * first matching SMIL clip's start to the last one's end. Clips are contiguous in eBible's
     * SMIL, so this is exactly the verse as the ePub's own media overlay marks it.
     *
     * [clipsFor] gives a chapter's parsed SMIL, or null if it isn't downloaded. Returns null when
     * any chapter is missing or a ref matches no clip.
     */
    fun segments(
        refs: List<EbibleClipRef>,
        clipsFor: (ebibleChapter: Int) -> List<SmilClip>?,
        pathFor: (ebibleChapter: Int) -> String,
    ): List<AudioSegment>? {
        if (refs.isEmpty()) return null
        val chapters = refs.map { it.chapter }.distinct()
        return chapters.map { chapter ->
            val clips = clipsFor(chapter) ?: return null
            val matching = clips.filter { clip -> refs.any { it.chapter == chapter && it.matches(clip.id) } }
            if (matching.isEmpty()) return null
            AudioSegment(pathFor(chapter), matching.minOf { it.begin }, matching.maxOf { it.end })
        }
    }
}

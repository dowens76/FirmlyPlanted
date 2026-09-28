package com.firmlyplanted.app.data.repository

import com.firmlyplanted.app.data.remote.EsvApiService
import com.firmlyplanted.app.data.remote.EsvVerseSplitter
import com.firmlyplanted.app.data.remote.FetchBibleService
import com.firmlyplanted.app.data.remote.ParsedVerse
import com.firmlyplanted.app.data.remote.UsxVerseParser
import com.firmlyplanted.app.domain.Translation
import com.firmlyplanted.app.domain.TranslationSource

/** Verse text lookup key: chapter/verse only (book/translation are fixed per call). */
data class TextRef(val chapter: Int, val verse: Int)

class TextFetcher(
    private val esvApi: EsvApiService,
    private val fetchBibleApi: FetchBibleService,
    private val esvApiKey: String,
) {
    /**
     * Fetches every verse that actually exists within [startChapter:startVerse,
     * endChapter:endVerse] for this translation/book, as an ordered list. Used at project
     * creation time to enumerate the real verse boundaries of a scope (deliberately not a
     * hardcoded versification table — see BookCatalog's doc comment).
     */
    suspend fun fetchRange(
        translation: Translation,
        bookName: String,
        bookCode: String,
        startChapter: Int,
        startVerse: Int,
        endChapter: Int,
        endVerse: Int,
    ): List<ParsedVerse> = when (translation.source) {
        TranslationSource.ESV_API -> fetchEsvRange(bookName, startChapter, startVerse, endChapter, endVerse)
        TranslationSource.FETCH_BIBLE -> {
            val usx = fetchBibleApi.getBookUsx(translation.sourceId, bookCode.lowercase())
            UsxVerseParser.parseRange(usx, startChapter, startVerse, endChapter, endVerse)
        }
    }

    /**
     * Fetches text for a known, specific set of verse refs (used by the rolling-window
     * refresh, where the refs already exist as VerseEntity rows). Internally still requests
     * the bounding range covering all refs, then keeps only what was asked for — for
     * fetch.bible sources the whole book is fetched either way (no smaller endpoint exists),
     * so the rest of that payload is simply discarded rather than persisted.
     */
    suspend fun fetchText(
        translation: Translation,
        bookName: String,
        bookCode: String,
        refs: List<TextRef>,
    ): Map<TextRef, String> {
        if (refs.isEmpty()) return emptyMap()
        val minChapter = refs.minOf { it.chapter }
        val maxChapter = refs.maxOf { it.chapter }
        val startVerse = refs.filter { it.chapter == minChapter }.minOf { it.verse }
        val endVerse = refs.filter { it.chapter == maxChapter }.maxOf { it.verse }
        val wanted = refs.toSet()

        return fetchRange(translation, bookName, bookCode, minChapter, startVerse, maxChapter, endVerse)
            .filter { TextRef(it.chapter, it.verse) in wanted }
            .associate { TextRef(it.chapter, it.verse) to it.text }
    }

    private suspend fun fetchEsvRange(
        bookName: String,
        startChapter: Int,
        startVerse: Int,
        endChapter: Int,
        endVerse: Int,
    ): List<ParsedVerse> {
        val query = if (startChapter == endChapter) {
            "$bookName $startChapter:$startVerse-$endVerse"
        } else {
            "$bookName $startChapter:$startVerse-$endChapter:$endVerse"
        }

        val response = esvApi.getPassageText(authHeader = "Token $esvApiKey", query = query)
        val passageText = response.passages.firstOrNull() ?: return emptyList()

        // ESV's response is one continuous string with only "[verse]" markers, no chapter
        // markers — so for a cross-chapter range we infer chapter rollover from the verse
        // number sequence resetting to a smaller value than the one before it.
        val result = mutableListOf<ParsedVerse>()
        var chapter = startChapter
        var previousVerse = 0
        for ((verseNumber, text) in EsvVerseSplitter.split(passageText)) {
            if (verseNumber <= previousVerse && chapter < endChapter) chapter++
            result += ParsedVerse(chapter, verseNumber, text)
            previousVerse = verseNumber
        }
        return result
    }
}

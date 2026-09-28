package com.firmlyplanted.app.domain

/** Where a translation's text is fetched from. */
enum class TranslationSource {
    ESV_API,
    FETCH_BIBLE,
}

enum class ProjectStatus {
    ACTIVE,
    COMPLETED,
    ARCHIVED,
}

/**
 * Graduated review phases, modeled after Andy Davis's cumulative-review method and the
 * Scripta Memoria approach: a verse is drilled hard right after it's learned, then reviewed
 * on lengthening intervals as it sticks.
 */
enum class VersePhase {
    NEW,
    LEARNING,
    REVIEW_SHORT,
    REVIEW_LONG,
    MASTERED,
}

/** A single reference, e.g. John 3:16. */
data class VerseRef(
    val book: String,
    val chapter: Int,
    val verse: Int,
) {
    fun display(): String = "$book $chapter:$verse"
}

/** A verse-range scope for a memory project, e.g. John 1:1 - John 3:36. */
data class PassageScope(
    val book: String,
    val startChapter: Int,
    val startVerse: Int,
    val endChapter: Int,
    val endVerse: Int,
)

data class Translation(
    val id: String,
    val displayName: String,
    val language: String,
    val source: TranslationSource,
    /** fetch.bible translation id, or resource abbreviation for ESV. Used to build requests. */
    val sourceId: String,
    val copyrightNoticeResName: String,
    val licenseSummary: String,
    /** Hard cap on verses that may be cached at once, if the license imposes one (e.g. ESV: 500). */
    val maxCachedVerses: Int? = null,
    /** Official public web reader for this translation, used for the "Read more" fallback. */
    val approvedWebReaderUrlTemplate: String? = null,
    val isDefault: Boolean = false,
    val testaments: Set<Testament> = setOf(Testament.OLD, Testament.NEW),
)

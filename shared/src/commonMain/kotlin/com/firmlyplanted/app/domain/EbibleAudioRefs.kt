package com.firmlyplanted.app.domain

/** Which of eBible.org's two editions of the Shmuelof reading a project plays. */
enum class AudioSpeed(val fileWord: String) {
    FLUENT("fluent"),

    /** The same recording slowed by 20%, for learners. */
    SLOWER("slower"),
}

/** One of the 39 audio ePubs, e.g. "Tanakh fluent 08-Ruth רות.epub". */
data class EbibleBookFile(val number: Int, val englishName: String, val hebrewName: String) {
    fun fileName(speed: AudioSpeed): String =
        "Tanakh ${speed.fileWord} ${number.toString().padStart(2, '0')}-$englishName $hebrewName.epub"
}

/** App verses [verseFrom]..[verseTo] of [chapter] are ePub chapter [ebibleChapter], verse + [verseOffset]. */
data class EbibleRangeRule(
    val book: String,
    val chapter: Int,
    val verseFrom: Int,
    val verseTo: Int,
    val ebibleChapter: Int,
    val verseOffset: Int,
)

data class EbibleVerseKey(val book: String, val chapter: Int, val verse: Int)

/**
 * Clips in one ePub chapter's SMIL: either the exact [clipId], or (when [clipId] is null) every
 * clip of [verse] — ids `N`, `Na`, `Nb`, ...
 */
data class EbibleClipRef(val chapter: Int, val verse: Int? = null, val clipId: String? = null) {
    fun matches(id: String): Boolean =
        if (clipId != null) id == clipId else verse != null && clipVerseNumber(id) == verse

    companion object {
        /** "117c" -> 117, "12" -> 12, "a" -> null. */
        fun clipVerseNumber(id: String): Int? = id.takeWhile { it.isDigit() }.toIntOrNull()
    }
}

/** Resolves an app (WLC) verse to its audio clips, via the generated [EbibleAudioIndex]. */
object EbibleAudioRefs {
    /** Attribution required by CC BY; shown in Project Settings and About. */
    const val CREDIT = "Hebrew audio: the Hebrew Bible read by Abraham Shmuelof, from eBible.org " +
        "(ebible.org/multimedia/hbo), licensed CC BY-NC-ND 4.0. Each verse is played as recorded, " +
        "unaltered; audio is downloaded only for your WLC projects' verses and deleted when a " +
        "project is completed, archived or deleted."

    private val rulesByChapter: Map<Pair<String, Int>, List<EbibleRangeRule>> =
        EbibleAudioIndex.rangeRules.groupBy { it.book to it.chapter }

    fun bookFile(book: String): EbibleBookFile? = EbibleAudioIndex.bookFiles[book]

    /** The verse's clips in reading order, or an empty list when there's no audio for it. */
    fun resolve(book: String, chapter: Int, verse: Int): List<EbibleClipRef> {
        if (book !in EbibleAudioIndex.bookFiles) return emptyList()
        EbibleAudioIndex.overrides[EbibleVerseKey(book, chapter, verse)]?.let { clips ->
            return clips.map { clip ->
                val (c, id) = clip.split(':', limit = 2)
                EbibleClipRef(chapter = c.toInt(), clipId = id)
            }
        }
        val rule = rulesByChapter[book to chapter]?.firstOrNull { verse in it.verseFrom..it.verseTo }
        return if (rule == null) {
            listOf(EbibleClipRef(chapter = chapter, verse = verse))
        } else {
            listOf(EbibleClipRef(chapter = rule.ebibleChapter, verse = verse + rule.verseOffset))
        }
    }
}

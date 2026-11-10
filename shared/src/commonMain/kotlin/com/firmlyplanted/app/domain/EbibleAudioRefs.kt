package com.firmlyplanted.app.domain

/** Which recording of an edition a project plays; only the Hebrew reading has a slower one. */
enum class AudioSpeed(val fileWord: String) {
    FLUENT("fluent"),

    /** The same recording slowed by 20%, for learners. */
    SLOWER("slower"),
}

/** One book's audio ePub: its number in eBible.org's file names, and its English and native names. */
data class EbibleBookFile(val number: Int, val englishName: String, val nativeName: String)

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

/**
 * One of eBible.org's audio-ePub recordings, tied to the app translation whose verses it reads.
 * The tables come from a generated index (see scripts/build_ebible_audio_map.py).
 */
class EbibleAudioEdition(
    val translationId: String,
    /** Section title in Project Settings, e.g. "Hebrew audio". */
    val label: String,
    /** Attribution required by CC BY; shown in Project Settings and About. */
    val credit: String,
    val baseUrl: String,
    /** The recordings on offer; the first is the default. */
    val speeds: List<AudioSpeed>,
    val bookFiles: Map<String, EbibleBookFile>,
    rangeRules: List<EbibleRangeRule>,
    private val overrides: Map<EbibleVerseKey, List<String>>,
    private val differingVerses: Set<EbibleVerseKey>,
    private val fileNamer: (EbibleBookFile, AudioSpeed) -> String,
) {
    private val rulesByChapter: Map<Pair<String, Int>, List<EbibleRangeRule>> =
        rangeRules.groupBy { it.book to it.chapter }

    fun bookFile(book: String): EbibleBookFile? = bookFiles[book]

    /** The ePub's file name (not URL-encoded). [speed] is ignored by single-speed editions. */
    fun fileName(file: EbibleBookFile, speed: AudioSpeed): String =
        fileNamer(file, if (speed in speeds) speed else speeds.first())

    /** The verse's clips in reading order, or an empty list when there's no audio for it. */
    fun resolve(book: String, chapter: Int, verse: Int): List<EbibleClipRef> {
        if (book !in bookFiles) return emptyList()
        overrides[EbibleVerseKey(book, chapter, verse)]?.let { clips ->
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

    /** True when the recording's words for this verse differ from the app's text (beyond spelling). */
    fun wordingDiffers(book: String, chapter: Int, verse: Int): Boolean =
        EbibleVerseKey(book, chapter, verse) in differingVerses
}

/** The recordings the app can play, by translation. */
object EbibleAudioRefs {
    val HEBREW = EbibleAudioEdition(
        translationId = DefaultTranslations.WLC_ID,
        label = "Hebrew audio",
        credit = "Hebrew audio: the Hebrew Bible read by Abraham Shmuelof, from eBible.org " +
            "(ebible.org/multimedia/hbo), licensed CC BY-NC-ND 4.0. The chapter recordings are stored " +
            "unaltered, and each verse is played from them using the timings in eBible.org's own ePub. " +
            "Audio is downloaded only for the chapters your projects use and deleted when a " +
            "project is completed, archived or deleted.",
        baseUrl = EbibleHebrewAudioIndex.BASE_URL,
        speeds = listOf(AudioSpeed.FLUENT, AudioSpeed.SLOWER),
        bookFiles = EbibleHebrewAudioIndex.bookFiles,
        rangeRules = EbibleHebrewAudioIndex.rangeRules,
        overrides = EbibleHebrewAudioIndex.overrides,
        differingVerses = EbibleHebrewAudioIndex.differingVerses,
        fileNamer = { file, speed ->
            "Tanakh ${speed.fileWord} ${file.number.toString().padStart(2, '0')}-${file.englishName} ${file.nativeName}.epub"
        },
    )

    val GREEK = EbibleAudioEdition(
        translationId = DefaultTranslations.SBLGNT_ID,
        label = "Greek audio",
        credit = "Greek audio: the Greek New Testament (Nestle 1904 text) read by Andy Martin, from " +
            "eBible.org (ebible.org/multimedia/grc), licensed CC BY-NC-ND 4.0. The recording follows " +
            "Nestle 1904, so its wording differs from the SBLGNT in some verses. The chapter " +
            "recordings are stored unaltered, and each verse is played from them using the timings " +
            "in eBible.org's own ePub. Audio is downloaded only for the chapters your projects use " +
            "and deleted when a project is completed, archived or deleted.",
        baseUrl = EbibleGreekAudioIndex.BASE_URL,
        speeds = listOf(AudioSpeed.FLUENT),
        bookFiles = EbibleGreekAudioIndex.bookFiles,
        rangeRules = EbibleGreekAudioIndex.rangeRules,
        overrides = EbibleGreekAudioIndex.overrides,
        differingVerses = EbibleGreekAudioIndex.differingVerses,
        fileNamer = { file, _ ->
            "${file.number.toString().padStart(2, '0')}_${file.englishName}_${file.nativeName}.epub"
        },
    )

    val all: List<EbibleAudioEdition> = listOf(HEBREW, GREEK)

    fun forTranslation(translationId: String): EbibleAudioEdition? = all.firstOrNull { it.translationId == translationId }
}

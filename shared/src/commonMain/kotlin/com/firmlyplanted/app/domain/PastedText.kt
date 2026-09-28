package com.firmlyplanted.app.domain

/** One verse read out of pasted text. */
data class PastedVerse(val chapter: Int, val verse: Int, val text: String)

/**
 * What the paste says about itself, from a reference line like "John 3:16-17 ESV" and/or a
 * bible.com link like https://bible.com/bible/59/jhn.3.16-17.ESV (YouVersion adds both).
 * Any part may be missing; the New Project flow lets the user fill in or correct it.
 */
data class PastedReference(
    val book: BookInfo? = null,
    val startChapter: Int? = null,
    val startVerse: Int? = null,
    val endChapter: Int? = null,
    val endVerse: Int? = null,
    val translationAbbrev: String? = null,
    val link: String? = null,
)

data class PastedPassage(
    val reference: PastedReference,
    /** Chapters count up from `reference.startChapter ?: 1`, rolling over when numbering restarts at 1. */
    val verses: List<PastedVerse>,
    /** Text found before the first verse number that couldn't be assigned to a verse (e.g. a heading). */
    val skippedPrefix: String? = null,
)

/**
 * Reads pasted Bible text with inline verse numbers ("16 For God so loved… 17 For God did not…"),
 * as copied from YouVersion and similar apps, into individual verses.
 *
 * A number only counts as a verse marker when it's the next one in sequence (or 1, starting a
 * new chapter), and only when it stands alone before text — so numbers inside verses ("153
 * fish", "7,000 men", "3:16") aren't mistaken for markers.
 */
object PastedTextParser {

    fun parse(raw: String): Result<PastedPassage> {
        val lines = normalize(raw).lines().toMutableList()

        val link = extractLink(lines)
        val lineRef = extractReferenceLine(lines, linkPresent = link != null)
        val reference = PastedReference(
            book = link?.book ?: lineRef?.book,
            startChapter = lineRef?.startChapter ?: link?.startChapter,
            startVerse = lineRef?.startVerse ?: link?.startVerse,
            endChapter = lineRef?.endChapter ?: link?.endChapter,
            endVerse = lineRef?.endVerse ?: link?.endVerse,
            translationAbbrev = lineRef?.translationAbbrev ?: link?.translationAbbrev,
            link = link?.link,
        )

        val body = unwrapQuotes(lines.joinToString("\n").trim())
        if (body.isBlank()) return Result.failure(PasteException("There's no verse text to memorize — paste the verses, not just the reference."))

        val baseChapter = reference.startChapter ?: 1
        val markers = findMarkers(body, reference.startVerse)
        if (markers.isEmpty()) {
            val verse = reference.startVerse
                ?: return Result.failure(PasteException("Couldn't find any verse numbers. Include them in the pasted text (YouVersion does when you copy several verses), or include the reference line, e.g. \"John 3:16 ESV\"."))
            return Result.success(PastedPassage(reference, listOf(PastedVerse(baseChapter, verse, clean(body)))))
        }

        val verses = mutableListOf<PastedVerse>()
        var skippedPrefix: String? = null
        val prefix = clean(body.substring(0, markers.first().start))
        if (prefix.isNotEmpty()) {
            // First verse copied without its number (e.g. "For God so loved… 17 For God did not…").
            if (reference.startVerse != null && markers.first().verse == reference.startVerse + 1) {
                verses += PastedVerse(baseChapter, reference.startVerse, prefix)
            } else {
                skippedPrefix = prefix
            }
        }
        markers.forEachIndexed { index, marker ->
            val end = markers.getOrNull(index + 1)?.start ?: body.length
            val text = clean(body.substring(marker.textStart, end))
            if (text.isNotEmpty()) verses += PastedVerse(baseChapter + marker.chapterOffset, marker.verse, text)
        }
        return Result.success(PastedPassage(reference, verses, skippedPrefix))
    }

    /** Guesses the script so Hebrew/Greek get the right font and direction (see Fonts.kt). */
    fun detectLanguage(text: String): String {
        var hebrew = 0
        var greek = 0
        var letters = 0
        for (c in text) {
            if (!c.isLetter()) continue
            letters++
            when (c.code) {
                in 0x0590..0x05FF, in 0xFB1D..0xFB4F -> hebrew++
                in 0x0370..0x03FF, in 0x1F00..0x1FFF -> greek++
            }
        }
        return when {
            letters == 0 -> "Other"
            hebrew * 2 > letters -> "Hebrew"
            greek * 2 > letters -> "Greek"
            else -> "Other"
        }
    }

    class PasteException(message: String) : Exception(message)

    // --- Normalization -----------------------------------------------------------------------

    /** YouVersion wraps reference parts in invisible direction marks (U+202D/U+202C) — strip those. */
    private val INVISIBLES = Regex("[\\u200B-\\u200F\\u202A-\\u202E\\u2060-\\u2069\\uFEFF]")

    private fun normalize(raw: String): String =
        raw.replace(INVISIBLES, "")
            .replace(' ', ' ')
            .replace(' ', ' ')
            .replace("\r\n", "\n")
            .replace('\r', '\n')

    private fun clean(text: String): String = text.trim().replace(Regex("\\s+"), " ")

    /** A single shared verse comes as “text” — drop the wrapping quotes, keep any inner ones. */
    private fun unwrapQuotes(body: String): String {
        if (body.length < 2) return body
        val opens = body.first() == '“' || body.first() == '"'
        val closes = body.last() == '”' || body.last() == '"'
        return if (opens && closes) body.substring(1, body.length - 1) else body
    }

    // --- Reference detection -----------------------------------------------------------------

    private val LINK = Regex(
        "https?://(?:www\\.)?bible\\.com/(?:[a-zA-Z-]+/)?bible/\\d+/([1-3]?[A-Za-z]{2,3})\\.(\\d{1,3})" +
            "(?:\\.(\\d{1,3})(?:-(\\d{1,3}))?)?(?:\\.([A-Za-z0-9-]+))?\\S*",
    )

    /** Finds and removes a bible.com link, returning what it encodes. */
    private fun extractLink(lines: MutableList<String>): PastedReference? {
        for (i in lines.indices.reversed()) {
            val match = LINK.find(lines[i]) ?: continue
            lines[i] = lines[i].removeRange(match.range)
            val (code, chapter, start, end, abbrev) = match.destructured
            return PastedReference(
                book = BookCatalog.byCode(code),
                startChapter = chapter.toInt(),
                startVerse = start.toIntOrNull(),
                endChapter = chapter.toInt(),
                endVerse = end.toIntOrNull() ?: start.toIntOrNull(),
                translationAbbrev = abbrev.ifEmpty { null },
                link = match.value,
            )
        }
        return null
    }

    /** "John 3:16", "1 John 1:9-10 NIV", "Ruth 1:22-2:2 ESV" — optionally right after a closing quote. */
    private val REFERENCE = Regex(
        "((?:[1-3] ?)?\\p{L}[\\p{L} .']*?)\\s+(\\d{1,3}):(\\d{1,3})" +
            "(?:\\s*[-–]\\s*(\\d{1,3})(?::(\\d{1,3}))?)?(?:\\s+([\\p{L}\\d-]{2,12}))?\\s*$",
    )

    /**
     * Finds and removes the reference: usually the last non-empty line, sometimes the first. A
     * whole-line reference is trusted even for an unrecognized (e.g. non-English) book name; one
     * trailing verse text ("…eternal life.” John 3:16 NIV") is only taken when the book is
     * recognized or a link confirmed it, so ordinary text can't be mistaken for a reference.
     */
    private fun extractReferenceLine(lines: MutableList<String>, linkPresent: Boolean): PastedReference? {
        val last = lines.indexOfLast { it.isNotBlank() }
        if (last < 0) return null
        return extractReference(lines, last, linkPresent, allowTrailing = true)
            ?: extractReference(lines, lines.indexOfFirst { it.isNotBlank() }, linkPresent, allowTrailing = false)
    }

    private fun extractReference(
        lines: MutableList<String>,
        index: Int,
        linkPresent: Boolean,
        allowTrailing: Boolean,
    ): PastedReference? {
        val line = lines[index].trim()
        val match = REFERENCE.find(line) ?: return null

        val prefix = line.substring(0, match.range.first)
        val wholeLine = prefix.isBlank()
        val afterQuote = prefix.trimEnd().lastOrNull()?.let { it == '”' || it == '"' || it == '’' } == true
        if (!wholeLine && !(allowTrailing && afterQuote)) return null

        val (bookName, chapter, startVerse, endA, endB, abbrev) = match.destructured
        val book = BookCatalog.byNameOrAlias(bookName)
        if (!wholeLine && book == null && !linkPresent) return null
        if (wholeLine && line.length > 60) return null

        lines[index] = if (wholeLine) "" else prefix.trimEnd()
        // "16-17" is a verse range; "22-2:2" means through chapter 2, verse 2.
        val (endChapter, endVerse) = when {
            endB.isNotEmpty() -> endA.toInt() to endB.toInt()
            endA.isNotEmpty() -> chapter.toInt() to endA.toInt()
            else -> chapter.toInt() to startVerse.toInt()
        }
        return PastedReference(
            book = book,
            startChapter = chapter.toInt(),
            startVerse = startVerse.toInt(),
            endChapter = endChapter,
            endVerse = endVerse,
            translationAbbrev = abbrev.ifEmpty { null },
        )
    }

    // --- Verse markers -----------------------------------------------------------------------

    private class Marker(val start: Int, val textStart: Int, val verse: Int, val chapterOffset: Int)

    private const val OPENERS = "“\"‘'(["
    private const val NOT_AFTER_NUMBER = ",.:;%)]"

    private fun findMarkers(body: String, referenceStartVerse: Int?): List<Marker> {
        val markers = mutableListOf<Marker>()
        var i = 0
        while (i < body.length) {
            val standsAlone = i == 0 || body[i - 1].isWhitespace() || body[i - 1] in OPENERS
            if (!body[i].isDigit() || !standsAlone) {
                i++
                continue
            }
            var j = i
            while (j < body.length && body[j].isDigit()) j++
            val followedByText = j < body.length && body[j] !in NOT_AFTER_NUMBER && body.substring(j).isNotBlank()
            val number = if (j - i <= 3 && followedByText) body.substring(i, j).toInt() else null

            if (number != null) {
                val previous = markers.lastOrNull()
                val accepted = if (previous == null) {
                    body.substring(0, i).isBlank() ||
                        number == referenceStartVerse ||
                        (referenceStartVerse != null && number == referenceStartVerse + 1)
                } else {
                    number == previous.verse + 1 || (number == 1 && previous.verse > 1)
                }
                if (accepted) {
                    val rollover = previous != null && number == 1
                    val offset = (previous?.chapterOffset ?: 0) + if (rollover) 1 else 0
                    markers += Marker(start = i, textStart = j, verse = number, chapterOffset = offset)
                }
            }
            i = j
        }
        return markers
    }
}

/** Builds the per-project Translation row a pasted project points at. */
object PastedTranslations {
    const val ID_PREFIX = "pasted-"

    fun create(id: String, label: String, language: String, link: String?): Translation {
        val name = label.trim().ifEmpty { "Pasted text" }
        return Translation(
            id = id,
            displayName = name,
            language = language,
            source = TranslationSource.PASTED,
            sourceId = "",
            // No bundled notice: CopyrightNotice falls back to licenseSummary, written for this text.
            copyrightNoticeResName = "",
            licenseSummary = "Pasted from your own copy of $name. Check that version's copyright terms — " +
                "including any limit on how many verses may be stored or quoted — and stay within them.",
            maxCachedVerses = null,
            approvedWebReaderUrlTemplate = link,
            isDefault = false,
            testaments = setOf(Testament.OLD, Testament.NEW),
        )
    }
}

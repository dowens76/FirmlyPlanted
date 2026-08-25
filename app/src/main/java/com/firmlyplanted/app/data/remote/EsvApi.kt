package com.firmlyplanted.app.data.remote

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

/**
 * api.esv.org — see https://api.esv.org/docs/passage-text/. Auth: "Authorization: Token <key>"
 * header, key from BuildConfig.ESV_API_KEY (get one free, non-commercial, at api.esv.org).
 */
interface EsvApiService {
    @GET("v3/passage/text/")
    suspend fun getPassageText(
        @Header("Authorization") authHeader: String,
        @Query("q") query: String,
        @Query("include-headings") includeHeadings: Boolean = false,
        @Query("include-footnotes") includeFootnotes: Boolean = false,
        @Query("include-verse-numbers") includeVerseNumbers: Boolean = true,
        @Query("include-first-verse-numbers") includeFirstVerseNumbers: Boolean = true,
        @Query("include-short-copyright") includeShortCopyright: Boolean = false,
        @Query("include-passage-references") includePassageReferences: Boolean = false,
    ): EsvPassageTextResponse

    companion object {
        const val BASE_URL = "https://api.esv.org/"
    }
}

@Serializable
data class EsvPassageTextResponse(
    val query: String = "",
    val canonical: String = "",
    val passages: List<String> = emptyList(),
)

/**
 * Splits an ESV passage string (verse numbers rendered inline as "[16] text...") into
 * verse-number -> text pairs. Relies on include-verse-numbers=true and
 * include-first-verse-numbers=true being set on the request.
 */
object EsvVerseSplitter {
    private val versePattern = Regex("""\[(\d+)]\s*""")

    fun split(passageText: String): List<Pair<Int, String>> {
        val matches = versePattern.findAll(passageText).toList()
        if (matches.isEmpty()) return emptyList()

        return matches.mapIndexed { index, match ->
            val verseNumber = match.groupValues[1].toInt()
            val start = match.range.last + 1
            val end = matches.getOrNull(index + 1)?.range?.first ?: passageText.length
            val text = passageText.substring(start, end).trim().replace(Regex("\\s+"), " ")
            verseNumber to text
        }
    }
}

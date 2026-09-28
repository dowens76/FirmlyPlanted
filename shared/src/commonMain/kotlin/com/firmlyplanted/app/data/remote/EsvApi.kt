package com.firmlyplanted.app.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * api.esv.org — see https://api.esv.org/docs/passage-text/. Auth: "Authorization: Token <key>"
 * header, key from BuildConfig.ESV_API_KEY (get one free, non-commercial, at api.esv.org).
 */
class EsvApiService(private val client: HttpClient, private val json: Json) {
    suspend fun getPassageText(
        authHeader: String,
        query: String,
        includeHeadings: Boolean = false,
        includeFootnotes: Boolean = false,
        includeVerseNumbers: Boolean = true,
        includeFirstVerseNumbers: Boolean = true,
        includeShortCopyright: Boolean = false,
        includePassageReferences: Boolean = false,
    ): EsvPassageTextResponse {
        val body = client.get(BASE_URL + "v3/passage/text/") {
            header(HttpHeaders.Authorization, authHeader)
            parameter("q", query)
            parameter("include-headings", includeHeadings)
            parameter("include-footnotes", includeFootnotes)
            parameter("include-verse-numbers", includeVerseNumbers)
            parameter("include-first-verse-numbers", includeFirstVerseNumbers)
            parameter("include-short-copyright", includeShortCopyright)
            parameter("include-passage-references", includePassageReferences)
        }.bodyAsText()
        return json.decodeFromString(body)
    }

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

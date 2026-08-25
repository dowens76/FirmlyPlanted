package com.firmlyplanted.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * fetch.bible's public collection. Endpoints verified live (Aug 2026):
 *   - GET https://v1.fetch.bible/manifest.json           -> catalog of all translations
 *   - GET https://v1.fetch.bible/bibles/{id}/usx/{book}.usx -> whole-book USX 3 XML
 * (book codes are lowercase 3-4 letter USX/OSIS codes, e.g. "gen", "1sa", "jhn" — see BookCatalog)
 */
interface FetchBibleService {
    @GET("manifest.json")
    suspend fun getManifest(): FetchBibleManifest

    @GET("bibles/{translationId}/usx/{bookCode}.usx")
    suspend fun getBookUsx(
        @Path("translationId") translationId: String,
        @Path("bookCode") bookCode: String,
    ): String

    companion object {
        const val BASE_URL = "https://v1.fetch.bible/"
    }
}

@Serializable
data class FetchBibleManifest(
    val bibles: Map<String, FetchBibleEntry> = emptyMap(),
)

@Serializable
data class FetchBibleEntry(
    val name: FetchBibleName = FetchBibleName(),
    val year: Int? = null,
    val copyright: FetchBibleCopyright? = null,
    @SerialName("books_ot") val booksOt: JsonElement? = null,
    @SerialName("books_nt") val booksNt: JsonElement? = null,
) {
    /** books_ot/books_nt are `true`, `false`, `[]`, or a list of book codes in the raw manifest. */
    fun hasOldTestament(): Boolean = isTruthy(booksOt)
    fun hasNewTestament(): Boolean = isTruthy(booksNt)

    private fun isTruthy(element: JsonElement?): Boolean = when {
        element == null -> false
        element is JsonPrimitive -> element.booleanOrNull == true
        else -> runCatching { element.jsonArray.isNotEmpty() }.getOrDefault(false)
    }
}

@Serializable
data class FetchBibleName(
    val english: String = "",
    val local: String = "",
)

@Serializable
data class FetchBibleCopyright(
    val attribution: String = "",
    @SerialName("attribution_url") val attributionUrl: String = "",
    val licenses: List<FetchBibleLicense> = emptyList(),
) {
    /** A best-effort, human-readable notice built from the manifest's own metadata. */
    fun buildNotice(): String {
        val licenseNames = licenses.joinToString(", ") { it.license }
        return buildString {
            append(attribution.ifBlank { "Copyright holder not specified in fetch.bible metadata." })
            if (licenseNames.isNotBlank()) append(" ($licenseNames)")
        }
    }
}

@Serializable
data class FetchBibleLicense(
    val license: String = "",
    val url: String = "",
)

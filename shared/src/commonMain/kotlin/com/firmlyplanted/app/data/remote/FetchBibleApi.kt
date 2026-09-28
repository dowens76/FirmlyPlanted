package com.firmlyplanted.app.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray

/**
 * fetch.bible's public collection. Endpoints verified live (Aug 2026):
 *   - GET https://v1.fetch.bible/manifest.json           -> catalog of all translations
 *   - GET https://v1.fetch.bible/bibles/{id}/usx/{book}.usx -> whole-book USX 3 XML
 * (book codes are lowercase 3-4 letter USX/OSIS codes, e.g. "gen", "1sa", "jhn" — see BookCatalog)
 */
class FetchBibleService(private val client: HttpClient, private val json: Json) {
    suspend fun getManifest(): FetchBibleManifest =
        json.decodeFromString(client.get(BASE_URL + "manifest.json").bodyAsText())

    /** Returned as raw text: USX is XML, parsed separately by [UsxVerseParser]. */
    suspend fun getBookUsx(translationId: String, bookCode: String): String =
        client.get(BASE_URL + "bibles/${translationId.encodeURLPathPart()}/usx/${bookCode.encodeURLPathPart()}.usx")
            .bodyAsText()

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
    /** Usually a license name string, but a few entries use an object of `forbid_*` flags instead. */
    @SerialName("license") val rawLicense: JsonElement? = null,
    val url: String = "",
) {
    val license: String
        get() = when (rawLicense) {
            is JsonPrimitive -> rawLicense.contentOrNull.orEmpty()
            is JsonObject -> describeCustomLicense(rawLicense)
            else -> ""
        }

    private fun describeCustomLicense(flags: JsonObject): String {
        val restrictions = CUSTOM_LICENSE_FLAGS.mapNotNull { (key, label) ->
            label.takeIf { (flags[key] as? JsonPrimitive)?.booleanOrNull == true }
        }
        return if (restrictions.isEmpty()) "Custom license" else "Custom license: ${restrictions.joinToString(", ")}"
    }

    private companion object {
        val CUSTOM_LICENSE_FLAGS = listOf(
            "forbid_commercial" to "non-commercial",
            "forbid_derivatives" to "no derivatives",
            "forbid_attributionless" to "attribution required",
            "forbid_limitless" to "limited quantities",
            "forbid_other" to "other restrictions",
        )
    }
}

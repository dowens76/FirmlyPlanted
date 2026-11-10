package com.firmlyplanted.app.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpHeaders

/** Inflates raw DEFLATE data (zip method 8, no zlib header). Platform zlib on each side. */
internal expect fun inflateRaw(data: ByteArray, uncompressedSize: Int): ByteArray

/**
 * Reads single entries out of a remote zip (an ePub here) with HTTP Range requests, so a 50 MB
 * book never has to be downloaded to get one chapter out of it. Needs a server that honours Range
 * (eBible.org does). Supports stored and deflated entries; no zip64.
 */
class RangedZipReader(private val client: HttpClient) {

    data class Entry(
        val name: String,
        val method: Int,
        val compressedSize: Int,
        val uncompressedSize: Int,
        val localHeaderOffset: Long,
    )

    /** Parses the zip's central directory: entry name -> entry. */
    suspend fun directory(url: String): Map<String, Entry> {
        val tailResponse = fetch(url, "-$TAIL_BYTES")
        val tail = tailResponse.readRawBytes()
        val total = tailResponse.headers[HttpHeaders.ContentRange]?.substringAfterLast('/')?.toLongOrNull()
            ?: error("Server didn't return a byte range for $url")
        val eocd = lastIndexOf(tail, EOCD_SIGNATURE)
        require(eocd >= 0) { "Not a zip file: $url" }
        val cdSize = tail.u32(eocd + 12)
        val cdOffset = tail.u32(eocd + 16)
        val tailStart = total - tail.size
        val cd = if (cdOffset >= tailStart) {
            val from = (cdOffset - tailStart).toInt()
            tail.copyOfRange(from, from + cdSize.toInt())
        } else {
            fetch(url, "$cdOffset-${cdOffset + cdSize - 1}").readRawBytes()
        }
        val entries = LinkedHashMap<String, Entry>()
        var p = 0
        while (p + 46 <= cd.size && cd.u32(p) == CENTRAL_SIGNATURE) {
            val nameLength = cd.u16(p + 28)
            val extraLength = cd.u16(p + 30)
            val commentLength = cd.u16(p + 32)
            val name = cd.decodeToString(p + 46, p + 46 + nameLength)
            entries[name] = Entry(
                name = name,
                method = cd.u16(p + 10),
                compressedSize = cd.u32(p + 20).toInt(),
                uncompressedSize = cd.u32(p + 24).toInt(),
                localHeaderOffset = cd.u32(p + 42),
            )
            p += 46 + nameLength + extraLength + commentLength
        }
        return entries
    }

    /** Downloads and (if needed) inflates one entry. Two small range requests plus the data. */
    suspend fun read(url: String, entry: Entry): ByteArray {
        val headerStart = entry.localHeaderOffset
        val header = fetch(url, "$headerStart-${headerStart + 29}").readRawBytes()
        require(header.size >= 30 && header.u32(0) == LOCAL_SIGNATURE) { "Bad zip entry ${entry.name}" }
        val dataStart = headerStart + 30 + header.u16(26) + header.u16(28)
        val data = if (entry.compressedSize == 0) {
            ByteArray(0)
        } else {
            fetch(url, "$dataStart-${dataStart + entry.compressedSize - 1}").readRawBytes()
        }
        return when (entry.method) {
            METHOD_STORED -> data
            METHOD_DEFLATED -> inflateRaw(data, entry.uncompressedSize)
            else -> error("Unsupported zip compression method ${entry.method} for ${entry.name}")
        }
    }

    private suspend fun fetch(url: String, range: String): HttpResponse =
        client.get(url) { header(HttpHeaders.Range, "bytes=$range") }.also {
            require(it.status.value == 206) { "Server ignored the byte range for $url (HTTP ${it.status.value})" }
        }

    companion object {
        /** Big enough for the end record plus the central directory of a 150-chapter book. */
        private const val TAIL_BYTES = 65536
        private const val EOCD_SIGNATURE = 0x06054b50L
        private const val CENTRAL_SIGNATURE = 0x02014b50L
        private const val LOCAL_SIGNATURE = 0x04034b50L
        private const val METHOD_STORED = 0
        private const val METHOD_DEFLATED = 8

        private fun ByteArray.u16(i: Int): Int = (this[i].toInt() and 0xFF) or ((this[i + 1].toInt() and 0xFF) shl 8)

        private fun ByteArray.u32(i: Int): Long = (u16(i).toLong()) or (u16(i + 2).toLong() shl 16)

        private fun lastIndexOf(bytes: ByteArray, signature: Long): Int {
            for (i in bytes.size - 22 downTo 0) if (bytes.u32(i) == signature) return i
            return -1
        }
    }
}

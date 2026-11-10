package com.firmlyplanted.app.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RangedZipReaderTest {

    /** "shalom shalom shalom shalom" as raw DEFLATE (Python: zlib.compressobj(9, DEFLATED, -15)). */
    private val deflated = intArrayOf(43, 206, 72, 204, 201, 207, 85, 40, 198, 66, 1, 0).map { it.toByte() }.toByteArray()
    private val inflated = "shalom shalom shalom shalom".encodeToByteArray()

    private class ZipEntry(val name: String, val method: Int, val data: ByteArray, val uncompressedSize: Int)

    /** A minimal zip: local headers + data, central directory, end record. */
    private fun zip(entries: List<ZipEntry>): ByteArray {
        val out = mutableListOf<Byte>()
        fun u16(v: Int) { out += (v and 0xFF).toByte(); out += ((v shr 8) and 0xFF).toByte() }
        fun u32(v: Long) { u16((v and 0xFFFF).toInt()); u16(((v shr 16) and 0xFFFF).toInt()) }
        val offsets = mutableListOf<Int>()
        for (e in entries) {
            offsets += out.size
            u32(0x04034b50); u16(20); u16(0); u16(e.method); u16(0); u16(0); u32(0)
            u32(e.data.size.toLong()); u32(e.uncompressedSize.toLong())
            val name = e.name.encodeToByteArray()
            u16(name.size); u16(0); out += name.toList(); out += e.data.toList()
        }
        val cdStart = out.size
        entries.forEachIndexed { i, e ->
            u32(0x02014b50); u16(20); u16(20); u16(0); u16(e.method); u16(0); u16(0); u32(0)
            u32(e.data.size.toLong()); u32(e.uncompressedSize.toLong())
            val name = e.name.encodeToByteArray()
            u16(name.size); u16(0); u16(0); u16(0); u16(0); u32(0); u32(offsets[i].toLong())
            out += name.toList()
        }
        val cdSize = out.size - cdStart
        u32(0x06054b50); u16(0); u16(0); u16(entries.size); u16(entries.size)
        u32(cdSize.toLong()); u32(cdStart.toLong()); u16(0)
        return out.toByteArray()
    }

    private val file = zip(
        listOf(
            ZipEntry("Audio/00-RUT-001.mp3", 0, byteArrayOf(1, 2, 3, 4, 5), 5),
            ZipEntry("Text/00-RUT-001.xhtml.smil", 8, deflated, inflated.size),
        ),
    )

    var requests = 0

    /** Serves [file] honouring "bytes=a-b" and "bytes=-n" like eBible.org; [honourRange] false ignores it. */
    private fun client(honourRange: Boolean = true) = HttpClient(
        MockEngine { request ->
            requests++
            val range = request.headers[HttpHeaders.Range]!!.removePrefix("bytes=")
            if (!honourRange) return@MockEngine respond(file, HttpStatusCode.OK)
            val (from, to) = if (range.startsWith("-")) {
                (file.size - range.drop(1).toInt()).coerceAtLeast(0) to file.size - 1
            } else {
                range.substringBefore('-').toInt() to range.substringAfter('-').toInt().coerceAtMost(file.size - 1)
            }
            respond(
                file.copyOfRange(from, to + 1),
                HttpStatusCode.PartialContent,
                headersOf(HttpHeaders.ContentRange, "bytes $from-$to/${file.size}"),
            )
        },
    )

    @Test
    fun readsDirectoryAndEntries() = runTest {
        val reader = RangedZipReader(client())
        val dir = reader.directory("https://example.test/book.epub")
        assertEquals(listOf("Audio/00-RUT-001.mp3", "Text/00-RUT-001.xhtml.smil"), dir.keys.toList())
        assertEquals(listOf<Byte>(1, 2, 3, 4, 5), reader.read("https://example.test/book.epub", dir.getValue("Audio/00-RUT-001.mp3")).toList())
        assertEquals(
            inflated.decodeToString(),
            reader.read("https://example.test/book.epub", dir.getValue("Text/00-RUT-001.xhtml.smil")).decodeToString(),
        )
    }

    @Test
    fun refusesServersThatIgnoreRanges() = runTest {
        assertFailsWith<IllegalArgumentException> {
            RangedZipReader(client(honourRange = false)).directory("https://example.test/book.epub")
        }
    }

    @Test
    fun inflatesRawDeflate() {
        assertEquals(inflated.decodeToString(), inflateRaw(deflated, inflated.size).decodeToString())
    }
}

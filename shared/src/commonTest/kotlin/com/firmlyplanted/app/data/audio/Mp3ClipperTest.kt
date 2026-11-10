package com.firmlyplanted.app.data.audio

import kotlin.test.Test
import kotlin.test.assertEquals

class Mp3ClipperTest {

    // MPEG-2 Layer III, 24 kbps, 16 kHz, no CRC — like eBible.org's files. 72 * 24000 / 16000 = 108
    // bytes and 576 / 16000 = 36 ms per frame.
    private val header = byteArrayOf(0xFF.toByte(), 0xF3.toByte(), 0x38, 0x00)
    private val frameSize = 108
    private val frameSeconds = 0.036

    /** ID3v2 tag, a Xing header frame, then [count] audio frames whose payload byte is their index. */
    private fun mp3(count: Int): ByteArray {
        val id3 = byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 3, 0, 0, 0, 0, 0, 5) + ByteArray(5)
        val xing = header + "Xing".encodeToByteArray() + ByteArray(frameSize - 8)
        val frames = (0 until count).map { i -> header + ByteArray(frameSize - 4) { i.toByte() } }
        return frames.fold(id3 + xing) { acc, f -> acc + f }
    }

    private fun frameIndices(clip: ByteArray): List<Int> =
        clip.toList().chunked(frameSize).map { it[4].toInt() }

    @Test
    fun skipsTagsAndMeasuresDuration() {
        val clipper = Mp3Clipper(mp3(100))
        assertEquals(100 * frameSeconds, clipper.duration, 1e-9)
    }

    @Test
    fun cutsWholeFramesWithLeadIn() {
        val clip = Mp3Clipper(mp3(100)).clip(begin = 1.0, end = 2.0)
        assertEquals(0, clip.size % frameSize)
        // Frame 27 (0.972-1.008 s) holds 1.0 s; two lead-in frames make it start at 25. The last
        // frame starting before 2.0 s is 55 (1.98 s).
        assertEquals((25..55).toList(), frameIndices(clip))
        clip.toList().chunked(frameSize).forEach { assertEquals(header.toList(), it.take(4)) }
    }

    @Test
    fun clampsAtTheStartAndEnd() {
        val clipper = Mp3Clipper(mp3(10))
        assertEquals((0..2).toList(), frameIndices(clipper.clip(0.0, 0.1)))
        assertEquals((6..9).toList(), frameIndices(clipper.clip(0.3, 99.0)))
    }
}

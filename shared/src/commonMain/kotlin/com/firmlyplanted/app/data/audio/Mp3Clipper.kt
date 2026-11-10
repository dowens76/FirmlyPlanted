package com.firmlyplanted.app.data.audio

/**
 * Cuts time spans out of an MP3 (MPEG 1/2/2.5 Layer III) at frame boundaries, without decoding
 * or re-encoding. Frames are self-describing, so a run of frames copied verbatim is itself a
 * playable MP3. eBible.org's chapter MP3s are VBR, so cutting by frame time is far more exact than
 * asking a player to seek inside the whole chapter.
 */
class Mp3Clipper(private val mp3: ByteArray) {

    private class Frame(val offset: Int, val size: Int, val start: Double, val duration: Double)

    private val frames: List<Frame> = scanFrames()

    /** Total audio duration in seconds. */
    val duration: Double get() = frames.lastOrNull()?.let { it.start + it.duration } ?: 0.0

    /**
     * The frames covering [begin, end) seconds, starting [LEAD_IN_FRAMES] early: Layer III frames
     * can borrow bits from the frames before them, so the first frame or two of a cut may decode
     * as a click. The extra ~70 ms falls in the pause before a verse.
     */
    fun clip(begin: Double, end: Double): ByteArray {
        val first = frames.indexOfFirst { it.start + it.duration > begin }
        if (first < 0) return ByteArray(0)
        val from = (first - LEAD_IN_FRAMES).coerceAtLeast(0)
        val to = frames.indexOfLast { it.start < end }.coerceAtLeast(first)
        val start = frames[from].offset
        val stop = frames[to].offset + frames[to].size
        return mp3.copyOfRange(start, stop)
    }

    private fun scanFrames(): List<Frame> {
        val out = mutableListOf<Frame>()
        var p = skipId3v2()
        var time = 0.0
        var first = true
        while (p + 4 <= mp3.size) {
            val header = parseHeader(p)
            // Require the following frame to sync too, so stray 0xFFE bytes in data aren't mistaken
            // for a header (the last frame of the file has nothing after it).
            if (header == null || (p + header.size + 4 <= mp3.size && parseHeader(p + header.size) == null &&
                    !isTag(p + header.size))
            ) {
                p++
                continue
            }
            if (p + header.size > mp3.size) break
            // The first frame of a VBR file is usually a silent Xing/Info/VBRI header, not audio.
            if (!(first && isVbrHeaderFrame(p, header.size))) {
                out += Frame(p, header.size, time, header.duration)
                time += header.duration
            }
            first = false
            p += header.size
        }
        return out
    }

    private class Header(val size: Int, val duration: Double)

    private fun byte(i: Int): Int = mp3[i].toInt() and 0xFF

    private fun parseHeader(p: Int): Header? {
        if (p + 4 > mp3.size) return null
        val b1 = byte(p)
        val b2 = byte(p + 1)
        val b3 = byte(p + 2)
        if (b1 != 0xFF || (b2 and 0xE0) != 0xE0) return null
        val version = (b2 shr 3) and 3 // 0 = MPEG 2.5, 2 = MPEG 2, 3 = MPEG 1
        val layer = (b2 shr 1) and 3 // 1 = Layer III
        if (version == 1 || layer != 1) return null
        val bitrateIndex = b3 shr 4
        val sampleRateIndex = (b3 shr 2) and 3
        if (bitrateIndex == 0 || bitrateIndex == 15 || sampleRateIndex == 3) return null
        val padding = (b3 shr 1) and 1
        val mpeg1 = version == 3
        val bitrate = (if (mpeg1) MPEG1_BITRATES else MPEG2_BITRATES)[bitrateIndex] * 1000
        val sampleRate = SAMPLE_RATES[sampleRateIndex] / (if (mpeg1) 1 else if (version == 2) 2 else 4)
        val samples = if (mpeg1) 1152 else 576
        val size = samples / 8 * bitrate / sampleRate + padding
        return Header(size, samples.toDouble() / sampleRate)
    }

    private fun isTag(p: Int): Boolean =
        p + 3 <= mp3.size && (mp3.decodeAscii(p, 3) == "TAG" || mp3.decodeAscii(p, 3) == "ID3")

    private fun isVbrHeaderFrame(offset: Int, size: Int): Boolean {
        val text = mp3.decodeAscii(offset, minOf(size, 64))
        return "Xing" in text || "Info" in text || "VBRI" in text
    }

    private fun skipId3v2(): Int {
        if (mp3.size < 10 || mp3.decodeAscii(0, 3) != "ID3") return 0
        val size = (byte(6) shl 21) or (byte(7) shl 14) or (byte(8) shl 7) or byte(9)
        val footer = if ((byte(5) and 0x10) != 0) 10 else 0
        return (10 + size + footer).coerceAtMost(mp3.size)
    }

    private fun ByteArray.decodeAscii(offset: Int, length: Int): String =
        CharArray(length.coerceAtMost(size - offset).coerceAtLeast(0)) { (this[offset + it].toInt() and 0xFF).toChar() }
            .concatToString()

    companion object {
        const val LEAD_IN_FRAMES = 2
        private val MPEG1_BITRATES = intArrayOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320)
        private val MPEG2_BITRATES = intArrayOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160)
        private val SAMPLE_RATES = intArrayOf(44100, 48000, 32000)
    }
}

package com.firmlyplanted.app.data.audio

import com.firmlyplanted.app.data.remote.SimpleXmlReader

/** One `<par>` of an EPUB3 media overlay: the text element id and its audio span, in seconds. */
data class SmilClip(val id: String, val begin: Double, val end: Double)

/** Parses the media-overlay (.smil) files in eBible.org's audio ePubs. */
object SmilParser {
    fun parse(smil: String): List<SmilClip> {
        val clips = mutableListOf<SmilClip>()
        var pendingId: String? = null
        for (event in SimpleXmlReader(smil).events()) {
            if (event !is SimpleXmlReader.Event.StartTag) continue
            when (event.name.substringAfter(':')) {
                "par" -> pendingId = null
                "text" -> pendingId = event.attributes["src"]?.substringAfter('#', missingDelimiterValue = "")
                "audio" -> {
                    val id = pendingId?.takeIf { it.isNotEmpty() } ?: continue
                    val begin = parseClock(event.attributes["clipBegin"]) ?: continue
                    val end = parseClock(event.attributes["clipEnd"]) ?: continue
                    clips += SmilClip(id, begin, end)
                    pendingId = null
                }
            }
        }
        return clips
    }

    /** SMIL clock values: "4.88", "4.88s", "880ms", "1:02.5" or "0:01:02.5". */
    internal fun parseClock(value: String?): Double? {
        val v = value?.trim() ?: return null
        return when {
            ':' in v -> v.split(':').fold(0.0 as Double?) { acc, part ->
                val n = part.toDoubleOrNull()
                if (acc == null || n == null) null else acc * 60 + n
            }
            v.endsWith("ms") -> v.removeSuffix("ms").toDoubleOrNull()?.div(1000)
            v.endsWith("s") -> v.removeSuffix("s").toDoubleOrNull()
            else -> v.toDoubleOrNull()
        }
    }
}

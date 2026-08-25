package com.firmlyplanted.app.data.remote

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

data class ParsedVerse(val chapter: Int, val verse: Int, val text: String)

/**
 * Extracts clean per-verse plain text from a USX 3 book file, using fetch.bible's confirmed
 * milestone-style verse markers (spot-checked live, Aug 2026):
 *   <verse style="v" number="N" sid="BOOK C:V" /> ...running text... <verse eid="BOOK C:V" />
 * Footnote/cross-reference <note> content is skipped, and inline word-level wrapper tags (e.g.
 * WLC's <char style="w" strong="..." x-morph="...">) are transparent — only their text content
 * is kept, since we only gate capture on verse/note state, not on every wrapper tag name.
 */
object UsxVerseParser {

    fun parse(usx: String): List<ParsedVerse> {
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(usx))

        val results = mutableListOf<ParsedVerse>()
        var currentChapter = 0
        var currentVerse = 0
        var buffer = StringBuilder()
        var inVerse = false
        var noteDepth = 0

        fun flush() {
            if (inVerse && currentVerse > 0) {
                val text = buffer.toString().trim().replace(Regex("\\s+"), " ")
                if (text.isNotEmpty()) {
                    results += ParsedVerse(currentChapter, currentVerse, text)
                }
            }
            buffer = StringBuilder()
        }

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "chapter" -> {
                        val number = parser.getAttributeValue(null, "number")
                        if (number != null) {
                            flush()
                            inVerse = false
                            currentChapter = number.toIntOrNull() ?: currentChapter
                        }
                    }
                    "verse" -> {
                        val number = parser.getAttributeValue(null, "number")
                        val eid = parser.getAttributeValue(null, "eid")
                        when {
                            number != null -> {
                                flush()
                                currentVerse = number.toIntOrNull() ?: currentVerse
                                inVerse = true
                            }
                            eid != null -> {
                                flush()
                                inVerse = false
                            }
                        }
                    }
                    "note" -> noteDepth++
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "note" && noteDepth > 0) noteDepth--
                }
                XmlPullParser.TEXT -> {
                    if (inVerse && noteDepth == 0) {
                        buffer.append(parser.text)
                        buffer.append(' ')
                    }
                }
            }
            event = parser.next()
        }
        flush()
        return results
    }

    /** Convenience: only the verses within [startChapter:startVerse, endChapter:endVerse]. */
    fun parseRange(
        usx: String,
        startChapter: Int,
        startVerse: Int,
        endChapter: Int,
        endVerse: Int,
    ): List<ParsedVerse> = parse(usx).filter { v ->
        val afterStart = v.chapter > startChapter || (v.chapter == startChapter && v.verse >= startVerse)
        val beforeEnd = v.chapter < endChapter || (v.chapter == endChapter && v.verse <= endVerse)
        afterStart && beforeEnd
    }
}

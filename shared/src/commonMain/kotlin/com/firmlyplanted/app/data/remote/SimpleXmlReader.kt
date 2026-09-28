package com.firmlyplanted.app.data.remote

/**
 * A minimal, non-validating XML event reader — just enough for USX, which is plain well-formed
 * XML (no DTD entities, no namespaces we care about). Replaces Android's XmlPullParser so the
 * parser runs on iOS too, and mirrors how `XmlPullParser.next()` reports events:
 *   - a self-closing tag reports a start tag immediately followed by an end tag;
 *   - the five predefined and numeric character entities are resolved inside text/attributes;
 *   - comments, processing instructions and the DOCTYPE are skipped, and CDATA is plain text;
 *   - element names are reported raw (no namespace processing, like `Xml.newPullParser()`).
 * Malformed input throws [IllegalArgumentException].
 */
internal class SimpleXmlReader(private val xml: String) {

    sealed interface Event {
        data class StartTag(val name: String, val attributes: Map<String, String>) : Event
        data class EndTag(val name: String) : Event
        data class Text(val text: String) : Event
    }

    fun events(): Sequence<Event> = sequence {
        var i = 0
        val text = StringBuilder()

        suspend fun SequenceScope<Event>.flushText() {
            if (text.isNotEmpty()) {
                yield(Event.Text(text.toString()))
                text.clear()
            }
        }

        while (i < xml.length) {
            val c = xml[i]
            when {
                c == '&' -> i = appendEntity(i, text)
                c != '<' -> { text.append(c); i++ }
                xml.startsWith("<!--", i) -> i = indexAfter("-->", i + 4)
                xml.startsWith("<![CDATA[", i) -> {
                    val end = indexOf("]]>", i + 9)
                    text.append(xml, i + 9, end)
                    i = end + 3
                }
                xml.startsWith("<?", i) -> i = indexAfter("?>", i + 2)
                xml.startsWith("<!", i) -> i = skipDeclaration(i)
                xml.startsWith("</", i) -> {
                    flushText()
                    val end = indexOf(">", i + 2)
                    yield(Event.EndTag(xml.substring(i + 2, end).trim()))
                    i = end + 1
                }
                else -> {
                    flushText()
                    i = readStartTag(i + 1) { yield(it) }
                }
            }
        }
        flushText()
    }

    /** Parses `name attr="v" ... >` or `... />` starting just after '<'; returns the index after '>'. */
    private inline fun readStartTag(start: Int, emit: (Event) -> Unit): Int {
        var i = start
        while (i < xml.length && !xml[i].isWhitespace() && xml[i] != '/' && xml[i] != '>') i++
        val name = xml.substring(start, i)
        require(name.isNotEmpty()) { "Empty tag name at $start" }

        val attributes = LinkedHashMap<String, String>()
        while (true) {
            i = skipWhitespace(i)
            require(i < xml.length) { "Unterminated tag <$name" }
            when (xml[i]) {
                '>' -> {
                    emit(Event.StartTag(name, attributes))
                    return i + 1
                }
                '/' -> {
                    require(xml.getOrNull(i + 1) == '>') { "Expected '>' after '/' in <$name" }
                    emit(Event.StartTag(name, attributes))
                    emit(Event.EndTag(name))
                    return i + 2
                }
                else -> {
                    val nameStart = i
                    while (i < xml.length && !xml[i].isWhitespace() && xml[i] != '=') i++
                    val attrName = xml.substring(nameStart, i)
                    i = skipWhitespace(i)
                    require(xml.getOrNull(i) == '=') { "Expected '=' after attribute $attrName in <$name" }
                    i = skipWhitespace(i + 1)
                    val quote = xml.getOrNull(i)
                    require(quote == '"' || quote == '\'') { "Unquoted attribute $attrName in <$name" }
                    val end = indexOf(quote.toString(), i + 1)
                    attributes[attrName] = decodeEntities(xml.substring(i + 1, end))
                    i = end + 1
                }
            }
        }
    }

    /** Skips `<!DOCTYPE ...>` (including any `[...]` internal subset). */
    private fun skipDeclaration(start: Int): Int {
        var depth = 0
        var i = start + 2
        while (i < xml.length) {
            when (xml[i]) {
                '[' -> depth++
                ']' -> depth--
                '>' -> if (depth <= 0) return i + 1
            }
            i++
        }
        throw IllegalArgumentException("Unterminated declaration at $start")
    }

    /** Appends the character(s) for the entity at [start] (which is '&'); returns the index after ';'. */
    private fun appendEntity(start: Int, out: StringBuilder): Int {
        val end = xml.indexOf(';', start)
        if (end < 0 || end - start > 12) {
            out.append('&') // Not a well-formed reference; keep it literally.
            return start + 1
        }
        val resolved = resolveEntity(xml.substring(start + 1, end))
        if (resolved == null) {
            out.append(xml, start, end + 1)
        } else {
            out.append(resolved)
        }
        return end + 1
    }

    private fun decodeEntities(value: String): String {
        if ('&' !in value) return value
        return SimpleXmlReader(value).events()
            .filterIsInstance<Event.Text>()
            .joinToString("") { it.text }
    }

    private fun resolveEntity(name: String): String? = when {
        name == "lt" -> "<"
        name == "gt" -> ">"
        name == "amp" -> "&"
        name == "quot" -> "\""
        name == "apos" -> "'"
        name.startsWith("#x") || name.startsWith("#X") -> name.substring(2).toIntOrNull(16)?.let(::codePointToString)
        name.startsWith("#") -> name.substring(1).toIntOrNull()?.let(::codePointToString)
        else -> null
    }

    private fun codePointToString(codePoint: Int): String? = when {
        codePoint < 0 || codePoint > 0x10FFFF -> null
        codePoint < 0x10000 -> codePoint.toChar().toString()
        else -> {
            val offset = codePoint - 0x10000
            charArrayOf(
                (0xD800 + (offset shr 10)).toChar(),
                (0xDC00 + (offset and 0x3FF)).toChar(),
            ).concatToString()
        }
    }

    private fun skipWhitespace(start: Int): Int {
        var i = start
        while (i < xml.length && xml[i].isWhitespace()) i++
        return i
    }

    private fun indexOf(token: String, from: Int): Int {
        val index = xml.indexOf(token, from)
        require(index >= 0) { "Expected '$token' after position $from" }
        return index
    }

    private fun indexAfter(token: String, from: Int): Int = indexOf(token, from) + token.length
}

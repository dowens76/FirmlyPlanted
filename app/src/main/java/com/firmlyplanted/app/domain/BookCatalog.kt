package com.firmlyplanted.app.domain

enum class Testament { OLD, NEW }

/** A Bible book: display name plus the standard USX/OSIS 3-4 letter code used by most APIs. */
data class BookInfo(
    val name: String,
    val code: String,
    val testament: Testament,
)

/**
 * The 66 Protestant-canon books in canonical order, with their standard USX/OSIS codes.
 * Used to populate pickers and to build fetch.bible / bible.com deep links. Deliberately does
 * NOT include a hardcoded verse-per-chapter table: verse counts and scope validation (e.g. the
 * ESV 500-verse cap) are derived from the actual fetched passage data instead, so a typo here
 * can't silently under/over-count a licensed limit.
 */
object BookCatalog {
    val books: List<BookInfo> = listOf(
        BookInfo("Genesis", "GEN", Testament.OLD),
        BookInfo("Exodus", "EXO", Testament.OLD),
        BookInfo("Leviticus", "LEV", Testament.OLD),
        BookInfo("Numbers", "NUM", Testament.OLD),
        BookInfo("Deuteronomy", "DEU", Testament.OLD),
        BookInfo("Joshua", "JOS", Testament.OLD),
        BookInfo("Judges", "JDG", Testament.OLD),
        BookInfo("Ruth", "RUT", Testament.OLD),
        BookInfo("1 Samuel", "1SA", Testament.OLD),
        BookInfo("2 Samuel", "2SA", Testament.OLD),
        BookInfo("1 Kings", "1KI", Testament.OLD),
        BookInfo("2 Kings", "2KI", Testament.OLD),
        BookInfo("1 Chronicles", "1CH", Testament.OLD),
        BookInfo("2 Chronicles", "2CH", Testament.OLD),
        BookInfo("Ezra", "EZR", Testament.OLD),
        BookInfo("Nehemiah", "NEH", Testament.OLD),
        BookInfo("Esther", "EST", Testament.OLD),
        BookInfo("Job", "JOB", Testament.OLD),
        BookInfo("Psalms", "PSA", Testament.OLD),
        BookInfo("Proverbs", "PRO", Testament.OLD),
        BookInfo("Ecclesiastes", "ECC", Testament.OLD),
        BookInfo("Song of Solomon", "SNG", Testament.OLD),
        BookInfo("Isaiah", "ISA", Testament.OLD),
        BookInfo("Jeremiah", "JER", Testament.OLD),
        BookInfo("Lamentations", "LAM", Testament.OLD),
        BookInfo("Ezekiel", "EZK", Testament.OLD),
        BookInfo("Daniel", "DAN", Testament.OLD),
        BookInfo("Hosea", "HOS", Testament.OLD),
        BookInfo("Joel", "JOL", Testament.OLD),
        BookInfo("Amos", "AMO", Testament.OLD),
        BookInfo("Obadiah", "OBA", Testament.OLD),
        BookInfo("Jonah", "JON", Testament.OLD),
        BookInfo("Micah", "MIC", Testament.OLD),
        BookInfo("Nahum", "NAM", Testament.OLD),
        BookInfo("Habakkuk", "HAB", Testament.OLD),
        BookInfo("Zephaniah", "ZEP", Testament.OLD),
        BookInfo("Haggai", "HAG", Testament.OLD),
        BookInfo("Zechariah", "ZEC", Testament.OLD),
        BookInfo("Malachi", "MAL", Testament.OLD),
        BookInfo("Matthew", "MAT", Testament.NEW),
        BookInfo("Mark", "MRK", Testament.NEW),
        BookInfo("Luke", "LUK", Testament.NEW),
        BookInfo("John", "JHN", Testament.NEW),
        BookInfo("Acts", "ACT", Testament.NEW),
        BookInfo("Romans", "ROM", Testament.NEW),
        BookInfo("1 Corinthians", "1CO", Testament.NEW),
        BookInfo("2 Corinthians", "2CO", Testament.NEW),
        BookInfo("Galatians", "GAL", Testament.NEW),
        BookInfo("Ephesians", "EPH", Testament.NEW),
        BookInfo("Philippians", "PHP", Testament.NEW),
        BookInfo("Colossians", "COL", Testament.NEW),
        BookInfo("1 Thessalonians", "1TH", Testament.NEW),
        BookInfo("2 Thessalonians", "2TH", Testament.NEW),
        BookInfo("1 Timothy", "1TI", Testament.NEW),
        BookInfo("2 Timothy", "2TI", Testament.NEW),
        BookInfo("Titus", "TIT", Testament.NEW),
        BookInfo("Philemon", "PHM", Testament.NEW),
        BookInfo("Hebrews", "HEB", Testament.NEW),
        BookInfo("James", "JAS", Testament.NEW),
        BookInfo("1 Peter", "1PE", Testament.NEW),
        BookInfo("2 Peter", "2PE", Testament.NEW),
        BookInfo("1 John", "1JN", Testament.NEW),
        BookInfo("2 John", "2JN", Testament.NEW),
        BookInfo("3 John", "3JN", Testament.NEW),
        BookInfo("Jude", "JUD", Testament.NEW),
        BookInfo("Revelation", "REV", Testament.NEW),
    )

    fun byName(name: String): BookInfo? = books.find { it.name.equals(name, ignoreCase = true) }

    /** Books available for a given translation, based on which testament(s) it covers. */
    fun booksFor(testaments: Set<Testament>): List<BookInfo> = books.filter { it.testament in testaments }
}

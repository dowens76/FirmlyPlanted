package com.firmlyplanted.app.domain

/** Builds the "Read more" approved-site URL for a translation/book/chapter — see LICENSING.md. */
object ReadMoreUrlBuilder {
    fun build(translation: Translation, bookName: String, chapter: Int): String? {
        val template = translation.approvedWebReaderUrlTemplate ?: return fallbackHomepage(translation)
        val bookCode = BookCatalog.byName(bookName)?.code ?: bookName
        return template
            .replace("{book}", bookName.replace(" ", "+"))
            .replace("{chapter}", chapter.toString())
            .replace("{osis}", bookCode)
    }

    /** For sources with no confirmed per-passage deep link (e.g. SBLGNT — see DefaultTranslations). */
    private fun fallbackHomepage(translation: Translation): String? = when (translation.id) {
        DefaultTranslations.SBLGNT_ID -> "https://sblgnt.com/"
        else -> null
    }
}

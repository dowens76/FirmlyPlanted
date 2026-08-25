package com.firmlyplanted.app.domain

/**
 * The four texts shown up front, per Daniel's spec. Everything else lives behind "More" and is
 * discovered at runtime from the fetch.bible collection (see TranslationRepository).
 *
 * All endpoints and ids below were verified live against fetch.bible's manifest and USX
 * endpoints (Aug 2026): base https://v1.fetch.bible/, book content at
 * bibles/{sourceId}/usx/{lowercase-3-letter-code}.usx, ids confirmed as hbo_wlc / grc_sbl /
 * vie_kt (NOT the guessed heb_wlc / grc_sblgnt / vie_1925 from before verification).
 *
 * approvedWebReaderUrlTemplate values were spot-checked against the live site (Aug 2026):
 *   - esv.org: confirmed working pattern https://www.esv.org/{Book}+{chapter}
 *   - bible.com: confirmed working pattern https://www.bible.com/bible/193/{OSIS}.{chapter}.VIE1925
 *   - sblgnt.com has no per-passage online reader (download/print only), so SBLGNT's template
 *     is left null; the "Read more" screen falls back to the publisher's homepage in that case.
 *   - WLC text is public domain, so no fallback reader is legally required; left null rather
 *     than guessing an unverified deep-link URL.
 *
 * Vietnamese 1925 licensing note: fetch.bible/eBible.org's own copyright page for this exact
 * text (ebible.org/Scriptures/details.php?id=vie1934) states the NT entered the public domain
 * 2019-01-01 and the OT 2021-01-01 (US copyright-term expiration), and lists it as public
 * domain — which is what this app now relies on for the fetch.bible-sourced file. That is a
 * different, better-grounded read than an earlier general web search, which surfaced a claim
 * that a separate 1998 United Bible Societies *digitization/typesetting* of the same
 * underlying translation is copyrighted with a 1,000-verse quotation cap. Both can be true at
 * once (old translation out of copyright; a particular publisher's edition of it claiming its
 * own rights) — this app only fetches eBible.org's public-domain-declared file, so it is
 * treated as unrestricted like WLC, with a soft in-app note rather than a hard scope cap. See
 * LICENSING.md before any public release if you want a second opinion on this.
 */
object DefaultTranslations {
    const val ESV_ID = "ESV"
    const val WLC_ID = "WLC"
    const val SBLGNT_ID = "SBLGNT"
    const val VIE1925_ID = "VIE1925"

    val esv = Translation(
        id = ESV_ID,
        displayName = "English Standard Version (ESV)",
        language = "English",
        source = TranslationSource.ESV_API,
        sourceId = "esv",
        copyrightNoticeResName = "copyright_esv",
        licenseSummary = "Non-commercial use only. Max 500 verses (or half a book) cached/displayed at once.",
        maxCachedVerses = 500,
        approvedWebReaderUrlTemplate = "https://www.esv.org/{book}+{chapter}",
        isDefault = true,
        testaments = setOf(Testament.OLD, Testament.NEW),
    )

    val wlc = Translation(
        id = WLC_ID,
        displayName = "Westminster Leningrad Codex (Hebrew)",
        language = "Hebrew",
        source = TranslationSource.FETCH_BIBLE,
        sourceId = "hbo_wlc",
        copyrightNoticeResName = "copyright_wlc",
        licenseSummary = "Public domain.",
        maxCachedVerses = null,
        approvedWebReaderUrlTemplate = null,
        isDefault = true,
        testaments = setOf(Testament.OLD),
    )

    val sblgnt = Translation(
        id = SBLGNT_ID,
        displayName = "SBL Greek New Testament",
        language = "Greek",
        source = TranslationSource.FETCH_BIBLE,
        sourceId = "grc_sbl",
        copyrightNoticeResName = "copyright_sblgnt",
        licenseSummary = "Free redistribution (CC BY 4.0-style); cannot be sold standalone; no unlicensed Greek-English diglot.",
        maxCachedVerses = null,
        approvedWebReaderUrlTemplate = null,
        isDefault = true,
        testaments = setOf(Testament.NEW),
    )

    val vie1925 = Translation(
        id = VIE1925_ID,
        displayName = "Vietnamese Bible 1925 (Bản Truyền Thống)",
        language = "Vietnamese",
        source = TranslationSource.FETCH_BIBLE,
        sourceId = "vie_kt",
        copyrightNoticeResName = "copyright_vie1925",
        licenseSummary = "Public domain per eBible.org (NT: Jan 2019, OT: Jan 2021, US copyright-term expiration). See in-app note on a separate UBS-edition caveat.",
        maxCachedVerses = null,
        approvedWebReaderUrlTemplate = "https://www.bible.com/bible/193/{osis}.{chapter}.VIE1925",
        isDefault = true,
        testaments = setOf(Testament.OLD, Testament.NEW),
    )

    val all: List<Translation> = listOf(esv, wlc, sblgnt, vie1925)
}

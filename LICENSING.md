# Licensing notes for Firmly Planted

This file consolidates the licensing research behind how this app fetches, caches, and displays
Scripture text. It's a reference for future changes, not legal advice — if you plan to publish
this app widely (Play Store, beyond your own device), it's worth a quick read-through, and for
anything marked "**worth confirming**" below, a quick check with the actual rights holder before
you rely on it at scale.

## How the app limits its own exposure, regardless of source

Rather than caching a whole memory project's text up front, the app only ever caches a small
rolling window — the verses currently due for review or newly introduced each day, plus ~8
verses of surrounding context (`ProjectRepository.WINDOW_PADDING`). Everything outside that
window is evicted from the local database automatically. This keeps cached text far under any
per-source numeric cap no matter how large a project's declared scope is, and it's why the ESV
500-verse limit is enforced both structurally (the window is tiny) and explicitly (a
belt-and-suspenders check at project creation, `LicensePolicy.checkScope`). Marking a project
complete (or hitting "Clear cached text now" in its settings) clears its cached text
immediately; the project's own progress history is kept.

For any text a user wants to read beyond that small window (e.g. "show me the whole chapter"),
the app opens a WebView to that translation's own official public reader (`ReadMoreUrlBuilder`)
instead of fetching/caching more raw text itself.

## ESV (English Standard Version)

- Source: `api.esv.org` v3 passage-text endpoint. Requires a free, personal API key —
  register yourself at https://api.esv.org/ (account creation isn't something an assistant can
  do on your behalf).
- **Non-commercial use only.** If you ever want to charge for this app or run ads in it, you
  need a separate commercial license from Crossway — don't ship a paid/ad-supported build
  against the free API key.
- Cap: 500 verses, or half a book (whichever is smaller), cached/displayed at once. Rate
  limits: 5,000 queries/day, 1,000/hour, 60/minute.
- Required notice (already wired into `strings.xml` / shown by `CopyrightNotice`):
  > Scripture quotations are from the ESV® Bible (The Holy Bible, English Standard Version®),
  > copyright © 2001 by Crossway, a publishing ministry of Good News Publishers. Used by
  > permission. All rights reserved.
- Source: https://www.esv.org/about/terms/ and https://api.esv.org/docs/

## fetch.bible

Base `https://v1.fetch.bible/`; `manifest.json` lists every translation with its own
`copyright.attribution` / `copyright.licenses[]` metadata; book text is served whole-book as
USX 3 XML at `bibles/{id}/usx/{book-code}.usx`. Endpoints and the three ids below were spot-
checked live against the real service while building this app (Aug 2026) — re-verify if
`FetchBibleService`/`DefaultTranslations` ever start returning errors, since this is an
unofficial integration against a public but undocumented-for-mobile API.

### WLC — Westminster Leningrad Codex (`hbo_wlc`)
Public domain, per fetch.bible's own manifest and the Groves Center's own statement. No
restrictions; a courtesy acknowledgement to the Groves Center is appreciated, not required.

### SBLGNT — SBL Greek New Testament (`grc_sbl`)
CC BY 4.0-style license. Free to redistribute; cannot be sold standalone; if it makes up ≥25%
of a work you sell, you need SBL's permission first. **May not be used in an unlicensed
Greek-English diglot** (Greek text shown side-by-side with an English translation) — the app's
UI should never pair SBLGNT verse text with an English gloss on the same screen
(`LicensePolicy.forbidsDiglot`). Attribution (used verbatim in `strings.xml`):
> Scripture quotations marked SBLGNT are from the SBL Greek New Testament. Copyright 2010
> Society of Biblical Literature and Logos Bible Software.

sblgnt.com has no online per-passage reader (download/print only), so the "Read more" fallback
for this translation opens their homepage rather than a deep link.

### Vietnamese Bible 1925 (`vie_kt`)
fetch.bible/eBible.org's own copyright page for this exact file
(https://ebible.org/Scriptures/details.php?id=vie1934) states it is public domain: the New
Testament entered the public domain 2019-01-01 and the Old Testament 2021-01-01 (US
copyright-term expiration), attributing the original translation to William Cadman
(1883–1948) and the Christian & Missionary Alliance. That's what this app relies on for the
fetch.bible-sourced file, and it's treated the same as WLC (no scope cap).

**Worth confirming before wide release:** an earlier general web search separately turned up a
claim that a *different*, separately-typeset 1998 United Bible Societies digitization/edition
of the same underlying translation is distributed elsewhere (e.g. some listings on bible.com)
under its own UBS copyright notice with a 1,000-verse/not-a-whole-book quotation limit. Both
can be true simultaneously — an old translation can be out of copyright while a particular
publisher's later edition of it claims its own rights in that specific typesetting. This app
only ever fetches eBible.org's public-domain-declared file, never a UBS-branded one, so the
inconsistency shouldn't actually affect what the app does — but if you're going to publish this
widely, it costs little to get a second opinion (e.g. from the Vietnamese Bible Society /
United Bible Societies, or a lawyer familiar with Vietnamese copyright term rules) before
leaning on the public-domain read at scale.

### "More" catalog (any other fetch.bible translation)
`TranslationRepository.fetchMoreCatalog()` reads each entry's `copyright.attribution` /
`copyright.licenses[]` straight from fetch.bible's manifest and shows it verbatim
(`FetchBibleCopyright.buildNotice()`); where a translation's metadata is missing or blank, the
app falls back to treating it as all-rights-reserved and says so in the notice.

## Hebrew audio — Abraham Shmuelof's reading (eBible.org)

WLC projects can play each verse aloud, using the Hebrew Bible read by Abraham Shmuelof as
published at https://ebible.org/multimedia/hbo/ (the same recording Mechon Mamre hosts; there,
credited ℗ 1992 Talking Bibles International, with permission required to reuse it). eBible.org
publishes it as audio ePubs under **CC BY-NC-ND 4.0**:

- **BY** — credit is shown in each WLC project's settings and on the About screen
  (`EbibleAudioRefs.CREDIT`).
- **NC** — fine while the app is non-commercial (it already must be, for the ESV terms).
- **ND** — the audio is never cut, re-encoded, mixed or altered. The app downloads each needed
  chapter's MP3 and its EPUB3 media overlay (SMIL) directly from eBible.org to the user's device
  (HTTP Range requests read only those entries of the ePub) and stores both byte for byte as they
  are in the ePub, only undoing the zip's own compression. To play a verse it plays the time
  range the SMIL gives for that verse from the unaltered chapter file — what any EPUB 3 reading
  system does with an audio ePub. Nothing is redistributed: the files live only in the app's
  private storage and are deleted when the project is completed, archived or deleted. Still
  worth a courtesy note to eBible.org before publishing beyond personal use.

The app's verse numbering (fetch.bible `hbo_wlc`) differs from the ePubs' Masoretic numbering and
the ePubs' timing tags have a few errors, so `scripts/build_ebible_audio_map.py` generates
`EbibleHebrewAudioIndex.kt` by aligning the two texts. Only factual timing/numbering data is
derived; no audio is part of the app or the repository.

## Greek audio — Andy Martin's reading of the Nestle 1904 text (eBible.org)

SBLGNT projects can play each verse aloud, using the Greek New Testament (public-domain 1904
Nestle text) read by Andy Martin, as published at https://ebible.org/multimedia/grc/ under
**CC BY-NC-ND 4.0** ("All these resources are published under a Creative Commons 4.0 BY-NC-ND
License"). So far eBible.org offers 13 books: Matthew, Mark, Luke, John, Romans, 1 Corinthians,
Ephesians, Philippians, Colossians, 1–3 John and Jude; other books have no audio until eBible.org
adds them (add them to `GREEK_BOOKS` in the script and re-run it).

The BY, NC and ND points above apply in the same way: credit in Project Settings and About
(`EbibleAudioRefs.GREEK.credit`), non-commercial use, and chapter MP3s and SMIL files stored byte
for byte and played as time ranges, never cut or altered.

The recording reads Nestle 1904, not the SBLGNT the app displays. `EbibleGreekAudioIndex.kt` maps
SBLGNT verses to the recording by aligning the two texts, and lists the verses whose recorded
words differ (beyond spelling such as Δαυείδ/Δαυίδ or Ἰωάνης/Ἰωάννης and word division); the app
shows a short note with those verses' Listen button. Mark 6:27–28, Luke 24:45–46 and Ephesians
5:13–14 divide the verses differently and are mapped by clip.

## The app's own code

MIT License (see `LICENSE`) — that only covers the app's source code, not any Scripture text it
fetches at runtime, which remains under the terms above.

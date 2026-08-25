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

## The app's own code

MIT License (see `LICENSE`) — that only covers the app's source code, not any Scripture text it
fetches at runtime, which remains under the terms above.

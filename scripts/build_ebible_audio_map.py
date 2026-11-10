#!/usr/bin/env python3
"""
Builds the tables that map this app's verse references to the timed audio clips in eBible.org's
audio ePubs, one generated Kotlin file per edition (in shared/src/commonMain/.../domain/):

- `hbo` -> EbibleHebrewAudioIndex.kt: WLC (fetch.bible `hbo_wlc`, English-style chapter/verse
  numbering) against Abraham Shmuelof's Hebrew reading (https://ebible.org/multimedia/hbo/), which
  uses Masoretic numbering. Two speeds (fluent, slower) with identical timing ids.
- `grc` -> EbibleGreekAudioIndex.kt: SBLGNT (fetch.bible `grc_sbl`) against Andy Martin's reading
  of the Nestle 1904 text (https://ebible.org/multimedia/grc/), 13 books so far. The two texts
  differ in places, so the table also lists the verses whose recorded wording isn't identical.

The ePubs tag clips `{verse}`, `{verse}a`, `{verse}b`, ...

Why a generated table: the two numberings differ (Mal 4:1 = Mal 3:19, Joel 3 = Joel 4, psalm titles
are Hebrew verse 1, ...) and the ePubs' own tags are occasionally wrong (Ps 119:118 is tagged
"117c"). So instead of trusting either numbering, this script aligns the two texts word by word
(consonants only, ketiv kept on both sides) and assigns every audio clip to the app verse whose
words it reads.

Only the ePubs' small text/SMIL entries are downloaded, via HTTP Range requests (the MP3s are
never fetched). Standard library only. Usage:

    python3 scripts/build_ebible_audio_map.py [--edition hbo|grc|all] [--cache DIR]

`--cache` keeps downloaded pieces between runs. Re-run only if eBible.org or fetch.bible change.
"""
import argparse
import difflib
import json
import os
import re
import struct
import sys
import unicodedata
import urllib.parse
import urllib.request
import zlib
from collections import defaultdict

FETCH_BIBLE_USX = "https://v1.fetch.bible/bibles/{bible}/usx/{code}.usx"
USER_AGENT = "FirmlyPlanted-build-script (https://github.com/)"
DOMAIN_DIR = os.path.join(
    os.path.dirname(os.path.abspath(__file__)), "..",
    "shared/src/commonMain/kotlin/com/firmlyplanted/app/domain",
)

# (app book name as in BookCatalog, USX code, ePub number, name in the ePub file name)
HEBREW_BOOKS = [
    ("Genesis", "GEN", 1, "בראשית"), ("Exodus", "EXO", 2, "שמות"), ("Leviticus", "LEV", 3, "ויקרא"),
    ("Numbers", "NUM", 4, "במדבר"), ("Deuteronomy", "DEU", 5, "דברים"), ("Joshua", "JOS", 6, "יהושע"),
    ("Judges", "JDG", 7, "שפטים"), ("Ruth", "RUT", 8, "רות"), ("1 Samuel", "1SA", 9, "שמואל א"),
    ("2 Samuel", "2SA", 10, "שמואל ב"), ("1 Kings", "1KI", 11, "מלכים א"), ("2 Kings", "2KI", 12, "מלכים ב"),
    ("1 Chronicles", "1CH", 13, "דברי הימים א"), ("2 Chronicles", "2CH", 14, "דברי הימים ב"),
    ("Ezra", "EZR", 15, "עזרא"), ("Nehemiah", "NEH", 16, "נחמיה"), ("Esther", "EST", 17, "אסתר"),
    ("Job", "JOB", 18, "איוב"), ("Psalms", "PSA", 19, "תהלים"), ("Proverbs", "PRO", 20, "משלי"),
    ("Ecclesiastes", "ECC", 21, "קהלת"), ("Song of Songs", "SNG", 22, "שיר השירים"),
    ("Isaiah", "ISA", 23, "ישעיהו"), ("Jeremiah", "JER", 24, "ירמיהו"), ("Lamentations", "LAM", 25, "איכה"),
    ("Ezekiel", "EZK", 26, "יחזקאל"), ("Daniel", "DAN", 27, "דניאל"), ("Hosea", "HOS", 28, "הושע"),
    ("Joel", "JOL", 29, "יואל"), ("Amos", "AMO", 30, "עמוס"), ("Obadiah", "OBA", 31, "עבדיה"),
    ("Jonah", "JON", 32, "יונה"), ("Micah", "MIC", 33, "מיכה"), ("Nahum", "NAM", 34, "נחום"),
    ("Habakkuk", "HAB", 35, "חבקוק"), ("Zephaniah", "ZEP", 36, "צפניה"), ("Haggai", "HAG", 37, "חגי"),
    ("Zechariah", "ZEC", 38, "זכריה"), ("Malachi", "MAL", 39, "מלאכי"),
]

# The books eBible.org has recorded so far (its page lists them; others are "to be added").
GREEK_BOOKS = [
    ("Matthew", "MAT", 46, "ΚΑΤΑ ΜΑΘΘΑΙΟΝ"), ("Mark", "MRK", 47, "ΚΑΤΑ ΜΑΡΚΟΝ"),
    ("Luke", "LUK", 48, "ΚΑΤΑ ΛΟΥΚΑΝ"), ("John", "JHN", 49, "ΚΑΤΑ ΙΩΑΝΗΝ"),
    ("Romans", "ROM", 51, "ΠΡΟΣ ΡΩΜΑΙΟΥΣ"), ("1 Corinthians", "1CO", 52, "ΠΡΟΣ ΚΟΡΙΝΘΙΟΥΣ Α"),
    ("Ephesians", "EPH", 55, "ΠΡΟΣ ΕΦΕΣΙΟΥΣ"), ("Philippians", "PHP", 56, "ΠΡΟΣ ΦΙΛΙΠΠΗΣΙΟΥΣ"),
    ("Colossians", "COL", 57, "ΠΡΟΣ ΚΟΛΟΣΣΑΕΙΣ"), ("1 John", "1JN", 68, "ΙΩΑΝΟΥ Α"),
    ("2 John", "2JN", 69, "ΙΩΑΝΟΥ Β"), ("3 John", "3JN", 70, "ΙΩΑΝΟΥ Γ"), ("Jude", "JUD", 71, "ΙΟΥΔΑ"),
]

# English names used in the Hebrew ePub file names where they differ from BookCatalog.
HEBREW_EPUB_ENGLISH = {
    "1 Samuel": "First Samuel", "2 Samuel": "Second Samuel", "1 Kings": "First Kings",
    "2 Kings": "Second Kings", "1 Chronicles": "First Chronicles", "2 Chronicles": "Second Chronicles",
}

EDITIONS = {
    "hbo": dict(
        base="https://ebible.org/multimedia/hbo/", bible="hbo_wlc", books=HEBREW_BOOKS,
        english=HEBREW_EPUB_ENGLISH, speeds=["fluent", "slower"],
        file_name=lambda book, number, native, speed: "Tanakh %s %02d-%s %s.epub" % (
            speed, number, HEBREW_EPUB_ENGLISH.get(book, book), native),
        out="EbibleHebrewAudioIndex.kt", object="EbibleHebrewAudioIndex", track_differences=False,
        max_unmatched=3,
        doc="this app's WLC verse references to clips in eBible.org's audio ePubs of Abraham\n"
            " * Shmuelof's Hebrew reading (Masoretic numbering)",
    ),
    "grc": dict(
        base="https://ebible.org/multimedia/grc/", bible="grc_sbl", books=GREEK_BOOKS,
        english={}, speeds=[None],
        file_name=lambda book, number, native, speed: "%02d_%s_%s.epub" % (number, book, native),
        out="EbibleGreekAudioIndex.kt", object="EbibleGreekAudioIndex", track_differences=True,
        max_unmatched=3,
        doc="this app's SBLGNT verse references to clips in eBible.org's audio ePubs of Andy\n"
            " * Martin's reading of the Nestle 1904 Greek New Testament",
    ),
}

# ---------------------------------------------------------------- HTTP with an optional disk cache

CACHE = None


def http_get(url, rng=None):
    key = None
    if CACHE:
        key = os.path.join(CACHE, re.sub(r"[^A-Za-z0-9]+", "_", urllib.parse.unquote(url))[-120:]
                           + ("_" + rng.replace("-", "_") if rng else ""))
        if os.path.exists(key):
            with open(key + ".total" if os.path.exists(key + ".total") else os.devnull) as f:
                total = f.read() or None
            with open(key, "rb") as f:
                return f.read(), (int(total) if total else None)
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    if rng:
        req.add_header("Range", "bytes=" + rng)
    with urllib.request.urlopen(req, timeout=120) as resp:
        data = resp.read()
        content_range = resp.headers.get("Content-Range")
    total = int(content_range.split("/")[1]) if content_range else None
    if key:
        with open(key, "wb") as f:
            f.write(data)
        if total:
            with open(key + ".total", "w") as f:
                f.write(str(total))
    return data, total


# ---------------------------------------------------------------- Ranged zip reading

def read_text_entries(url):
    """Returns {entry name: bytes} for every Text/ entry of the remote ePub, in 2 range requests."""
    tail, total = http_get(url, "-65536")
    eocd = tail.rfind(b"PK\x05\x06")
    if eocd < 0:
        raise RuntimeError("No end-of-central-directory record in " + url)
    cd_size, cd_offset = struct.unpack("<II", tail[eocd + 12:eocd + 20])
    tail_start = total - len(tail)
    if cd_offset >= tail_start:
        cd = tail[cd_offset - tail_start:cd_offset - tail_start + cd_size]
    else:
        cd, _ = http_get(url, "%d-%d" % (cd_offset, cd_offset + cd_size - 1))
    entries = []
    p = 0
    while p < len(cd):
        method, = struct.unpack("<H", cd[p + 10:p + 12])
        csize, usize = struct.unpack("<II", cd[p + 20:p + 28])
        name_len, extra_len, comment_len = struct.unpack("<HHH", cd[p + 28:p + 34])
        local_offset, = struct.unpack("<I", cd[p + 42:p + 46])
        name = cd[p + 46:p + 46 + name_len].decode("utf-8")
        entries.append((name, method, csize, usize, local_offset))
        p += 46 + name_len + extra_len + comment_len
    wanted = [e for e in entries if re.search(r"(^|/)Text/", e[0])]
    lo = min(e[4] for e in wanted)
    hi = max(e[4] + 30 + len(e[0].encode()) + e[2] + 1024 for e in wanted)  # slack for extra fields
    hi = min(hi, cd_offset) - 1
    blob, _ = http_get(url, "%d-%d" % (lo, hi))
    out = {}
    for name, method, csize, usize, local_offset in wanted:
        h = local_offset - lo
        if blob[h:h + 4] != b"PK\x03\x04":
            raise RuntimeError("Bad local header for %s in %s" % (name, url))
        n_len, x_len = struct.unpack("<HH", blob[h + 26:h + 30])
        start = h + 30 + n_len + x_len
        data = blob[start:start + csize]
        out[name] = zlib.decompress(data, -15) if method == 8 else data
    return out


# ---------------------------------------------------------------- Text normalisation

HEBREW_LETTER = re.compile(r"[א-ת]+")
GREEK_LETTER = re.compile(r"[α-ω]+")


def words_of(text):
    """Letter-only words, so both texts tokenise the same way. Hebrew: consonants only (maqaf
    splits words). Greek: accents and breathings removed, lower case, final sigma as σ, and the
    common orthographic variants folded (ει/ι, νν/ν: Πειλᾶτος / Πιλᾶτος, Ἰωάνης / Ἰωάννης) so
    names spelled differently in Nestle 1904 and SBLGNT still align."""
    text = text.replace("־", " ")
    if any("\u0370" <= ch <= "\u03ff" or "\u1f00" <= ch <= "\u1fff" for ch in text):  # Greek, Greek Extended
        text = "".join(c for c in unicodedata.normalize("NFD", text) if not unicodedata.combining(c))
        text = text.lower().replace("ς", "σ").replace("ει", "ι").replace("νν", "ν")
        pattern = GREEK_LETTER
    else:
        pattern = HEBREW_LETTER
    out = []
    for raw in text.split():
        letters = "".join(pattern.findall(raw))
        if letters:
            out.append(letters)
    return out


def ebible_units(xhtml_by_chapter, smil_by_chapter):
    """[(chapter, id, [words])] in reading order, for every SMIL clip of the book."""
    units = []
    for chapter in sorted(smil_by_chapter):
        smil = smil_by_chapter[chapter]
        xhtml = xhtml_by_chapter[chapter]
        ids = re.findall(r'<text src="[^"#]*#([^"]+)"', smil)
        texts = []  # (text element id, body) in reading order
        for m in re.finditer(r'<div id="([^"]+)" class="aud">(.*?)</div>', xhtml, re.S):
            body = re.sub(r"<sup>.*?</sup>", " ", m.group(2), flags=re.S)  # verse number
            body = re.sub(r"<[^>]+>", " ", body)
            body = re.sub(r"\*\*\S+", " ", body)  # drop qere (read form); WLC text carries the ketiv
            body = body.replace("*", "")
            texts.append((m.group(1), body))
        if len(texts) == len(ids):
            # Pair clips with text elements by position: some SMIL ids are mislabelled (Gen 24:62's
            # clip is tagged "61c", 1 Chr 1 uses bare "a", "b", ...) while their order is right.
            for i, (_, body) in zip(ids, texts):
                units.append((chapter, i, words_of(body)))
        else:
            text_by_id = dict(texts)
            for i in ids:
                # A clip with no matching text element can't be placed; it's left out, and any verse
                # it read is reported as unmatched (e.g. 1 Chr 1:41-42, read as clips "a", "b", "c").
                units.append((chapter, i, words_of(text_by_id[i]) if i in text_by_id else None))
    return units


def app_verses(usx):
    """[(chapter, verse, [words])] from fetch.bible USX; titles (para style d) are outside verses."""
    usx = re.sub(r"<note\b.*?</note>", " ", usx, flags=re.S)
    verses = []
    for m in re.finditer(r'<verse [^>]*sid="([A-Z0-9]+ (\d+):(\d+))"[^>]*/>', usx):
        chapter, verse = int(m.group(2)), int(m.group(3))
        end = usx.find('eid="%s"' % m.group(1), m.end())
        if end < 0:
            end = usx.find("<verse", m.end())
        body = usx[m.end():end]
        # Paragraph-level titles in the middle of a verse span are not part of the verse.
        body = re.sub(r'<para style="d"[^>]*>.*?</para>', " ", body, flags=re.S)
        body = re.sub(r"<[^>]+>", " ", body)
        verses.append((chapter, verse, words_of(body)))
    return verses


# ---------------------------------------------------------------- Alignment

def align(app, units, fill_parts=False):
    """Assigns each ebible unit to the app verse covering most of its words. Returns
    {(chapter, verse): [(ebible chapter, id), ...]} in reading order."""
    a_words, a_owner = [], []
    for idx, (_, _, ws) in enumerate(app):
        a_words += ws
        a_owner += [idx] * len(ws)
    e_words, e_owner = [], []
    for idx, (_, _, ws) in enumerate(units):
        e_words += ws or []
        e_owner += [idx] * len(ws or [])
    votes = defaultdict(lambda: defaultdict(int))  # unit -> app verse -> matched words
    sm = difflib.SequenceMatcher(None, e_words, a_words, autojunk=False)
    for block in sm.get_matching_blocks():
        for k in range(block.size):
            votes[e_owner[block.a + k]][a_owner[block.b + k]] += 1
    assigned = {}  # unit index -> app verse index
    for u_idx, unit in enumerate(units):
        if unit[2] is None:
            continue  # no text to tell which verse it reads (see ebible_units)
        if not votes[u_idx]:
            continue  # e.g. a psalm title, which isn't a verse in the app's text
        best = max(votes[u_idx].items(), key=lambda kv: kv[1])
        # Require the unit to be mostly matched, so a title that shares a few words isn't assigned.
        if best[1] * 2 < len(unit[2]):
            continue
        assigned[u_idx] = best[0]
    # A short part whose words mostly differ (a variant reading) isn't placed by the vote. If every
    # placed part with the same ePub verse number went to one app verse, it belongs there too.
    # Greek only: in Hebrew an unplaced part is usually a psalm title, which must stay out.
    owners = defaultdict(set)
    for u_idx, a_idx in assigned.items():
        owners[(units[u_idx][0], verse_num(units[u_idx][1]))].add(a_idx)
    for u_idx, unit in enumerate(units):
        if not fill_parts or u_idx in assigned or unit[2] is None:
            continue
        candidates = owners.get((unit[0], verse_num(unit[1])))
        if candidates and len(candidates) == 1:
            assigned[u_idx] = next(iter(candidates))
    mapping = defaultdict(list)
    for u_idx in sorted(assigned):
        v_chapter, v_verse, _ = app[assigned[u_idx]]
        mapping[(v_chapter, v_verse)].append((units[u_idx][0], units[u_idx][1]))
    return mapping


def spelling_insensitive(words):
    """For telling real wording differences from spelling ones between the Nestle 1904 recording
    and SBLGNT: ignores word division (μη ποτε / μηποτε) and the two common orthographic
    differences in names (Δαυείδ / Δαυίδ, Ἰωάνης / Ἰωάννης)."""
    return "".join(words).replace("ει", "ι").replace("νν", "ν")


def verse_num(unit_id):
    m = re.match(r"\d+", unit_id)
    return int(m.group()) if m else None


def build_book(ed, book, code, number, native):
    files = [read_text_entries(ed["base"] + urllib.parse.quote(ed["file_name"](book, number, native, speed)))
             for speed in ed["speeds"]]
    entry = re.compile(r"(?:^|/)Text/\d+-%s-(\d+)\.xhtml(\.smil)?$" % code)
    smil, xhtml = {}, {}
    for name, data in files[0].items():
        m = entry.search(name)
        if not m or int(m.group(1)) == 0:
            continue
        (smil if m.group(2) else xhtml)[int(m.group(1))] = data.decode("utf-8")
    for other_file in files[1:]:
        other_smil = {int(m.group(1)): d.decode("utf-8") for n, d in other_file.items()
                      for m in [entry.search(n)] if m and m.group(2)}
        for chapter, s_text in smil.items():
            ids = re.findall(r"#([^\"]+)\"", s_text)
            if chapter not in other_smil or re.findall(r"#([^\"]+)\"", other_smil[chapter]) != ids:
                raise RuntimeError("%s %d: the editions' clip ids differ" % (book, chapter))
    units = ebible_units(xhtml, smil)
    usx, _ = http_get(FETCH_BIBLE_USX.format(bible=ed["bible"], code=code.lower()))
    app = app_verses(usx.decode("utf-8"))
    mapping = align(app, units, fill_parts=ed["track_differences"])

    ids_by_verse = defaultdict(list)  # (ebible chapter, ebible verse) -> all ids, reading order
    words_by_unit = {}
    for chapter, unit_id, ws in units:
        ids_by_verse[(chapter, verse_num(unit_id))].append(unit_id)
        words_by_unit[(chapter, unit_id)] = ws or []

    rules, overrides, unmatched, differing = [], [], [], []
    for chapter, verse, app_words in app:
        clips = mapping.get((chapter, verse))
        if not clips:
            unmatched.append("%s %d:%d" % (book, chapter, verse))
            continue
        if ed["track_differences"]:
            recorded = [w for c in clips for w in words_by_unit[c]]
            if spelling_insensitive(recorded) != spelling_insensitive(app_words):
                differing.append((chapter, verse))
        e_chapter = clips[0][0]
        e_verses = {verse_num(i) for c, i in clips}
        whole = (all(c == e_chapter for c, _ in clips) and len(e_verses) == 1 and None not in e_verses
                 and [i for _, i in clips] == ids_by_verse[(e_chapter, next(iter(e_verses)))])
        if whole:
            rules.append((chapter, verse, e_chapter, next(iter(e_verses)) - verse))
        else:
            overrides.append((chapter, verse, ["%d:%s" % (c, i) for c, i in clips]))
    return rules, overrides, unmatched, differing, len(app)


def compress_rules(rules):
    """Collapses per-verse (chapter, verse, ebible chapter, offset) into ranges, dropping identity."""
    out = []
    for chapter, verse, e_chapter, offset in rules:
        if e_chapter == chapter and offset == 0:
            continue
        last = out[-1] if out else None
        if last and last[0] == chapter and last[2] == verse - 1 and last[3] == e_chapter and last[4] == offset:
            last[2] = verse
        else:
            out.append([chapter, verse, verse, e_chapter, offset])
    return out


def kotlin_string(s):
    return '"' + s.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$") + '"'


def build_edition(key):
    ed = EDITIONS[key]
    out_path = os.path.join(DOMAIN_DIR, ed["out"])
    book_lines, rule_lines, override_lines, differing_lines = [], [], [], []
    failed = False
    total_unmatched = total_verses = total_differing = 0
    for book, code, number, native in ed["books"]:
        rules, overrides, unmatched, differing, count = build_book(ed, book, code, number, native)
        ranges = compress_rules(rules)
        total_unmatched += len(unmatched)
        total_verses += count
        total_differing += len(differing)
        print("%-14s verses %5d  ranges %3d  overrides %3d  differing %4d  unmatched %d %s" % (
            book, count, len(ranges), len(overrides), len(differing), len(unmatched), " ".join(unmatched[:6])))
        if len(unmatched) > ed["max_unmatched"]:
            failed = True
        book_lines.append("        %s to EbibleBookFile(%d, %s, %s)," % (
            kotlin_string(book), number, kotlin_string(ed["english"].get(book, book)), kotlin_string(native)))
        for chapter, v_from, v_to, e_chapter, offset in ranges:
            rule_lines.append("        EbibleRangeRule(%s, %d, %d, %d, %d, %d)," % (
                kotlin_string(book), chapter, v_from, v_to, e_chapter, offset))
        for chapter, verse, clips in overrides:
            override_lines.append("        EbibleVerseKey(%s, %d, %d) to listOf(%s)," % (
                kotlin_string(book), chapter, verse, ", ".join(kotlin_string(c) for c in clips)))
        for ref in unmatched:
            b, cv = ref.rsplit(" ", 1)
            c, v = cv.split(":")
            override_lines.append("        EbibleVerseKey(%s, %s, %s) to emptyList(), // no matching audio" % (
                kotlin_string(b), c, v))
        for chapter, verse in differing:
            differing_lines.append("        EbibleVerseKey(%s, %d, %d)," % (kotlin_string(book), chapter, verse))
    if failed:
        sys.exit("Too many unmatched verses in at least one book; not writing %s" % out_path)
    print("%s: %d verses, %d unmatched, %d with differing wording" % (
        key, total_verses, total_unmatched, total_differing))

    def block(lines, empty):
        return ("\n" + "\n".join(lines) + "\n    ") if lines else empty

    kt = """// GENERATED by scripts/build_ebible_audio_map.py — do not edit by hand; re-run the script instead.
package com.firmlyplanted.app.domain

/**
 * Maps %s.
 * Verses not listed in [rangeRules] or [overrides] use the same chapter and verse number in the
 * ePub (all of that verse's clips: `N`, `Na`, `Nb`, ...). See the script for how the table is
 * derived. %d of %d app verses have no matching audio.
 */
object %s {
    const val BASE_URL = "%s"

    /** App book name -> its ePub file. */
    val bookFiles: Map<String, EbibleBookFile> = mapOf(%s)

    /** App verses [verseFrom]..[verseTo] of [chapter] are ePub chapter [ebibleChapter], verse (app verse + [verseOffset]). */
    val rangeRules: List<EbibleRangeRule> = listOf(%s)

    /** App verses whose clips don't follow a rule, as "ebibleChapter:clipId"; empty = no audio. */
    val overrides: Map<EbibleVerseKey, List<String>> = mapOf(%s)

    /**
     * App verses whose recorded words differ from the app's text (%d), ignoring word division and
     * spelling-only differences (ει/ι, νν/ν). Empty when not tracked for this edition.
     */
    val differingVerses: Set<EbibleVerseKey> = %s
}
""" % (ed["doc"], total_unmatched, total_verses, ed["object"], ed["base"],
       block(book_lines, ""), block(rule_lines, ""), block(override_lines, ""), total_differing,
       ("setOf(" + block(differing_lines, "") + ")") if differing_lines else "emptySet()")
    with open(out_path, "w", encoding="utf-8") as f:
        f.write(kt)
    print("Wrote %s" % os.path.relpath(out_path))


def main():
    global CACHE
    parser = argparse.ArgumentParser()
    parser.add_argument("--edition", choices=["hbo", "grc", "all"], default="all")
    parser.add_argument("--cache", help="directory to keep downloaded pieces between runs")
    args = parser.parse_args()
    if args.cache:
        CACHE = args.cache
        os.makedirs(CACHE, exist_ok=True)
    for key in (["hbo", "grc"] if args.edition == "all" else [args.edition]):
        build_edition(key)


if __name__ == "__main__":
    main()

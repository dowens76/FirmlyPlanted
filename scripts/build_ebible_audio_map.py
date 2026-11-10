#!/usr/bin/env python3
"""
Builds shared/src/commonMain/kotlin/com/firmlyplanted/app/domain/EbibleAudioIndex.kt, the table that
maps this app's WLC verse references (fetch.bible `hbo_wlc`, English-style chapter/verse numbering)
to the timed audio clips in eBible.org's audio ePubs of Abraham Shmuelof's Hebrew reading
(https://ebible.org/multimedia/hbo/), which use Masoretic numbering and tag half-verses as
`{verse}a`, `{verse}b`, ...

Why a generated table: the two numberings differ (Mal 4:1 = Mal 3:19, Joel 3 = Joel 4, psalm titles
are Hebrew verse 1, ...) and the ePubs' own tags are occasionally wrong (Ps 119:118 is tagged
"117c"). So instead of trusting either numbering, this script aligns the two texts word by word
(consonants only, ketiv kept on both sides) and assigns every audio clip to the app verse whose
words it reads.

Only the ePubs' small text/SMIL entries are downloaded, via HTTP Range requests (the MP3s are
never fetched). Standard library only. Usage:

    python3 scripts/build_ebible_audio_map.py [--cache DIR]

`--cache` keeps downloaded pieces between runs. Re-run only if eBible.org or fetch.bible change.
"""
import argparse
import difflib
import json
import os
import re
import struct
import sys
import urllib.parse
import urllib.request
import zlib
from collections import defaultdict

EBIBLE_BASE = "https://ebible.org/multimedia/hbo/"
FETCH_BIBLE_USX = "https://v1.fetch.bible/bibles/hbo_wlc/usx/{code}.usx"
USER_AGENT = "FirmlyPlanted-build-script (https://github.com/)"
OUT = os.path.join(
    os.path.dirname(os.path.abspath(__file__)), "..",
    "shared/src/commonMain/kotlin/com/firmlyplanted/app/domain/EbibleAudioIndex.kt",
)

# (app book name as in BookCatalog, USX code, ePub number, Hebrew name in the ePub file name)
BOOKS = [
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

# English names used in the ePub file names where they differ from BookCatalog.
EPUB_ENGLISH = {
    "1 Samuel": "First Samuel", "2 Samuel": "Second Samuel", "1 Kings": "First Kings",
    "2 Kings": "Second Kings", "1 Chronicles": "First Chronicles", "2 Chronicles": "Second Chronicles",
}

# Unmatched app verses tolerated per book before the script fails.
MAX_UNMATCHED_PER_BOOK = 3


def epub_file_name(book, number, hebrew, speed):
    return "Tanakh %s %02d-%s %s.epub" % (speed, number, EPUB_ENGLISH.get(book, book), hebrew)


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
    wanted = [e for e in entries if e[0].startswith("Text/")]
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


def words_of(text):
    """Consonant-only words; maqaf and spaces split words, so both sides tokenise the same way."""
    text = text.replace("־", " ")
    out = []
    for raw in text.split():
        letters = "".join(HEBREW_LETTER.findall(raw))
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

def align(app, units):
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
    mapping = defaultdict(list)
    for u_idx, unit in enumerate(units):
        if unit[2] is None:
            continue  # no text to tell which verse it reads (see ebible_units)
        if not votes[u_idx]:
            continue  # e.g. a psalm title, which isn't a verse in the app's text
        best = max(votes[u_idx].items(), key=lambda kv: kv[1])
        # Require the unit to be mostly matched, so a title that shares a few words isn't assigned.
        if best[1] * 2 < len(unit[2]):
            continue
        v_chapter, v_verse, _ = app[best[0]]
        mapping[(v_chapter, v_verse)].append((unit[0], unit[1]))
    return mapping


def verse_num(unit_id):
    m = re.match(r"\d+", unit_id)
    return int(m.group()) if m else None


def build_book(book, code, number, hebrew):
    fluent = read_text_entries(EBIBLE_BASE + urllib.parse.quote(epub_file_name(book, number, hebrew, "fluent")))
    slower = read_text_entries(EBIBLE_BASE + urllib.parse.quote(epub_file_name(book, number, hebrew, "slower")))
    smil, xhtml = {}, {}
    for name, data in fluent.items():
        m = re.match(r"Text/\d+-%s-(\d+)\.xhtml(\.smil)?$" % code, name)
        if not m or int(m.group(1)) == 0:
            continue
        (smil if m.group(2) else xhtml)[int(m.group(1))] = data.decode("utf-8")
    for chapter, s in smil.items():
        other = slower.get("Text/00-%s-%03d.xhtml.smil" % (code, chapter))
        ids = re.findall(r"#([^\"]+)\"", s)
        if other is None or re.findall(r"#([^\"]+)\"", other.decode("utf-8")) != ids:
            raise RuntimeError("%s %d: slower ePub's clip ids differ from fluent" % (book, chapter))
    units = ebible_units(xhtml, smil)
    usx, _ = http_get(FETCH_BIBLE_USX.format(code=code.lower()))
    app = app_verses(usx.decode("utf-8"))
    mapping = align(app, units)

    ids_by_verse = defaultdict(list)  # (ebible chapter, ebible verse) -> all ids, reading order
    for chapter, unit_id, _ in units:
        ids_by_verse[(chapter, verse_num(unit_id))].append(unit_id)

    rules, overrides, unmatched = [], [], []
    for chapter, verse, _ in app:
        clips = mapping.get((chapter, verse))
        if not clips:
            unmatched.append("%s %d:%d" % (book, chapter, verse))
            continue
        e_chapter = clips[0][0]
        e_verses = {verse_num(i) for c, i in clips}
        whole = (all(c == e_chapter for c, _ in clips) and len(e_verses) == 1 and None not in e_verses
                 and [i for _, i in clips] == ids_by_verse[(e_chapter, next(iter(e_verses)))])
        if whole:
            rules.append((chapter, verse, e_chapter, next(iter(e_verses)) - verse))
        else:
            overrides.append((chapter, verse, ["%d:%s" % (c, i) for c, i in clips]))
    return rules, overrides, unmatched, len(app)


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


def main():
    global CACHE
    parser = argparse.ArgumentParser()
    parser.add_argument("--cache", help="directory to keep downloaded pieces between runs")
    args = parser.parse_args()
    if args.cache:
        CACHE = args.cache
        os.makedirs(CACHE, exist_ok=True)

    book_lines, rule_lines, override_lines = [], [], []
    failed = False
    total_unmatched = 0
    for book, code, number, hebrew in BOOKS:
        rules, overrides, unmatched, count = build_book(book, code, number, hebrew)
        ranges = compress_rules(rules)
        total_unmatched += len(unmatched)
        print("%-14s verses %5d  ranges %3d  overrides %3d  unmatched %d %s" % (
            book, count, len(ranges), len(overrides), len(unmatched), " ".join(unmatched[:6])))
        if len(unmatched) > MAX_UNMATCHED_PER_BOOK:
            failed = True
        book_lines.append("        %s to EbibleBookFile(%d, %s, %s)," % (
            kotlin_string(book), number, kotlin_string(EPUB_ENGLISH.get(book, book)), kotlin_string(hebrew)))
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
    if failed:
        sys.exit("Too many unmatched verses in at least one book; not writing %s" % OUT)

    kt = """// GENERATED by scripts/build_ebible_audio_map.py — do not edit by hand; re-run the script instead.
package com.firmlyplanted.app.domain

/**
 * Maps this app's WLC verse references to clips in eBible.org's audio ePubs of Abraham Shmuelof's
 * Hebrew reading. Verses not listed in [rangeRules] or [overrides] use the same chapter and verse
 * number in the ePub (all of that verse's clips: `N`, `Na`, `Nb`, ...). See the script for how
 * the table is derived. %d app verses have no matching audio.
 */
object EbibleAudioIndex {
    const val BASE_URL = "https://ebible.org/multimedia/hbo/"

    /** App book name -> its ePub file. */
    val bookFiles: Map<String, EbibleBookFile> = mapOf(
%s
    )

    /** App verses [verseFrom]..[verseTo] of [chapter] are ePub chapter [ebibleChapter], verse (app verse + [verseOffset]). */
    val rangeRules: List<EbibleRangeRule> = listOf(
%s
    )

    /** App verses whose clips don't follow a rule, as "ebibleChapter:clipId"; empty = no audio. */
    val overrides: Map<EbibleVerseKey, List<String>> = mapOf(
%s
    )
}
""" % (total_unmatched, "\n".join(book_lines), "\n".join(rule_lines), "\n".join(override_lines))
    with open(OUT, "w", encoding="utf-8") as f:
        f.write(kt)
    print("Wrote %s" % os.path.relpath(OUT))


if __name__ == "__main__":
    main()

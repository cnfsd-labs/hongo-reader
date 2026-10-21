"""Builds Honmani's sample books: EPUB 3 from Aozora Bunko ruby texts, with a cover.

    python3 make_epub.py TEXT TITLE AUTHOR COVER OUT [--first-chapter]

TEXT is an Aozora Bunko ruby text (the .txt inside the card's zip, Shift_JIS). The text keeps its
furigana and headings; Aozora's notes on the source edition and the volunteers who typed and
proofread it (底本, 入力, 校正) close the book, as Aozora Bunko asks. COVER is a PNG rendered from
cover.html (see README.md). The output is reproducible: fixed dates and identifiers.
"""
import html
import re
import sys
import uuid
import zipfile

KANJI = r"[々-〇㐀-䶿一-鿿豈-﫿ヵヶ※]"
# Small kana Aozora names without a code Unicode has (read the same as the full-size kana).
SMALL_KANA = {"小書き平仮名ん": "ん"}
STAMP = (2026, 1, 1, 0, 0, 0)


def split(text):
    lines = text.replace("\r\n", "\n").split("\n")
    # Title and author, then a ------- block of notes on the notation.
    dashes = [i for i, line in enumerate(lines) if line.startswith("-------")]
    start = dashes[1] + 1 if len(dashes) >= 2 else 2
    end = next((i for i, line in enumerate(lines) if line.startswith("底本：")), len(lines))
    colophon = [line for line in lines[end:] if line.strip()]
    return lines[start:end], colophon


def character(note):
    unicode = re.search(r"U\+([0-9A-Fa-f]{4,5})", note)
    if unicode:
        return chr(int(unicode.group(1), 16))
    # JIS X 0213 plane-row-cell, as EUC-JIS-2004 bytes (plane 2 behind 0x8F).
    jis = re.search(r"(?:第[34]水準)?([12])-(\d{1,2})-(\d{1,2})", note)
    if jis:
        plane, row, cell = (int(g) for g in jis.groups())
        encoded = bytes(([0x8F] if plane == 2 else []) + [0xA0 + row, 0xA0 + cell])
        try:
            decoded = encoded.decode("euc_jis_2004")
            if len(decoded) >= 1 and "\ufffd" not in decoded:
                return decoded
        except UnicodeDecodeError:
            pass
    for name, value in SMALL_KANA.items():
        if name in note:
            return value
    return "〓"


def convert(line):
    heading = re.search(r"［＃「(.+?)」は(?:大|中|小)見出し］", line)
    line = re.sub(r"※［＃(.*?)］", lambda m: character(m.group(1)), line)
    line = re.sub(r"［＃.*?］", "", line)
    line = html.escape(line, quote=False)
    line = re.sub(r"｜(.+?)《(.+?)》", r"<ruby>\1<rt>\2</rt></ruby>", line)
    line = re.sub("(" + KANJI + r"+)《(.+?)》", r"<ruby>\1<rt>\2</rt></ruby>", line)
    return heading.group(1) if heading else None, line


def chapters(lines):
    parts, current, title = [], [], None
    for raw in lines:
        heading, line = convert(raw)
        if heading:
            if any(p != "<p><br/></p>" for p in current):
                parts.append((title, current))
            current, title = [], heading
            current.append(f"<h2>{html.escape(heading)}</h2>")
            continue
        current.append(f"<p>{line}</p>" if line.strip() else "<p><br/></p>")
    if current:
        parts.append((title, current))
    return parts


def xhtml(title, body):
    return ('<?xml version="1.0" encoding="UTF-8"?>\n'
            '<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="ja">\n'
            f"<head><title>{html.escape(title)}</title></head>\n<body>\n" + "\n".join(body) + "\n</body>\n</html>\n")


def write(out, title, author, docs, cover):
    """docs: [(file name, toc title, xhtml)]."""
    manifest = ['<item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>',
                '<item id="cover" href="cover.png" media-type="image/png" properties="cover-image"/>']
    spine, toc = [], []
    for i, (name, toc_title, _) in enumerate(docs):
        manifest.append(f'<item id="c{i}" href="{name}" media-type="application/xhtml+xml"/>')
        spine.append(f'<itemref idref="c{i}"/>')
        toc.append(f'<li><a href="{name}">{html.escape(toc_title)}</a></li>')
    identifier = "urn:uuid:" + str(uuid.uuid5(uuid.NAMESPACE_URL, "honmani-sample:" + title + author))
    opf = f"""<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id" xml:lang="ja">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:identifier id="id">{identifier}</dc:identifier>
    <dc:title>{html.escape(title)}</dc:title>
    <dc:creator>{html.escape(author)}</dc:creator>
    <dc:language>ja</dc:language>
    <meta property="dcterms:modified">2026-01-01T00:00:00Z</meta>
    <meta name="cover" content="cover"/>
  </metadata>
  <manifest>
    {chr(10).join("    " + m for m in manifest).strip()}
  </manifest>
  <spine page-progression-direction="rtl">{"".join(spine)}</spine>
</package>
"""
    nav = ('<?xml version="1.0" encoding="UTF-8"?>\n<html xmlns="http://www.w3.org/1999/xhtml" '
           'xmlns:epub="http://www.idpf.org/2007/ops" xml:lang="ja"><head><title>目次</title></head><body>'
           f'<nav epub:type="toc"><ol>{"".join(toc)}</ol></nav></body></html>\n')
    container = ('<?xml version="1.0" encoding="UTF-8"?>\n<container version="1.0" '
                 'xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles>'
                 '<rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>'
                 "</rootfiles></container>\n")
    with zipfile.ZipFile(out, "w") as z:
        z.writestr(zipfile.ZipInfo("mimetype", STAMP), "application/epub+zip")
        for name, data in [("META-INF/container.xml", container), ("OEBPS/content.opf", opf),
                           ("OEBPS/nav.xhtml", nav)] + [("OEBPS/" + n, x) for n, _, x in docs]:
            z.writestr(zipfile.ZipInfo(name, STAMP), data, zipfile.ZIP_DEFLATED)
        with open(cover, "rb") as f:
            z.writestr(zipfile.ZipInfo("OEBPS/cover.png", STAMP), f.read(), zipfile.ZIP_STORED)


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    first_chapter = "--first-chapter" in sys.argv
    text_path, title, author, cover, out = args
    with open(text_path, "rb") as f:
        text = f.read().decode("cp932")
    body, colophon = split(text)
    parts = chapters(body)
    if first_chapter:
        parts = parts[:1]
    note = ("青空文庫（https://www.aozora.gr.jp/）作成のテキストから Honmani のサンプルとして"
            + ("一章のみ " if first_chapter else " ") + "EPUB 化。")
    closing = ["<p class=\"attribution\">―――</p>"]
    closing += [f'<p class="attribution">{html.escape(line)}</p>' for line in colophon]
    closing.append(f'<p class="attribution">{note}</p>')
    parts[-1] = (parts[-1][0], parts[-1][1] + closing)
    docs = [(f"ch{i + 1}.xhtml", t or title, xhtml(t or title, body)) for i, (t, body) in enumerate(parts)]
    write(out, title, author, docs, cover)


if __name__ == "__main__":
    main()

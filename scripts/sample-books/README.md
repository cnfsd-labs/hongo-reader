# Sample books

The books in `app/src/main/assets/samples/`, offered on the first-start screen (手袋を買いに) and in
Settings → Sample books. `SampleBook.kt` lists them, roughly easiest first.

## Licences

- **Texts:** Aozora Bunko (https://www.aozora.gr.jp/) ruby texts of public-domain works. Only
  authors who died before 1950, so the works are free in Japan, the EU (70 years) and the US: 新美南吉
  (1943), 宮沢賢治 (1933), 芥川龍之介 (1927), 夏目漱石 (1916). Not 太宰治 (died 1948, but his works
  were still protected in Japan in 1996, so the US restored them: 走れメロス until 2035, 人間失格 until
  2043). Aozora Bunko lets its files be used freely and asks that the source edition and the
  volunteers who typed and proofread them stay named: each book ends with the text's own
  colophon (底本, 入力, 校正) and a line saying it was converted for Honmani (`SampleBookTest`
  checks this).
- **Covers:** made for Honmani (`cover.html`), rendered with Shippori Mincho ExtraBold and Zen
  Kaku Gothic New (SIL Open Font License, from github.com/google/fonts `ofl/`); the images are not
  font software, so they carry no licence terms.

## Building

1. For each line of `books.txt`, download the ruby zip from the card's page
   (`https://www.aozora.gr.jp/cards/<author>/<card>.html`) and unpack its `.txt`.
2. Put the two fonts in `fonts/` next to `cover.html`, open `cover.html?v=<key>` in Chromium at
   800 × 1200 and save a screenshot as `cover-<key>.png`.
3. `python3 make_epub.py <txt> <title> <author> cover-<key>.png <key>.epub [--first-chapter]`.

The output is reproducible (fixed dates and identifiers), so rebuilding an unchanged book gives the
same file.

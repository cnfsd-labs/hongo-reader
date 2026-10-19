# HonGO!

A Japanese ebook reader for Android with a pop-up dictionary. Open a book, tap any word and see
what it means, even when it's conjugated: 食べさせられなかった leads straight to 食べる.

- Dictionaries with one tap on the first start (English or Japanese definitions), or any
  Yomitan dictionary
- EPUB and text files (Aozora Bunko too), vertical or horizontal text, furigana, reader fonts
- Readings, definitions, word frequency, pitch accent and kanji details for every tap
- Every word you look up is saved with its sentence; send words to Anki (AnkiDroid) as cards
- Reading statistics: time, characters, speed
- Your own book server (OPDS, such as Calibre-Web): browse, search, download
- No account, no ads, no tracking ([privacy policy](docs/privacy-policy.md))

Books are shown by [ッツ Ebook Reader](https://github.com/ttu-ttu/ebook-reader) inside the app;
everything around it (library, dictionaries, lookup, words, Anki, statistics) is HonGO!'s own.

## Status

In testing before its first release on Google Play (Android 8.0 and later). Installable test
builds come from CI: the `hongo-test-apks` artifact of a green run of the Android workflow
(see [`docs/device-testing.md`](docs/device-testing.md)).

## Stack

- Kotlin, Jetpack Compose (Material 3); one screen with tabs, overlays instead of navigation routes
- Room (KSP), manual dependency injection (`AppContainer`)
- `minSdk` 26, `targetSdk`/`compileSdk` 36
- Unit tests: JUnit 4, kotlinx-coroutines-test, Robolectric for Room DAO tests

## Building

Requires JDK 21, the Android SDK (platform 36), and Node.js 24 with pnpm 12 on `PATH` (for
ッツ Ebook Reader; the versions follow its CI). Clone with submodules:

```sh
git clone --recurse-submodules <repo>    # or: git submodule update --init
./gradlew lintDebug testDebugUnitTest assembleRelease
scripts/check-apk.sh app/build/outputs/apk/release/app-release-unsigned.apk
```

Browser tests for the reader's JavaScript run against the ttu build Gradle produces:

```sh
cd web-tests && npm ci && npx playwright install --with-deps chromium && npx playwright test
```

`check-apk.sh` is the release gate CI runs on every push. It checks that native libraries are
compatible with 16 KB memory pages, that the bundled reader is complete (including ttu's `_app/`
directory, which Android's default asset filter would drop), and that the APK requests no
Play-restricted permissions, such as all-files access or `QUERY_ALL_PACKAGES`.

The Room schema is exported to `app/schemas/`. Version 1 is what users have installed: a schema
change needs a new database version, a migration in `HongoDatabase.MIGRATIONS`, a test in
`DatabaseMigrationTest` and the newly exported JSON (CI fails on an uncommitted schema change).

Play bundles come from the "Release bundle" workflow, which signs `bundleRelease` with the
maintainers' upload key (repository secrets) when a `release/*` tag is pushed. Other builds are
unsigned (release) or debug-signed (internal, debug).

## Reader architecture

- `third_party/ttu-ebook-reader` is a pinned submodule. `:app:buildTtu` builds it with the patches
  in `third_party/patches/ttu-ebook-reader` and adds it as generated assets under `ttu/`. Web fonts
  (~150 MB) and the service worker are left out.
- `ReaderWebView` serves those assets on `https://appassets.androidplatform.net` through
  `WebViewAssetLoader`. Every request on that origin is answered locally (missing files are 404s),
  and the WebView blocks network loads. The app's INTERNET permission is only used for dictionary
  downloads and book servers. ttu keeps its library in IndexedDB for that origin, so
  the origin must not change.
- Scripts from `app/src/main/assets/hongo/` are injected at document start, for that origin
  only. `ttu-defaults.js` defaults ttu to system fonts, text size 28, the reading position saved on
  every page turn and a theme that follows the system's dark mode. `tap-bridge.js` turns a tap on book text
  into a `{text, sentence, sentenceOffset, ruby}` message to the app's `hongo` web message
  listener (`ruby`: the book's furigana, used to rank the entry with that reading first).
  `keep-position.js` brings back the first character on screen after a resize (rotation), since
  ttu restores only by paragraph. `library-bridge.js` lets the native library hand books to ttu
  (see below). `viewport-log.js` logs sizes and positions for diagnostics. `book-end.js` reports when the last character of a book is on screen (ttu's own progress stops
  where the last page starts), which marks the book finished. `book-shown.js` reports when an opened book is laid out, so the library stays over the reader until then (no blank flash). `help-text.js` shows ttu's help texts at
  the bottom of the screen (Popper placed them off screen in the WebView). `reading-activity.js`
  reports touches and every page turn's position (for reading statistics). `reader-settings.js`
  reads and writes ttu's settings for HonGO!'s reader settings screen and slims ttu's top bar.
- Back: from ttu's settings it returns to where they were opened, from a book to the library
  (`ui/reader/ReaderBack`).
- Reader settings are HonGO!'s own screen (`ui/settings/ReaderSettingsScreen`): colours, font,
  text size, line spacing, direction, page turns, furigana, progress, keeping the screen on.
  ttu's localStorage stays the only store; the hidden reader page reloads when the screen
  closes. ttu's full settings page is one row away for everything else.
- ttu patches (see `third_party/patches/ttu-ebook-reader/README.md`): no AGPL user-agent parser,
  Shift_JIS text, an explicit height for vertical text, no bookmark marker, finer positions in
  long paragraphs.
- ttu's file inputs open the system document picker, so no storage permission is needed.

## Library

The home screen has four tabs with a bar at the bottom (Library, Words, Dictionaries, Settings;
`ui/HomeNavigationBar`), drawn over the reader WebView, which stays loaded on ttu's book list
underneath. Settings has the accent colour, the reader settings, Anki, book servers and
diagnostics. On the very first start a welcome screen comes first (`ui/onboarding/`): a starter
set of dictionaries with one tap (English or Japanese definitions, plus kanji, frequency and
pitch) and the first chapter of 吾輩は猫である as a sample book (`assets/samples/`). A
compact toolbar floating over the covers (no background, they fade out towards the tabs) searches title and author and filters by reading status (not
started, reading, finished: from 98% progress, the last page reached, or marked by hand;
`ui/library/LibraryFilter`). Books can be hidden (long-press); they then only show on a hidden
shelf at the far left of the toolbar. A "Continue reading" bubble at the right opens the book read last
while it is being read. A search in Latin letters also matches romaji: titles are read with
the installed dictionary (`lookup/TextReading`) and compared in a loose romaji form
(`lookup/Romaji`: Hepburn or kunrei, long vowels written or not).
Adding a book (`data/library/BookImporter`) copies
the EPUB or TXT file into app storage under its SHA-256, reads title, author and cover from the
EPUB package document (`EpubMetadata`), and stores a `book` row. The content hash plus source is
the book's stable identity (duplicates, later sync). Text files are stored as UTF-8
(`TextEncoding`); Aozora Bunko texts are converted to EPUB (`AozoraText`, `AozoraEpub`), so
their furigana show as ruby and their headings become chapters.

ttu still parses and renders books from its own IndexedDB. `ui/reader/TtuLibrary` drives
`library-bridge.js`: the app serves the library file at `/hongo-books/<hash>.<ext>` on the reader
origin (`BookPathHandler`), the bridge feeds it to ttu's own import input and reports the id ttu
stored it under. Opening a book checks that ttu still holds that id and title (re-importing from
the library file if not) and loads `/b?id=…`. Reading progress comes from ttu's bookmarks when
the library is shown; removing a book deletes it from ttu too.

## Book catalogues (OPDS)

"Add book" offers files on the phone or a book server: an OPDS 1.x catalogue such as
Calibre-Web (`data/opds/`). `OpdsParser` reads Atom navigation and acquisition feeds (paging,
thumbnails, OpenSearch search); `OpdsClient` fetches them with Basic auth, follows redirects
itself and sends the login only to the catalogue's own origin. Typing just a host works: missing
`https://` is added and `/opds` is tried. Passwords are encrypted with an AES key in the Android
Keystore before they go into the `opds_catalog` table. A download goes through the same import
as a picked file (`ui/library/BookAdder`) with `BookSource.OPDS` and the entry id, so the
catalogue marks books already in the library. Plain http and user-installed CAs are allowed
(`res/xml/network_security_config.xml`) because such servers often run at home; the dialog warns
when a password would go over http. With automatic import on (per catalogue, default on), each
app start downloads new Japanese books from the catalogue's newest-books feed
(`data/opds/OpdsAutoImport`); books the reader removed are remembered and stay away.

## Saved words

Every lookup with a result is recorded (`data/words/WordRepository`): the best match's
dictionary form and reading, its first sense, the sentence from the book and the book. Words are
ranked by a decayed score (each lookup counts half as much after two weeks; `WordScore` stores a
time-independent rank key so SQLite can sort by it), so the list shows what the reader looks up
now and old words sink instead of being deleted. A second tap on the same word in the same
sentence within an hour does not count again. The latest 10 sentences per word are kept, for
example sentences on Anki cards later. The Words tab (`ui/words/`) lists words being looked up,
starred and learned ones (learned = marked by the reader; not the reader's whole vocabulary).

## Anki

Words go to AnkiDroid as cards of HonGO!'s own note type in the deck "HonGO!" (`anki/`): the
word and the book's sentence with the word in bold on the front; furigana, the senses of the
first dictionary that has the word, the book and audio on the back (`AnkiCard`). They are added
from the lookup panel, from the Words tab (with the latest or a chosen sentence), or all at once
from "For Anki": words looked up more than once that are not learned and not in Anki yet.
`AnkiDroid` is a small client of AnkiDroid's content provider (runtime permission
`com.ichi2.anki.permission.READ_WRITE_DATABASE`, `<queries>` for its authority); `AnkiExporter`
creates the note type and deck, skips duplicates (Anki's first-field checksum plus the reading)
and remembers the note id on the word. Audio (`CardVoice`) is off, the phone's text-to-speech, or
Google Cloud Text-to-Speech with the reader's own API key (stored encrypted, sent only to Google in
a header); words are spoken from their kana reading so they are never misread. Audio files reach
AnkiDroid through a `FileProvider` and are deleted after AnkiDroid copied them.

## Dictionaries

The Dictionaries screen offers one-tap downloads (Jitendex, JMdict, the Japanese Wiktionary for
definitions in Japanese, KANJIDIC, Jiten frequency, Kanjium pitch accents;
`data/dictionary/DictionaryCatalog`) from the URLs the dictionaries publish themselves, and
imports Yomitan-format `.zip` files from the device (format 1–3): `index.json`, term banks, kanji
banks, term meta banks (frequency and pitch accent, `TermMetaParser`, stored in `term_meta`) and
tag banks. Kanjium has no Yomitan release, so HonGO! downloads its source list (`accents.txt`)
and converts it (`KanjiumAccents`). Media files (images) are not imported yet. An import runs in
WorkManager as a data-sync foreground service (`DictionaryImportWorker`) so that it finishes when
the app is left; `DictionaryImporter` streams the term banks into Room in transactions of 5,000
rows and removes everything it wrote if the import fails. Definitions are stored deflated with an
8 KB preset sampled from the dictionary's own entries (`GlossaryPacker`; Jitendex 466 → 59 MB),
and only the entries a lookup matches are unpacked. Installed dictionaries are checked for
updates at app start (`DictionaryUpdateChecker`) and can be put in order, which decides the
order in the panel and which dictionary gives a card its meaning.

## Reading statistics

`data/stats/ReadingTracker` counts reading time while a book is on screen and the app is in
front (a stretch without touches counts at most 5 minutes) and characters from ttu's reading
position: a page counts once it was on screen for 5 seconds, up to what that time allows, so
flicking through pages and table-of-contents jumps are not reading. Totals are kept per book and
day (`reading_day`). The statistics screen shows today, the last 7 days or all time, with the
reading speed compared with an earlier span and a weekly speed trend; each book has its own
sheet from its long-press menu.

## Lookup

`lookup/Deinflector` undoes verb and i-adjective inflection with HonGO!'s own suffix rules
(written from Japanese grammar, not taken from Yomitan). Rules are applied repeatedly and track
which word classes a form can still be, so 食べさせられなかった reaches 食べる (ichidan) via
causative, passive/potential, negative and past. `test/resources/deinflection-corpus.tsv` holds
the hand-written forms it must handle.

`ui/reader/LookupPanel` shows the results without blocking the book: each word with its reading,
the tapped form and what was undone (labels say what each form means, e.g. "past (did …)"), and
numbered senses per dictionary. Words carry furigana over their kanji (`Furigana.segments`
splits the reading around the word's kana). Swiping down on the header, or on the results when
they are scrolled to the top, closes the panel. Definitions are drawn natively from Yomitan structured content
(`lookup/StructuredContent` reads them, `lookup/GlossLayout` lays them out as lines,
`ui/reader/GlossView` draws them): meanings with their labels as chips, example sentences with
furigana and the word marked, notes, word origins, cross-references and antonyms. A tapped
reference opens that word in the panel (back returns). Images, source attributions and
Jitendex's spelling tables are left out. The matched text is marked in the book with the CSS
Custom Highlight API.

`lookup/DictionaryLookup` deinflects every prefix of the tapped text, finds entries by written
form and reading in enabled dictionaries, drops entries whose word class does not fit, and ranks
the rest: longer match, fewer inflection steps, written-form match, frequency (best rank among
the enabled frequency dictionaries), dictionary priority, popularity. Each word carries its
frequencies and pitch accent (`WordMeta`); the panel draws the accent over the reading
(`lookup/PitchAccent`) and Anki cards get it as HTML.

## Project layout

```
app/src/main/java/app/hongo/reader/
  AppContainer.kt          dependency wiring
  data/                    Room database, entities, DAOs, repositories
  ui/                      navigation, theme and one package per screen
  lookup/                  deinflection and dictionary lookup
  ui/reader/               ttu WebView, asset serving, tap and library messages
  ui/library/              the library (home screen), adding books
  ui/opds/                 browsing book catalogues
  ui/words/                saved words
  ui/stats/                reading statistics
  ui/settings/             settings and the reader settings
  ui/onboarding/           first start
  anki/                    AnkiDroid client, cards, audio
app/src/main/assets/hongo/ scripts injected into the ttu WebView
third_party/               ッツ Ebook Reader submodule and the patches applied to it
web-tests/                 Playwright tests for the injected scripts and ttu end-to-end
scripts/check-apk.sh       16 KB alignment and permission checks
```

## Licence

HonGO! is free software under the [GNU General Public License v3.0](LICENSE) or (at your option)
any later version. It comes with no warranty.

It includes [ッツ Ebook Reader](https://github.com/ttu-ttu/ebook-reader) (BSD 3-Clause) and the
libraries listed in the app under Settings → About HonGO!. Dictionaries and fonts are downloaded
from their publishers and keep their own licences.

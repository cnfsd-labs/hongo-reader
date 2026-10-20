# HonGO! – notes for Claude

Android reader for Japanese EPUBs with a tap-to-look-up pop-up dictionary (Yomitan dictionaries,
conjugated forms resolved), saved words, Anki cards and reading statistics. Published by
CNFSD Labs under GPL-3.0-or-later; `README.md` is the architecture overview,
`docs/device-testing.md` the checklist for builds on a phone.

**Product principle:** a focused app that works without setup: install, get a dictionary with one
tap, open a book, tap words. Anything needing configuration must have defaults and stay optional.

**Maintainer's notes:** status, plans, devices and publishing are in the private repository
`cnfsd-labs/hongo-notes` (`working-notes.md` first). Read it when you have access. This
repository is public: never copy those notes, personal details or discussions into it.

## Decisions already made (don't relitigate without new facts)

- Fresh build in Kotlin + Compose, not a jidoujisho fork (outdated) and not a Readest fork
  (AGPL-3.0, very large).
- Copying code or data from other projects is allowed when its licence is
  compatible with GPL-3.0-or-later: GPL-3.0 or later (jidoujisho, Yomitan), LGPL-3.0, Apache-2.0,
  MPL-2.0, MIT, BSD, ISC, 0BSD, CC0. Not GPL-2.0-only, "non-commercial" or unlicensed code.
  AGPL-3.0 (Readest) can legally be combined (GPL-3.0 §13) but adds its terms to that part: ask
  the maintainer first. When copying: keep the original copyright and licence notice in the file, say
  where it came from (repository, path, commit), add the project to the credits on the About
  screen (`AboutScreen`, plus its licence text if new), and mention it in the commit message.
- ttu is a pinned submodule (`third_party/ttu-ebook-reader`), built by `:app:buildTtu` from a copy
  with `third_party/patches/ttu-ebook-reader/*.patch` applied. Keep patches minimal; prefer
  injected scripts in `app/src/main/assets/hongo/` (document-start, reader origin only).
- Reader origin `https://appassets.androidplatform.net` via `WebViewAssetLoader`. ttu's library
  lives in IndexedDB for this origin, so never change it. The WebView blocks network loads; INTERNET
  is only for downloads (dictionaries, fonts), book servers, progress sync and Google voices, all
  listed in `docs/privacy-policy.md`, which must change with any new network use.
- Licence: **GPL-3.0-or-later**, free on Play, copyright "© 2026 CNFSD Labs" (no personal names
  in the app, the docs or commits; commits are authored "CNFSD Labs"). `LICENSE` at the root;
  Settings → "About HonGO!" shows copyright, the no-warranty notice, the GPL text, the source
  link and the credits (`AboutScreen`; `buildTtu` lists the libraries in ttu's bundle from
  hidden source maps). The source link points to this repository (`AboutScreen.SOURCE_URL`).
- Backup (cloud and device transfer) keeps only the shared preferences file `settings` (the
  reader's choices). Books, fonts, ttu's data and the database are left out (orphans without the
  database, and over 25 MB Android skips the whole backup); what belongs to this phone (first
  start done, the Keystore-encrypted Google key, AnkiDroid's note type ids, the sync account) is
  in `device` preferences, never backed up. `BackupRulesTest` guards the rules.
- Play bundles: the "Release bundle" workflow (a pushed `release/*` tag, or by hand) signs
  `bundleRelease` with the upload key from repository secrets; its run number is the version
  code. Play App Signing holds the app signing key. Other builds stay unsigned or debug-signed.
- Application ID `app.hongo.reader` (permanent once on Play).
- Single Room database, **schema frozen since 2026-10-19** (version 1, exported in `app/schemas`).
  A schema change needs a new version, a migration in `HongoDatabase.MIGRATIONS`, a test in
  `DatabaseMigrationTest` and the new exported JSON committed; CI fails when `app/schemas`
  changes uncommitted. No destructive fallback: users' data survives updates. Manual DI in
  `AppContainer`, no Hilt.
- Glossaries are stored deflated with a per-dictionary 8 KB preset (`GlossaryPacker`; Jitendex
  466 → 59 MB, about 5 µs to unpack an entry); always on, users are not asked.
- Dictionaries: Yomitan formats 1–3 (term, term meta for frequency and pitch, kanji and tag banks;
  no images); imports serialized by a mutex in `DictionaryImporter`; WorkManager worker as
  dataSync foreground service.
- Lookup is tap-based (not selection), scan length 20, longest match first; deinflection rules are
  HonGO!'s own (`lookup/Deinflector.kt`).
- Version set: AGP 9.4 with its built-in Kotlin (no `org.jetbrains.kotlin.android` plugin),
  Gradle 9.8, Kotlin 2.4.21, KSP 2.3, compileSdk 37, targetSdk 36 (raise it deliberately: it
  changes behaviour on new Android versions). Dependabot proposes updates; Kotlin and KSP move
  together.
- Reader theme follows system dark mode (ttu `black-theme` / `light-theme`) until the user picks
  a theme (`hongoAutoTheme` in localStorage remembers what HonGO! set; "Like the app" in the
  reader settings sets it again).
- Reader settings: ttu's localStorage is the only store (no copy in shared preferences), so
  ttu's full settings page and HonGO!'s screen never disagree. The screen shows a small set; ttu's
  page stays reachable for the rest.
- Saved words: recorded automatically on every lookup with results (best match); ranked by a
  decayed score (half-life 14 days), not deleted when old; learned words stay learned when
  looked up again. "Learned" (not "Known"): it only covers words the reader looked up. Anki cards
  are suggested, not pushed by default.
- Anki: AnkiDroid's content provider through HonGO!'s own client (not the LGPL JitPack library);
  note type and deck "HonGO!"; words spoken from their kana reading; for Anki on a computer an
  `.apkg` with audio (`AnkiPackage`, schema 11, fixed note type id, guid from the word) or a
  text file that names deck and tags in columns (Anki ignores a `#deck:` header for a missing
  deck); the Google Cloud key is Keystore-encrypted and only sent to Google in the
  `X-Goog-Api-Key` header.
- OPDS: own Atom parser (DOM via `SafeXml`) and `HttpURLConnection` client, no library. Login
  only sent to the catalogue's origin (redirects followed manually); Basic first, Digest (RFC 7616)
  when a 401 asks for it, then remembered per origin for the session. No OPDS 2.0 (servers in use
  speak 1.x). Passwords AES-GCM-encrypted with an Android Keystore key (not androidx
  security-crypto, which is deprecated). Cleartext and user CAs allowed for home servers, with a
  warning in the login dialogs. Over plain http a login (OPDS and sync) only goes after the
  server answered 401, remembered per server and per Android network (`data/net/LoginGate`), so
  a device with the home server's address on another Wi-Fi never gets it unasked. Catalogues are opened from "Add book", not a separate tab.
- Progress sync: KOReader's sync protocol (`data/sync/`), optional, EPUB only, document id =
  KOReader's partial MD5 of the file. Positions are translated exactly between KOReader's
  XPointers and ttu's character count (`EpubPositions`, ttu's counting rules); the server's
  position is written into ttu's bookmark before the book opens. Account in `device` prefs.
  Calibre-Web Automated wants its web password as HTTP Basic (no MD5 key): `KoSyncClient.signIn`
  falls back to `<address>/kosync` and Basic (only there), and only then keeps the password.
  The book server dialog has a "Sync reading progress here" switch (`CatalogSync`): sync on the
  catalogue's origin with its login; nothing is saved if that sign-in fails.
  No account creation in HonGO! (Play wants deletion for accounts an app creates; the protocol
  has none): accounts come from KOReader or the server.
- Library: HonGO! owns the book files (app storage, named by SHA-256); ttu's IndexedDB is a
  rebuildable cache. Books reach ttu through ttu's own import input (`library-bridge.js`), not a
  ttu patch and not a native EPUB renderer.

## Layout

```
app/src/main/java/app/hongo/reader/
  AppContainer.kt            manual DI
  data/                      Room DB; dictionary/ (entities, DAOs, importer, worker, downloader,
                             catalog, update checker, Glossary + GlossaryPacker, yomitan/ reader)
  lookup/                    Deinflector, DictionaryLookup (+ frequency/pitch as WordMeta, KanjiInfo),
                             LookupGroups, StructuredContent + GlossLayout (definitions as
                             lines), GlossaryFormatter (plain senses for cards), PitchAccent
  diagnostics/               ReaderDiagnostics (console buffer, book snapshot script)
  data/library/              books: BookEntity/BookDao, BookImporter (hash, copy), EpubMetadata,
                             TextEncoding, AozoraText + AozoraEpub (Aozora .txt → EPUB)
  data/words/                saved words: WordEntity/WordLookupEntity, WordDao, WordRepository
                             (record + dedupe), WordScore (decayed ranking as a rank key)
  data/opds/                 OPDS: OpdsParser (feeds, OpenSearch), OpdsClient (HTTP, Basic and
                             Digest auth),
                             OpdsRepository + catalogue table, Keystore CredentialCipher,
                             OpdsAutoImport (new Japanese books on app start)
  data/settings/             AppearanceSettings (accent colour), OnboardingSettings (shared
                             preferences), ReaderSettings (model over ttu's localStorage keys)
  data/net/                  LoginGate (logins over plain http wait for the server to ask)
  data/sync/                 KOReader progress sync: KoSyncClient, KoSyncSettings, PartialMd5,
                             EpubPositions (XPointer <-> ttu characters), Xhtml, ProgressSync,
                             CatalogSync (sync with a book server's login)
  data/stats/                reading statistics: ReadingDayEntity/ReadingDao, ReadingTracker
                             (time + characters), ReadingStats (summaries)
  anki/                      AnkiCard (note type, fields), AnkiDroid (content provider client),
                             AnkiPackage (.apkg), AnkiTextFile,
                             AnkiExporter, AnkiSettings, CardVoice (phone TTS), GoogleCloudVoice
  ui/anki/                   AnkiAccess (permission, install check, result messages)
  ui/library/                LibraryScreen (home, drawn over the reader; toolbar with search and
                             status filters, LibraryFilter) + ViewModel, AddBookSheet,
                             BookAdder (import + hand to ttu, shared with OPDS downloads)
  ui/opds/                   CatalogScreens (CatalogBrowser, CatalogDialog), OpdsViewModel
  ui/words/                  WordsScreen + ViewModel
  ui/settings/               SettingsScreen (accent colour, diagnostics), AboutScreen (licences),
                             AnkiSettingsSection, ProgressSyncSection, ReaderSettingsScreen
                             (HonGO!'s reader settings over ttu's localStorage) + ViewModel
  ui/HomeNavigationBar.kt    the bottom bar; ReaderScreen hosts the tabs over the WebView
  ui/reader/                 ReaderScreen, ReaderWebView, LookupPanel, GlossView, TapLookup, TtuLibrary
                             (library-bridge commands), asset + book file serving
  ui/dictionaries/           Dictionaries screen + ViewModel
  ui/stats/                  StatisticsScreen (+ BookStatisticsSheet) + ViewModel
  ui/onboarding/             OnboardingScreen (first start: starter dictionaries, sample book)
app/src/main/assets/hongo/   tap-bridge.js, ttu-defaults.js, keep-position.js, library-bridge.js,
                             viewport-log.js, help-text.js, book-end.js, book-shown.js,
                             reading-activity.js, reader-settings.js (injected)
app/src/test/resources/      deinflection-corpus.tsv
third_party/                 ttu submodule + patches (see patches/README.md)
web-tests/                   Playwright: tap bridge on fixtures, ttu end-to-end against the built ttu
scripts/check-apk.sh         APK gate: 16 KB alignment, ttu files present, no restricted permissions
docs/                        device-testing.md, privacy-policy.md, samples/, branding/
```

## Build, test, CI

- Local prerequisites: JDK 21, Android SDK 37, Node 24 + pnpm 12, `git submodule update --init`.
- Local builds are signed with the local debug key: uninstall a CI build before installing one.
  Machine-specific setup (JDK paths, devices) is in the maintainer's notes.
- `./gradlew lintDebug testDebugUnitTest assembleRelease assembleInternal assembleDebug`
- `scripts/check-apk.sh <apk>`
- `cd web-tests && npm ci && npx playwright install --with-deps chromium && npx playwright test`
  (needs the ttu build from `:app:buildTtu`, or `TTU_DIR=...`).
- CI (`.github/workflows/android.yml`) runs all of this on every push, with
  `cancel-in-progress`: pushing cancels the running build. Don't push while someone waits for an
  APK from a running build.
- **Test APKs:** artifact `hongo-test-apks` of a green run → `app-internal.apk` (release code,
  debug-signed, "HonGO! internal"). Every run has a new signing key, so the old build must be
  uninstalled first (this deletes imported dictionaries). Give the direct artifact link
  (`…/actions/runs/<run>/artifacts/<id>`).

## Cloud-sandbox notes (Claude Code on the web)

- `dl.google.com` / Google Maven is blocked, so the Android build cannot run in the sandbox; CI
  is the only Android compiler. Maven Central sometimes rate-limits (429).
- What can be checked locally: pure-Kotlin classes and their tests in a throwaway JVM Gradle project
  (copy the files; stub Room entities by stripping annotations); ttu build and Playwright tests with
  Node 24 from nodejs.org (`~/node24`) and the preinstalled Chromium (Playwright 1.56.1). The
  default Linux CJK fallback font has broken vertical metrics, so tests use `IPAGothic`.
- Newer Chromium for checks: `npm i playwright@<latest>` + `npx playwright install chromium` works.

## Lessons learned (each cost a CI or device round trip)

- When changing an interface, update **every** implementation, including test fakes
  (`grep -rn ": InterfaceName"`). Room DAOs and `DictionaryImportScheduler` have fakes in tests.
- Unit tests compile against `android.jar`: JDK-only APIs such as `com.sun.net.httpserver` are
  unavailable. `DictionaryDownloaderTest` has a small `ServerSocket` HTTP server instead.
- JUnit rejects test methods with a return value (`initializationError`): a `= runBlocking { … }`
  test must end in a statement such as an assert, not an expression.
- Write non-ASCII control characters (BOM) as escapes: lint fails on a literal BOM in source.
- Android's regex engine (ICU) is stricter than the JVM's, where unit tests run: an unescaped `}`
  passed every test and closed the app at startup (a companion-object `Regex`). Escape `{` and
  `}` always, and parse bridge messages as JSON rather than with regular expressions.
- Assert the part of a UI state a test is about, not the whole state object: new fields with
  non-empty values break whole-state comparisons.
- Room in-memory test DBs need `allowMainThreadQueries()` for raw queries in tests.
- Room 2.8's `MigrationTestHelper` fails on Windows (it compares the database path with `/`
  separators); `DatabaseMigrationTest` passes on Linux CI.
- AAPT's default `ignoreAssetsPattern` drops directories starting with `_` (ttu's `_app/`); it is
  overridden in `app/build.gradle.kts`, and `check-apk.sh` guards it.
- `git apply` inside this repo silently skips paths for a work dir under it; `buildTtu` sets
  `GIT_CEILING_DIRECTORIES`.
- Kotlin block comments nest: never write `/*` inside KDoc (e.g. `patches/*.patch`).
- Android WebView differs from desktop Chromium: in vertical mode an orthogonal-flow container
  without an explicit height got a 0 line length (blank pages). Reproduce device issues in
  Chromium by forcing the reported condition, verify the fix, and add a regression test. Don't
  infer line counts from `Range.getClientRects()`: those are inline boxes.
- Chromium's back skips history entries a page added without a tap inside it. Books are opened
  from the native library (script click, no tap in the page), so `WebView.goBackOrForward` did
  nothing for the first book after start; history steps go through `history.go` in the page.
- ttu cancels clicks on `<ruby>` (`stopImmediatePropagation`), so the tap bridge listens in the
  capture phase.
- Test reader behaviour with realistic books: synthetic test books with short paragraphs hid
  ttu's paragraph-level position restore. Use mobile emulation (`isMobile`, DPR 4) and paragraphs
  longer than a screen.
- WebView methods must run on the main thread, or Android closes the app. `TtuLibrary` commands
  drive the WebView, so never call `BookAdder.add` / `TtuLibrary` inside `withContext(IO)`
  (the OPDS download crash); the reader page now posts to the main thread as a safety net.
- ttu (with patch 0002) reads TXT as UTF-8 or Shift_JIS only, and drops Aozora furigana.
  `TextEncoding` converts every text file to UTF-8 on import (UTF-16, EUC-JP too), and Aozora
  Bunko texts become EPUBs (`AozoraText` + `AozoraEpub`) with ruby, headings as chapters and
  emphasis dots; the content hash stays the original file's.
- ttu's Popper popovers land off screen in the Android WebView for icons further down a page
  (fine in Chromium, not reproduced there). `help-text.js` pins text popovers to the bottom of the
  screen instead; ask the tester where on the screen a WebView bug happens.
- Drag offsets: update a plain state synchronously in the drag callback. Launching a coroutine
  per move let late moves cancel the settle animation (the panel stayed half open).
- A coroutine continuation resumed from callbacks on other threads (text-to-speech, providers)
  must be resumed at most once (an `AtomicBoolean`), or the app closes with "Already resumed".
  Catch exceptions from another app's content provider; they must never close HonGO!.
- Android 13+ shows no notifications, not even a foreground service's, without the runtime
  `POST_NOTIFICATIONS` permission; an Android 12 phone shows them anyway, so a notification
  seen there proves nothing for newer phones. Ask when the work starts (`rememberWithNotifications`).
- ttu declares some fonts itself (`reservedFontNames`: Klee One, Shippori Mincho, Noto …) with
  files the app doesn't bundle. A user font of the same family never counts as loaded
  (`document.fonts.check` needs every face of the family), so books waited for ttu's 30 s font
  timeout. Downloaded fonts use their own family ("HonGO Klee One"), and ttu's face is only
  loaded once something requests it: `reader-settings.js` does, as ttu hides the book until then.
## Conventions

- Commit messages: imperative subject; body explains why; end with the session attribution
  lines from the system prompt. No model names in commits or code.
- Tests accompany behaviour changes (JVM unit tests, Robolectric for Room, Playwright for injected
  JS / ttu).
- Strings in `res/values/strings.xml`; user-facing errors are readable sentences.
- Don't create a PR unless asked.
- Explain changes in plain terms; device testing is the real check. Ask for "Share diagnostics"
  output when the reader misbehaves.

# Device testing

## Getting the APK

1. Open the latest green run of the **Android** workflow on GitHub (repository → Actions →
   Android → newest run with a green check).
2. Under **Artifacts**, download **honmani-test-apks**. It is a zip with two APKs:
   - `app-internal.apk` ("Honmani internal"): release code, use this for the checks below and
     for timing.
   - `app-debug.apk` ("Honmani debug"): debuggable; the reader WebView can be inspected from
     `chrome://inspect` on a computer.
3. Copy the APK to the phone and open it; allow installing from that app when Android asks.

Each CI run signs with a different throwaway key: uninstall the previous build before
installing a newer one (this also clears its data, including imported dictionaries).

Useful for timing: with the phone connected over USB,
`adb logcat -s Honmani.lookup` prints how long each lookup takes.

**If something in the reader looks wrong** (e.g. a blank page): open the book, wait 5 seconds,
go back to the library, then Settings → **Share diagnostics** and send the text (to
cnfsd.labs@gmail.com, or attach it to a GitHub issue). It contains the
device and WebView versions, the reader's console messages and how the last book was laid out
(settings, fonts, positions, the first 40 characters). Nothing is sent automatically.

## Test files

- **Sample book:** [`docs/samples/wagahai-neko-1.epub`](samples/wagahai-neko-1.epub), chapter 1 of
  吾輩は猫である with furigana, converted from Aozora Bunko (also built into the app: the
  first-start screen offers it). It is known to be well-formed, so if it shows no text the
  problem is in the app, not the file.
- Any other DRM-free Japanese EPUB, or an Aozora Bunko `.txt` file (from a work's page on
  aozora.gr.jp, the zip under "テキストファイル(ルビあり)"; unzip it first).
- JMdict for Yomitan: `JMdict_english.zip` from
  https://github.com/yomidevs/jmdict-yomitan/releases/latest
- Jitendex for Yomitan: `jitendex-yomitan.zip` from https://jitendex.org

## Before a release

A quick pass over what users hit first, on a fresh install of the release build (10 minutes):
first start with the sample book; tap a few words (conjugated forms, furigana); add your own EPUB
and an Aozora `.txt`; vertical and horizontal mode; rotate; back to the library; dark mode;
add a card to AnkiDroid; Words → export (arrow up) → "Anki package" and import the .apkg in Anki on a
computer (cards with audio in the chosen deck); force-stop and reopen (books, dictionaries,
position kept).

## Reading progress sync (KOReader)

With KOReader on an e-reader (or Calibre-Web Automated, whose own KOReader plugin "CWA Progress
Sync" the e-reader needs; in Honmani its plain address and Calibre-Web login are enough) signed in
to the same sync server:
put the same EPUB file on both. In Honmani: Settings → Reading progress sync, sign in, read a few
pages, go back to the library. In KOReader, open the book: it offers Honmani's position (top of
the page Honmani showed). Read on in KOReader, then open the book in Honmani: it says "Continued
at …% from …" and shows the page KOReader was on. Books converted from Aozora text files only
match if both devices have the same EPUB.

## Known limitations (no need to report)

- Diagnostics show "Failed to register a ServiceWorker": ttu tries to install its offline cache,
  which Honmani doesn't need (the reader is in the app). Harmless.
- ttu's Google Drive / OneDrive sync and backup downloads do not work.
- Dictionary images (pictures in some entries) are not shown.
- Choosing one of ttu's web fonts in its settings brings back a 10-second wait when opening
  books (those fonts are not bundled).

# Privacy policy for HonGO!

*Last updated: 2026-10-20.*

HonGO! is a free reader for Japanese books, published by CNFSD Labs. It has no account, no ads,
no analytics and no tracking. CNFSD Labs does not receive any data from the app.

## What stays on your phone

Your books, imported dictionaries, saved words, reading statistics and settings are stored only
on your phone, in HonGO!'s own app storage. Uninstalling the app deletes them.

If you have turned on Android's backup to your Google account, Android includes only HonGO!'s
settings (such as the accent colour and the Anki deck) in that backup. Books, dictionaries,
saved words, statistics and passwords are never part of it.

## When HonGO! connects to the internet

HonGO! only connects to the internet for these things, and only to these places:

- **Dictionaries and reader fonts** you choose to download, and a check for dictionary
  updates when the app starts: GitHub (github.com, raw.githubusercontent.com), Hugging Face
  (huggingface.co) and Jiten (api.jiten.moe). These sites see an ordinary download request
  (your IP address and the app's name); HonGO! sends no personal data. Their own privacy
  policies apply.
- **Book servers you add yourself** (OPDS catalogues such as Calibre-Web). HonGO! connects only
  to the addresses you enter. If you give a user name and password, the password is stored
  encrypted on your phone and sent only to that server.
  At an unencrypted address (`http://`) anyone on the same network can read a password that
  is sent, and on another Wi-Fi the address can belong to someone else's device. There HonGO!
  sends the login only after the server has asked for one, and asks again on every new
  network; a device that asks on purpose still gets it, so use `https://` or a VPN away from
  home. The same applies to reading progress sync.
- **Reading progress sync, only if you sign in** (Settings → Reading progress sync) to a
  KOReader sync server of your choice: for EPUB books, HonGO! sends that server an identifier
  computed from the book file (not its title or text), your reading position and percentage,
  the name of your phone model and a random id HonGO! made for this phone (so the server can
  tell your devices apart), and reads back the position saved by your other devices. You can
  also turn this on from a book server's settings, with that server's address and login.
  For KOReader's sync servers HonGO! keeps only an MD5 hash of your password, encrypted on
  your phone, as KOReader does. Calibre-Web Automated checks your Calibre-Web password
  itself: for it, the password is stored encrypted on your phone and sent only to that server.
  Signing out deletes the stored login.
  The server's operator's privacy policy applies.
- **Google Cloud text-to-speech, only if you turn it on** and enter your own Google Cloud API
  key: to make audio for Anki cards, HonGO! sends the word or example sentence and your key to
  Google (texttospeech.googleapis.com). Google's privacy policy applies. The default voice is
  your phone's own, which works offline.

The book reader itself never loads anything from the internet.

## Other apps

- **AnkiDroid:** if you add words to Anki, HonGO! hands them to the AnkiDroid app on your phone,
  after you allow it. Nothing leaves your phone for this.
- **Files for Anki on a computer:** saving your words as an Anki package or text file writes it
  where you choose in Android's save dialog. HonGO! sends it nowhere.
- **Share diagnostics:** only when you tap it in Settings, HonGO! prepares a short technical
  report (app and Android version, the reader's log, layout details of the last opened book,
  which can include short excerpts of its text) and opens Android's share menu. You choose who
  receives it, if anyone.

## Children

HonGO! is not directed at children under 13.

## Changes

If this policy changes, the new version will be published here with a new date.

## Contact

CNFSD Labs – cnfsd.labs@gmail.com

Source code: https://github.com/cnfsd-labs/hongo-reader (GPL-3.0-or-later)

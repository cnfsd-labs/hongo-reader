package app.hongo.reader.anki

import app.hongo.reader.data.words.WordEntity
import app.hongo.reader.data.words.WordLookupEntity
import app.hongo.reader.data.words.WordRepository
import java.io.File
import java.security.MessageDigest
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** The outcome of adding a word to Anki. */
sealed interface AnkiResult {
    /** Added; [audioProblem] is set if the card went in without audio. */
    data class Added(val noteId: Long, val audioProblem: VoiceProblem? = null) : AnkiResult

    data object AlreadyInAnki : AnkiResult

    data object NotInstalled : AnkiResult

    data object NoPermission : AnkiResult

    /** AnkiDroid didn't take it, for example because it was never opened (no collection yet). */
    data object Refused : AnkiResult

    /** Something unexpected went wrong; [reason] is for the message (and a bug report). */
    data class Failed(val reason: String) : AnkiResult
}

/**
 * Adds saved words to AnkiDroid as HonGO! cards ([AnkiCard]): sets up the note type and the
 * deck chosen in [AnkiSettings], skips words already there, and attaches audio from the chosen
 * voice. Also writes words as a text file for Anki's import ([textFile]).
 */
class AnkiExporter(
    private val anki: AnkiApi,
    private val settings: AnkiSettings,
    private val words: WordRepository,
    private val phoneVoice: () -> CardVoice,
    /** Where audio waits until AnkiDroid has copied it (shared through the file provider). */
    private val audioDir: File,
    /** What the installed dictionaries say about a word (senses from the best dictionary). */
    private val details: suspend (expression: String, reading: String) -> WordDetails,
) {
    private val mutex = Mutex()

    /** Adds [word] with the sentence of [lookup], or of its latest lookup if null. */
    suspend fun add(word: WordEntity, lookup: WordLookupEntity? = null): AnkiResult = add(content(word, lookup))

    /** [words] as a text file for Anki's import ([AnkiTextFile]), for the chosen deck. */
    suspend fun textFile(words: List<WordEntity>): String {
        val cards = words.map { content(it, null) }
        return AnkiTextFile.text(cards, settings.deck.value)
    }

    /** The card for [word] with the sentence of [lookup] (null: the latest). */
    private suspend fun content(word: WordEntity, lookup: WordLookupEntity?): CardContent {
        val sentence = lookup?.sentence ?: word.lastSentence
        val start = lookup?.surfaceStart ?: word.lastSurfaceStart
        val end = lookup?.surfaceEnd ?: word.lastSurfaceEnd
        val book = if (lookup != null) lookup.bookTitle else word.lastBookTitle
        val found = try {
            details(word.expression, word.reading)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            WordDetails()
        }
        return CardContent(
            expression = word.expression,
            reading = word.reading,
            senses = found.senses.ifEmpty { listOf(listOf(word.gloss)) },
            sentence = sentence,
            surfaceStart = start,
            surfaceEnd = end,
            bookTitle = book,
            pitches = found.pitches,
            frequency = found.frequency,
        )
    }

    /** Adds a card for [content] and marks the saved word, if there is one, as in Anki. */
    suspend fun add(content: CardContent): AnkiResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                addLocked(content)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // AnkiDroid's provider throws for problems on its side; never let that close the app.
                AnkiResult.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private suspend fun addLocked(content: CardContent): AnkiResult {
        if (anki.packageName() == null) return AnkiResult.NotInstalled
        if (!anki.hasPermission()) return AnkiResult.NoPermission
        val noteType = noteType() ?: return AnkiResult.Refused
        val deck = anki.deck(settings.deck.value) ?: return AnkiResult.Refused
        val plain = AnkiCard.fields(content)
        anki.findNote(noteType, plain[0], plain[1])?.let { existing ->
            markInAnki(content, existing)
            return AnkiResult.AlreadyInAnki
        }

        var audioProblem: VoiceProblem? = null
        val made = mutableListOf<File>()
        try {
            val voice = settings.voice(phoneVoice)
            var wordAudio = ""
            var sentenceAudio = ""
            if (voice != null) {
                try {
                    // The reading, not the written form: a voice can misread kanji, never kana.
                    wordAudio = sound(voice, content.reading.ifEmpty { content.expression }, made)
                    if (content.sentence.isNotBlank()) {
                        sentenceAudio = sound(voice, content.sentence, made, settings.sentenceSpeed.value.rate)
                    }
                } catch (e: VoiceException) {
                    audioProblem = e.problem
                }
            }
            val fields = AnkiCard.fields(content, wordAudio, sentenceAudio)
            val noteId = anki.addNote(noteType, deck, fields, setOf(AnkiCard.TAG)) ?: return AnkiResult.Refused
            markInAnki(content, noteId)
            return AnkiResult.Added(noteId, audioProblem)
        } finally {
            made.forEach { it.delete() }
        }
    }

    /** Speaks [text], hands the file to AnkiDroid and returns the field's `[sound:…]`. */
    private suspend fun sound(voice: CardVoice, text: String, made: MutableList<File>, rate: Double = 1.0): String {
        // The rate is part of the name: AnkiDroid keeps the first file of a name.
        val name = "hongo_" + shortHash(text + "\u0000" + voice.javaClass.simpleName + "\u0000" + rate)
        val file = File(audioDir, "$name.${voice.extension}")
        made += file
        voice.speak(text, file, rate)
        return anki.addSound(file, name) ?: throw VoiceException(VoiceProblem.FAILED)
    }

    private fun noteType(): Long? {
        val id = settings.noteTypeId?.takeIf { anki.noteTypeExists(it) }
            ?: anki.findNoteType(AnkiCard.NOTE_TYPE, AnkiCard.FIELDS)
            ?: anki.addNoteType(
                name = AnkiCard.NOTE_TYPE,
                fields = AnkiCard.FIELDS,
                cardName = AnkiCard.CARD_NAME,
                front = AnkiCard.FRONT,
                back = AnkiCard.BACK,
                css = AnkiCard.CSS,
            )
            ?: return null
        settings.noteTypeId = id
        checkCard(id)
        return id
    }

    /**
     * Once per note type: if its card doesn't show the sentence (AnkiDroid's placeholder card,
     * left behind when setting the card failed when the note type was made), sets HonGO!'s card.
     * Later edits in Anki are left alone.
     */
    private fun checkCard(noteType: Long) {
        if (settings.checkedNoteTypeId == noteType) return
        val front = anki.cardFront(noteType)
        val fine = front != null && "{{Sentence}}" in front
        if (fine || anki.setCard(noteType, AnkiCard.CARD_NAME, AnkiCard.FRONT, AnkiCard.BACK)) {
            settings.checkedNoteTypeId = noteType
        }
    }

    private suspend fun markInAnki(content: CardContent, noteId: Long) {
        words.find(content.expression, content.reading)?.let { words.setAnkiNote(it.id, noteId) }
    }

    private fun shortHash(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray(Charsets.UTF_8))
            .take(8)
            .joinToString("") { "%02x".format(it) }
}

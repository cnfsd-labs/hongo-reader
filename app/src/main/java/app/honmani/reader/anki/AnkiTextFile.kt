package app.honmani.reader.anki

/**
 * Cards as a text file for Anki's File → Import (Anki 2.1.54 and later read the header lines),
 * for readers without AnkiDroid, such as Anki on a computer. The columns are the fields of
 * Honmani's note type ([AnkiCard.FIELDS]) without the audio, which only AnkiDroid can store;
 * the note type "Honmani" is preset if it exists (it does once AnkiDroid's cards are synced),
 * otherwise the reader maps the named columns to a note type of their own.
 *
 * Deck and tags are columns of their own, not `#deck:` and `#tags:` headers: Anki 25.02 ignores
 * a `#deck:` header naming a deck that doesn't exist yet (the cards went to "Default") but
 * creates the deck from a deck column, and without a tags column it takes a field column as the
 * tags when it falls back to another note type.
 */
object AnkiTextFile {
    /** The fields that go into the file, by name. */
    val COLUMNS = AnkiCard.FIELDS - setOf("WordAudio", "SentenceAudio")

    /** Labels of the two columns after the fields. */
    const val DECK_COLUMN = "Deck"
    const val TAGS_COLUMN = "Tags"

    fun text(cards: List<CardContent>, deck: String): String = buildString {
        append("#separator:tab\n")
        append("#html:true\n")
        append("#notetype:").append(AnkiCard.NOTE_TYPE).append('\n')
        append("#deck column:").append(COLUMNS.size + 1).append('\n')
        append("#tags column:").append(COLUMNS.size + 2).append('\n')
        append("#columns:").append((COLUMNS + DECK_COLUMN + TAGS_COLUMN).joinToString("\t")).append('\n')
        for (card in cards) {
            val byName = AnkiCard.FIELDS.zip(AnkiCard.fields(card)).toMap()
            (COLUMNS.map(byName::getValue) + deck + AnkiCard.TAG).joinTo(this, "\t") { quoted(it) }
            append('\n')
        }
    }

    /**
     * Every field in double quotes (inner quotes doubled), as Anki's importer reads CSV; the
     * fields are HTML, so line breaks become `<br>` and tabs spaces.
     */
    private fun quoted(field: String): String =
        "\"" + field.replace("\r\n", "\n").replace("\n", "<br>").replace("\t", " ").replace("\"", "\"\"") + "\""
}

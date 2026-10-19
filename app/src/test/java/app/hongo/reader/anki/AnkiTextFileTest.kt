package app.hongo.reader.anki

import org.junit.Assert.assertEquals
import org.junit.Test

class AnkiTextFileTest {
    private val card = CardContent(
        expression = "猫",
        reading = "ねこ",
        senses = listOf(listOf("cat")),
        sentence = "猫が\t鳴いた",
        surfaceStart = 0,
        surfaceEnd = 1,
        bookTitle = "Line one\nline two",
        pitches = listOf(1),
    )

    @Test
    fun headersThenOneQuotedLinePerCard() {
        val lines = AnkiTextFile.text(listOf(card, card), deck = "Deck\tname").lines()

        // Deck and tags as columns (9 and 10, counted from 1): Anki creates a missing deck from a
        // deck column but ignores a "#deck:" header naming one.
        assertEquals(
            listOf("#separator:tab", "#html:true", "#notetype:HonGO!", "#deck column:9", "#tags column:10"),
            lines.take(5),
        )
        assertEquals(
            AnkiTextFile.COLUMNS + listOf("Deck", "Tags"),
            lines[5].removePrefix("#columns:").split('\t'),
        )
        assertEquals(lines[6], lines[7])
        assertEquals("", lines[8])

        val fields = lines[6].split('\t')
        assertEquals(AnkiTextFile.COLUMNS.size + 2, fields.size)
        assertEquals(listOf("\"Deck name\"", "\"hongo\""), fields.takeLast(2))
        // Tabs and line breaks inside a field can't end it; quotes in the pitch HTML are doubled.
        assertEquals("\"<b>猫</b>が 鳴いた\"", fields[AnkiTextFile.COLUMNS.indexOf("Sentence")])
        assertEquals("\"Line one<br>line two\"", fields[AnkiTextFile.COLUMNS.indexOf("Book")])
        val pitch = fields[AnkiTextFile.COLUMNS.indexOf("Pitch")]
        assertEquals(true, pitch.contains("class=\"\""))
    }
}

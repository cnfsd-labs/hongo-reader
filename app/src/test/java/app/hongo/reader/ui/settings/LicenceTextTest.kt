package app.hongo.reader.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class LicenceTextTest {
    @Test
    fun hardLineBreaksAreJoined_paragraphsKept() {
        val text = "                    GNU GENERAL PUBLIC LICENSE\r\n" +
            "                       Version 3, 29 June 2007\r\n" +
            "\r\n" +
            " Everyone is permitted to copy and distribute verbatim copies\n" +
            " of this license document.\n" +
            "  \n" +
            "\n" +
            "svelte 4.2.20 (MIT)\n"

        assertEquals(
            listOf(
                "GNU GENERAL PUBLIC LICENSE Version 3, 29 June 2007",
                "Everyone is permitted to copy and distribute verbatim copies of this license document.",
                "svelte 4.2.20 (MIT)",
            ),
            LicenceText.paragraphs(text),
        )
    }
}

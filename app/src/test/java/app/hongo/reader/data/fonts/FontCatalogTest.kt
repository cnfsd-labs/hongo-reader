package app.hongo.reader.data.fonts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FontCatalogTest {
    @Test
    fun familiesDifferFromTtusOwnFonts() {
        // ttu declares these itself with files the app doesn't bundle: a catalog font of the same
        // family would never count as loaded, and books would wait for ttu's 30 s timeout.
        val reserved = FontCatalog.TTU_FONT_FAMILIES.map { it.lowercase() }.toSet()
        for (font in FontCatalog.fonts) assertTrue(font.family, font.family.lowercase() !in reserved)
    }

    @Test
    fun entriesAreDistinctPinnedHttpsFilesWithChecksums() {
        val fonts = FontCatalog.fonts
        assertEquals(fonts.size, fonts.map { it.id }.toSet().size)
        assertEquals(fonts.size, fonts.map { it.family }.toSet().size)
        assertEquals(fonts.size, fonts.map { it.fileName }.toSet().size)
        for (font in fonts) {
            assertTrue(font.url, font.url.startsWith("https://raw.githubusercontent.com/google/fonts/"))
            assertTrue(font.url, font.url.endsWith("/" + font.fileName))
            assertTrue(font.sha256, Regex("[0-9a-f]{64}").matches(font.sha256))
            // Served only under plain names (see FontPathHandler and reader-settings.js).
            assertTrue(font.fileName, Regex("[A-Za-z0-9-]+\\.ttf").matches(font.fileName))
        }
    }
}

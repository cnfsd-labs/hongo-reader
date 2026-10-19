package app.hongo.reader.data.sync

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PartialMd5Test {
    @get:Rule
    val folder = TemporaryFolder()

    /** Expected values from KOReader's algorithm (samples at 0, 1 KB, 4 KB, 16 KB, 64 KB …), computed outside HonGO!. */
    @Test
    fun matchesKoreader() {
        val large = folder.newFile("a.epub").apply { writeBytes(ByteArray(70_000) { ((it * 31 + 7) % 256).toByte() }) }
        assertEquals("7c495b34b24f561520622156e96f834d", PartialMd5.of(large))

        val small = folder.newFile("b.epub").apply { writeBytes(ByteArray(512) { it.toByte() }) }
        assertEquals("f5c8e3c31c044bae0e65569560b54332", PartialMd5.of(small))
    }
}

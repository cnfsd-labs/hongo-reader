package app.honmani.reader.ui.onboarding

import app.honmani.reader.data.dictionary.DictionaryCatalog
import app.honmani.reader.data.dictionary.ImportError
import app.honmani.reader.data.dictionary.ImportStatus
import app.honmani.reader.ui.dictionaries.CatalogItem
import app.honmani.reader.ui.dictionaries.DictionariesUiState
import org.junit.Assert.assertEquals
import org.junit.Test

class StarterPackStateTest {
    private val pack = DictionaryCatalog.starterPack(japanese = false)

    private fun state(installed: Int, imports: List<ImportStatus> = emptyList()) = DictionariesUiState.Loaded(
        dictionaries = emptyList(),
        imports = imports,
        catalog = DictionaryCatalog.dictionaries.map { dictionary ->
            val index = pack.indexOf(dictionary)
            val itemState = if (index in 0 until installed) CatalogItem.State.INSTALLED else CatalogItem.State.AVAILABLE
            CatalogItem(dictionary, itemState)
        },
    )

    @Test
    fun followsTheDownloads() {
        assertEquals(StarterPackState.NotStarted, StarterPackState.of(pack, DictionariesUiState.Loading, started = false))
        assertEquals(StarterPackState.NotStarted, StarterPackState.of(pack, state(0), started = false))
        assertEquals(StarterPackState.Downloading(1, 4), StarterPackState.of(pack, state(1), started = true))
        assertEquals(StarterPackState.Ready, StarterPackState.of(pack, state(4), started = true))
        // Installed before (from the Dictionaries tab, say): ready without a download.
        assertEquals(StarterPackState.Ready, StarterPackState.of(pack, state(4), started = false))
        val failed = ImportStatus.Failed("x", ImportError.entries.first(), null)
        assertEquals(StarterPackState.Failed, StarterPackState.of(pack, state(2, listOf(failed)), started = true))
    }
}

package app.honmani.reader.data.dictionary

import kotlinx.coroutines.flow.Flow

class DictionaryRepository(private val dao: DictionaryDao) {
    /** Installed dictionaries; imports in progress are not included. */
    val dictionaries: Flow<List<DictionaryEntity>> = dao.observeReady()

    suspend fun setEnabled(id: Long, enabled: Boolean) = dao.setEnabled(id, enabled)

    suspend fun delete(id: Long) = dao.delete(id)

    /**
     * Moves dictionary [id] one place up ([by] -1) or down (+1) in the order lookups show
     * dictionaries in; all places are written again as 0, 1, 2, …
     */
    suspend fun move(id: Long, by: Int) {
        val ordered = dao.allReady().sortedWith(compareBy({ it.priority }, { it.title })).toMutableList()
        val from = ordered.indexOfFirst { it.id == id }
        val to = from + by
        if (from < 0 || to !in ordered.indices) return
        ordered.add(to, ordered.removeAt(from))
        ordered.forEachIndexed { place, dictionary ->
            if (dictionary.priority != place) dao.setPriority(dictionary.id, place)
        }
    }
}

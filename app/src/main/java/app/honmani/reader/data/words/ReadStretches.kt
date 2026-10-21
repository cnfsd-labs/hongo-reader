package app.honmani.reader.data.words

import app.honmani.reader.data.difficulty.BookWords
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject

/**
 * The parts of a book really read: pages on screen long enough (`ReadingTracker`), in
 * [BookWords.POSITIONS] steps, as ascending pairs `from, to` (both included) that neither overlap
 * nor touch. Pages flicked past, jumps through the table of contents and positions from progress
 * sync add nothing.
 */
object ReadStretches {
    val NONE = IntArray(0)

    /** [stretches] with [from]..[to] (0 to 1) added. */
    fun add(stretches: IntArray, from: Float, to: Float): IntArray {
        val start = step(minOf(from, to))
        val end = step(maxOf(from, to))
        val merged = ArrayList<Int>(stretches.size + 2)
        var newStart = start
        var newEnd = end
        var placed = false
        for (i in stretches.indices step 2) {
            val a = stretches[i]
            val b = stretches[i + 1]
            when {
                b < newStart - 1 -> merged += listOf(a, b)
                a > newEnd + 1 -> {
                    if (!placed) {
                        merged += listOf(newStart, newEnd)
                        placed = true
                    }
                    merged += listOf(a, b)
                }
                else -> {
                    newStart = minOf(newStart, a)
                    newEnd = maxOf(newEnd, b)
                }
            }
        }
        if (!placed) merged += listOf(newStart, newEnd)
        return merged.toIntArray()
    }

    /** The share of the book read, 0 to 1. */
    fun share(stretches: IntArray): Float {
        var steps = 0
        for (i in stretches.indices step 2) steps += stretches[i + 1] - stretches[i]
        return (steps.toFloat() / BookWords.POSITIONS).coerceIn(0f, 1f)
    }

    private fun step(share: Float): Int = (share.coerceIn(0f, 1f) * BookWords.POSITIONS).roundToInt()
}

/** Keeps the [ReadStretches] of each book by content hash, in app storage. */
class ReadStretchStore(private val file: File) {
    private val lock = Any()
    private var loaded: Map<String, IntArray>? = null
    private val changes = MutableStateFlow(0)

    /** Every book's stretches, again after each change. */
    val stretches: Flow<Map<String, IntArray>> = changes.map { synchronized(lock) { current() } }

    fun add(contentHash: String, from: Float, to: Float) {
        synchronized(lock) {
            val updated = current().toMutableMap()
            updated[contentHash] = ReadStretches.add(updated[contentHash] ?: ReadStretches.NONE, from, to)
            loaded = updated
            save(updated)
        }
        changes.update { it + 1 }
    }

    /** Under [lock]. */
    private fun current(): Map<String, IntArray> = loaded ?: load().also { loaded = it }

    private fun load(): Map<String, IntArray> = runCatching {
        val json = JSONObject(file.readText())
        json.keys().asSequence().associateWith { hash ->
            val values = json.getJSONArray(hash)
            IntArray(values.length()) { values.getInt(it) }
        }
    }.getOrDefault(emptyMap())

    private fun save(stretches: Map<String, IntArray>) {
        val json = JSONObject()
        for ((hash, values) in stretches) json.put(hash, JSONArray().apply { values.forEach { put(it) } })
        file.parentFile?.mkdirs()
        val temporary = File(file.path + ".tmp")
        temporary.writeText(json.toString())
        temporary.renameTo(file)
    }
}

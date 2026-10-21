package app.honmani.reader.data.difficulty

/**
 * Every written form and reading of the installed dictionaries, with the word classes they can
 * have and how common they are, packed into a few arrays so that a book can be split into words
 * without a database query per word. Holding Jitendex this way takes about 10 MB.
 */
class Lexicon private constructor(
    private val chars: CharArray,
    /** Where each key starts in [chars]; one more entry marks the end of the last key. */
    private val starts: IntArray,
    private val classes: IntArray,
    private val ranks: IntArray,
) {
    val size: Int get() = classes.size

    /** The key's index, or -1 if no dictionary has it. */
    fun find(key: String): Int {
        var low = 0
        var high = size - 1
        while (low <= high) {
            val middle = (low + high) ushr 1
            val order = compare(middle, key)
            when {
                order < 0 -> low = middle + 1
                order > 0 -> high = middle - 1
                else -> return middle
            }
        }
        return -1
    }

    /** [WordClass] flags of the entries with this key (0 for nouns, particles and the like). */
    fun classes(index: Int): Int = classes[index]

    /** The best frequency rank of a word with this key; [UNRANKED] if no frequency list has it. */
    fun rank(index: Int): Int = ranks[index]

    private fun compare(index: Int, key: String): Int {
        val start = starts[index]
        val length = starts[index + 1] - start
        for (i in 0 until minOf(length, key.length)) {
            val difference = chars[start + i] - key[i]
            if (difference != 0) return difference
        }
        return length - key.length
    }

    /** Collects keys in any order; a key added again takes the union of classes and the best rank. */
    class Builder {
        private val text = StringBuilder()
        private var starts = IntArray(1024)
        private var classes = IntArray(1024)
        private var ranks = IntArray(1024)
        private var count = 0

        fun add(key: String, classes: Int, rank: Int): Builder {
            if (key.isEmpty()) return this
            if (count == this.classes.size) {
                starts = starts.copyOf(count * 2)
                this.classes = this.classes.copyOf(count * 2)
                ranks = ranks.copyOf(count * 2)
            }
            starts[count] = text.length
            this.classes[count] = classes
            ranks[count] = if (rank > 0) rank else UNRANKED
            text.append(key)
            count++
            return this
        }

        fun build(): Lexicon {
            val all = CharArray(text.length).also { text.getChars(0, text.length, it, 0) }
            fun end(i: Int) = if (i + 1 < count) starts[i + 1] else all.size
            fun compare(a: Int, b: Int): Int {
                val lengthA = end(a) - starts[a]
                val lengthB = end(b) - starts[b]
                for (i in 0 until minOf(lengthA, lengthB)) {
                    val difference = all[starts[a] + i] - all[starts[b] + i]
                    if (difference != 0) return difference
                }
                return lengthA - lengthB
            }
            val order = sortIndices(count, ::compare)

            val packed = StringBuilder(all.size)
            val outStarts = IntArray(count + 1)
            val outClasses = IntArray(count)
            val outRanks = IntArray(count)
            var size = 0
            for (i in order) {
                if (size > 0 && sameAsLast(packed, outStarts, size, all, starts[i], end(i))) {
                    outClasses[size - 1] = outClasses[size - 1] or classes[i]
                    outRanks[size - 1] = minOf(outRanks[size - 1], ranks[i])
                    continue
                }
                outStarts[size] = packed.length
                packed.appendRange(all, starts[i], end(i))
                outClasses[size] = classes[i]
                outRanks[size] = ranks[i]
                size++
            }
            outStarts[size] = packed.length
            val chars = CharArray(packed.length).also { packed.getChars(0, packed.length, it, 0) }
            return Lexicon(chars, outStarts.copyOf(size + 1), outClasses.copyOf(size), outRanks.copyOf(size))
        }

        /** 0 until [count] ordered by [compare], stable; a merge sort on ints, no boxing. */
        private fun sortIndices(count: Int, compare: (Int, Int) -> Int): IntArray {
            var from = IntArray(count) { it }
            var to = IntArray(count)
            var width = 1
            while (width < count) {
                var start = 0
                while (start < count) {
                    val middle = minOf(start + width, count)
                    val end = minOf(start + 2 * width, count)
                    var left = start
                    var right = middle
                    var out = start
                    while (left < middle && right < end) {
                        to[out++] = if (compare(from[left], from[right]) <= 0) from[left++] else from[right++]
                    }
                    while (left < middle) to[out++] = from[left++]
                    while (right < end) to[out++] = from[right++]
                    start = end
                }
                from = to.also { to = from }
                width *= 2
            }
            return from
        }

        private fun sameAsLast(packed: StringBuilder, starts: IntArray, size: Int, all: CharArray, from: Int, to: Int): Boolean {
            val lastStart = starts[size - 1]
            if (packed.length - lastStart != to - from) return false
            for (i in 0 until to - from) {
                if (packed[lastStart + i] != all[from + i]) return false
            }
            return true
        }
    }

    companion object {
        /** In a dictionary, but in no frequency list: rarer than anything ranked. */
        const val UNRANKED = Int.MAX_VALUE
    }
}

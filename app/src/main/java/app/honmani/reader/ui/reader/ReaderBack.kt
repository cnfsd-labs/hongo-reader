package app.honmani.reader.ui.reader

/** What the back button does in the reader WebView. */
sealed interface ReaderBack {
    /** Move through the WebView history by [steps] (negative: back). */
    data class Step(val steps: Int) : ReaderBack

    /** The book list is not in the history: load it. */
    data object LoadLibrary : ReaderBack

    companion object {
        /** ttu's book list. */
        val LIBRARY_PATHS = setOf("/manage", "/")

        /** ttu pages opened on top of a book or the book list; back closes them. */
        private val OVERLAY_PATHS = setOf("/settings", "/statistics")

        /**
         * Back from [historyPaths]`[currentIndex]`: settings and statistics go back to the page
         * they were opened from; anything else (a book) goes straight to the book list, however
         * many pages were turned or visited. Not called on the book list itself.
         */
        fun from(historyPaths: List<String>, currentIndex: Int): ReaderBack {
            if (historyPaths.getOrNull(currentIndex) in OVERLAY_PATHS && currentIndex > 0) {
                return Step(-1)
            }
            val target = (currentIndex - 1 downTo 0).firstOrNull { historyPaths[it] in LIBRARY_PATHS }
            return if (target != null) Step(target - currentIndex) else LoadLibrary
        }
    }
}

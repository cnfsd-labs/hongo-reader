package app.honmani.reader.ui.reader

/**
 * Maps request paths on the reader origin to files of the bundled ッツ Ebook Reader build.
 *
 * ttu is a prerendered SvelteKit app, so `/manage` is served from `manage.html` and `/` from
 * `index.html`. Kept in sync with `web-tests/support/ttu-server.js`, which serves the build the
 * same way for browser tests.
 */
object TtuAssetResolver {
    /** Asset directory that `:app:buildTtu` writes the ttu build into. */
    const val ASSET_DIR = "ttu"

    data class Resolved(val assetPath: String, val mimeType: String)

    private val mimeTypes = mapOf(
        "html" to "text/html",
        "js" to "text/javascript",
        "mjs" to "text/javascript",
        "css" to "text/css",
        "json" to "application/json",
        "webmanifest" to "application/manifest+json",
        "svg" to "image/svg+xml",
        "png" to "image/png",
        "ico" to "image/x-icon",
        "woff2" to "font/woff2",
        "txt" to "text/plain",
    )

    /**
     * Resolves [path] (as passed to a `WebViewAssetLoader.PathHandler` registered at "/", so
     * without the leading slash or query) or returns null if it is not a valid asset path.
     */
    fun resolve(path: String): Resolved? {
        val trimmed = path.trimStart('/')
        val segments = trimmed.split('/')
        if (segments.any { it == ".." || it == "." } || trimmed.contains('\\')) return null

        val file = when {
            trimmed.isEmpty() -> "index.html"
            '.' in segments.last() -> trimmed
            else -> "$trimmed.html"
        }
        val extension = file.substringAfterLast('.', missingDelimiterValue = "")
        return Resolved(
            assetPath = "$ASSET_DIR/$file",
            mimeType = mimeTypes[extension] ?: "application/octet-stream",
        )
    }
}

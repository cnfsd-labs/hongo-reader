package app.honmani.reader.ui.reader

import android.content.res.AssetManager
import android.webkit.WebResourceResponse
import androidx.webkit.WebViewAssetLoader
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException

/**
 * Serves the bundled ッツ Ebook Reader build. Missing files get an explicit 404 so that no
 * request on the reader origin ever falls through to the network.
 */
class TtuPathHandler(private val assets: AssetManager) : WebViewAssetLoader.PathHandler {
    override fun handle(path: String): WebResourceResponse {
        val resolved = TtuAssetResolver.resolve(path) ?: return notFound()
        return try {
            WebResourceResponse(resolved.mimeType, null, assets.open(resolved.assetPath))
        } catch (_: FileNotFoundException) {
            notFound()
        }
    }

    private fun notFound() = WebResourceResponse(
        "text/plain",
        "utf-8",
        404,
        "Not Found",
        emptyMap(),
        ByteArrayInputStream(ByteArray(0)),
    )
}

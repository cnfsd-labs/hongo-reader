package app.honmani.reader.ui.reader

import app.honmani.reader.ui.reader.TtuAssetResolver.Resolved
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TtuAssetResolverTest {
    @Test
    fun root_servesIndex() {
        assertEquals(Resolved("ttu/index.html", "text/html"), TtuAssetResolver.resolve(""))
        assertEquals(Resolved("ttu/index.html", "text/html"), TtuAssetResolver.resolve("/"))
    }

    @Test
    fun extensionlessRoutes_servePrerenderedHtml() {
        assertEquals(Resolved("ttu/manage.html", "text/html"), TtuAssetResolver.resolve("manage"))
        assertEquals(Resolved("ttu/b.html", "text/html"), TtuAssetResolver.resolve("b"))
    }

    @Test
    fun files_keepTheirPathAndGetTheRightMimeType() {
        assertEquals(
            Resolved("ttu/_app/immutable/entry/start.Abc123.js", "text/javascript"),
            TtuAssetResolver.resolve("_app/immutable/entry/start.Abc123.js"),
        )
        assertEquals("text/css", TtuAssetResolver.resolve("_app/immutable/assets/0.x.css")?.mimeType)
        assertEquals("application/manifest+json", TtuAssetResolver.resolve("manifest.webmanifest")?.mimeType)
        assertEquals("image/svg+xml", TtuAssetResolver.resolve("safari-pinned-tab.svg")?.mimeType)
    }

    @Test
    fun unknownExtension_isOctetStream() {
        assertEquals("application/octet-stream", TtuAssetResolver.resolve("data.bin")?.mimeType)
    }

    @Test
    fun dotSegmentsAndBackslashes_areRejected() {
        assertNull(TtuAssetResolver.resolve("../honmani/tap-bridge.js"))
        assertNull(TtuAssetResolver.resolve("_app/../../secret"))
        assertNull(TtuAssetResolver.resolve("./index.html"))
        assertNull(TtuAssetResolver.resolve("..\\honmani\\tap-bridge.js"))
    }
}

package app.honmani.reader.data.library

import app.honmani.reader.data.SafeXml
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.URLDecoder
import java.util.zip.ZipException
import java.util.zip.ZipFile
import org.w3c.dom.Element
import org.xml.sax.SAXException

/** Title, author and cover of an EPUB, read from its package document (OPF). */
data class EpubMetadata(
    val title: String?,
    val author: String?,
    val cover: Cover?,
) {
    class Cover(val bytes: ByteArray, val extension: String)

    companion object {
        /** Covers larger than this are skipped; the library shows the title instead. */
        const val MAX_COVER_BYTES = 10 * 1024 * 1024
        private const val MAX_XML_BYTES = 4 * 1024 * 1024
        private const val CONTAINER_PATH = "META-INF/container.xml"

        /**
         * Reads [file], or throws [InvalidEpubException] if it is not a readable EPUB. A missing
         * title, author or cover is not an error.
         */
        fun read(file: File): EpubMetadata = try {
            ZipFile(file).use { zip -> read(zip) }
        } catch (e: ZipException) {
            throw InvalidEpubException("Not a zip file", e)
        } catch (e: SAXException) {
            throw InvalidEpubException("Malformed XML", e)
        } catch (e: IOException) {
            throw InvalidEpubException("Unreadable file", e)
        }

        private fun read(zip: ZipFile): EpubMetadata {
            val container = zip.xml(CONTAINER_PATH)
                ?: throw InvalidEpubException("No $CONTAINER_PATH")
            val opfPath = container.elements("rootfile").firstNotNullOfOrNull {
                it.getAttribute("full-path").ifBlank { null }
            } ?: throw InvalidEpubException("No package document")
            val opf = zip.xml(opfPath) ?: throw InvalidEpubException("Missing $opfPath")

            val title = opf.elements("title").firstNotNullOfOrNull { it.text() }
            val author = opf.elements("creator").firstNotNullOfOrNull { it.text() }
            val cover = coverHref(opf)
                ?.let { resolve(opfPath, it) }
                ?.let { zip.bytes(it, MAX_COVER_BYTES) }
                ?.let { bytes -> imageExtension(bytes)?.let { Cover(bytes, it) } }
            return EpubMetadata(title, author, cover)
        }

        /** EPUB 3 `properties="cover-image"`, else the EPUB 2 `<meta name="cover">` item. */
        private fun coverHref(opf: Element): String? {
            val items = opf.elements("item")
            items.firstOrNull { "cover-image" in it.getAttribute("properties").split(' ') }
                ?.let { return it.getAttribute("href") }
            val coverId = opf.elements("meta")
                .firstOrNull { it.getAttribute("name") == "cover" }
                ?.getAttribute("content")
                ?: return null
            return items.firstOrNull { it.getAttribute("id") == coverId }
                ?.takeIf { it.getAttribute("media-type").startsWith("image/") }
                ?.getAttribute("href")
        }

        /** Resolves [href] (URL-encoded, relative to the OPF) to a zip entry path. */
        internal fun resolve(opfPath: String, href: String): String? {
            val decoded = try {
                URLDecoder.decode(href.substringBefore('#').replace("+", "%2B"), "UTF-8")
            } catch (_: IllegalArgumentException) {
                return null
            }
            val parts = opfPath.substringBeforeLast('/', "").split('/').filter { it.isNotEmpty() }
                .toMutableList()
            for (part in decoded.split('/')) {
                when (part) {
                    "", "." -> Unit
                    ".." -> parts.removeLastOrNull() ?: return null
                    else -> parts += part
                }
            }
            return parts.joinToString("/").ifEmpty { null }
        }

        private fun imageExtension(bytes: ByteArray): String? = when {
            bytes.startsWith(0xFF, 0xD8, 0xFF) -> "jpg"
            bytes.startsWith(0x89, 'P'.code, 'N'.code, 'G'.code) -> "png"
            bytes.startsWith('G'.code, 'I'.code, 'F'.code) -> "gif"
            bytes.size > 12 && bytes.startsWith('R'.code, 'I'.code, 'F'.code, 'F'.code) &&
                String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP" -> "webp"
            else -> null
        }

        private fun ByteArray.startsWith(vararg prefix: Int): Boolean =
            size >= prefix.size && prefix.indices.all { this[it].toInt() and 0xFF == prefix[it] }

        private fun ZipFile.bytes(path: String, limit: Int): ByteArray? {
            val entry = getEntry(path) ?: return null
            if (entry.size > limit) return null
            return getInputStream(entry).use { input ->
                val bytes = input.readUpTo(limit + 1)
                bytes.takeIf { it.size <= limit }
            }
        }

        private fun InputStream.readUpTo(limit: Int): ByteArray {
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (out.size() < limit) {
                val read = read(buffer, 0, minOf(buffer.size, limit - out.size()))
                if (read < 0) break
                out.write(buffer, 0, read)
            }
            return out.toByteArray()
        }

        private fun ZipFile.xml(path: String): Element? {
            val bytes = bytes(path, MAX_XML_BYTES) ?: return null
            return SafeXml.parse(bytes)
        }

        /** Descendant elements with this local name, in any namespace. */
        private fun Element.elements(localName: String): List<Element> {
            val nodes = getElementsByTagNameNS("*", localName)
            return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
        }

        private fun Element.text(): String? =
            textContent?.replace(Regex("\\s+"), " ")?.trim()?.ifEmpty { null }
    }
}

class InvalidEpubException(message: String, cause: Throwable? = null) : Exception(message, cause)

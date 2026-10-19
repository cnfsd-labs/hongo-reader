package app.hongo.reader.data.opds

import app.hongo.reader.data.SafeXml
import java.net.URI
import java.net.URISyntaxException
import java.net.URLEncoder
import org.w3c.dom.Element
import org.xml.sax.SAXException

const val EPUB_TYPE = "application/epub+zip"
const val TEXT_TYPE = "text/plain"

/** One page of an OPDS 1.x catalogue (an Atom feed). URLs are absolute. */
data class OpdsFeed(
    val title: String,
    val entries: List<OpdsEntry>,
    /** The next page of this feed, if it is paged. */
    val nextUrl: String? = null,
    val search: OpdsSearch? = null,
    /** The feed of the newest books, if the feed links one (`rel="http://opds-spec.org/sort/new"`). */
    val newestUrl: String? = null,
)

/** A catalogue entry: a link to another feed (navigation), a book, or both. */
data class OpdsEntry(
    val id: String,
    val title: String,
    val authors: List<String> = emptyList(),
    val summary: String? = null,
    /** The feed this entry leads to, for navigation entries ("Authors", "Recently added"). */
    val feedUrl: String? = null,
    val acquisitions: List<OpdsAcquisition> = emptyList(),
    val thumbnailUrl: String? = null,
    /** Language codes from `dc:language` / `dcterms:language` (Calibre-Web: "jpn"). */
    val languages: List<String> = emptyList(),
) {
    /** The file HonGO! would download: an EPUB if there is one, else a text file. */
    val book: OpdsAcquisition?
        get() = acquisitions.firstOrNull { it.type == EPUB_TYPE }
            ?: acquisitions.firstOrNull { it.type == TEXT_TYPE }

    /** A book in formats HonGO! can't open (PDF, MOBI, …). */
    val hasOnlyOtherFormats: Boolean get() = book == null && acquisitions.isNotEmpty()

    /**
     * Whether this is a Japanese book: by its language code, or, if the catalogue gives none,
     * by kana in its title (kanji alone could be Chinese).
     */
    val isJapanese: Boolean
        get() = if (languages.isNotEmpty()) {
            languages.any { code ->
                val lang = code.trim().lowercase()
                lang == "ja" || lang == "jpn" || lang.startsWith("ja-") || lang.startsWith("ja_")
            }
        } else {
            title.any { it in '\u3041'..'\u30FF' }
        }
}

/** The file name the library sees for a download: the title (text books take their title from it). */
fun bookFileName(title: String, type: String): String {
    val extension = if (type == EPUB_TYPE) "epub" else "txt"
    val safe = title.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().take(100)
    return "${safe.ifEmpty { "book" }}.$extension"
}

data class OpdsAcquisition(val url: String, val type: String)

/** How to search a catalogue: a template with `{searchTerms}`, or an OpenSearch description. */
sealed interface OpdsSearch {
    data class Template(val template: String) : OpdsSearch {
        fun urlFor(terms: String): String {
            val encoded = URLEncoder.encode(terms.trim(), "UTF-8").replace("+", "%20")
            return template
                .replace("{searchTerms}", encoded)
                // Other OpenSearch parameters (start page, language, …) are left to the server.
                .replace(Regex("\\{[^}]*\\}"), "")
        }
    }

    data class Description(val url: String) : OpdsSearch
}

/** The bytes are not an OPDS feed (an HTML page, for example). */
class NotAFeedException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Parses OPDS 1.x Atom feeds and OpenSearch descriptions. */
object OpdsParser {
    /** Parses the feed at [baseUrl]; relative links are resolved against it. */
    fun parseFeed(bytes: ByteArray, baseUrl: String): OpdsFeed {
        val root = parseXml(bytes)
        if (root.localName != "feed") throw NotAFeedException("Not an Atom feed: <${root.localName}>")
        val links = root.childElements("link")
        val searchLinks = links.filter { it.getAttribute("rel") == "search" }
        val template = searchLinks.firstOrNull { isAtom(it.getAttribute("type")) }
            ?.let { resolve(baseUrl, it.getAttribute("href")) }
            ?.takeIf { "{searchTerms}" in it }
        val description = searchLinks
            .firstOrNull { it.getAttribute("type").startsWith(OPENSEARCH_TYPE) }
            ?.let { resolve(baseUrl, it.getAttribute("href")) }
        return OpdsFeed(
            title = root.childText("title").orEmpty(),
            // Ids key the list, so a feed that repeats one shows it once.
            entries = root.childElements("entry").mapNotNull { parseEntry(it, baseUrl) }.distinctBy { it.id },
            nextUrl = links.firstOrNull { it.getAttribute("rel") == "next" }
                ?.let { resolve(baseUrl, it.getAttribute("href")) },
            search = template?.let { OpdsSearch.Template(it) }
                ?: description?.let { OpdsSearch.Description(it) },
            newestUrl = (links + root.childElements("entry").flatMap { it.childElements("link") })
                .firstOrNull { it.getAttribute("rel") == SORT_NEW_REL }
                ?.let { resolve(baseUrl, it.getAttribute("href")) },
        )
    }

    /** Reads the Atom search template from an OpenSearch description, if it has one. */
    fun parseSearchDescription(bytes: ByteArray, baseUrl: String): OpdsSearch.Template? {
        val root = parseXml(bytes)
        val urls = root.childElements("Url").filter { it.getAttribute("template").isNotEmpty() }
        val url = urls.firstOrNull { isAtom(it.getAttribute("type")) } ?: urls.firstOrNull() ?: return null
        return resolve(baseUrl, url.getAttribute("template"))?.let { OpdsSearch.Template(it) }
    }

    private fun parseEntry(entry: Element, baseUrl: String): OpdsEntry? {
        val title = entry.childText("title") ?: return null
        val links = entry.childElements("link")
        val acquisitions = links
            .filter { it.getAttribute("rel") in ACQUISITION_RELS }
            .mapNotNull { link ->
                val url = resolve(baseUrl, link.getAttribute("href")) ?: return@mapNotNull null
                OpdsAcquisition(url, link.getAttribute("type").substringBefore(';').trim().lowercase())
            }
        val feedUrl = if (acquisitions.isEmpty()) {
            links.firstOrNull { isAtom(it.getAttribute("type")) && it.getAttribute("rel") !in NOT_NAVIGATION_RELS }
                ?.let { resolve(baseUrl, it.getAttribute("href")) }
        } else {
            null
        }
        val thumbnail = THUMBNAIL_RELS.firstNotNullOfOrNull { rel ->
            links.firstOrNull { it.getAttribute("rel") == rel }
        }
        return OpdsEntry(
            id = entry.childText("id") ?: feedUrl ?: acquisitions.firstOrNull()?.url ?: title,
            title = title,
            authors = entry.childElements("author").mapNotNull { it.childText("name") },
            summary = summary(entry),
            feedUrl = feedUrl,
            acquisitions = acquisitions,
            thumbnailUrl = thumbnail?.let { resolve(baseUrl, it.getAttribute("href")) },
            languages = entry.childElements("language").mapNotNull { it.textContent?.trim()?.ifEmpty { null } },
        )
    }

    private fun summary(entry: Element): String? {
        entry.childText("summary")?.let { return it }
        val content = entry.childElements("content").firstOrNull() ?: return null
        val text = when (content.getAttribute("type")) {
            "html" -> stripHtml(content.textContent)
            else -> content.textContent // text, or xhtml: the text of its elements
        }
        return text.collapseWhitespace()
    }

    private fun stripHtml(html: String): String = html
        .replace(BREAK_TAG, " ")
        .replace(TAG, "")
        .replace("&nbsp;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&amp;", "&")

    private fun parseXml(bytes: ByteArray): Element = try {
        SafeXml.parse(bytes)
    } catch (e: SAXException) {
        throw NotAFeedException("Not XML", e)
    }

    /** Resolves [href] against [base]; null for empty or malformed links and non-web schemes. */
    internal fun resolve(base: String, href: String): String? {
        if (href.isBlank()) return null
        return try {
            // Templates contain braces, which URI rejects; keep them through resolution.
            val escaped = href.trim().replace("{", "%7B").replace("}", "%7D")
            val resolved = URI(base).resolve(escaped).toString().replace("%7B", "{").replace("%7D", "}")
            resolved.takeIf { it.startsWith("https://") || it.startsWith("http://") }
        } catch (_: URISyntaxException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun isAtom(type: String) = type.startsWith("application/atom+xml")

    private fun Element.childElements(localName: String): List<Element> {
        val nodes = childNodes
        return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
            .filter { (it.localName ?: it.tagName) == localName }
    }

    private fun Element.childText(localName: String): String? =
        childElements(localName).firstOrNull()?.textContent?.collapseWhitespace()

    private fun String.collapseWhitespace(): String? = replace(WHITESPACE, " ").trim().ifEmpty { null }

    // Compiled once: every entry of a feed goes through them.
    private val BREAK_TAG = Regex("<(br|/p|/div|/li)\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val TAG = Regex("<[^>]*>")
    private val WHITESPACE = Regex("\\s+")
    private const val OPENSEARCH_TYPE = "application/opensearchdescription+xml"
    private const val SORT_NEW_REL = "http://opds-spec.org/sort/new"
    private val ACQUISITION_RELS = setOf(
        "http://opds-spec.org/acquisition",
        "http://opds-spec.org/acquisition/open-access",
    )
    private val NOT_NAVIGATION_RELS = setOf("self", "search", "next", "previous", "up", "alternate")
    private val THUMBNAIL_RELS = listOf(
        "http://opds-spec.org/image/thumbnail",
        "http://opds-spec.org/thumbnail",
        "http://opds-spec.org/image",
        "http://opds-spec.org/cover",
    )
}

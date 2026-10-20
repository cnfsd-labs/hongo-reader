package app.honmani.reader.data

import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.xml.sax.InputSource

/** XML parsing for untrusted input (book files, catalogue feeds): no DTDs, no external entities. */
object SafeXml {
    /** Parses [bytes] namespace-aware; throws [org.xml.sax.SAXException] if they are not XML. */
    fun parse(bytes: ByteArray): Element {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            trySetFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            trySetFeature("http://xml.org/sax/features/external-general-entities", false)
            trySetFeature("http://xml.org/sax/features/external-parameter-entities", false)
            isExpandEntityReferences = false
        }
        val builder = factory.newDocumentBuilder().apply {
            setEntityResolver { _, _ -> InputSource(StringReader("")) }
            setErrorHandler(null)
        }
        return builder.parse(bytes.inputStream()).documentElement
    }

    private fun DocumentBuilderFactory.trySetFeature(name: String, value: Boolean) {
        try {
            setFeature(name, value)
        } catch (_: Exception) {
            // Not every parser knows every feature; the entity resolver still blocks fetches.
        }
    }
}

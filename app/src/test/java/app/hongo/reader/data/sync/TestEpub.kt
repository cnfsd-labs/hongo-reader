package app.hongo.reader.data.sync

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** A two-chapter EPUB: DOCTYPE, ruby, an entity, nested blocks, a file name with a space. */
internal fun testEpub(file: File): File {
    val entries = mapOf(
        "META-INF/container.xml" to """<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""",
        "OEBPS/content.opf" to """<?xml version="1.0"?><package xmlns="http://www.idpf.org/2007/opf" version="3.0"><manifest>
            <item id="c1" href="text/ch%201.xhtml" media-type="application/xhtml+xml"/>
            <item id="c2" href="text/ch2.xhtml" media-type="application/xhtml+xml"/>
            </manifest><spine><itemref idref="c1"/><itemref idref="c2"/></spine></package>""",
        "OEBPS/text/ch 1.xhtml" to """<?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE html>
            <html xmlns="http://www.w3.org/1999/xhtml"><head><title>一</title></head>
            <body>
              <div class="main">
                <p>吾輩は猫である。</p>
                <p>名前は<ruby>未<rt>ま</rt></ruby>だ無い。&nbsp;</p>
              </div>
              <!-- the end -->
            </body></html>""",
        "OEBPS/text/ch2.xhtml" to """<html xmlns="http://www.w3.org/1999/xhtml"><body>
            <h1>二</h1>
            <p>どこで生れたかとんと見当がつかぬ。</p>
            </body></html>""",
    )
    ZipOutputStream(file.outputStream()).use { zip ->
        for ((name, text) in entries) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(text.toByteArray())
            zip.closeEntry()
        }
    }
    return file
}

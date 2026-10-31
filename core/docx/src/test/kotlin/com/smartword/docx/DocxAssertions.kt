package com.smartword.docx

import com.smartword.docx.model.DocxDocument
import com.smartword.docx.pkg.DocxPackage
import com.smartword.docx.xml.Ns
import com.smartword.docx.xml.XmlElement
import com.smartword.docx.xml.XmlParser
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue

/** Shared checks for tests that save and inspect documents. */
object DocxAssertions {

    // Written out independently of the engine, straight from the schema (CT_RPr / CT_PPr).
    private val RPR_ORDER = listOf(
        "rStyle", "rFonts", "b", "bCs", "i", "iCs", "caps", "smallCaps", "strike", "dstrike",
        "outline", "shadow", "emboss", "imprint", "noProof", "snapToGrid", "vanish", "webHidden",
        "color", "spacing", "w", "kern", "position", "sz", "szCs", "highlight", "u", "effect",
        "bdr", "shd", "fitText", "vertAlign", "rtl", "cs", "em", "lang", "eastAsianLayout",
        "specVanish", "oMath", "rPrChange",
    )

    private val PPR_ORDER = listOf(
        "pStyle", "keepNext", "keepLines", "pageBreakBefore", "framePr", "widowControl", "numPr",
        "suppressLineNumbers", "pBdr", "shd", "tabs", "suppressAutoHyphens", "kinsoku", "wordWrap",
        "overflowPunct", "topLinePunct", "autoSpaceDE", "autoSpaceDN", "bidi", "adjustRightInd",
        "snapToGrid", "spacing", "ind", "contextualSpacing", "mirrorIndents", "suppressOverlap",
        "jc", "textDirection", "textAlignment", "textboxTightWrap", "outlineLvl", "divId",
        "cnfStyle", "rPr", "sectPr", "pPrChange",
    )

    fun mainXml(docxBytes: ByteArray): String {
        val part = DocxPackage.read(docxBytes).find("word/document.xml")
            ?: throw AssertionError("word/document.xml is missing")
        return String(part.bytes, Charsets.UTF_8)
    }

    fun mainXml(doc: DocxDocument): String = mainXml(doc.toByteArray())

    fun count(haystack: String, needle: String): Int {
        var n = 0
        var from = 0
        while (true) {
            val at = haystack.indexOf(needle, from)
            if (at < 0) return n
            n++
            from = at + needle.length
        }
    }

    /**
     * Checks the OPC rules Word relies on: every part has a content type, every override
     * names an existing part, all XML parts are well-formed and every internal relationship
     * points to an existing part.
     */
    fun assertPackageIntegrity(docxBytes: ByteArray) {
        val pkg = DocxPackage.read(docxBytes)
        val types = XmlParser.parse(
            (pkg.find("[Content_Types].xml") ?: throw AssertionError("[Content_Types].xml is missing")).bytes,
        ).root
        val defaults = HashSet<String>()
        val overrides = HashSet<String>()
        for (e in types.elements()) {
            if (e.localName == "Default") e.attr("Extension")?.let { defaults.add(it.lowercase()) }
            if (e.localName == "Override") e.attr("PartName")?.let { overrides.add(it) }
        }

        for (part in pkg.parts) {
            if (part.name == "[Content_Types].xml") continue
            val ext = part.name.substringAfterLast('.', "").lowercase()
            assertTrue("No content type for ${part.name}", overrides.contains("/${part.name}") || defaults.contains(ext))
            if (ext == "xml" || ext == "rels") XmlParser.parse(part.bytes)
        }
        for (o in overrides) {
            assertNotNull("Content type override for a missing part: $o", pkg.find(o.removePrefix("/")))
        }
        for (part in pkg.parts) {
            if (!part.name.endsWith(".rels")) continue
            val baseDir = part.name.substringBefore("_rels/")
            for (rel in XmlParser.parse(part.bytes).root.elements()) {
                if (rel.attr("TargetMode") == "External") continue
                val target = rel.attr("Target") ?: continue
                val resolved = if (target.startsWith("/")) target.removePrefix("/") else normalizePath(baseDir + target)
                assertNotNull("${part.name}: relationship target $target does not exist", pkg.find(resolved))
            }
        }
    }

    private fun normalizePath(path: String): String {
        val out = ArrayList<String>()
        for (segment in path.split('/')) {
            when (segment) {
                "", "." -> Unit
                ".." -> if (out.isNotEmpty()) out.removeAt(out.size - 1)
                else -> out.add(segment)
            }
        }
        return out.joinToString("/")
    }

    /** Fails if any w:rPr or w:pPr in the document lists its children out of schema order. */
    fun assertSchemaOrder(docxBytes: ByteArray) {
        val root = XmlParser.parse(mainXml(docxBytes)).root
        check(root)
    }

    private fun check(el: XmlElement) {
        if (el.namespaceUri == Ns.W && el.localName == "rPr") assertOrdered(el, RPR_ORDER)
        if (el.namespaceUri == Ns.W && el.localName == "pPr") assertOrdered(el, PPR_ORDER)
        for (child in el.elements()) check(child)
    }

    private fun assertOrdered(props: XmlElement, order: List<String>) {
        var last = -1
        var lastName = ""
        for (child in props.elements()) {
            if (child.namespaceUri != Ns.W) continue
            val idx = order.indexOf(child.localName)
            if (idx < 0) continue
            assertTrue(
                "<${child.localName}> must not follow <$lastName> in ${props.localName}",
                idx >= last,
            )
            last = idx
            lastName = child.localName
        }
    }
}

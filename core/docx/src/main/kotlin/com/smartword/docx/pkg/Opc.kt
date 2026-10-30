package com.smartword.docx.pkg

import com.smartword.docx.InvalidDocxException
import com.smartword.docx.xml.XmlParseException
import com.smartword.docx.xml.XmlParser

/** Helpers for the Open Packaging Conventions layer of a DOCX file. */
object Opc {
    const val CONTENT_TYPES_PART = "[Content_Types].xml"
    const val ROOT_RELS_PART = "_rels/.rels"
    const val DEFAULT_MAIN_PART = "word/document.xml"
    const val REL_OFFICE_DOCUMENT =
        "http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument"

    /**
     * Returns the name of the main document part, following the package-level
     * relationship of type officeDocument. Falls back to word/document.xml.
     */
    fun mainDocumentPartName(pkg: DocxPackage): String {
        if (pkg.find(CONTENT_TYPES_PART) == null) {
            throw InvalidDocxException("The file is not a DOCX document: [Content_Types].xml is missing")
        }
        var name: String? = null
        val rels = pkg.find(ROOT_RELS_PART)
        if (rels != null) {
            val root = try {
                XmlParser.parse(rels.bytes).root
            } catch (e: XmlParseException) {
                throw InvalidDocxException("The package relationships are damaged", e)
            }
            for (rel in root.elements()) {
                if (rel.localName != "Relationship") continue
                val type = rel.attr("Type") ?: continue
                if (!type.endsWith("/officeDocument")) continue
                if (rel.attr("TargetMode") == "External") continue
                val target = rel.attr("Target") ?: continue
                name = target.removePrefix("/").removePrefix("./")
                break
            }
        }
        val resolved = name ?: DEFAULT_MAIN_PART
        val part = pkg.find(resolved)
            ?: throw InvalidDocxException("The main document part '$resolved' is missing")
        return part.name
    }
}

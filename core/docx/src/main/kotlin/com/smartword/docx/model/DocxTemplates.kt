package com.smartword.docx.model

import com.smartword.docx.pkg.DocxPackage
import com.smartword.docx.pkg.DocxPart
import com.smartword.docx.xml.Ns
import com.smartword.docx.xml.XmlWriter
import java.time.Instant
import java.time.temporal.ChronoUnit

/** The parts of an empty Word document, as Word itself would accept them. */
internal object DocxTemplates {
    private const val DECL = XmlWriter.DECLARATION
    private const val REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val CT = "application/vnd.openxmlformats-officedocument.wordprocessingml"

    private val CONTENT_TYPES = DECL +
        "<Types xmlns=\"${Ns.CONTENT_TYPES}\">" +
        "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
        "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
        "<Override PartName=\"/word/document.xml\" ContentType=\"$CT.document.main+xml\"/>" +
        "<Override PartName=\"/word/styles.xml\" ContentType=\"$CT.styles+xml\"/>" +
        "<Override PartName=\"/word/settings.xml\" ContentType=\"$CT.settings+xml\"/>" +
        "<Override PartName=\"/docProps/core.xml\" ContentType=\"application/vnd.openxmlformats-package.core-properties+xml\"/>" +
        "<Override PartName=\"/docProps/app.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.extended-properties+xml\"/>" +
        "</Types>"

    private val ROOT_RELS = DECL +
        "<Relationships xmlns=\"${Ns.PKG_RELS}\">" +
        "<Relationship Id=\"rId1\" Type=\"$REL/officeDocument\" Target=\"word/document.xml\"/>" +
        "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties\" Target=\"docProps/core.xml\"/>" +
        "<Relationship Id=\"rId3\" Type=\"$REL/extended-properties\" Target=\"docProps/app.xml\"/>" +
        "</Relationships>"

    private val DOCUMENT_RELS = DECL +
        "<Relationships xmlns=\"${Ns.PKG_RELS}\">" +
        "<Relationship Id=\"rId1\" Type=\"$REL/styles\" Target=\"styles.xml\"/>" +
        "<Relationship Id=\"rId2\" Type=\"$REL/settings\" Target=\"settings.xml\"/>" +
        "</Relationships>"

    private val STYLES = DECL +
        "<w:styles xmlns:w=\"${Ns.W}\">" +
        "<w:docDefaults>" +
        "<w:rPrDefault><w:rPr>" +
        "<w:rFonts w:ascii=\"Calibri\" w:hAnsi=\"Calibri\" w:eastAsia=\"Calibri\" w:cs=\"Arial\"/>" +
        "<w:sz w:val=\"24\"/><w:szCs w:val=\"24\"/>" +
        "<w:lang w:val=\"en-US\" w:eastAsia=\"en-US\" w:bidi=\"ar-SA\"/>" +
        "</w:rPr></w:rPrDefault>" +
        "<w:pPrDefault><w:pPr><w:spacing w:after=\"160\" w:line=\"259\" w:lineRule=\"auto\"/></w:pPr></w:pPrDefault>" +
        "</w:docDefaults>" +
        "<w:style w:type=\"paragraph\" w:default=\"1\" w:styleId=\"Normal\"><w:name w:val=\"Normal\"/><w:qFormat/></w:style>" +
        "<w:style w:type=\"character\" w:default=\"1\" w:styleId=\"DefaultParagraphFont\">" +
        "<w:name w:val=\"Default Paragraph Font\"/><w:uiPriority w:val=\"1\"/><w:semiHidden/><w:unhideWhenUsed/></w:style>" +
        "</w:styles>"

    private val SETTINGS = DECL +
        "<w:settings xmlns:w=\"${Ns.W}\">" +
        "<w:defaultTabStop w:val=\"720\"/>" +
        "<w:characterSpacingControl w:val=\"doNotCompress\"/>" +
        "<w:compat><w:compatSetting w:name=\"compatibilityMode\" w:uri=\"http://schemas.microsoft.com/office/word\" w:val=\"15\"/></w:compat>" +
        "</w:settings>"

    private val APP = DECL +
        "<Properties xmlns=\"http://schemas.openxmlformats.org/officeDocument/2006/extended-properties\">" +
        "<Application>Smart Word</Application></Properties>"

    private fun core(stamp: String) = DECL +
        "<cp:coreProperties xmlns:cp=\"http://schemas.openxmlformats.org/package/2006/metadata/core-properties\" " +
        "xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:dcterms=\"http://purl.org/dc/terms/\" " +
        "xmlns:dcmitype=\"http://purl.org/dc/dcmitype/\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">" +
        "<dc:creator>Smart Word</dc:creator><cp:lastModifiedBy>Smart Word</cp:lastModifiedBy>" +
        "<dcterms:created xsi:type=\"dcterms:W3CDTF\">$stamp</dcterms:created>" +
        "<dcterms:modified xsi:type=\"dcterms:W3CDTF\">$stamp</dcterms:modified>" +
        "</cp:coreProperties>"

    private fun document(rtl: Boolean): String {
        val paragraph = if (rtl) "<w:p><w:pPr><w:bidi/></w:pPr></w:p>" else "<w:p/>"
        val bidi = if (rtl) "<w:bidi/>" else ""
        return DECL +
            "<w:document xmlns:w=\"${Ns.W}\" xmlns:r=\"${Ns.R}\"><w:body>" +
            paragraph +
            "<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/>" +
            "<w:pgMar w:top=\"1440\" w:right=\"1440\" w:bottom=\"1440\" w:left=\"1440\" w:header=\"708\" w:footer=\"708\" w:gutter=\"0\"/>" +
            "<w:cols w:space=\"708\"/>$bidi</w:sectPr>" +
            "</w:body></w:document>"
    }

    /** An empty A4 document with one empty paragraph (right-to-left when [rtl] is set). */
    fun newDocument(rtl: Boolean, now: Instant): ByteArray {
        val stamp = now.truncatedTo(ChronoUnit.SECONDS).toString()
        fun part(name: String, content: String) = DocxPart(name, content.toByteArray(Charsets.UTF_8))
        val pkg = DocxPackage(
            listOf(
                part("[Content_Types].xml", CONTENT_TYPES),
                part("_rels/.rels", ROOT_RELS),
                part("word/document.xml", document(rtl)),
                part("word/_rels/document.xml.rels", DOCUMENT_RELS),
                part("word/styles.xml", STYLES),
                part("word/settings.xml", SETTINGS),
                part("docProps/core.xml", core(stamp)),
                part("docProps/app.xml", APP),
            ),
        )
        return pkg.toByteArray()
    }
}

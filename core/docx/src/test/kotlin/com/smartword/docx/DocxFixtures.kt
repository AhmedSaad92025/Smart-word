package com.smartword.docx

import com.smartword.docx.ZipFixtures.text
import com.smartword.docx.xml.Ns

/** Builds DOCX files that look like the output of Word, for use in tests. */
object DocxFixtures {
    const val MARKUP_COMPAT = "http://schemas.openxmlformats.org/markup-compatibility/2006"
    const val W14 = "http://schemas.microsoft.com/office/word/2010/wordml"
    const val DECL = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\r\n"

    const val SECT_PR = "<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/></w:sectPr>"

    private const val CONTENT_TYPES = DECL +
        "<Types xmlns=\"${Ns.CONTENT_TYPES}\">" +
        "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
        "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
        "<Default Extension=\"png\" ContentType=\"image/png\"/>" +
        "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>" +
        "</Types>"

    private const val ROOT_RELS = DECL +
        "<Relationships xmlns=\"${Ns.PKG_RELS}\">" +
        "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>" +
        "</Relationships>"

    private const val DOC_RELS = DECL +
        "<Relationships xmlns=\"${Ns.PKG_RELS}\">" +
        "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"media/image1.png\"/>" +
        "</Relationships>"

    fun documentXml(body: String, sectPr: String = SECT_PR): String =
        DECL + "<w:document xmlns:w=\"${Ns.W}\" xmlns:r=\"${Ns.R}\" " +
            "xmlns:mc=\"$MARKUP_COMPAT\" xmlns:w14=\"$W14\" mc:Ignorable=\"w14\">" +
            "<w:body>$body$sectPr</w:body></w:document>"

    /** A complete DOCX whose body contains [body]. Includes an image and other untouched parts. */
    fun docx(body: String, extra: List<Pair<String, ByteArray>> = emptyList()): ByteArray =
        rawDocx(documentXml(body), extra)

    fun rawDocx(documentXml: String, extra: List<Pair<String, ByteArray>> = emptyList()): ByteArray =
        ZipFixtures.zip(
            listOf(
                "[Content_Types].xml" to text(CONTENT_TYPES),
                "_rels/.rels" to text(ROOT_RELS),
                "word/document.xml" to text(documentXml),
                "word/_rels/document.xml.rels" to text(DOC_RELS),
                "word/styles.xml" to text(DECL + "<w:styles xmlns:w=\"${Ns.W}\"/>"),
                "word/media/image1.png" to ZipFixtures.binary(5000, 7L),
                "docProps/core.xml" to text(DECL + "<cp:coreProperties xmlns:cp=\"urn:core\"/>"),
            ) + extra,
        )

    fun run(text: String, rPr: String = ""): String {
        val props = if (rPr.isEmpty()) "" else "<w:rPr>$rPr</w:rPr>"
        return "<w:r>$props<w:t xml:space=\"preserve\">$text</w:t></w:r>"
    }

    fun para(inner: String, pPr: String = ""): String {
        val props = if (pPr.isEmpty()) "" else "<w:pPr>$pPr</w:pPr>"
        return "<w:p>$props$inner</w:p>"
    }
}

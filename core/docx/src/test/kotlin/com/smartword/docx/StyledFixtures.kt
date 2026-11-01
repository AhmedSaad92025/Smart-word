package com.smartword.docx

import com.smartword.docx.ZipFixtures.text
import com.smartword.docx.xml.Ns

/** Builds DOCX files with styles, numbering and a theme, wired up with proper relationships. */
object StyledFixtures {
    private const val DECL = DocxFixtures.DECL
    private const val REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val CT = "application/vnd.openxmlformats-officedocument"
    private const val A = "http://schemas.openxmlformats.org/drawingml/2006/main"

    // ---- styles ----

    fun docDefaults(rPr: String = "", pPr: String = ""): String =
        "<w:docDefaults><w:rPrDefault><w:rPr>$rPr</w:rPr></w:rPrDefault>" +
            "<w:pPrDefault><w:pPr>$pPr</w:pPr></w:pPrDefault></w:docDefaults>"

    fun style(
        id: String,
        name: String = id,
        type: String = "paragraph",
        basedOn: String? = null,
        pPr: String = "",
        rPr: String = "",
        default: Boolean = false,
    ): String {
        val flag = if (default) " w:default=\"1\"" else ""
        val base = if (basedOn == null) "" else "<w:basedOn w:val=\"$basedOn\"/>"
        val p = if (pPr.isEmpty()) "" else "<w:pPr>$pPr</w:pPr>"
        val r = if (rPr.isEmpty()) "" else "<w:rPr>$rPr</w:rPr>"
        return "<w:style w:type=\"$type\"$flag w:styleId=\"$id\"><w:name w:val=\"$name\"/>$base$p$r</w:style>"
    }

    fun normal(pPr: String = "", rPr: String = ""): String = style("Normal", "Normal", default = true, pPr = pPr, rPr = rPr)

    fun stylesXml(vararg parts: String): String =
        DECL + "<w:styles xmlns:w=\"${Ns.W}\">" + parts.joinToString("") + "</w:styles>"

    // ---- numbering ----

    fun lvl(
        ilvl: Int,
        numFmt: String,
        lvlText: String,
        start: Int = 1,
        pPr: String = "",
        pStyle: String? = null,
        isLgl: Boolean = false,
        restart: Int? = null,
    ): String {
        val restartEl = if (restart == null) "" else "<w:lvlRestart w:val=\"$restart\"/>"
        val link = if (pStyle == null) "" else "<w:pStyle w:val=\"$pStyle\"/>"
        val legal = if (isLgl) "<w:isLgl/>" else ""
        val props = if (pPr.isEmpty()) "" else "<w:pPr>$pPr</w:pPr>"
        return "<w:lvl w:ilvl=\"$ilvl\"><w:start w:val=\"$start\"/><w:numFmt w:val=\"$numFmt\"/>$restartEl$link$legal" +
            "<w:lvlText w:val=\"$lvlText\"/>$props</w:lvl>"
    }

    fun abstractNum(id: Int, vararg levels: String): String =
        "<w:abstractNum w:abstractNumId=\"$id\">" + levels.joinToString("") + "</w:abstractNum>"

    fun num(numId: Int, abstractId: Int, overrides: String = ""): String =
        "<w:num w:numId=\"$numId\"><w:abstractNumId w:val=\"$abstractId\"/>$overrides</w:num>"

    fun startOverride(ilvl: Int, value: Int): String =
        "<w:lvlOverride w:ilvl=\"$ilvl\"><w:startOverride w:val=\"$value\"/></w:lvlOverride>"

    fun numberingXml(vararg parts: String): String =
        DECL + "<w:numbering xmlns:w=\"${Ns.W}\">" + parts.joinToString("") + "</w:numbering>"

    /** The numPr element of a list paragraph. */
    fun numPr(numId: Int, ilvl: Int? = null): String {
        val level = if (ilvl == null) "" else "<w:ilvl w:val=\"$ilvl\"/>"
        return "<w:numPr>$level<w:numId w:val=\"$numId\"/></w:numPr>"
    }

    // ---- theme ----

    private const val CLR_SCHEME = "<a:clrScheme name=\"Office\">" +
        "<a:dk1><a:sysClr val=\"windowText\" lastClr=\"000000\"/></a:dk1>" +
        "<a:lt1><a:sysClr val=\"window\" lastClr=\"FFFFFF\"/></a:lt1>" +
        "<a:dk2><a:srgbClr val=\"44546A\"/></a:dk2><a:lt2><a:srgbClr val=\"E7E6E6\"/></a:lt2>" +
        "<a:accent1><a:srgbClr val=\"4472C4\"/></a:accent1><a:accent2><a:srgbClr val=\"ED7D31\"/></a:accent2>" +
        "<a:accent3><a:srgbClr val=\"A5A5A5\"/></a:accent3><a:accent4><a:srgbClr val=\"FFC000\"/></a:accent4>" +
        "<a:accent5><a:srgbClr val=\"5B9BD5\"/></a:accent5><a:accent6><a:srgbClr val=\"70AD47\"/></a:accent6>" +
        "<a:hlink><a:srgbClr val=\"0563C1\"/></a:hlink><a:folHlink><a:srgbClr val=\"954F72\"/></a:folHlink>" +
        "</a:clrScheme>"

    fun themeXml(
        majorLatin: String = "Calibri Light",
        minorLatin: String = "Calibri",
        minorCs: String = "",
        minorArabic: String = "Arial",
    ): String =
        DECL + "<a:theme xmlns:a=\"$A\" name=\"Office\"><a:themeElements>" + CLR_SCHEME + "<a:fontScheme name=\"Office\">" +
            "<a:majorFont><a:latin typeface=\"$majorLatin\"/><a:ea typeface=\"\"/><a:cs typeface=\"\"/>" +
            "<a:font script=\"Arab\" typeface=\"Times New Roman\"/></a:majorFont>" +
            "<a:minorFont><a:latin typeface=\"$minorLatin\"/><a:ea typeface=\"\"/><a:cs typeface=\"$minorCs\"/>" +
            "<a:font script=\"Arab\" typeface=\"$minorArabic\"/></a:minorFont>" +
            "</a:fontScheme></a:themeElements></a:theme>"

    // ---- package ----

    /**
     * A DOCX whose document relationships point at the given styles, numbering and theme parts.
     * Parts passed as null are left out of the package altogether.
     */
    fun docx(
        body: String,
        styles: String? = stylesXml(normal()),
        numbering: String? = null,
        theme: String? = null,
        sectPr: String = DocxFixtures.SECT_PR,
    ): ByteArray {
        val overrides = StringBuilder()
        val rels = StringBuilder()
        val parts = ArrayList<Pair<String, ByteArray>>()

        overrides.append("<Override PartName=\"/word/document.xml\" ContentType=\"$CT.wordprocessingml.document.main+xml\"/>")
        if (styles != null) {
            overrides.append("<Override PartName=\"/word/styles.xml\" ContentType=\"$CT.wordprocessingml.styles+xml\"/>")
            rels.append("<Relationship Id=\"rId1\" Type=\"$REL/styles\" Target=\"styles.xml\"/>")
        }
        if (numbering != null) {
            overrides.append("<Override PartName=\"/word/numbering.xml\" ContentType=\"$CT.wordprocessingml.numbering+xml\"/>")
            rels.append("<Relationship Id=\"rId2\" Type=\"$REL/numbering\" Target=\"numbering.xml\"/>")
        }
        if (theme != null) {
            overrides.append("<Override PartName=\"/word/theme/theme1.xml\" ContentType=\"$CT.theme+xml\"/>")
            rels.append("<Relationship Id=\"rId3\" Type=\"$REL/theme\" Target=\"theme/theme1.xml\"/>")
        }

        parts.add(
            "[Content_Types].xml" to text(
                DECL + "<Types xmlns=\"${Ns.CONTENT_TYPES}\">" +
                    "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
                    "<Default Extension=\"xml\" ContentType=\"application/xml\"/>$overrides</Types>",
            ),
        )
        parts.add(
            "_rels/.rels" to text(
                DECL + "<Relationships xmlns=\"${Ns.PKG_RELS}\">" +
                    "<Relationship Id=\"rId1\" Type=\"$REL/officeDocument\" Target=\"word/document.xml\"/></Relationships>",
            ),
        )
        parts.add("word/document.xml" to text(DocxFixtures.documentXml(body, sectPr)))
        parts.add(
            "word/_rels/document.xml.rels" to text(
                DECL + "<Relationships xmlns=\"${Ns.PKG_RELS}\">$rels</Relationships>",
            ),
        )
        if (styles != null) parts.add("word/styles.xml" to text(styles))
        if (numbering != null) parts.add("word/numbering.xml" to text(numbering))
        if (theme != null) parts.add("word/theme/theme1.xml" to text(theme))
        return ZipFixtures.zip(parts)
    }
}

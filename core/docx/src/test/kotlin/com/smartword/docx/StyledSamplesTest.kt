package com.smartword.docx

import com.smartword.docx.DocxFixtures.para
import com.smartword.docx.DocxFixtures.run
import com.smartword.docx.StyledFixtures.abstractNum
import com.smartword.docx.StyledFixtures.docDefaults
import com.smartword.docx.StyledFixtures.lvl
import com.smartword.docx.StyledFixtures.normal
import com.smartword.docx.StyledFixtures.num
import com.smartword.docx.StyledFixtures.numPr
import com.smartword.docx.StyledFixtures.numberingXml
import com.smartword.docx.StyledFixtures.style
import com.smartword.docx.StyledFixtures.stylesXml
import com.smartword.docx.model.DocxDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A document with styles, headings, multi-level lists, Arabic lists and theme colors, written
 * after an edit to build/samples. CI renders it with LibreOffice and checks heading sizes,
 * list labels and indents, RTL positions and colors against what the engine reports.
 */
class StyledSamplesTest {

    private val dir = File(System.getProperty("smartword.samples") ?: "build/samples")

    private val sectPr = "<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/>" +
        "<w:pgMar w:top=\"1440\" w:right=\"1440\" w:bottom=\"1440\" w:left=\"1440\" " +
        "w:header=\"708\" w:footer=\"708\" w:gutter=\"0\"/></w:sectPr>"

    private val styles = stylesXml(
        docDefaults(rPr = "<w:sz w:val=\"22\"/><w:szCs w:val=\"22\"/>"),
        normal(),
        style(
            "Heading1", "heading 1", basedOn = "Normal",
            pPr = "<w:keepNext/><w:outlineLvl w:val=\"0\"/>",
            rPr = "<w:b/><w:bCs/><w:color w:val=\"2F5496\" w:themeColor=\"accent1\" w:themeShade=\"BF\"/>" +
                "<w:sz w:val=\"40\"/><w:szCs w:val=\"40\"/>",
        ),
        style(
            "Heading2", "heading 2", basedOn = "Normal",
            pPr = "<w:keepNext/><w:outlineLvl w:val=\"1\"/>",
            rPr = "<w:b/><w:bCs/><w:sz w:val=\"28\"/><w:szCs w:val=\"28\"/>",
        ),
        // "left" in a right-to-left paragraph means the start side, which is the right.
        style("ArabicStart", "Arabic Start", basedOn = "Normal", pPr = "<w:bidi/><w:jc w:val=\"left\"/>"),
    )

    private val numbering = StyledFixtures.numberingXml(
        abstractNum(
            0,
            lvl(0, "decimal", "%1.", pPr = "<w:ind w:left=\"720\" w:hanging=\"360\"/>"),
            lvl(1, "lowerLetter", "%2)", pPr = "<w:ind w:left=\"1440\" w:hanging=\"360\"/>"),
        ),
        abstractNum(
            1,
            lvl(0, "decimal", "%1.", pPr = "<w:ind w:left=\"720\" w:hanging=\"360\"/>"),
            lvl(1, "lowerLetter", "%2)", pPr = "<w:ind w:left=\"1440\" w:hanging=\"360\"/>", restart = 0),
        ),
        abstractNum(
            2,
            lvl(0, "arabicAlpha", "%1.", pPr = "<w:ind w:left=\"720\" w:hanging=\"360\"/>"),
        ),
        abstractNum(
            3,
            lvl(0, "decimal", "%1."),
            lvl(1, "decimal", "%1.%2"),
            lvl(2, "decimal", "%1.%2.%3"),
        ),
        abstractNum(
            4,
            lvl(0, "arabicAbjad", "%1-", pPr = "<w:ind w:left=\"720\" w:hanging=\"360\"/>"),
            lvl(1, "hindiNumbers", "%1.%2", pPr = "<w:ind w:left=\"1440\" w:hanging=\"360\"/>"),
        ),
        num(1, 0),
        num(2, 1),
        num(3, 2),
        num(4, 3),
        num(5, 4),
    )

    private fun item(text: String, numId: Int, ilvl: Int, extra: String = "") =
        para(run(text), numPr(numId, ilvl) + extra)

    @Test
    fun stylesListsAndThemeColors() {
        val body =
            para(run("HEADONE"), "<w:pStyle w:val=\"Heading1\"/>") +
                para(run("HEADTWO"), "<w:pStyle w:val=\"Heading2\"/>") +
                para(run("BODYTEXT")) +
                para("<w:r><w:rPr><w:color w:val=\"ED7D31\" w:themeColor=\"accent2\"/></w:rPr><w:t>THEMEMARK</w:t></w:r>") +
                item("NUMA", 1, 0) + item("NUMB", 1, 1) + item("NUMC", 1, 1) +
                item("NUMD", 1, 0) + item("NUME", 1, 1) +
                item("RSTA", 2, 0) + item("RSTB", 2, 1) + item("RSTC", 2, 0) + item("RSTD", 2, 1) +
                item("عنصر ARLISTA", 3, 0, "<w:bidi/>") + item("عنصر ARLISTB", 3, 0, "<w:bidi/>") +
                item("DEEPA", 4, 0) + item("DEEPB", 4, 1) + item("DEEPC", 4, 2) + item("DEEPD", 4, 2) +
                item("DEEPE", 4, 1) + item("DEEPF", 4, 2) + item("DEEPG", 4, 0) +
                item("عنصر ARMA", 5, 0, "<w:bidi/>") + item("عنصر ARMB", 5, 1, "<w:bidi/>") +
                item("عنصر ARMC", 5, 1, "<w:bidi/>") + item("عنصر ARMD", 5, 0, "<w:bidi/>") +
                para(run("نص STYLERTL"), "<w:pStyle w:val=\"ArabicStart\"/>")

        val doc = DocxDocument.open(StyledFixtures.docx(body, styles, numbering, StyledFixtures.themeXml(), sectPr))
        assertTrue(doc.warnings.isEmpty())

        // What the engine reports must be what LibreOffice renders (checked in CI).
        val labels = doc.listLabels()
        val byText = doc.paragraphs.associateBy { it.text }
        fun label(text: String) = labels[byText.getValue(text)]?.text
        assertEquals(listOf("1.", "a)", "b)", "2.", "a)"), listOf("NUMA", "NUMB", "NUMC", "NUMD", "NUME").map(::label))
        assertEquals(listOf("1.", "a)", "2.", "b)"), listOf("RSTA", "RSTB", "RSTC", "RSTD").map(::label))
        assertEquals(
            listOf("1.", "1.1", "1.1.1", "1.1.2", "1.2", "1.2.1", "2."),
            listOf("DEEPA", "DEEPB", "DEEPC", "DEEPD", "DEEPE", "DEEPF", "DEEPG").map(::label),
        )
        assertEquals(
            listOf("أ-", "أ.١", "أ.٢", "ب-"),
            listOf("عنصر ARMA", "عنصر ARMB", "عنصر ARMC", "عنصر ARMD").map(::label),
        )
        assertEquals(1, byText.getValue("HEADONE").headingLevel)
        assertEquals(2, byText.getValue("HEADTWO").headingLevel)

        doc.addParagraph("EDITMARK")
        val bytes = doc.toByteArray()
        DocxAssertions.assertSchemaOrder(bytes)
        DocxAssertions.assertPackageIntegrity(bytes)

        dir.mkdirs()
        File(dir, "styles-lists.docx").writeBytes(bytes)
        File(dir, "styles-lists.expect").writeText(
            listOf("HEADONE", "HEADTWO", "BODYTEXT", "THEMEMARK", "NUMA", "RSTD", "ARLISTB", "DEEPG", "ARMD", "STYLERTL", "EDITMARK")
                .joinToString("\n"),
            Charsets.UTF_8,
        )
    }
}

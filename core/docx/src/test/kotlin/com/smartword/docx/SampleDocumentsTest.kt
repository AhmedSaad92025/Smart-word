package com.smartword.docx

import com.smartword.docx.DocxFixtures.para
import com.smartword.docx.DocxFixtures.run
import com.smartword.docx.model.Alignment
import com.smartword.docx.model.DocxDocument
import com.smartword.docx.model.RunFormat
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Writes sample documents produced by the engine to build/samples. CI opens them with
 * LibreOffice, an independent implementation, and checks text and paragraph alignment.
 */
class SampleDocumentsTest {

    private val dir = File(System.getProperty("smartword.samples") ?: "build/samples")

    private fun write(name: String, doc: DocxDocument, expected: List<String>) {
        val bytes = doc.toByteArray()
        DocxAssertions.assertSchemaOrder(bytes)
        DocxAssertions.assertPackageIntegrity(bytes)
        val text = DocxDocument.open(bytes).plainText
        for (line in expected) assertTrue("$name should contain '$line'", text.contains(line))

        dir.mkdirs()
        File(dir, "$name.docx").writeBytes(bytes)
        File(dir, "$name.expect").writeText(expected.joinToString("\n"), Charsets.UTF_8)
    }

    @Test
    fun rightToLeftLayout() {
        val doc = DocxDocument.create(rtl = true)
        val title = doc.paragraphs[0]
        title.insertText(0, "عنوان المستند TITLEMARK", RunFormat(bold = true, fontSizePt = 20f))
        title.setAlignment(Alignment.CENTER)
        doc.addParagraph("فقرة عربية افتراضية RTLDEFAULT")
        doc.addParagraph("فقرة عربية يسار RTLLEFT", alignment = Alignment.LEFT)
        doc.addParagraph("فقرة عربية يمين RTLRIGHT", alignment = Alignment.RIGHT)
        doc.addParagraph("English default LTRDEFAULT", rtl = false)
        doc.addParagraph("English right LTRRIGHT", rtl = false, alignment = Alignment.RIGHT)
        write(
            "rtl-layout", doc,
            listOf("TITLEMARK", "RTLDEFAULT", "RTLLEFT", "RTLRIGHT", "LTRDEFAULT", "LTRRIGHT"),
        )
    }

    @Test
    fun formattedText() {
        val doc = DocxDocument.create()
        val p = doc.paragraphs[0]
        p.insertText(0, "Bold Italic Underline Colored Large Courier")
        p.applyFormat(0, 4, RunFormat(bold = true))
        p.applyFormat(5, 11, RunFormat(italic = true))
        p.applyFormat(12, 21, RunFormat(underline = true))
        p.applyFormat(22, 29, RunFormat(colorHex = "C00000"))
        p.applyFormat(30, 35, RunFormat(fontSizePt = 24f))
        p.applyFormat(36, 43, RunFormat(fontName = "Courier New"))
        val arabic = doc.addParagraph("مرحبا بالعالم FMTARABIC")
        arabic.applyFormat(0, 5, RunFormat(bold = true, fontSizePt = 18f))
        write("formatted", doc, listOf("Bold Italic Underline Colored Large Courier", "FMTARABIC"))
    }

    @Test
    fun editedDocumentWithTablesAndLinks() {
        val table = "<w:tbl><w:tblPr/><w:tblGrid><w:gridCol w:w=\"4000\"/></w:tblGrid><w:tr><w:tc>" +
            "<w:tcPr><w:tcW w:w=\"4000\" w:type=\"dxa\"/></w:tcPr>" +
            "<w:p><w:r><w:t>cell</w:t></w:r></w:p></w:tc></w:tr></w:tbl>"
        val link = "<w:hyperlink w:anchor=\"mark\"><w:r><w:t>link</w:t></w:r></w:hyperlink>"
        val body = para(run("Title", "<w:b/>")) +
            para(run("untouched")) +
            table +
            para(run("see ") + link + run(" here")) +
            para(run("مرحبا"), "<w:bidi/>")
        val doc = DocxDocument.open(DocxFixtures.docx(body))
        doc.paragraphs[0].insertText(5, "!")
        doc.paragraphs[2].insertText(0, ">> ")
        doc.addParagraph("appended")
        write("edited-complex", doc, listOf("Title!", "untouched", "cell", "link", "appended", "مرحبا"))
    }
}

package com.smartword.docx.style

import com.smartword.docx.DocxAssertions
import com.smartword.docx.DocxAssertions.mainXml
import com.smartword.docx.DocxFixtures
import com.smartword.docx.DocxFixtures.para
import com.smartword.docx.DocxFixtures.run
import com.smartword.docx.StyledFixtures
import com.smartword.docx.StyledFixtures.normal
import com.smartword.docx.StyledFixtures.style
import com.smartword.docx.StyledFixtures.stylesXml
import com.smartword.docx.model.Alignment
import com.smartword.docx.model.DocxDocument
import com.smartword.docx.model.RunFormat
import com.smartword.docx.model.TextRun
import com.smartword.docx.pkg.DocxPackage
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class StyleEditTest {

    private val styles = stylesXml(
        normal(),
        style(
            "Heading1", "heading 1", basedOn = "Normal",
            pPr = "<w:outlineLvl w:val=\"0\"/>",
            rPr = "<w:b/><w:bCs/><w:sz w:val=\"32\"/><w:szCs w:val=\"32\"/>",
        ),
        style("Strong", "Strong", type = "character", rPr = "<w:b/>"),
    )

    private val numbering = StyledFixtures.numberingXml(
        StyledFixtures.abstractNum(0, StyledFixtures.lvl(0, "decimal", "%1.")),
        StyledFixtures.num(1, 0),
    )

    private val theme = StyledFixtures.themeXml()

    private fun docOf(body: String) =
        DocxDocument.open(StyledFixtures.docx(body, styles = styles, numbering = numbering, theme = theme))

    private fun reopen(doc: DocxDocument): DocxDocument {
        val bytes = doc.toByteArray()
        DocxAssertions.assertSchemaOrder(bytes)
        return DocxDocument.open(bytes)
    }

    @Test
    fun applyingAStyleChangesTheEffectiveFormatting() {
        val doc = docOf(para(run("Title")))
        val p = doc.paragraphs[0]
        assertFalse(p.effectiveFormat(p.inlines[0] as TextRun).bold)
        p.setStyle("Heading1")
        val f = p.effectiveFormat(p.inlines[0] as TextRun)
        assertTrue(f.bold)
        assertEquals(16f, f.fontSizePt, 0.001f)
        assertEquals(1, p.headingLevel)
    }

    @Test
    fun theStyleIsWrittenFirstInTheParagraphProperties() {
        val doc = docOf(para(run("x"), "<w:jc w:val=\"center\"/>"))
        doc.paragraphs[0].setStyle("Heading1")
        assertTrue(mainXml(doc).contains("<w:pPr><w:pStyle w:val=\"Heading1\"/><w:jc w:val=\"center\"/></w:pPr>"))
        val saved = reopen(doc)
        assertEquals("Heading1", saved.paragraphs[0].styleId)
        assertEquals(Alignment.CENTER, saved.paragraphs[0].alignment)
    }

    @Test
    fun changingAndRemovingAStyle() {
        val doc = docOf(para(run("x"), "<w:pStyle w:val=\"Heading1\"/>"))
        val p = doc.paragraphs[0]
        p.setStyle(null)
        assertNull(reopen(doc).paragraphs[0].styleId)
        assertFalse(mainXml(doc).contains("pStyle"))
        p.setStyle("Normal")
        assertEquals("Normal", reopen(doc).paragraphs[0].styleId)
    }

    @Test
    fun onlyExistingParagraphStylesCanBeApplied() {
        val doc = docOf(para(run("x")))
        assertThrows(IllegalArgumentException::class.java) { doc.paragraphs[0].setStyle("Missing") }
        assertThrows(IllegalArgumentException::class.java) { doc.paragraphs[0].setStyle("Strong") }
        assertFalse(doc.hasUnsavedChanges)
    }

    @Test
    fun editingTextKeepsTheParagraphStyleAndFormattingStaysEffective() {
        val doc = docOf(para(run("Title"), "<w:pStyle w:val=\"Heading1\"/>"))
        val p = doc.paragraphs[0]
        p.insertText(5, " two")
        p.applyFormat(0, 3, RunFormat(italic = true))
        val saved = reopen(doc)
        val sp = saved.paragraphs[0]
        assertEquals("Heading1", sp.styleId)
        assertEquals("Title two", sp.text)
        val runs = sp.inlines.filterIsInstance<TextRun>()
        assertTrue(sp.effectiveFormat(runs[0]).italic)
        assertTrue(sp.effectiveFormat(runs[0]).bold)
        assertFalse(sp.effectiveFormat(runs[1]).italic)
        assertTrue(sp.effectiveFormat(runs[1]).bold)
    }

    @Test
    fun splittingAStyledParagraphKeepsTheStyleOnBothParts() {
        val doc = docOf(para(run("Hello World"), "<w:pStyle w:val=\"Heading1\"/>"))
        doc.splitParagraph(doc.paragraphs[0], 5)
        val saved = reopen(doc)
        assertEquals(listOf("Heading1", "Heading1"), saved.paragraphs.map { it.styleId })
        assertEquals(listOf(1, 1), saved.paragraphs.map { it.headingLevel })
    }

    @Test
    fun turningOffAStyleBoldNeedsAnExplicitDirectValue() {
        val doc = docOf(para(run("Title"), "<w:pStyle w:val=\"Heading1\"/>"))
        val p = doc.paragraphs[0]
        p.applyFormat(0, 5, RunFormat(bold = false))
        val saved = reopen(doc)
        val sp = saved.paragraphs[0]
        assertFalse(sp.effectiveFormat(sp.inlines[0] as TextRun).bold)
    }

    @Test
    fun editingNeverChangesStylesNumberingOrThemeParts() {
        val original = StyledFixtures.docx(
            para(run("Title"), "<w:pStyle w:val=\"Heading1\"/>") + para(run("item"), StyledFixtures.numPr(1, 0)),
            styles = styles, numbering = numbering, theme = theme,
        )
        val doc = DocxDocument.open(original)
        doc.paragraphs[0].setStyle(null)
        doc.paragraphs[1].insertText(0, ">> ")
        doc.addParagraph("added")
        val saved = doc.toByteArray()
        DocxAssertions.assertPackageIntegrity(saved)

        val before = DocxPackage.read(original)
        val after = DocxPackage.read(saved)
        assertEquals(before.parts.map { it.name }, after.parts.map { it.name })
        for (part in before.parts) {
            if (part.name == "word/document.xml") continue
            assertArrayEquals(part.name, part.bytes, after.find(part.name)!!.bytes)
        }
    }

    @Test
    fun readingStylesAloneDoesNotChangeTheDocument() {
        val original = StyledFixtures.docx(
            para(run("Title"), "<w:pStyle w:val=\"Heading1\"/>"),
            styles = styles, numbering = numbering, theme = theme,
        )
        val doc = DocxDocument.open(original)
        doc.paragraphs[0].format
        doc.paragraphs[0].headingLevel
        doc.listLabels()
        assertFalse(doc.hasUnsavedChanges)
        val a = DocxPackage.read(original)
        val b = DocxPackage.read(doc.toByteArray())
        for (part in a.parts) assertArrayEquals(part.name, part.bytes, b.find(part.name)!!.bytes)
    }

    // ---- documents created by the engine ----

    @Test
    fun newDocumentsOfferHeadingStyles() {
        val doc = DocxDocument.create(now = Instant.parse("2026-01-01T00:00:00Z"))
        val ids = doc.styles.paragraphStyles.map { it.id }
        for (id in listOf("Normal", "Title", "Heading1", "Heading2", "Heading3", "ListParagraph")) {
            assertTrue("$id is missing", ids.contains(id))
        }
        assertTrue(doc.warnings.isEmpty())
    }

    @Test
    fun applyingAHeadingToANewDocumentWorksAndSurvivesSaving() {
        val doc = DocxDocument.create(now = Instant.parse("2026-01-01T00:00:00Z"))
        val p = doc.paragraphs[0]
        p.insertText(0, "Chapter one")
        p.setStyle("Heading1")
        doc.addParagraph("Body text")

        val bytes = doc.toByteArray()
        DocxAssertions.assertPackageIntegrity(bytes)
        val saved = DocxDocument.open(bytes)
        val heading = saved.paragraphs[0]
        val f = heading.effectiveFormat(heading.inlines[0] as TextRun)
        assertEquals(1, heading.headingLevel)
        assertTrue(f.bold)
        assertEquals(16f, f.fontSizePt, 0.001f)
        assertEquals("2F5496", f.colorHex)
        assertNull(saved.paragraphs[1].headingLevel)
        assertEquals(12f, saved.paragraphs[1].let { it.effectiveFormat(it.inlines[0] as TextRun) }.fontSizePt, 0.001f)
    }

    @Test
    fun headingsInNewArabicDocumentsKeepComplexScriptFormatting() {
        val doc = DocxDocument.create(rtl = true, now = Instant.parse("2026-01-01T00:00:00Z"))
        val p = doc.paragraphs[0]
        p.insertText(0, "عنوان")
        p.setStyle("Heading2")
        val saved = DocxDocument.open(doc.toByteArray())
        val sp = saved.paragraphs[0]
        val f = sp.effectiveFormat(sp.inlines[0] as TextRun)
        assertTrue(sp.isRtl)
        assertEquals(2, sp.headingLevel)
        assertTrue(f.bold)
        assertEquals(13f, f.fontSizePt, 0.001f)
        assertEquals("Arial", f.fontName)
        assertEquals(Alignment.RIGHT, sp.format.alignment)
    }

    @Test
    fun theDefaultFontsOfNewDocumentsAreReported() {
        val doc = DocxDocument.create(now = Instant.parse("2026-01-01T00:00:00Z"))
        doc.paragraphs[0].insertText(0, "abc")
        doc.addParagraph("مرحبا")
        val latin = doc.paragraphs[0].let { it.effectiveFormat(it.inlines[0] as TextRun) }
        val arabic = doc.paragraphs[1].let { it.effectiveFormat(it.inlines[0] as TextRun) }
        assertEquals("Calibri", latin.fontName)
        assertEquals(12f, latin.fontSizePt, 0.001f)
        assertEquals("Arial", arabic.fontName)
    }

    @Test
    fun theSampleFixtureStillOpensWithoutWarnings() {
        val doc = DocxDocument.open(DocxFixtures.docx(para(run("plain"))))
        assertTrue(doc.warnings.isEmpty())
    }
}

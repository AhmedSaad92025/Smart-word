package com.smartword.docx.style

import com.smartword.docx.DocxFixtures
import com.smartword.docx.DocxFixtures.para
import com.smartword.docx.DocxFixtures.run
import com.smartword.docx.StyledFixtures
import com.smartword.docx.StyledFixtures.normal
import com.smartword.docx.StyledFixtures.stylesXml
import com.smartword.docx.model.DocxDocument
import com.smartword.docx.model.TextRun
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Optional parts only refine the display, so damaged ones must never stop a document from opening. */
class StyleResilienceTest {

    private fun firstFormat(doc: DocxDocument) =
        doc.paragraphs[0].effectiveFormat(doc.paragraphs[0].inlines.filterIsInstance<TextRun>()[0])

    @Test
    fun malformedStylesAreIgnoredWithAWarning() {
        val doc = DocxDocument.open(StyledFixtures.docx(para(run("hello")), styles = "<w:styles><oops>"))
        assertEquals("hello", doc.plainText)
        assertEquals(1, doc.warnings.size)
        assertTrue(doc.warnings[0].contains("styles"))
        assertTrue(doc.styles.styles.isEmpty())
        assertEquals(10f, firstFormat(doc).fontSizePt, 0.001f)
    }

    @Test
    fun aDoctypeInStylesIsRejectedSafely() {
        val styles = "<?xml version=\"1.0\"?><!DOCTYPE s [<!ENTITY x \"y\">]><w:styles xmlns:w=\"${com.smartword.docx.xml.Ns.W}\"/>"
        val doc = DocxDocument.open(StyledFixtures.docx(para(run("hello")), styles = styles))
        assertEquals(1, doc.warnings.size)
        assertEquals("hello", doc.plainText)
    }

    @Test
    fun malformedNumberingAndThemeAreIgnored() {
        val doc = DocxDocument.open(
            StyledFixtures.docx(
                para(run("hello")),
                styles = stylesXml(normal()),
                numbering = "<w:numbering",
                theme = "not xml at all",
            ),
        )
        assertEquals(2, doc.warnings.size)
        assertTrue(doc.listLabels().isEmpty())
        assertEquals("hello", doc.paragraphs[0].text)
    }

    @Test
    fun documentsWithoutAStylesPartStillOpen() {
        val doc = DocxDocument.open(StyledFixtures.docx(para(run("hello")), styles = null))
        assertTrue(doc.warnings.isEmpty())
        assertTrue(doc.styles.styles.isEmpty())
        assertEquals(10f, firstFormat(doc).fontSizePt, 0.001f)
        assertFalse(firstFormat(doc).bold)
    }

    @Test
    fun theFallbackLocationIsUsedWhenNoRelationshipPointsAtTheStyles() {
        // The shared fixture has word/styles.xml but no relationship for it.
        val doc = DocxDocument.open(DocxFixtures.docx(para(run("hello"))))
        assertTrue(doc.warnings.isEmpty())
        assertEquals("hello", doc.plainText)
    }

    @Test
    fun stylesWithoutIdsOrWithOddAttributesAreSkipped() {
        val styles = stylesXml(
            "<w:style w:type=\"paragraph\"><w:name w:val=\"NoId\"/></w:style>",
            "<w:style w:styleId=\"Plain\"><w:name w:val=\"Plain\"/></w:style>",
            "<w:style w:type=\"weird\" w:styleId=\"Odd\"/>",
            normal(),
        )
        val doc = DocxDocument.open(StyledFixtures.docx(para(run("x")), styles = styles))
        assertEquals(listOf("Plain", "Odd", "Normal"), doc.styles.styles.map { it.id })
        assertEquals(StyleType.PARAGRAPH, doc.styles["Plain"]?.type)
        assertEquals(StyleType.OTHER, doc.styles["Odd"]?.type)
    }

    @Test
    fun nonNumericValuesAreIgnored() {
        val styles = stylesXml(
            normal(
                pPr = "<w:spacing w:before=\"abc\" w:line=\"x\"/><w:ind w:left=\"1.5\"/>",
                rPr = "<w:sz w:val=\"big\"/>",
            ),
        )
        val doc = DocxDocument.open(StyledFixtures.docx(para(run("x")), styles = styles))
        val f = doc.paragraphs[0].format
        assertEquals(0f, f.spaceBeforePt, 0.001f)
        assertEquals(0f, f.indentStartPt, 0.001f)
        assertEquals(10f, firstFormat(doc).fontSizePt, 0.001f)
    }

    @Test
    fun duplicateStyleIdsKeepTheFirstDefinition() {
        val styles = stylesXml(
            normal(),
            StyledFixtures.style("Dup", rPr = "<w:b/>"),
            StyledFixtures.style("Dup", rPr = "<w:i/>"),
        )
        val doc = DocxDocument.open(
            StyledFixtures.docx(para(run("x"), "<w:pStyle w:val=\"Dup\"/>"), styles = styles),
        )
        assertTrue(firstFormat(doc).bold)
        assertFalse(firstFormat(doc).italic)
    }

    @Test
    fun aNumberingReferenceToAMissingDefinitionIsHarmless() {
        val numbering = StyledFixtures.numberingXml(StyledFixtures.num(1, 42))
        val doc = DocxDocument.open(
            StyledFixtures.docx(
                para(run("x"), StyledFixtures.numPr(1, 0)),
                styles = stylesXml(normal()),
                numbering = numbering,
            ),
        )
        assertTrue(doc.listLabels().isEmpty())
        assertNull(doc.listLabels()[doc.paragraphs[0]])
        assertEquals(1, doc.paragraphs[0].format.listId)
    }

    @Test
    fun cleanDocumentsReportNoWarnings() {
        val doc = DocxDocument.open(
            StyledFixtures.docx(
                para(run("x")),
                styles = stylesXml(normal()),
                numbering = StyledFixtures.numberingXml(),
                theme = StyledFixtures.themeXml(),
            ),
        )
        assertTrue(doc.warnings.isEmpty())
    }
}

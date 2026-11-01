package com.smartword.app.preview

import com.smartword.docx.model.Alignment
import com.smartword.docx.model.DocxDocument
import com.smartword.docx.model.RunFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class PreviewBuilderTest {

    private val now = Instant.parse("2026-01-01T00:00:00Z")

    private fun paragraphs(doc: DocxDocument) = PreviewBuilder.build(doc).filterIsInstance<PreviewParagraph>()

    @Test
    fun aNewArabicDocumentIsRightToLeftAndRightAligned() {
        val doc = DocxDocument.create(rtl = true, now = now)
        doc.paragraphs[0].insertText(0, "مرحبا")
        val p = paragraphs(doc)[0]
        assertTrue(p.rtl)
        assertEquals(Alignment.RIGHT, p.alignment)
        assertEquals("مرحبا", p.spans.joinToString("") { it.text })
    }

    @Test
    fun formattingBecomesSpans() {
        val doc = DocxDocument.create(now = now)
        val p = doc.paragraphs[0]
        p.insertText(0, "Bold plain")
        p.applyFormat(0, 4, RunFormat(bold = true, colorHex = "C00000"))
        val spans = paragraphs(doc)[0].spans
        assertEquals(listOf("Bold", " plain"), spans.map { it.text })
        assertTrue(spans[0].bold)
        assertEquals("C00000", spans[0].colorHex)
        assertFalse(spans[1].bold)
        assertNull(spans[1].colorHex)
    }

    @Test
    fun headingsCarryTheirLevelAndSize() {
        val doc = DocxDocument.create(now = now)
        doc.paragraphs[0].insertText(0, "Title")
        doc.paragraphs[0].setStyle("Heading1")
        doc.addParagraph("Body")
        val result = paragraphs(doc)
        assertEquals(1, result[0].headingLevel)
        assertTrue(result[0].spans[0].bold)
        assertTrue(result[0].spans[0].sizePt > result[1].spans[0].sizePt)
        assertNull(result[1].headingLevel)
    }

    @Test
    fun emptyParagraphsAreKept() {
        val doc = DocxDocument.create(now = now)
        doc.addParagraph("")
        val result = paragraphs(doc)
        assertEquals(2, result.size)
        assertTrue(result.all { it.isEmpty })
    }

    @Test
    fun buildingThePreviewDoesNotChangeTheDocument() {
        val doc = DocxDocument.create(rtl = true, now = now)
        doc.paragraphs[0].insertText(0, "نص")
        val bytes = doc.toByteArray()
        val reopened = DocxDocument.open(bytes)
        PreviewBuilder.build(reopened)
        assertFalse(reopened.hasUnsavedChanges)
        assertTrue(bytes.contentEquals(reopened.toByteArray()))
    }
}

package com.smartword.app.preview

import com.smartword.docx.model.Alignment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewStatsTest {

    private fun paragraph(text: String, label: String? = null) = PreviewParagraph(
        spans = if (text.isEmpty()) emptyList() else listOf(PreviewSpan(text, false, false, false, 11f, null, null)),
        alignment = Alignment.LEFT, rtl = false, headingLevel = null, listLabel = label,
        indentStartPt = 0f, indentEndPt = 0f, spaceBeforePt = 0f, spaceAfterPt = 0f,
    )

    @Test
    fun countsParagraphsCharactersAndOtherBlocks() {
        val stats = PreviewStats.of(listOf(paragraph("abc"), paragraph("de", label = "1."), PreviewPlaceholder("table")))
        assertEquals(2, stats.paragraphs)
        assertEquals(7, stats.characters)
        assertEquals(1, stats.otherBlocks)
        assertTrue(stats.hasText)
    }

    @Test
    fun anEmptyDocumentHasNoText() {
        val stats = PreviewStats.of(listOf(paragraph("")))
        assertEquals(1, stats.paragraphs)
        assertFalse(stats.hasText)
        assertFalse(PreviewStats.of(emptyList()).hasText)
    }
}

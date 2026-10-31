package com.smartword.docx.model

import com.smartword.docx.DocxAssertions
import com.smartword.docx.DocxAssertions.count
import com.smartword.docx.DocxAssertions.mainXml
import com.smartword.docx.DocxFixtures
import com.smartword.docx.DocxFixtures.para
import com.smartword.docx.DocxFixtures.run
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DocxFormatTest {

    private val link = "<w:hyperlink r:id=\"rId9\"><w:r><w:t>link</w:t></w:r></w:hyperlink>"

    private fun docOf(body: String) = DocxDocument.open(DocxFixtures.docx(body))

    private fun reopen(doc: DocxDocument): DocxDocument {
        val bytes = doc.toByteArray()
        DocxAssertions.assertSchemaOrder(bytes)
        return DocxDocument.open(bytes)
    }

    private fun runs(p: DocxParagraph): List<TextRun> = p.inlines.filterIsInstance<TextRun>()

    @Test
    fun boldOnAPartOfARunSplitsIt() {
        val doc = docOf(para(run("abcdef")))
        doc.paragraphs[0].applyFormat(2, 4, RunFormat(bold = true))
        val r = runs(reopen(doc).paragraphs[0])
        assertEquals(listOf("ab", "cd", "ef"), r.map { it.text })
        assertNull(r[0].format.bold)
        assertEquals(true, r[1].format.bold)
        assertNull(r[2].format.bold)
    }

    @Test
    fun formattingTheWholeParagraphMergesRunsBack() {
        val doc = docOf(para(run("abcdef")))
        doc.paragraphs[0].applyFormat(2, 4, RunFormat(bold = true))
        doc.paragraphs[0].applyFormat(0, 6, RunFormat(bold = true))
        val saved = reopen(doc)
        assertEquals(1, saved.paragraphs[0].inlines.size)
        assertEquals(true, runs(saved.paragraphs[0])[0].format.bold)
    }

    @Test
    fun applyingTheSameFormatTwiceDoesNotDuplicateElements() {
        val doc = docOf(para(run("abc")))
        doc.paragraphs[0].applyFormat(0, 3, RunFormat(bold = true, italic = true, underline = true))
        doc.paragraphs[0].applyFormat(0, 3, RunFormat(bold = true, italic = true, underline = true))
        val xml = mainXml(doc)
        assertEquals(1, count(xml, "<w:b/>"))
        assertEquals(1, count(xml, "<w:i/>"))
        assertEquals(1, count(xml, "<w:u "))
    }

    @Test
    fun turningBoldOffWritesAnExplicitOffValue() {
        val doc = docOf(para(run("abc", "<w:b/><w:bCs/>")))
        doc.paragraphs[0].applyFormat(0, 3, RunFormat(bold = false))
        val saved = reopen(doc)
        assertEquals(false, runs(saved.paragraphs[0])[0].format.bold)
        assertTrue(mainXml(doc).contains("<w:b w:val=\"0\"/>"))
    }

    @Test
    fun turningBoldBackOnRemovesTheOffValue() {
        val doc = docOf(para(run("abc", "<w:b w:val=\"0\"/>")))
        doc.paragraphs[0].applyFormat(0, 3, RunFormat(bold = true))
        assertFalse(mainXml(doc).contains("w:val=\"0\""))
        assertEquals(true, runs(reopen(doc).paragraphs[0])[0].format.bold)
    }

    @Test
    fun underlineOnAndOff() {
        val doc = docOf(para(run("abc")))
        doc.paragraphs[0].applyFormat(0, 3, RunFormat(underline = true))
        assertTrue(mainXml(doc).contains("<w:u w:val=\"single\"/>"))
        assertEquals(true, runs(reopen(doc).paragraphs[0])[0].format.underline)
        doc.paragraphs[0].applyFormat(0, 3, RunFormat(underline = false))
        assertTrue(mainXml(doc).contains("<w:u w:val=\"none\"/>"))
        assertEquals(false, runs(reopen(doc).paragraphs[0])[0].format.underline)
    }

    @Test
    fun fontSizeIsWrittenInHalfPointsForBothScripts() {
        val doc = docOf(para(run("abc")))
        doc.paragraphs[0].applyFormat(0, 3, RunFormat(fontSizePt = 12.5f))
        val xml = mainXml(doc)
        assertTrue(xml.contains("<w:sz w:val=\"25\"/>"))
        assertTrue(xml.contains("<w:szCs w:val=\"25\"/>"))
        assertEquals(12.5f, runs(reopen(doc).paragraphs[0])[0].format.fontSizePt!!, 0.001f)
    }

    @Test
    fun fontSizeIsClampedToWordLimits() {
        val doc = docOf(para(run("abc")))
        doc.paragraphs[0].applyFormat(0, 3, RunFormat(fontSizePt = 5000f))
        assertTrue(mainXml(doc).contains("<w:sz w:val=\"3276\"/>"))
    }

    @Test
    fun invalidFontSizeIsRejected() {
        val doc = docOf(para(run("abc")))
        assertThrows(IllegalArgumentException::class.java) {
            doc.paragraphs[0].applyFormat(0, 3, RunFormat(fontSizePt = 0f))
        }
        assertThrows(IllegalArgumentException::class.java) {
            doc.paragraphs[0].applyFormat(0, 3, RunFormat(fontSizePt = -3f))
        }
    }

    @Test
    fun fontNameReplacesThemeFonts() {
        val doc = docOf(para(run("abc", "<w:rFonts w:asciiTheme=\"minorHAnsi\" w:hAnsiTheme=\"minorHAnsi\"/>")))
        doc.paragraphs[0].applyFormat(0, 3, RunFormat(fontName = "Cairo"))
        val xml = mainXml(doc)
        assertFalse(xml.contains("Theme"))
        for (attr in listOf("ascii", "hAnsi", "cs", "eastAsia")) {
            assertTrue(attr, xml.contains("w:$attr=\"Cairo\""))
        }
        assertEquals("Cairo", runs(reopen(doc).paragraphs[0])[0].format.fontName)
    }

    @Test
    fun blankFontNameIsRejected() {
        val doc = docOf(para(run("abc")))
        assertThrows(IllegalArgumentException::class.java) {
            doc.paragraphs[0].applyFormat(0, 3, RunFormat(fontName = "  "))
        }
    }

    @Test
    fun colorReplacesThemeColorAndIsUppercased() {
        val doc = docOf(para(run("abc", "<w:color w:val=\"auto\" w:themeColor=\"accent1\" w:themeShade=\"BF\"/>")))
        doc.paragraphs[0].applyFormat(0, 3, RunFormat(colorHex = "ff8800"))
        val xml = mainXml(doc)
        assertTrue(xml.contains("<w:color w:val=\"FF8800\"/>"))
        assertFalse(xml.contains("theme"))
        assertEquals("FF8800", runs(reopen(doc).paragraphs[0])[0].format.colorHex)
    }

    @Test
    fun invalidColorsAreRejected() {
        val doc = docOf(para(run("abc")))
        for (bad in listOf("red", "#FF0000", "FFF", "GG0000", "")) {
            assertThrows(bad, IllegalArgumentException::class.java) {
                doc.paragraphs[0].applyFormat(0, 3, RunFormat(colorHex = bad))
            }
        }
    }

    @Test
    fun runPropertiesStayInSchemaOrder() {
        val doc = docOf(para(run("abc", "<w:color w:val=\"FF0000\"/><w:sz w:val=\"20\"/><w:lang w:val=\"en-US\"/>")))
        doc.paragraphs[0].applyFormat(
            0, 3,
            RunFormat(bold = true, italic = true, underline = true, fontName = "Arial", rtl = true),
        )
        val bytes = doc.toByteArray()
        DocxAssertions.assertSchemaOrder(bytes)
        val xml = DocxAssertions.mainXml(bytes)
        val order = listOf("<w:rFonts", "<w:b/>", "<w:bCs/>", "<w:i/>", "<w:iCs/>", "<w:color", "<w:sz ", "<w:u ", "<w:rtl/>", "<w:lang")
        var last = -1
        for (marker in order) {
            val at = xml.indexOf(marker)
            assertTrue("$marker is missing", at >= 0)
            assertTrue("$marker is out of order", at > last)
            last = at
        }
    }

    @Test
    fun extensionElementsStayBeforeChangeTracking() {
        val ext = "<w14:ligatures w14:val=\"all\"/>"
        val doc = docOf(para(run("abc", ext)))
        doc.paragraphs[0].applyFormat(0, 3, RunFormat(bold = true))
        val xml = mainXml(doc)
        assertTrue(xml.indexOf("<w:b/>") < xml.indexOf("<w14:ligatures"))
    }

    @Test
    fun formattingLeavesTheUnselectedTextAlone() {
        val doc = docOf(para(run("keep ", "<w:i/>") + run("change") + run(" keep", "<w:u w:val=\"single\"/>")))
        doc.paragraphs[0].applyFormat(5, 11, RunFormat(bold = true))
        val r = runs(reopen(doc).paragraphs[0])
        assertEquals(listOf("keep ", "change", " keep"), r.map { it.text })
        assertEquals(true, r[0].format.italic)
        assertEquals(true, r[1].format.bold)
        assertEquals(true, r[2].format.underline)
        assertNull(r[2].format.bold)
    }

    @Test
    fun formattingSkipsReadOnlyContent() {
        val doc = docOf(para(run("ab") + link + run("cd")))
        doc.paragraphs[0].applyFormat(0, 8, RunFormat(bold = true))
        val saved = reopen(doc)
        assertEquals("ablinkcd", saved.paragraphs[0].text)
        assertEquals(listOf(true, true), runs(saved.paragraphs[0]).map { it.format.bold })
        assertTrue(saved.paragraphs[0].inlines[1] is OpaqueInline)
        assertTrue(mainXml(doc).contains("r:id=\"rId9\""))
    }

    @Test
    fun emptyOrNoOpFormattingChangesNothing() {
        val doc = docOf(para(run("abc")))
        doc.paragraphs[0].applyFormat(1, 1, RunFormat(bold = true))
        doc.paragraphs[0].applyFormat(0, 3, RunFormat.NONE)
        assertFalse(doc.hasUnsavedChanges)
    }

    @Test
    fun formattingRejectsBadRanges() {
        val doc = docOf(para(run("abc")))
        assertThrows(IllegalArgumentException::class.java) { doc.paragraphs[0].applyFormat(2, 1, RunFormat(bold = true)) }
        assertThrows(IllegalArgumentException::class.java) { doc.paragraphs[0].applyFormat(0, 9, RunFormat(bold = true)) }
    }

    @Test
    fun arabicTextGetsComplexScriptFormatting() {
        val doc = docOf(para(run("مرحبا بالعالم", "<w:rtl/>"), "<w:bidi/>"))
        doc.paragraphs[0].applyFormat(0, 13, RunFormat(bold = true, italic = true, fontSizePt = 16f, fontName = "Cairo"))
        val saved = reopen(doc)
        val f = runs(saved.paragraphs[0])[0].format
        assertEquals(true, f.bold)
        assertEquals(true, f.italic)
        assertEquals(16f, f.fontSizePt!!, 0.001f)
        assertEquals("Cairo", f.fontName)
        assertEquals(true, f.rtl)
        val xml = mainXml(doc)
        assertTrue(xml.contains("<w:bCs/>"))
        assertTrue(xml.contains("<w:szCs w:val=\"32\"/>"))
    }

    @Test
    fun mixedArabicAndLatinKeepsOneConsistentFormat() {
        val doc = docOf(para(run("مرحبا Hello")))
        doc.paragraphs[0].applyFormat(0, 11, RunFormat(bold = true))
        val saved = reopen(doc)
        assertEquals(1, saved.paragraphs[0].inlines.size)
        assertEquals(true, runs(saved.paragraphs[0])[0].format.bold)
    }

    @Test
    fun formatOverlayAppliesOnlySpecifiedFields() {
        val base = RunFormat(bold = true, fontSizePt = 12f)
        val merged = base.overlay(RunFormat(italic = true, fontSizePt = 14f))
        assertEquals(RunFormat(bold = true, italic = true, fontSizePt = 14f), merged)
        assertTrue(RunFormat.NONE.isEmpty)
        assertFalse(base.isEmpty)
    }
}

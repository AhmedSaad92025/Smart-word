package com.smartword.docx.model

import com.smartword.docx.DocxFixtures
import com.smartword.docx.DocxFixtures.para
import com.smartword.docx.DocxFixtures.run
import com.smartword.docx.EncryptedDocxException
import com.smartword.docx.InvalidDocxException
import com.smartword.docx.ZipFixtures
import com.smartword.docx.pkg.DocxPackage
import com.smartword.docx.xml.Ns
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class DocxReadTest {

    private fun docOf(body: String) = DocxDocument.open(DocxFixtures.docx(body))

    private fun firstRun(doc: DocxDocument): TextRun = doc.paragraphs[0].inlines[0] as TextRun

    // ---- text ----

    @Test
    fun readsParagraphsAndText() {
        val doc = docOf(para(run("Hello")) + para(run("World ") + run("again")))
        assertEquals(2, doc.paragraphs.size)
        assertEquals("Hello", doc.paragraphs[0].text)
        assertEquals("World again", doc.paragraphs[1].text)
        assertEquals("Hello\nWorld again", doc.plainText)
    }

    @Test
    fun readsEmptyParagraph() {
        val doc = docOf(para(""))
        assertEquals(1, doc.paragraphs.size)
        assertEquals("", doc.paragraphs[0].text)
        assertTrue(doc.paragraphs[0].inlines.isEmpty())
    }

    @Test
    fun readsTabsAndLineBreaksInsideRuns() {
        val doc = docOf(para("<w:r><w:t>a</w:t><w:tab/><w:t>b</w:t><w:br/><w:t>c</w:t></w:r>"))
        assertEquals("a\tb\nc", doc.paragraphs[0].text)
        assertTrue(doc.paragraphs[0].inlines[0] is TextRun)
    }

    @Test
    fun keepsLeadingAndTrailingSpaces() {
        val doc = docOf(para(run("  spaced  ")))
        assertEquals("  spaced  ", doc.paragraphs[0].text)
    }

    @Test
    fun readsArabicText() {
        val doc = docOf(para(run("مرحبا بالعالم", "<w:rtl/>"), "<w:bidi/>"))
        val p = doc.paragraphs[0]
        assertEquals("مرحبا بالعالم", p.text)
        assertTrue(p.isRtl)
        assertNull(p.alignment)
        assertEquals(true, firstRun(doc).format.rtl)
    }

    // ---- run formatting ----

    @Test
    fun readsRunFormatting() {
        val rPr = "<w:rFonts w:ascii=\"Arial\" w:hAnsi=\"Arial\"/><w:b/><w:i/>" +
            "<w:color w:val=\"ff0000\"/><w:sz w:val=\"28\"/><w:u w:val=\"single\"/>"
        val doc = docOf(para(run("x", rPr)))
        val f = firstRun(doc).format
        assertEquals(true, f.bold)
        assertEquals(true, f.italic)
        assertEquals(true, f.underline)
        assertEquals(14f, f.fontSizePt!!, 0.001f)
        assertEquals("FF0000", f.colorHex)
        assertEquals("Arial", f.fontName)
        assertNull(f.rtl)
    }

    @Test
    fun runWithoutPropertiesHasNoFormat() {
        val doc = docOf(para(run("x")))
        assertTrue(firstRun(doc).format.isEmpty)
    }

    @Test
    fun readsExplicitOffValues() {
        val doc = docOf(para(run("x", "<w:b w:val=\"0\"/><w:i w:val=\"false\"/><w:u w:val=\"none\"/>")))
        val f = firstRun(doc).format
        assertEquals(false, f.bold)
        assertEquals(false, f.italic)
        assertEquals(false, f.underline)
    }

    @Test
    fun readsHalfPointSizes() {
        val doc = docOf(para(run("x", "<w:sz w:val=\"21\"/>")))
        assertEquals(10.5f, firstRun(doc).format.fontSizePt!!, 0.001f)
    }

    @Test
    fun automaticColorIsNull() {
        val doc = docOf(para(run("x", "<w:color w:val=\"auto\"/>")))
        assertNull(firstRun(doc).format.colorHex)
    }

    @Test
    fun fontNameFallsBackToOtherAttributes() {
        val doc = docOf(para(run("x", "<w:rFonts w:cs=\"Cairo\"/>")))
        assertEquals("Cairo", firstRun(doc).format.fontName)
    }

    @Test
    fun complexScriptRunsUseCsProperties() {
        val rPr = "<w:bCs/><w:iCs/><w:szCs w:val=\"32\"/>"
        val arabic = firstRun(docOf(para(run("مرحبا", rPr)))).format
        assertEquals(true, arabic.bold)
        assertEquals(true, arabic.italic)
        assertEquals(16f, arabic.fontSizePt!!, 0.001f)

        val latin = firstRun(docOf(para(run("hello", rPr)))).format
        assertNull(latin.bold)
        assertNull(latin.italic)
        assertNull(latin.fontSizePt)
    }

    @Test
    fun rtlFlagSelectsComplexScriptProperties() {
        val f = firstRun(docOf(para(run("abc", "<w:rtl/><w:bCs/>")))).format
        assertEquals(true, f.rtl)
        assertEquals(true, f.bold)
    }

    // ---- opaque inline content ----

    @Test
    fun pageBreakRunIsOpaque() {
        val doc = docOf(para("<w:r><w:br w:type=\"page\"/></w:r>"))
        val inline = doc.paragraphs[0].inlines[0]
        assertTrue(inline is OpaqueInline)
        assertEquals("", doc.paragraphs[0].text)
    }

    @Test
    fun drawingRunIsOpaque() {
        val doc = docOf(para("<w:r><w:drawing/></w:r>" + run("after")))
        assertTrue(doc.paragraphs[0].inlines[0] is OpaqueInline)
        assertEquals("after", doc.paragraphs[0].text)
    }

    @Test
    fun hyperlinkIsReadOnlyButVisible() {
        val link = "<w:hyperlink r:id=\"rId9\"><w:r><w:t>link</w:t></w:r></w:hyperlink>"
        val doc = docOf(para(run("see ") + link + run(" now")))
        val inlines = doc.paragraphs[0].inlines
        assertEquals(3, inlines.size)
        assertTrue(inlines[0] is TextRun)
        assertEquals("link", (inlines[1] as OpaqueInline).previewText)
        assertTrue(inlines[2] is TextRun)
        assertEquals("see link now", doc.paragraphs[0].text)
    }

    @Test
    fun bookmarksAndProofingMarksAreZeroWidth() {
        val doc = docOf(
            para(
                "<w:bookmarkStart w:id=\"0\" w:name=\"b\"/>" + run("x") +
                    "<w:proofErr w:type=\"spellStart\"/><w:bookmarkEnd w:id=\"0\"/>",
            ),
        )
        assertEquals(4, doc.paragraphs[0].inlines.size)
        assertEquals("x", doc.paragraphs[0].text)
    }

    @Test
    fun deletedTextIsHiddenAndInsertedTextIsShown() {
        val deleted = "<w:del w:id=\"1\"><w:r><w:delText>gone</w:delText></w:r></w:del>"
        val inserted = "<w:ins w:id=\"2\"><w:r><w:t>kept</w:t></w:r></w:ins>"
        val doc = docOf(para(deleted + inserted))
        assertEquals("kept", doc.paragraphs[0].text)
    }

    @Test
    fun fieldRunsAreOpaque() {
        val doc = docOf(para("<w:r><w:fldChar w:fldCharType=\"begin\"/></w:r>" + run("5")))
        assertTrue(doc.paragraphs[0].inlines[0] is OpaqueInline)
        assertEquals("5", doc.paragraphs[0].text)
    }

    // ---- paragraph properties ----

    @Test
    fun alignmentInLeftToRightParagraphs() {
        val cases = mapOf(
            "left" to Alignment.LEFT,
            "center" to Alignment.CENTER,
            "right" to Alignment.RIGHT,
            "both" to Alignment.JUSTIFY,
        )
        for ((jc, expected) in cases) {
            val doc = docOf(para(run("x"), "<w:jc w:val=\"$jc\"/>"))
            assertEquals(jc, expected, doc.paragraphs[0].alignment)
            assertFalse(doc.paragraphs[0].isRtl)
        }
    }

    @Test
    fun alignmentIsMirroredInRightToLeftParagraphs() {
        val cases = mapOf(
            "left" to Alignment.RIGHT,
            "start" to Alignment.RIGHT,
            "right" to Alignment.LEFT,
            "end" to Alignment.LEFT,
            "center" to Alignment.CENTER,
            "both" to Alignment.JUSTIFY,
        )
        for ((jc, expected) in cases) {
            val doc = docOf(para(run("x"), "<w:bidi/><w:jc w:val=\"$jc\"/>"))
            assertEquals(jc, expected, doc.paragraphs[0].alignment)
            assertTrue(doc.paragraphs[0].isRtl)
        }
    }

    @Test
    fun paragraphWithoutAlignmentReportsNull() {
        assertNull(docOf(para(run("x"))).paragraphs[0].alignment)
    }

    @Test
    fun bidiCanBeSwitchedOffExplicitly() {
        val doc = docOf(para(run("x"), "<w:bidi w:val=\"0\"/>"))
        assertFalse(doc.paragraphs[0].isRtl)
    }

    // ---- blocks ----

    @Test
    fun tablesAreOpaqueBlocksInDocumentOrder() {
        val table = "<w:tbl><w:tr><w:tc>" + para(run("a")) + "</w:tc><w:tc>" + para(run("b")) + "</w:tc></w:tr></w:tbl>"
        val doc = docOf(para(run("before")) + table + para(run("after")))
        assertEquals(3, doc.blocks.size)
        val block = doc.blocks[1] as OpaqueBlock
        assertEquals(OpaqueKind.TABLE, block.kind)
        assertEquals("a\nb", block.previewText)
        assertEquals(2, doc.paragraphs.size)
        assertEquals("before\na\nb\nafter", doc.plainText)
    }

    @Test
    fun sectionPropertiesAreNotBlocks() {
        val doc = docOf(para(run("only")))
        assertEquals(1, doc.blocks.size)
    }

    @Test
    fun unknownBlockLevelElementsAreOpaque() {
        val sdt = "<w:sdt><w:sdtContent>" + para(run("inside")) + "</w:sdtContent></w:sdt>"
        val doc = docOf("<w:bookmarkStart w:id=\"0\" w:name=\"x\"/>" + sdt)
        assertEquals(OpaqueKind.OTHER, (doc.blocks[0] as OpaqueBlock).kind)
        val tag = doc.blocks[1] as OpaqueBlock
        assertEquals(OpaqueKind.STRUCTURED_TAG, tag.kind)
        assertEquals("inside", tag.previewText)
    }

    // ---- opening ----

    @Test
    fun opensFromInputStream() {
        val doc = DocxDocument.open(ByteArrayInputStream(DocxFixtures.docx(para(run("stream")))))
        assertEquals("stream", doc.plainText)
    }

    @Test
    fun unmodifiedDocumentKeepsEveryPartByteForByte() {
        val original = DocxFixtures.docx(para(run("x", "<w:b/>")) + "<w:tbl/>")
        val saved = DocxDocument.open(original).toByteArray()
        val a = DocxPackage.read(original)
        val b = DocxPackage.read(saved)
        assertEquals(a.parts.map { it.name }, b.parts.map { it.name })
        for (i in a.parts.indices) {
            assertArrayEquals(a.parts[i].name, a.parts[i].bytes, b.parts[i].bytes)
        }
    }

    @Test
    fun rejectsDocumentWithWrongRootElement() {
        val bytes = DocxFixtures.rawDocx(DocxFixtures.DECL + "<x/>")
        assertThrows(InvalidDocxException::class.java) { DocxDocument.open(bytes) }
    }

    @Test
    fun rejectsDocumentWithoutBody() {
        val bytes = DocxFixtures.rawDocx(DocxFixtures.DECL + "<w:document xmlns:w=\"${Ns.W}\"><w:foo/></w:document>")
        assertThrows(InvalidDocxException::class.java) { DocxDocument.open(bytes) }
    }

    @Test
    fun rejectsMalformedDocumentXml() {
        val bytes = DocxFixtures.rawDocx(DocxFixtures.DECL + "<w:document xmlns:w=\"${Ns.W}\"><w:body>")
        assertThrows(InvalidDocxException::class.java) { DocxDocument.open(bytes) }
    }

    @Test
    fun rejectsNonDocxData() {
        assertThrows(InvalidDocxException::class.java) {
            DocxDocument.open(ZipFixtures.text("plain text file"))
        }
    }

    @Test
    fun rejectsZipThatIsNotADocx() {
        val bytes = ZipFixtures.zip(listOf("readme.txt" to ZipFixtures.text("hi")))
        assertThrows(InvalidDocxException::class.java) { DocxDocument.open(bytes) }
    }

    @Test
    fun reportsLegacyOrEncryptedFiles() {
        val ole = byteArrayOf(
            0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(),
            0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte(), 0, 0,
        )
        assertThrows(EncryptedDocxException::class.java) { DocxDocument.open(ole) }
    }
}

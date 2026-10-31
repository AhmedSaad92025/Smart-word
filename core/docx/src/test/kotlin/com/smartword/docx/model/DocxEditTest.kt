package com.smartword.docx.model

import com.smartword.docx.DocxAssertions
import com.smartword.docx.DocxAssertions.count
import com.smartword.docx.DocxAssertions.mainXml
import com.smartword.docx.DocxFixtures
import com.smartword.docx.DocxFixtures.para
import com.smartword.docx.DocxFixtures.run
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DocxEditTest {

    private val link = "<w:hyperlink r:id=\"rId9\"><w:r><w:t>link</w:t></w:r></w:hyperlink>"

    private fun docOf(body: String) = DocxDocument.open(DocxFixtures.docx(body))

    private fun reopen(doc: DocxDocument): DocxDocument {
        val bytes = doc.toByteArray()
        DocxAssertions.assertSchemaOrder(bytes)
        return DocxDocument.open(bytes)
    }

    private fun runs(p: DocxParagraph): List<TextRun> = p.inlines.filterIsInstance<TextRun>()

    // ---- insertText ----

    @Test
    fun insertIntoTheMiddleOfARunKeepsItsFormatting() {
        val doc = docOf(para(run("Hello", "<w:b/>")))
        doc.paragraphs[0].insertText(2, "XY")
        val saved = reopen(doc)
        assertEquals("HeXYllo", saved.paragraphs[0].text)
        assertEquals(1, saved.paragraphs[0].inlines.size)
        assertEquals(true, runs(saved.paragraphs[0])[0].format.bold)
        assertTrue(
            mainXml(doc).contains(
                "<w:r><w:rPr><w:b/></w:rPr><w:t xml:space=\"preserve\">HeXYllo</w:t></w:r>",
            ),
        )
    }

    @Test
    fun insertAtTheStartAndEndExtendsTheNearestRun() {
        val doc = docOf(para(run("mid", "<w:i/>")))
        doc.paragraphs[0].insertText(0, "[")
        doc.paragraphs[0].insertText(4, "]")
        val saved = reopen(doc)
        assertEquals("[mid]", saved.paragraphs[0].text)
        assertEquals(1, saved.paragraphs[0].inlines.size)
        assertEquals(true, runs(saved.paragraphs[0])[0].format.italic)
    }

    @Test
    fun insertWithDifferentFormatSplitsTheRun() {
        val doc = docOf(para(run("abc")))
        doc.paragraphs[0].insertText(1, "X", RunFormat(bold = true))
        val saved = reopen(doc)
        val r = runs(saved.paragraphs[0])
        assertEquals(listOf("a", "X", "bc"), r.map { it.text })
        assertNull(r[0].format.bold)
        assertEquals(true, r[1].format.bold)
        assertNull(r[2].format.bold)
        assertTrue(mainXml(doc).contains("<w:b/><w:bCs/>"))
    }

    @Test
    fun insertWithTheSameFormatDoesNotSplit() {
        val doc = docOf(para(run("abc", "<w:b/>")))
        doc.paragraphs[0].insertText(1, "X", RunFormat(bold = true))
        assertEquals(1, doc.paragraphs[0].inlines.size)
        assertEquals("aXbc", doc.paragraphs[0].text)
    }

    @Test
    fun insertWithDifferentFormatAtTheStartOfARun() {
        val doc = docOf(para(run("abc")))
        doc.paragraphs[0].insertText(0, "X", RunFormat(italic = true))
        val r = runs(reopen(doc).paragraphs[0])
        assertEquals(listOf("X", "abc"), r.map { it.text })
        assertEquals(true, r[0].format.italic)
    }

    @Test
    fun insertIntoAnEmptyParagraphCreatesARun() {
        val doc = docOf(para(""))
        doc.paragraphs[0].insertText(0, "new")
        val saved = reopen(doc)
        assertEquals("new", saved.paragraphs[0].text)
        assertEquals(1, runs(saved.paragraphs[0]).size)
    }

    @Test
    fun arabicTextInARightToLeftParagraphIsMarkedRtl() {
        val doc = docOf(para("", "<w:bidi/>"))
        doc.paragraphs[0].insertText(0, "مرحبا")
        val saved = reopen(doc)
        assertEquals("مرحبا", saved.paragraphs[0].text)
        assertEquals(true, runs(saved.paragraphs[0])[0].format.rtl)
        assertTrue(mainXml(doc).contains("<w:rtl/>"))
    }

    @Test
    fun arabicTextInALeftToRightParagraphIsNotForcedRtl() {
        val doc = docOf(para(""))
        doc.paragraphs[0].insertText(0, "مرحبا")
        assertFalse(mainXml(doc).contains("<w:rtl/>"))
    }

    @Test
    fun tabsAndLineBreaksAreWrittenAsWordElements() {
        val doc = docOf(para(run("x")))
        doc.paragraphs[0].insertText(1, "\ty\nz")
        val xml = mainXml(doc)
        assertTrue(xml.contains("<w:tab/>"))
        assertTrue(xml.contains("<w:br/>"))
        assertEquals("x\ty\nz", reopen(doc).paragraphs[0].text)
    }

    @Test
    fun carriageReturnsAreNormalized() {
        val doc = docOf(para(run("a")))
        doc.paragraphs[0].insertText(1, "\r\nb\rc")
        assertEquals("a\nb\nc", doc.paragraphs[0].text)
    }

    @Test
    fun specialCharactersSurviveSaving() {
        val doc = docOf(para(run("a")))
        doc.paragraphs[0].insertText(1, " <&> \"q\" 😀 ")
        assertEquals("a <&> \"q\" 😀 ", reopen(doc).paragraphs[0].text)
    }

    @Test
    fun insertNextToReadOnlyContent() {
        val doc = docOf(para(run("ab") + link + run("cd")))
        val p = doc.paragraphs[0]
        p.insertText(2, "x")
        p.insertText(7, "y")
        assertEquals("abxlinkycd", p.text)
        assertEquals(3, p.inlines.size)
        assertTrue(p.inlines[1] is OpaqueInline)
    }

    @Test
    fun insertInsideReadOnlyContentIsRejected() {
        val doc = docOf(para(run("ab") + link + run("cd")))
        assertThrows(IllegalArgumentException::class.java) { doc.paragraphs[0].insertText(4, "x") }
        assertEquals("ablinkcd", doc.paragraphs[0].text)
    }

    @Test
    fun insertWithABadOffsetIsRejected() {
        val doc = docOf(para(run("abc")))
        assertThrows(IllegalArgumentException::class.java) { doc.paragraphs[0].insertText(-1, "x") }
        assertThrows(IllegalArgumentException::class.java) { doc.paragraphs[0].insertText(4, "x") }
    }

    @Test
    fun splittingASurrogatePairIsRejected() {
        val doc = docOf(para(run("a😀b")))
        assertThrows(IllegalArgumentException::class.java) { doc.paragraphs[0].insertText(2, "x") }
        assertThrows(IllegalArgumentException::class.java) { doc.paragraphs[0].deleteText(0, 2) }
        doc.paragraphs[0].insertText(3, "x")
        assertEquals("a😀xb", doc.paragraphs[0].text)
    }

    // ---- deleteText ----

    @Test
    fun deleteInsideARun() {
        val doc = docOf(para(run("abcdef", "<w:b/>")))
        doc.paragraphs[0].deleteText(1, 4)
        val saved = reopen(doc)
        assertEquals("aef", saved.paragraphs[0].text)
        assertEquals(true, runs(saved.paragraphs[0])[0].format.bold)
    }

    @Test
    fun deleteAcrossRuns() {
        val doc = docOf(para(run("abc", "<w:b/>") + run("def", "<w:i/>")))
        doc.paragraphs[0].deleteText(2, 4)
        val r = runs(reopen(doc).paragraphs[0])
        assertEquals(listOf("ab", "ef"), r.map { it.text })
        assertEquals(true, r[0].format.bold)
        assertEquals(true, r[1].format.italic)
    }

    @Test
    fun deleteEverythingLeavesAnEmptyParagraph() {
        val doc = docOf(para(run("abc")))
        doc.paragraphs[0].deleteText(0, 3)
        val saved = reopen(doc)
        assertEquals(1, saved.paragraphs.size)
        assertEquals("", saved.paragraphs[0].text)
        assertTrue(saved.paragraphs[0].inlines.isEmpty())
    }

    @Test
    fun deleteSkipsReadOnlyContent() {
        val doc = docOf(para(run("ab") + link + run("cd")))
        doc.paragraphs[0].deleteText(1, 7)
        assertEquals("alinkd", doc.paragraphs[0].text)
        assertTrue(doc.paragraphs[0].inlines.any { it is OpaqueInline })
    }

    @Test
    fun emptyDeleteChangesNothing() {
        val doc = docOf(para(run("abc")))
        doc.paragraphs[0].deleteText(1, 1)
        assertFalse(doc.hasUnsavedChanges)
    }

    @Test
    fun deleteRejectsBadRanges() {
        val doc = docOf(para(run("abc")))
        assertThrows(IllegalArgumentException::class.java) { doc.paragraphs[0].deleteText(2, 1) }
        assertThrows(IllegalArgumentException::class.java) { doc.paragraphs[0].deleteText(0, 9) }
    }

    // ---- paragraph direction and alignment ----

    @Test
    fun alignmentRoundTripsInBothDirections() {
        for (rtl in listOf(false, true)) {
            for (a in Alignment.values()) {
                val doc = docOf(para(run("x"), if (rtl) "<w:bidi/>" else ""))
                doc.paragraphs[0].setAlignment(a)
                assertEquals("$a rtl=$rtl", a, reopen(doc).paragraphs[0].alignment)
            }
        }
    }

    @Test
    fun rightToLeftParagraphsWriteMirroredJustification() {
        val doc = docOf(para(run("x"), "<w:bidi/>"))
        doc.paragraphs[0].setAlignment(Alignment.RIGHT)
        assertTrue(mainXml(doc).contains("<w:jc w:val=\"left\"/>"))
        doc.paragraphs[0].setAlignment(Alignment.LEFT)
        assertTrue(mainXml(doc).contains("<w:jc w:val=\"right\"/>"))
    }

    @Test
    fun clearingAlignmentRemovesTheSetting() {
        val doc = docOf(para(run("x"), "<w:jc w:val=\"center\"/>"))
        doc.paragraphs[0].setAlignment(null)
        val saved = reopen(doc)
        assertNull(saved.paragraphs[0].alignment)
        assertFalse(mainXml(doc).contains("<w:jc"))
    }

    @Test
    fun alignmentCreatesTheParagraphPropertiesWhenMissing() {
        val doc = docOf(para(run("x")))
        doc.paragraphs[0].setAlignment(Alignment.CENTER)
        assertTrue(mainXml(doc).contains("<w:pPr><w:jc w:val=\"center\"/></w:pPr>"))
    }

    @Test
    fun changingDirectionKeepsTheVisualAlignment() {
        val doc = docOf(para(run("x"), "<w:jc w:val=\"right\"/>"))
        val p = doc.paragraphs[0]
        assertEquals(Alignment.RIGHT, p.alignment)

        p.setRtl(true)
        assertTrue(p.isRtl)
        assertEquals(Alignment.RIGHT, p.alignment)
        assertTrue(mainXml(doc).contains("<w:jc w:val=\"left\"/>"))

        p.setRtl(false)
        assertFalse(p.isRtl)
        assertEquals(Alignment.RIGHT, p.alignment)
        val saved = reopen(doc)
        assertFalse(saved.paragraphs[0].isRtl)
        assertEquals(Alignment.RIGHT, saved.paragraphs[0].alignment)
    }

    @Test
    fun bidiIsInsertedBeforeJustification() {
        val doc = docOf(para(run("x"), "<w:jc w:val=\"center\"/>"))
        doc.paragraphs[0].setRtl(true)
        assertTrue(mainXml(doc).contains("<w:pPr><w:bidi/><w:jc w:val=\"center\"/></w:pPr>"))
    }

    // ---- block operations ----

    @Test
    fun addParagraphAppendsAndSaves() {
        val doc = docOf(para(run("first")))
        doc.addParagraph("second")
        val saved = reopen(doc)
        assertEquals(listOf("first", "second"), saved.paragraphs.map { it.text })
    }

    @Test
    fun newArabicParagraphsAreRightToLeft() {
        val doc = docOf(para(run("first")))
        val p = doc.addParagraph("مرحبا بالعالم")
        assertTrue(p.isRtl)
        val saved = reopen(doc)
        assertTrue(saved.paragraphs[1].isRtl)
        assertEquals(true, runs(saved.paragraphs[1])[0].format.rtl)
        assertNull(saved.paragraphs[1].alignment)
    }

    @Test
    fun newParagraphsAcceptExplicitDirectionAlignmentAndFormat() {
        val doc = docOf(para(run("first")))
        doc.addParagraph("title", RunFormat(bold = true, fontSizePt = 20f), rtl = false, alignment = Alignment.CENTER)
        val saved = reopen(doc)
        val p = saved.paragraphs[1]
        assertFalse(p.isRtl)
        assertEquals(Alignment.CENTER, p.alignment)
        assertEquals(true, runs(p)[0].format.bold)
        assertEquals(20f, runs(p)[0].format.fontSizePt!!, 0.001f)
    }

    @Test
    fun insertAndRemoveBlocks() {
        val doc = docOf(para(run("a")) + para(run("c")))
        doc.insertBlock(1, doc.createParagraph("b"))
        assertEquals(listOf("a", "b", "c"), reopen(doc).paragraphs.map { it.text })
        doc.removeBlock(0)
        assertEquals(listOf("b", "c"), reopen(doc).paragraphs.map { it.text })
    }

    @Test
    fun blockOperationsRejectBadInput() {
        val doc = docOf(para(run("a")))
        assertThrows(IllegalArgumentException::class.java) { doc.removeBlock(5) }
        assertThrows(IllegalArgumentException::class.java) { doc.insertBlock(9, doc.createParagraph("x")) }
        assertThrows(IllegalArgumentException::class.java) { doc.insertBlock(0, doc.blocks[0]) }
    }

    @Test
    fun paragraphsFromAnotherDocumentAreRejected() {
        val a = docOf(para(run("a")))
        val b = docOf(para(run("b")))
        assertThrows(IllegalArgumentException::class.java) { a.insertBlock(0, b.paragraphs[0]) }
    }

    @Test
    fun removingABlockWithTablesKeepsTheOthers() {
        val table = "<w:tbl><w:tr><w:tc>" + para(run("cell")) + "</w:tc></w:tr></w:tbl>"
        val doc = docOf(para(run("a")) + table + para(run("b")))
        doc.removeBlock(0)
        val xml = mainXml(doc)
        assertTrue(xml.contains("<w:tbl>"))
        assertEquals(1, reopen(doc).paragraphs.size)
    }

    // ---- split and merge ----

    @Test
    fun splitParagraphCopiesProperties() {
        val doc = docOf(para(run("Hello World"), "<w:jc w:val=\"center\"/>"))
        val second = doc.splitParagraph(doc.paragraphs[0], 5)
        assertEquals("Hello", doc.paragraphs[0].text)
        assertEquals(" World", second.text)
        val saved = reopen(doc)
        assertEquals(listOf("Hello", " World"), saved.paragraphs.map { it.text })
        assertEquals(Alignment.CENTER, saved.paragraphs[1].alignment)
        assertEquals(Alignment.CENTER, saved.paragraphs[0].alignment)
    }

    @Test
    fun splitAtTheStartAndEnd() {
        val doc = docOf(para(run("abc")))
        doc.splitParagraph(doc.paragraphs[0], 3)
        doc.splitParagraph(doc.paragraphs[0], 0)
        assertEquals(listOf("", "abc", ""), doc.paragraphs.map { it.text })
    }

    @Test
    fun splitKeepsFormattingOnBothSides() {
        val doc = docOf(para(run("abcd", "<w:b/>")))
        doc.splitParagraph(doc.paragraphs[0], 2)
        val saved = reopen(doc)
        assertEquals(true, runs(saved.paragraphs[0])[0].format.bold)
        assertEquals(true, runs(saved.paragraphs[1])[0].format.bold)
    }

    @Test
    fun splitMovesReadOnlyContentToTheRightParagraph() {
        val doc = docOf(para(run("ab") + link + run("cd")))
        doc.splitParagraph(doc.paragraphs[0], 2)
        assertEquals("ab", doc.paragraphs[0].text)
        assertEquals("linkcd", doc.paragraphs[1].text)
    }

    @Test
    fun aSectionBreakStaysWithTheLastParagraphAfterASplit() {
        val sect = "<w:sectPr><w:pgSz w:w=\"1\" w:h=\"2\"/></w:sectPr>"
        val doc = docOf(para(run("abcd"), sect))
        doc.splitParagraph(doc.paragraphs[0], 2)
        val saved = reopen(doc)
        assertNull(saved.paragraphs[0].pPr?.child("sectPr"))
        assertNotNull(saved.paragraphs[1].pPr?.child("sectPr"))
    }

    @Test
    fun mergeJoinsTextAndMergesEqualRuns() {
        val doc = docOf(para(run("ab", "<w:b/>")) + para(run("cd", "<w:b/>")))
        assertTrue(doc.mergeWithNext(doc.paragraphs[0]))
        val saved = reopen(doc)
        assertEquals(1, saved.paragraphs.size)
        assertEquals("abcd", saved.paragraphs[0].text)
        assertEquals(1, saved.paragraphs[0].inlines.size)
    }

    @Test
    fun mergeKeepsTheFirstParagraphProperties() {
        val doc = docOf(
            para(run("a"), "<w:jc w:val=\"center\"/>") + para(run("b"), "<w:jc w:val=\"right\"/>"),
        )
        doc.mergeWithNext(doc.paragraphs[0])
        assertEquals(Alignment.CENTER, reopen(doc).paragraphs[0].alignment)
    }

    @Test
    fun mergeNeverJoinsATable() {
        val table = "<w:tbl><w:tr><w:tc>" + para(run("cell")) + "</w:tc></w:tr></w:tbl>"
        val doc = docOf(para(run("a")) + table)
        assertFalse(doc.mergeWithNext(doc.paragraphs[0]))
        assertEquals(2, doc.blocks.size)
    }

    @Test
    fun mergeWithNothingAfterReturnsFalse() {
        val doc = docOf(para(run("a")))
        assertFalse(doc.mergeWithNext(doc.paragraphs[0]))
    }

    @Test
    fun mergeKeepsTheSectionBreakOfTheLaterParagraph() {
        val sect = "<w:sectPr><w:pgSz w:w=\"1\" w:h=\"2\"/></w:sectPr>"
        val doc = docOf(para(run("a")) + para(run("b"), sect))
        doc.mergeWithNext(doc.paragraphs[0])
        assertNotNull(reopen(doc).paragraphs[0].pPr?.child("sectPr"))
    }

    // ---- unsaved changes ----

    @Test
    fun unsavedChangesAreTracked() {
        val doc = docOf(para(run("abc")))
        assertFalse(doc.hasUnsavedChanges)
        doc.paragraphs[0].insertText(0, "x")
        assertTrue(doc.hasUnsavedChanges)
        doc.toByteArray()
        assertFalse(doc.hasUnsavedChanges)
        doc.addParagraph("y")
        assertTrue(doc.hasUnsavedChanges)
        doc.toByteArray()
        assertFalse(doc.hasUnsavedChanges)
    }

    @Test
    fun savingTwiceGivesTheSameDocument() {
        val doc = docOf(para(run("abc")))
        doc.paragraphs[0].insertText(1, "X", RunFormat(bold = true))
        val first = mainXml(doc)
        val second = mainXml(doc)
        assertEquals(first, second)
        assertEquals(1, count(first, "<w:b/>"))
    }
}

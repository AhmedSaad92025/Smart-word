package com.smartword.docx.model

import com.smartword.docx.DocxAssertions
import com.smartword.docx.DocxAssertions.mainXml
import com.smartword.docx.pkg.DocxPackage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class DocxCreateTest {

    private val fixed = Instant.parse("2026-01-01T00:00:00Z")

    private fun runs(p: DocxParagraph): List<TextRun> = p.inlines.filterIsInstance<TextRun>()

    @Test
    fun newDocumentHasOneEmptyParagraph() {
        val doc = DocxDocument.create(now = fixed)
        assertEquals(1, doc.blocks.size)
        assertEquals("", doc.plainText)
        assertFalse(doc.paragraphs[0].isRtl)
        assertFalse(doc.hasUnsavedChanges)
    }

    @Test
    fun newDocumentIsAConsistentPackage() {
        DocxAssertions.assertPackageIntegrity(DocxDocument.create(now = fixed).toByteArray())
        DocxAssertions.assertPackageIntegrity(DocxDocument.create(rtl = true, now = fixed).toByteArray())
    }

    @Test
    fun contentTypesComeFirstAndAllPartsArePresent() {
        val pkg = DocxPackage.read(DocxDocument.create(now = fixed).toByteArray())
        assertEquals("[Content_Types].xml", pkg.parts[0].name)
        for (name in listOf("_rels/.rels", "word/document.xml", "word/styles.xml", "word/settings.xml", "docProps/core.xml", "docProps/app.xml")) {
            assertNotNull(name, pkg.find(name))
        }
    }

    @Test
    fun timestampsComeFromTheGivenClock() {
        val pkg = DocxPackage.read(DocxDocument.create(now = fixed).toByteArray())
        val core = String(pkg.find("docProps/core.xml")!!.bytes, Charsets.UTF_8)
        assertTrue(core.contains(">2026-01-01T00:00:00Z<"))
    }

    @Test
    fun typingIntoANewDocumentRoundTrips() {
        val doc = DocxDocument.create(now = fixed)
        val p = doc.paragraphs[0]
        p.insertText(0, "Hello World")
        p.applyFormat(0, 5, RunFormat(bold = true, fontSizePt = 18f, colorHex = "336699"))
        doc.addParagraph("مرحبا بالعالم")

        val bytes = doc.toByteArray()
        DocxAssertions.assertSchemaOrder(bytes)
        DocxAssertions.assertPackageIntegrity(bytes)

        val saved = DocxDocument.open(bytes)
        assertEquals(listOf("Hello World", "مرحبا بالعالم"), saved.paragraphs.map { it.text })
        val r = runs(saved.paragraphs[0])
        assertEquals(listOf("Hello", " World"), r.map { it.text })
        assertEquals(true, r[0].format.bold)
        assertEquals(18f, r[0].format.fontSizePt!!, 0.001f)
        assertEquals("336699", r[0].format.colorHex)
        assertTrue(saved.paragraphs[1].isRtl)
    }

    @Test
    fun rightToLeftDocumentStartsWithARightToLeftParagraphAndPage() {
        val doc = DocxDocument.create(rtl = true, now = fixed)
        assertTrue(doc.paragraphs[0].isRtl)
        doc.paragraphs[0].insertText(0, "نص عربي")
        val bytes = doc.toByteArray()
        val xml = mainXml(bytes)
        assertTrue(xml.contains("<w:bidi/>"))
        assertTrue(xml.contains("<w:rtl/>"))
        val saved = DocxDocument.open(bytes)
        assertTrue(saved.paragraphs[0].isRtl)
        assertEquals(true, runs(saved.paragraphs[0])[0].format.rtl)
        DocxAssertions.assertSchemaOrder(bytes)
    }

    @Test
    fun aLeftToRightDocumentHasNoDirectionMarkers() {
        val xml = mainXml(DocxDocument.create(now = fixed).toByteArray())
        assertFalse(xml.contains("<w:bidi"))
    }

    @Test
    fun newDocumentsAreIndependent() {
        val a = DocxDocument.create(now = fixed)
        val b = DocxDocument.create(now = fixed)
        a.addParagraph("only in a")
        assertEquals(2, a.blocks.size)
        assertEquals(1, b.blocks.size)
    }

    @Test
    fun anUntouchedNewDocumentCanBeSavedAndReopened() {
        val doc = DocxDocument.open(DocxDocument.create(now = fixed).toByteArray())
        assertEquals(1, doc.paragraphs.size)
        assertEquals("", doc.paragraphs[0].text)
    }

    @Test
    fun theSystemClockIsUsedByDefault() {
        val before = Instant.now().epochSecond
        val pkg = DocxPackage.read(DocxDocument.create().toByteArray())
        val core = String(pkg.find("docProps/core.xml")!!.bytes, Charsets.UTF_8)
        val stamp = Regex("<dcterms:created[^>]*>([^<]+)<").find(core)!!.groupValues[1]
        val seconds = Instant.parse(stamp).epochSecond
        assertTrue(seconds >= before - 1 && seconds <= before + 60)
    }
}

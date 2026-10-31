package com.smartword.docx.model

import com.smartword.docx.DocxAssertions
import com.smartword.docx.DocxAssertions.mainXml
import com.smartword.docx.DocxFixtures
import com.smartword.docx.DocxFixtures.para
import com.smartword.docx.DocxFixtures.run
import com.smartword.docx.pkg.DocxPackage
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class DocxRoundTripTest {

    private val untouched =
        "<w:p w:rsidR=\"00AB12CD\" w:rsidRDefault=\"00AB12CD\"><w:pPr><w:jc w:val=\"center\"/></w:pPr>" +
            "<w:r w:rsidR=\"00AB12CD\"><w:t>untouched</w:t></w:r></w:p>"
    private val table = "<w:tbl><w:tblPr/><w:tr><w:tc><w:p><w:r><w:t>cell</w:t></w:r></w:p></w:tc></w:tr></w:tbl>"
    private val link = "<w:hyperlink r:id=\"rId9\"><w:r><w:t>link</w:t></w:r></w:hyperlink>"
    private val drawing = "<w:r><w:drawing><wp:inline xmlns:wp=\"urn:wp\"/></w:drawing></w:r>"
    private val bookmarks = "<w:bookmarkStart w:id=\"0\" w:name=\"mark\"/><w:bookmarkEnd w:id=\"0\"/>"

    private fun complexBody() =
        para(run("Title", "<w:b/>")) +
            untouched +
            table +
            para(run("see ") + link + run(" and ") + drawing + bookmarks) +
            para(run("مرحبا بالعالم", "<w:rtl/>"), "<w:bidi/>")

    @Test
    fun editingOneParagraphLeavesEverythingElseIdentical() {
        val original = DocxFixtures.docx(complexBody())
        val doc = DocxDocument.open(original)
        doc.paragraphs[0].insertText(5, "!")
        val saved = doc.toByteArray()

        val xml = mainXml(saved)
        assertTrue(xml.contains(untouched))
        assertTrue(xml.contains(table))
        assertTrue(xml.contains(link))
        assertTrue(xml.contains(drawing))
        assertTrue(xml.contains(bookmarks))
        assertTrue(xml.contains("mc:Ignorable=\"w14\""))
        assertTrue(xml.contains(DocxFixtures.SECT_PR))
        assertEquals("Title!", DocxDocument.open(saved).paragraphs[0].text)
    }

    @Test
    fun editingNeverChangesOtherPartsOfThePackage() {
        val original = DocxFixtures.docx(complexBody())
        val doc = DocxDocument.open(original)
        doc.paragraphs[0].insertText(0, "x")
        doc.addParagraph("added")
        val before = DocxPackage.read(original)
        val after = DocxPackage.read(doc.toByteArray())

        assertEquals(before.parts.map { it.name }, after.parts.map { it.name })
        for (part in before.parts) {
            if (part.name == "word/document.xml") continue
            assertArrayEquals(part.name, part.bytes, after.find(part.name)!!.bytes)
        }
    }

    @Test
    fun readOnlyContentSurvivesEditsAroundIt() {
        val doc = DocxDocument.open(DocxFixtures.docx(complexBody()))
        val p = doc.paragraphs[2]
        assertEquals("see link and ", p.text)
        p.insertText(0, ">> ")
        p.deleteText(p.text.length - 4, p.text.length)
        p.applyFormat(0, 3, RunFormat(italic = true))
        val xml = mainXml(doc)
        assertTrue(xml.contains(link))
        assertTrue(xml.contains(drawing))
        assertTrue(xml.contains(bookmarks))
        assertEquals(1, DocxAssertions.count(xml, "r:id=\"rId9\""))
    }

    @Test
    fun repeatedSavingAndReopeningIsStable() {
        var bytes = DocxFixtures.docx(complexBody())
        var last = ""
        for (round in 1..4) {
            val doc = DocxDocument.open(bytes)
            doc.paragraphs[0].insertText(0, "$round")
            bytes = doc.toByteArray()
            last = DocxDocument.open(bytes).paragraphs[0].text
            DocxAssertions.assertSchemaOrder(bytes)
        }
        assertEquals("4321Title", last)
        assertTrue(mainXml(bytes).contains(untouched))
    }

    @Test
    fun savedDocumentIsWellFormedAndKeepsNamespaceDeclarations() {
        val doc = DocxDocument.open(DocxFixtures.docx(complexBody()))
        doc.paragraphs[0].applyFormat(0, 5, RunFormat(bold = true, colorHex = "112233"))
        val xml = mainXml(doc)
        assertTrue(xml.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"))
        assertTrue(xml.contains("xmlns:w=\"${com.smartword.docx.xml.Ns.W}\""))
        assertTrue(xml.contains("xmlns:r=\""))
        assertTrue(xml.contains("xmlns:w14=\""))
    }

    @Test
    fun documentsUsingADefaultNamespaceCanBeEdited() {
        val xml = DocxFixtures.DECL +
            "<document xmlns=\"${com.smartword.docx.xml.Ns.W}\"><body><p><r><t>hello</t></r></p></body></document>"
        val doc = DocxDocument.open(DocxFixtures.rawDocx(xml))
        assertEquals("hello", doc.paragraphs[0].text)
        doc.paragraphs[0].applyFormat(0, 5, RunFormat(bold = true))
        doc.addParagraph("two", alignment = Alignment.CENTER)
        val saved = DocxDocument.open(doc.toByteArray())
        assertEquals(listOf("hello", "two"), saved.paragraphs.map { it.text })
        assertEquals(true, (saved.paragraphs[0].inlines[0] as TextRun).format.bold)
        assertEquals(Alignment.CENTER, saved.paragraphs[1].alignment)
    }

    @Test
    fun documentsUsingAnotherPrefixCanBeEdited() {
        val ns = com.smartword.docx.xml.Ns.W
        val xml = DocxFixtures.DECL +
            "<x:document xmlns:x=\"$ns\"><x:body><x:p><x:r><x:t>hi</x:t></x:r></x:p></x:body></x:document>"
        val doc = DocxDocument.open(DocxFixtures.rawDocx(xml))
        doc.paragraphs[0].insertText(2, "!", RunFormat(bold = true))
        val out = mainXml(doc)
        assertTrue(out.contains("<x:b/>"))
        assertEquals("hi!", DocxDocument.open(doc.toByteArray()).paragraphs[0].text)
    }

    @Test
    fun randomEditsMatchASimpleReferenceModel() {
        for (seed in 1L..40L) runRandomEdits(seed)
    }

    private fun runRandomEdits(seed: Long) {
        val random = Random(seed)
        val doc = DocxDocument.open(DocxFixtures.docx(para(run("start", "<w:b/>")) + para(run("two"))))
        val p = doc.paragraphs[0]
        var text = "start"
        val bold = ArrayList<Boolean>()
        repeat(text.length) { bold.add(true) }

        val pieces = listOf("a", "bc", " ", "مرحبا", "x y", "\t", "\n", "😀", "Z")

        fun validBoundary(o: Int): Boolean =
            !(o in 1 until text.length &&
                Character.isHighSurrogate(text[o - 1]) && Character.isLowSurrogate(text[o]))

        repeat(60) { step ->
            val where = "seed=$seed step=$step"
            when (random.nextInt(3)) {
                0 -> {
                    val o = random.nextInt(text.length + 1)
                    if (validBoundary(o)) {
                        val piece = pieces[random.nextInt(pieces.size)]
                        // New text continues the formatting of the character before it.
                        val inherited = if (o > 0) bold[o - 1] else if (bold.isNotEmpty()) bold[0] else false
                        p.insertText(o, piece)
                        text = text.substring(0, o) + piece + text.substring(o)
                        repeat(piece.length) { bold.add(o, inherited) }
                    }
                }
                1 -> {
                    val a = random.nextInt(text.length + 1)
                    val b = a + random.nextInt(text.length - a + 1)
                    if (validBoundary(a) && validBoundary(b)) {
                        p.deleteText(a, b)
                        text = text.substring(0, a) + text.substring(b)
                        repeat(b - a) { bold.removeAt(a) }
                    }
                }
                else -> {
                    val a = random.nextInt(text.length + 1)
                    val b = a + random.nextInt(text.length - a + 1)
                    if (validBoundary(a) && validBoundary(b)) {
                        val on = random.nextBoolean()
                        p.applyFormat(a, b, RunFormat(bold = on))
                        for (k in a until b) bold[k] = on
                    }
                }
            }
            assertEquals(where, text, p.text)
            assertEquals(where, bold, boldFlags(p))
        }

        val bytes = doc.toByteArray()
        DocxAssertions.assertSchemaOrder(bytes)
        val saved = DocxDocument.open(bytes)
        assertEquals("seed=$seed saved text", text, saved.paragraphs[0].text)
        assertEquals("seed=$seed saved bold", bold, boldFlags(saved.paragraphs[0]))
        assertEquals("seed=$seed second paragraph", "two", saved.paragraphs[1].text)
    }

    private fun boldFlags(p: DocxParagraph): List<Boolean> {
        val flags = ArrayList<Boolean>()
        for (item in p.inlines) {
            val on = item is TextRun && item.format.bold == true
            repeat(item.visibleText.length) { flags.add(on) }
        }
        return flags
    }
}

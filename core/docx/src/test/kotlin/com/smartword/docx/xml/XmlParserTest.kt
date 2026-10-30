package com.smartword.docx.xml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class XmlParserTest {

    private val w = Ns.W

    @Test
    fun parsesElementsAttributesAndText() {
        val doc = XmlParser.parse("<root a=\"1\" b='two'><child>hello</child><empty/></root>")
        val root = doc.root
        assertEquals("root", root.name)
        assertEquals("1", root.attr("a"))
        assertEquals("two", root.attr("b"))
        assertEquals(2, root.elements().size)
        assertEquals("hello", root.elements()[0].textContent())
        assertTrue(root.elements()[1].children.isEmpty())
    }

    @Test
    fun resolvesNamespacesByUriNotPrefix() {
        val doc = XmlParser.parse("<x:document xmlns:x=\"$w\"><x:body/></x:document>")
        assertTrue(doc.root.isW("document"))
        assertTrue(doc.root.elements()[0].isW("body"))
        assertFalse(doc.root.isElement("urn:other", "document"))
    }

    @Test
    fun appliesDefaultNamespace() {
        val doc = XmlParser.parse("<document xmlns=\"$w\"><body/></document>")
        assertTrue(doc.root.isW("document"))
        assertTrue(doc.root.elements()[0].isW("body"))
    }

    @Test
    fun innerNamespaceDeclarationOverridesOuter() {
        val doc = XmlParser.parse("<a xmlns:p=\"urn:one\"><p:b/><c xmlns:p=\"urn:two\"><p:d/></c></a>")
        val b = doc.root.elements()[0]
        val d = doc.root.elements()[1].elements()[0]
        assertEquals("urn:one", b.namespaceUri)
        assertEquals("urn:two", d.namespaceUri)
    }

    @Test
    fun unboundPrefixHasEmptyNamespace() {
        val doc = XmlParser.parse("<q:a/>")
        assertEquals("", doc.root.namespaceUri)
        assertEquals("a", doc.root.localName)
    }

    @Test
    fun attrMatchesByLocalNameIgnoringPrefix() {
        val doc = XmlParser.parse("<w:b xmlns:w=\"$w\" w:val=\"0\"/>")
        assertEquals("0", doc.root.attr("val"))
        assertNull(doc.root.attr("missing"))
    }

    @Test
    fun decodesEntitiesAndCharacterReferences() {
        val doc = XmlParser.parse("<a>&lt;&gt;&amp;&quot;&apos;&#65;&#x627;</a>")
        assertEquals("<>&\"'Aا", doc.root.textContent())
    }

    @Test
    fun decodesEntitiesInAttributes() {
        val doc = XmlParser.parse("<a v=\"x&amp;y&#10;z\"/>")
        assertEquals("x&y\nz", doc.root.attr("v"))
    }

    @Test
    fun normalizesCarriageReturns() {
        val doc = XmlParser.parse("<a>x\r\ny\rz</a>")
        assertEquals("x\ny\nz", doc.root.textContent())
    }

    @Test
    fun preservesWhitespaceInText() {
        val doc = XmlParser.parse("<a>  two  spaces\n</a>")
        assertEquals("  two  spaces\n", doc.root.textContent())
    }

    @Test
    fun keepsCdataCommentsAndProcessingInstructions() {
        val src = "<a><!-- note --><![CDATA[1 < 2 & 3]]><?pi data?></a>"
        val doc = XmlParser.parse(src)
        val kids = doc.root.children
        assertEquals(3, kids.size)
        assertTrue(kids[0] is XmlRaw)
        assertTrue(kids[1] is XmlText && (kids[1] as XmlText).cdata)
        assertEquals("1 < 2 & 3", (kids[1] as XmlText).text)
        assertTrue(kids[2] is XmlRaw)
    }

    @Test
    fun skipsXmlDeclarationAndKeepsPrologComments() {
        val doc = XmlParser.parse("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<!-- hi -->\n<a/>")
        assertEquals("a", doc.root.name)
        assertEquals(1, doc.prolog.size)
    }

    @Test
    fun handlesUtf8ByteOrderMark() {
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val doc = XmlParser.parse(bom + "<a>x</a>".toByteArray(Charsets.UTF_8))
        assertEquals("x", doc.root.textContent())
    }

    @Test
    fun handlesUtf16() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "<a>ب</a>".toByteArray(Charsets.UTF_16LE)
        assertEquals("ب", XmlParser.parse(bytes).root.textContent())
    }

    @Test
    fun decodesArabicUtf8() {
        val doc = XmlParser.parse("<a>مرحبا بالعالم</a>".toByteArray(Charsets.UTF_8))
        assertEquals("مرحبا بالعالم", doc.root.textContent())
    }

    @Test
    fun rejectsDoctype() {
        assertThrows(XmlParseException::class.java) {
            XmlParser.parse("<!DOCTYPE a [<!ENTITY x \"y\">]><a>&x;</a>")
        }
    }

    @Test
    fun rejectsMismatchedTags() {
        assertThrows(XmlParseException::class.java) { XmlParser.parse("<a><b></a></b>") }
    }

    @Test
    fun rejectsUnclosedElement() {
        assertThrows(XmlParseException::class.java) { XmlParser.parse("<a><b>text</b>") }
    }

    @Test
    fun rejectsUnknownEntity() {
        assertThrows(XmlParseException::class.java) { XmlParser.parse("<a>&nbsp;</a>") }
    }

    @Test
    fun rejectsInvalidCharacterReference() {
        assertThrows(XmlParseException::class.java) { XmlParser.parse("<a>&#0;</a>") }
    }

    @Test
    fun rejectsDuplicateAttributes() {
        assertThrows(XmlParseException::class.java) { XmlParser.parse("<a x=\"1\" x=\"2\"/>") }
    }

    @Test
    fun rejectsUnquotedAttributes() {
        assertThrows(XmlParseException::class.java) { XmlParser.parse("<a x=1/>") }
    }

    @Test
    fun rejectsContentAfterRoot() {
        assertThrows(XmlParseException::class.java) { XmlParser.parse("<a/><b/>") }
    }

    @Test
    fun rejectsEmptyAndNonXmlInput() {
        assertThrows(XmlParseException::class.java) { XmlParser.parse("") }
        assertThrows(XmlParseException::class.java) { XmlParser.parse("just text") }
    }

    @Test
    fun rejectsExcessiveNesting() {
        val deep = "<a>".repeat(300) + "</a>".repeat(300)
        assertThrows(XmlParseException::class.java) { XmlParser.parse(deep) }
    }

    @Test
    fun acceptsNestingWithinLimit() {
        val ok = "<a>".repeat(200) + "</a>".repeat(200)
        assertEquals("a", XmlParser.parse(ok).root.name)
    }
}

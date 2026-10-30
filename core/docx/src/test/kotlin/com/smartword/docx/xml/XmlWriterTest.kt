package com.smartword.docx.xml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XmlWriterTest {

    private fun body(xml: String): String {
        val out = XmlWriter.writeToString(XmlParser.parse(xml))
        assertTrue(out.startsWith(XmlWriter.DECLARATION))
        return out.removePrefix(XmlWriter.DECLARATION)
    }

    @Test
    fun writesDeclarationAndSelfClosingElements() {
        assertEquals("<a><b/></a>", body("<a><b></b></a>"))
    }

    @Test
    fun escapesTextAndAttributes() {
        val root = XmlElement("a")
        root.attributes.add(XmlAttribute("v", "q\"<&>\n\t"))
        root.children.add(XmlText("1 < 2 & 3 > 0"))
        val out = XmlWriter.writeElementToString(root)
        assertEquals("<a v=\"q&quot;&lt;&amp;&gt;&#10;&#9;\">1 &lt; 2 &amp; 3 &gt; 0</a>", out)
    }

    @Test
    fun dropsCharactersThatAreInvalidInXml() {
        val root = XmlElement("a")
        root.children.add(XmlText("a\u0000b\u0001c￿d"))
        assertEquals("<a>abcd</a>", XmlWriter.writeElementToString(root))
    }

    @Test
    fun dropsLoneSurrogatesButKeepsPairs() {
        val root = XmlElement("a")
        root.children.add(XmlText("x\uD83D" + "y" + "😀" + "\uDE00z"))
        assertEquals("<a>xy😀z</a>", XmlWriter.writeElementToString(root))
    }

    @Test
    fun roundTripKeepsPrefixesAttributeOrderAndNamespaceDeclarations() {
        val src = "<w:document xmlns:w=\"${Ns.W}\" xmlns:r=\"${Ns.R}\" w:b=\"1\" r:a=\"2\"><w:body/></w:document>"
        assertEquals(src, body(src))
    }

    @Test
    fun roundTripKeepsWhitespaceCdataAndComments() {
        val src = "<a> x <!-- c --><![CDATA[<raw>]]> y </a>"
        assertEquals(src, body(src))
    }

    @Test
    fun serializationIsStable() {
        val src = "<a xmlns:w=\"${Ns.W}\"><w:p w:rsidR=\"00A1\"><w:r><w:t xml:space=\"preserve\"> مرحبا &amp; hi </w:t></w:r></w:p></a>"
        val once = XmlWriter.writeToString(XmlParser.parse(src))
        val twice = XmlWriter.writeToString(XmlParser.parse(once))
        assertEquals(once, twice)
    }

    @Test
    fun writesBytesAsUtf8() {
        val doc = XmlParser.parse("<a>ع</a>")
        val bytes = XmlWriter.write(doc)
        val text = String(bytes, Charsets.UTF_8)
        assertTrue(text.endsWith("<a>ع</a>"))
    }

    @Test
    fun deepCopyIsIndependent() {
        val doc = XmlParser.parse("<a x=\"1\"><b>t</b></a>")
        val copy = doc.root.deepCopy()
        copy.setAttr("x", "2")
        (copy.elements()[0].children[0] as XmlText).text = "changed"
        assertEquals("1", doc.root.attr("x"))
        assertEquals("t", doc.root.elements()[0].textContent())
        assertEquals("2", copy.attr("x"))
    }
}

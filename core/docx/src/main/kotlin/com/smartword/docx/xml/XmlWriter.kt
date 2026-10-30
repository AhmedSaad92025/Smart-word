package com.smartword.docx.xml

/** Serializes an [XmlDocument] as UTF-8 with a standalone XML declaration. */
object XmlWriter {
    const val DECLARATION = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\r\n"

    fun write(doc: XmlDocument): ByteArray = writeToString(doc).toByteArray(Charsets.UTF_8)

    fun writeToString(doc: XmlDocument): String {
        val sb = StringBuilder()
        sb.append(DECLARATION)
        for (p in doc.prolog) sb.append(p.raw).append('\n')
        writeElement(sb, doc.root)
        for (p in doc.epilog) sb.append('\n').append(p.raw)
        return sb.toString()
    }

    fun writeElementToString(element: XmlElement): String {
        val sb = StringBuilder()
        writeElement(sb, element)
        return sb.toString()
    }

    private fun writeElement(sb: StringBuilder, el: XmlElement) {
        sb.append('<').append(el.name)
        for (a in el.attributes) {
            sb.append(' ').append(a.name).append("=\"")
            escape(sb, a.value, true)
            sb.append('"')
        }
        if (el.children.isEmpty()) {
            sb.append("/>")
            return
        }
        sb.append('>')
        for (c in el.children) {
            when (c) {
                is XmlText -> {
                    if (c.cdata) {
                        sb.append("<![CDATA[").append(c.text).append("]]>")
                    } else {
                        escape(sb, c.text, false)
                    }
                }
                is XmlRaw -> sb.append(c.raw)
                is XmlElement -> writeElement(sb, c)
            }
        }
        sb.append("</").append(el.name).append('>')
    }

    private fun escape(sb: StringBuilder, text: String, attribute: Boolean) {
        var k = 0
        while (k < text.length) {
            val c = text[k]
            when {
                c == '&' -> sb.append("&amp;")
                c == '<' -> sb.append("&lt;")
                c == '>' -> sb.append("&gt;")
                attribute && c == '"' -> sb.append("&quot;")
                attribute && c == '\t' -> sb.append("&#9;")
                attribute && c == '\n' -> sb.append("&#10;")
                attribute && c == '\r' -> sb.append("&#13;")
                Character.isHighSurrogate(c) -> {
                    if (k + 1 < text.length && Character.isLowSurrogate(text[k + 1])) {
                        sb.append(c).append(text[k + 1])
                        k++
                    }
                    // A lone surrogate is not valid XML and is dropped.
                }
                Character.isLowSurrogate(c) -> Unit
                c < ' ' && c != '\t' && c != '\n' && c != '\r' -> Unit
                c == '￾' || c == '￿' -> Unit
                else -> sb.append(c)
            }
            k++
        }
    }
}

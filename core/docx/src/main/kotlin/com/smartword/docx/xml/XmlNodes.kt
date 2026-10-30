package com.smartword.docx.xml

/**
 * Minimal XML tree used by the DOCX engine.
 *
 * The tree keeps qualified names exactly as written (prefixes, attribute order,
 * namespace declarations as ordinary attributes) so that elements the editor
 * does not understand survive a read/write cycle unchanged.
 */
sealed class XmlNode

/** Character data. [cdata] remembers that the text was written as a CDATA section. */
class XmlText(var text: String, val cdata: Boolean = false) : XmlNode()

/** A comment or processing instruction, kept verbatim including its delimiters. */
class XmlRaw(val raw: String) : XmlNode()

class XmlAttribute(var name: String, var value: String) {
    val localName: String get() = name.substringAfter(':')
}

class XmlElement(var name: String, var namespaceUri: String = "") : XmlNode() {
    val attributes: MutableList<XmlAttribute> = ArrayList()
    val children: MutableList<XmlNode> = ArrayList()

    val localName: String get() = name.substringAfter(':')
    val prefix: String get() = if (name.contains(':')) name.substringBefore(':') else ""

    fun isElement(ns: String, local: String): Boolean = namespaceUri == ns && localName == local

    /** True if this is the WordprocessingML element with the given local name. */
    fun isW(local: String): Boolean = isElement(Ns.W, local)

    fun elements(): List<XmlElement> = children.filterIsInstance<XmlElement>()

    fun child(local: String, ns: String = Ns.W): XmlElement? {
        for (c in children) {
            if (c is XmlElement && c.isElement(ns, local)) return c
        }
        return null
    }

    /** Value of the attribute whose local name is [local] (any prefix), or null. */
    fun attr(local: String): String? {
        for (a in attributes) {
            if (a.name == local || a.localName == local) return a.value
        }
        return null
    }

    /** Sets an attribute matched by local name, or appends [qualifiedName] if absent. */
    fun setAttr(qualifiedName: String, value: String) {
        val local = qualifiedName.substringAfter(':')
        for (a in attributes) {
            if (a.localName == local) {
                a.value = value
                return
            }
        }
        attributes.add(XmlAttribute(qualifiedName, value))
    }

    fun removeAttr(local: String) {
        attributes.removeAll { it.name == local || it.localName == local }
    }

    /** Concatenated text of this element and all descendants. */
    fun textContent(): String {
        val sb = StringBuilder()
        appendText(this, sb)
        return sb.toString()
    }

    fun deepCopy(): XmlElement {
        val copy = XmlElement(name, namespaceUri)
        for (a in attributes) copy.attributes.add(XmlAttribute(a.name, a.value))
        for (c in children) {
            when (c) {
                is XmlElement -> copy.children.add(c.deepCopy())
                is XmlText -> copy.children.add(XmlText(c.text, c.cdata))
                is XmlRaw -> copy.children.add(c)
            }
        }
        return copy
    }

    private fun appendText(el: XmlElement, sb: StringBuilder) {
        for (c in el.children) {
            when (c) {
                is XmlText -> sb.append(c.text)
                is XmlElement -> appendText(c, sb)
                is XmlRaw -> Unit
            }
        }
    }
}

class XmlDocument(var root: XmlElement) {
    /** Comments and processing instructions before the root element. */
    val prolog: MutableList<XmlRaw> = ArrayList()

    /** Comments and processing instructions after the root element. */
    val epilog: MutableList<XmlRaw> = ArrayList()
}

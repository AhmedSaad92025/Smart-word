package com.smartword.docx.model

import com.smartword.docx.xml.XmlElement
import com.smartword.docx.xml.XmlText

/** Converts WordprocessingML elements into the editable model. */
internal object DocxReader {

    fun readBlock(el: XmlElement): DocxBlock? {
        if (el.isW("sectPr")) return null
        if (el.isW("p")) return readParagraph(el)
        val kind = when {
            el.isW("tbl") -> OpaqueKind.TABLE
            el.isW("sdt") -> OpaqueKind.STRUCTURED_TAG
            else -> OpaqueKind.OTHER
        }
        return OpaqueBlock(el, kind, blockPreview(el))
    }

    fun readParagraph(p: XmlElement): DocxParagraph {
        var pPr: XmlElement? = null
        val inlines = ArrayList<DocxInline>()
        for (child in p.children) {
            if (child !is XmlElement) continue
            when {
                child.isW("pPr") -> pPr = child
                child.isW("r") -> inlines.add(readRun(child))
                else -> inlines.add(OpaqueInline(child, inlinePreview(child)))
            }
        }
        return DocxParagraph(p, pPr, inlines)
    }

    /**
     * A run made only of properties, text, tabs and plain line breaks is editable.
     * Anything else (drawings, fields, page breaks, footnote references, ...) is opaque.
     */
    private fun readRun(r: XmlElement): DocxInline {
        var rPr: XmlElement? = null
        val sb = StringBuilder()
        var editable = true
        for (c in r.children) {
            if (c is XmlText) {
                if (c.text.isNotBlank()) editable = false
                continue
            }
            if (c !is XmlElement) continue
            when {
                c.isW("rPr") -> rPr = c
                c.isW("t") -> sb.append(c.textContent())
                c.isW("tab") -> sb.append('\t')
                c.isW("cr") -> sb.append('\n')
                c.isW("br") -> {
                    val type = c.attr("type")
                    if (type == null || type == "textWrapping") sb.append('\n') else editable = false
                }
                c.isW("noBreakHyphen") -> sb.append('‑')
                c.isW("softHyphen") -> sb.append('­')
                c.isW("lastRenderedPageBreak") -> Unit
                else -> editable = false
            }
        }
        val text = sb.toString()
        if (!editable) return OpaqueInline(r, text)
        return TextRun(text, WordProps.readRunFormat(rPr, text), rPr, r)
    }

    /** Visible text of an inline element such as a hyperlink or tracked insertion. */
    private fun inlinePreview(el: XmlElement): String {
        val sb = StringBuilder()
        collectInlineText(el, sb)
        return sb.toString()
    }

    private fun collectInlineText(el: XmlElement, sb: StringBuilder) {
        for (c in el.children) {
            if (c !is XmlElement) continue
            when {
                c.isW("t") -> sb.append(c.textContent())
                c.isW("tab") -> sb.append('\t')
                c.isW("delText") || c.isW("instrText") -> Unit
                else -> collectInlineText(c, sb)
            }
        }
    }

    /** Text of all paragraphs inside a block-level element, one per line. */
    private fun blockPreview(el: XmlElement): String {
        val lines = ArrayList<String>()
        collectParagraphs(el, lines)
        return lines.joinToString("\n")
    }

    private fun collectParagraphs(el: XmlElement, lines: MutableList<String>) {
        for (c in el.children) {
            if (c !is XmlElement) continue
            if (c.isW("p")) {
                lines.add(inlinePreview(c))
            } else {
                collectParagraphs(c, lines)
            }
        }
    }
}

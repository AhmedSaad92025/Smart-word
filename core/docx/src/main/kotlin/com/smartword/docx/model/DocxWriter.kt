package com.smartword.docx.model

import com.smartword.docx.xml.Ns
import com.smartword.docx.xml.XmlAttribute
import com.smartword.docx.xml.XmlElement
import com.smartword.docx.xml.XmlText

/** Turns model objects back into WordprocessingML elements. */
internal object DocxWriter {

    /** Returns the original element for an unchanged paragraph, otherwise rebuilds it from the model. */
    fun paragraphElement(p: DocxParagraph): XmlElement {
        val src = p.source
        if (!p.dirty && src != null) return src

        val el = XmlElement(src?.name ?: p.ctx.qn("p"), Ns.W)
        if (src != null) {
            for (a in src.attributes) el.attributes.add(XmlAttribute(a.name, a.value))
        }
        val pPr = p.pPr
        if (pPr != null && pPr.elements().isNotEmpty()) el.children.add(pPr)
        for (item in p.inlines) {
            when (item) {
                is TextRun -> el.children.add(runElement(item))
                is OpaqueInline -> el.children.add(item.element)
            }
        }
        p.source = el
        p.dirty = false
        return el
    }

    private fun runElement(run: TextRun): XmlElement {
        val cached = run.source
        if (cached != null) return cached

        val ctx = run.ctx
        val r = ctx.el("r")
        val rPr = run.rPr
        if (rPr != null && rPr.elements().isNotEmpty()) r.children.add(rPr)

        val buffer = StringBuilder()
        for (c in run.text) {
            when (c) {
                '\n' -> {
                    flushText(r, buffer, ctx)
                    r.children.add(ctx.el("br"))
                }
                '\t' -> {
                    flushText(r, buffer, ctx)
                    r.children.add(ctx.el("tab"))
                }
                else -> buffer.append(c)
            }
        }
        flushText(r, buffer, ctx)
        run.source = r
        return r
    }

    private fun flushText(r: XmlElement, buffer: StringBuilder, ctx: WContext) {
        if (buffer.isEmpty()) return
        val t = ctx.el("t")
        t.attributes.add(XmlAttribute("xml:space", "preserve"))
        t.children.add(XmlText(buffer.toString()))
        r.children.add(t)
        buffer.setLength(0)
    }
}

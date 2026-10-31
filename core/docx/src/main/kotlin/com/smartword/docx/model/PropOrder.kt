package com.smartword.docx.model

import com.smartword.docx.xml.Ns
import com.smartword.docx.xml.XmlElement

/**
 * Child-element order required by the WordprocessingML schema for property containers.
 * Word is strict about this order, so new property elements are always inserted at
 * their schema position rather than appended.
 */
internal class PropOrder(private val names: List<String>, trailing: String) {

    private val trailingRank = names.indexOf(trailing) * 2

    private fun rank(el: XmlElement): Int {
        if (el.namespaceUri == Ns.W) {
            val idx = names.indexOf(el.localName)
            if (idx >= 0) return idx * 2
        }
        // Extension elements from other namespaces sit just before the change-tracking element.
        return trailingRank - 1
    }

    /** Inserts [child] at its schema position among the children of [parent]. */
    fun insert(parent: XmlElement, child: XmlElement) {
        val r = rank(child)
        var at = parent.children.size
        for (i in parent.children.indices) {
            val c = parent.children[i]
            if (c is XmlElement && rank(c) > r) {
                at = i
                break
            }
        }
        parent.children.add(at, child)
    }

    /** Returns the child with the given local name, creating it at the right position if absent. */
    fun getOrCreate(parent: XmlElement, local: String, ctx: WContext): XmlElement {
        val existing = parent.child(local)
        if (existing != null) return existing
        val created = ctx.el(local)
        insert(parent, created)
        return created
    }

    companion object {
        val RPR = PropOrder(
            listOf(
                "rStyle", "rFonts", "b", "bCs", "i", "iCs", "caps", "smallCaps", "strike",
                "dstrike", "outline", "shadow", "emboss", "imprint", "noProof", "snapToGrid",
                "vanish", "webHidden", "color", "spacing", "w", "kern", "position", "sz",
                "szCs", "highlight", "u", "effect", "bdr", "shd", "fitText", "vertAlign",
                "rtl", "cs", "em", "lang", "eastAsianLayout", "specVanish", "oMath", "rPrChange",
            ),
            "rPrChange",
        )

        val PPR = PropOrder(
            listOf(
                "pStyle", "keepNext", "keepLines", "pageBreakBefore", "framePr", "widowControl",
                "numPr", "suppressLineNumbers", "pBdr", "shd", "tabs", "suppressAutoHyphens",
                "kinsoku", "wordWrap", "overflowPunct", "topLinePunct", "autoSpaceDE",
                "autoSpaceDN", "bidi", "adjustRightInd", "snapToGrid", "spacing", "ind",
                "contextualSpacing", "mirrorIndents", "suppressOverlap", "jc", "textDirection",
                "textAlignment", "textboxTightWrap", "outlineLvl", "divId", "cnfStyle", "rPr",
                "sectPr", "pPrChange",
            ),
            "pPrChange",
        )
    }
}

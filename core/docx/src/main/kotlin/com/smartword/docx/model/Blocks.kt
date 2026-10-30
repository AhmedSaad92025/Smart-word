package com.smartword.docx.model

import com.smartword.docx.xml.XmlElement

/** A top-level item of the document body, in document order. */
sealed class DocxBlock

/** A paragraph made of editable text runs and untouched opaque items. */
class DocxParagraph internal constructor(
    internal var source: XmlElement?,
    internal var pPr: XmlElement?,
    inlines: List<DocxInline>,
) : DocxBlock() {
    private val inlineList = ArrayList<DocxInline>(inlines)

    val inlines: List<DocxInline> get() = inlineList

    /** All visible text of the paragraph, including read-only parts such as hyperlink text. */
    val text: String
        get() {
            val sb = StringBuilder()
            for (item in inlineList) sb.append(item.visibleText)
            return sb.toString()
        }

    /** True if the paragraph direction is right-to-left (w:bidi). */
    val isRtl: Boolean
        get() = WordProps.onOff(pPr?.child("bidi")) == true

    /**
     * Visual alignment, or null when the paragraph does not set one (then it starts at
     * the leading edge: left for LTR paragraphs, right for RTL paragraphs).
     */
    val alignment: Alignment?
        get() = WordProps.alignmentOf(pPr?.child("jc")?.attr("val"), isRtl)
}

/** What kind of block-level content an [OpaqueBlock] holds. */
enum class OpaqueKind { TABLE, STRUCTURED_TAG, OTHER }

/** Block-level content the editor keeps but does not edit (tables, content controls, ...). */
class OpaqueBlock internal constructor(
    internal val element: XmlElement,
    val kind: OpaqueKind,
    val previewText: String,
) : DocxBlock()

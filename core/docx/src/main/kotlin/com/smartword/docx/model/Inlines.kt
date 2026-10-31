package com.smartword.docx.model

import com.smartword.docx.xml.XmlElement

/** An item inside a paragraph, in document order. */
sealed class DocxInline {
    /** Text this item contributes to the paragraph text. */
    abstract val visibleText: String
}

/**
 * Editable text with uniform formatting. Tabs and line breaks inside the run are
 * represented as '\t' and '\n' characters.
 *
 * Instances are changed only through [DocxParagraph] operations.
 */
class TextRun internal constructor(
    internal val ctx: WContext,
    text: String,
    format: RunFormat,
    internal var rPr: XmlElement?,
    /** The original w:r element while the run is unchanged; null once it must be rebuilt. */
    internal var source: XmlElement?,
) : DocxInline() {
    var text: String = text
        private set

    var format: RunFormat = format
        private set

    override val visibleText: String get() = text

    internal fun setContent(newText: String) {
        text = newText
        source = null
        format = WordProps.readRunFormat(rPr, text)
    }

    internal fun applyPatch(patch: RunFormat) {
        val props = rPr ?: ctx.el("rPr")
        PropsWriter.applyRun(props, patch, ctx)
        rPr = props
        source = null
        format = WordProps.readRunFormat(props, text)
    }
}

/**
 * Content the editor does not understand (hyperlinks, fields, images, page breaks,
 * bookmarks, tracked changes, ...). It is kept untouched and written back as-is.
 * [previewText] is what a reader sees of it; it cannot be edited.
 */
class OpaqueInline internal constructor(
    internal val element: XmlElement,
    val previewText: String,
) : DocxInline() {
    override val visibleText: String get() = previewText
}

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
 */
class TextRun internal constructor(
    text: String,
    format: RunFormat,
    internal var rPr: XmlElement?,
    internal var source: XmlElement?,
) : DocxInline() {
    var text: String = text
        internal set

    var format: RunFormat = format
        internal set

    override val visibleText: String get() = text
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

package com.smartword.app.preview

import com.smartword.docx.model.Alignment

/** A piece of text with one look. */
data class PreviewSpan(
    val text: String,
    val bold: Boolean,
    val italic: Boolean,
    val underline: Boolean,
    val sizePt: Float,
    val colorHex: String?,
    val fontName: String?,
)

sealed class PreviewBlock

data class PreviewParagraph(
    val spans: List<PreviewSpan>,
    val alignment: Alignment,
    val rtl: Boolean,
    /** 1..9 for headings, otherwise null. */
    val headingLevel: Int?,
    /** The bullet or number the paragraph starts with, or null when it is not a list item. */
    val listLabel: String?,
    val indentStartPt: Float,
    val indentEndPt: Float,
    val spaceBeforePt: Float,
    val spaceAfterPt: Float,
) : PreviewBlock() {
    val isEmpty: Boolean get() = spans.all { it.text.isEmpty() }
}

/** Content the editor keeps but does not show in detail (tables, content controls). */
data class PreviewPlaceholder(val text: String) : PreviewBlock()

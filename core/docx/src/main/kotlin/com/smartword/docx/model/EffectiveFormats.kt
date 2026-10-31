package com.smartword.docx.model

enum class LineRule {
    /** [LineSpacing.value] is a multiple of the single line height (1.0 = single, 1.5, 2.0). */
    AUTO,

    /** [LineSpacing.value] is an exact line height in points. */
    EXACT,

    /** [LineSpacing.value] is a minimum line height in points. */
    AT_LEAST,
}

data class LineSpacing(val value: Float, val rule: LineRule) {
    companion object {
        val SINGLE = LineSpacing(1f, LineRule.AUTO)
    }
}

/**
 * Character formatting as the reader sees it: direct formatting combined with character
 * and paragraph styles and the document defaults. Every value is concrete.
 */
data class EffectiveRunFormat(
    val bold: Boolean,
    val italic: Boolean,
    val underline: Boolean,
    val fontSizePt: Float,
    /** The font that applies to this text, or null when the file names none. */
    val fontName: String?,
    /** Text color as uppercase RRGGBB, or null for automatic. */
    val colorHex: String?,
    val rtl: Boolean,
)

/**
 * Paragraph formatting as the reader sees it. Indents are measured from the leading edge
 * (the left for left-to-right paragraphs, the right for right-to-left ones).
 */
data class ParagraphFormat(
    val alignment: Alignment,
    val isRtl: Boolean,
    val spaceBeforePt: Float,
    val spaceAfterPt: Float,
    val lineSpacing: LineSpacing,
    val indentStartPt: Float,
    val indentEndPt: Float,
    /** First-line indent in points; negative for a hanging indent. */
    val firstLineIndentPt: Float,
    /** Outline level 1..9 when the paragraph is part of the document outline, otherwise null. */
    val outlineLevel: Int?,
    /** 1..9 for headings (by outline level or the built-in "heading N" style names), otherwise null. */
    val headingLevel: Int?,
    /** The paragraph's own style id as written in the file, or null if it names none. */
    val styleId: String?,
    /** Name of the style that applies (the default style's name when the paragraph names none). */
    val styleName: String?,
    /** The list definition this paragraph belongs to, or null when it is not a list item. */
    val listId: Int?,
    val listLevel: Int?,
)

/** The marker a list item shows. [text] is the bullet character or the formatted number, such as "2." or "ب-". */
class ListLabel(val numId: Int, val level: Int, val text: String, val isBullet: Boolean)

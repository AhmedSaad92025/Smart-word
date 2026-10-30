package com.smartword.docx.model

/** Visual paragraph alignment (what the reader sees, independent of text direction). */
enum class Alignment { LEFT, CENTER, RIGHT, JUSTIFY }

/**
 * Direct character formatting of a run. A null field means "not specified here",
 * i.e. the value is inherited from styles. When used as a patch, null means "leave as is".
 */
data class RunFormat(
    val bold: Boolean? = null,
    val italic: Boolean? = null,
    val underline: Boolean? = null,
    /** Font size in points; half-point sizes such as 10.5 are supported. */
    val fontSizePt: Float? = null,
    val fontName: String? = null,
    /** Text color as uppercase RRGGBB, or null for automatic/inherited. */
    val colorHex: String? = null,
    /** Run is marked as right-to-left text (w:rtl). */
    val rtl: Boolean? = null,
) {
    /** Returns this format with every non-null field of [patch] applied on top. */
    fun overlay(patch: RunFormat): RunFormat = RunFormat(
        bold = patch.bold ?: bold,
        italic = patch.italic ?: italic,
        underline = patch.underline ?: underline,
        fontSizePt = patch.fontSizePt ?: fontSizePt,
        fontName = patch.fontName ?: fontName,
        colorHex = patch.colorHex ?: colorHex,
        rtl = patch.rtl ?: rtl,
    )

    val isEmpty: Boolean
        get() = this == NONE

    companion object {
        val NONE = RunFormat()
    }
}

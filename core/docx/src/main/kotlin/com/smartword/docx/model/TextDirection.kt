package com.smartword.docx.model

/** Small helpers for deciding whether text needs complex-script (RTL) handling. */
object TextDirection {

    /** True for characters of right-to-left scripts: Hebrew, Arabic, Syriac, Thaana and presentation forms. */
    fun isRtlChar(c: Char): Boolean {
        val code = c.code
        return (code in 0x0590..0x08FF) ||
            (code in 0xFB1D..0xFDFF) ||
            (code in 0xFE70..0xFEFF)
    }

    fun containsRtl(text: String): Boolean {
        for (c in text) if (isRtlChar(c)) return true
        return false
    }

    /** True if the first strongly directional letter of [text] is a right-to-left one. */
    fun startsWithRtl(text: String): Boolean {
        for (c in text) {
            if (isRtlChar(c)) return true
            if (c.isLetter()) return false
        }
        return false
    }
}

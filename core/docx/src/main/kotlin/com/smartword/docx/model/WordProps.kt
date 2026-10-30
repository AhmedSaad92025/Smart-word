package com.smartword.docx.model

import com.smartword.docx.xml.XmlElement

/** Reading helpers for WordprocessingML property elements. */
internal object WordProps {

    /** OOXML on/off element: absent means null, present without a value or with a true value means true. */
    fun onOff(el: XmlElement?): Boolean? {
        if (el == null) return null
        return when (el.attr("val")?.lowercase()) {
            "0", "false", "off" -> false
            else -> true
        }
    }

    fun toggle(parent: XmlElement, name: String): Boolean? = onOff(parent.child(name))

    /** Value of a size element in half-points converted to points, or null. */
    fun sizePt(parent: XmlElement, name: String): Float? {
        val half = parent.child(name)?.attr("val")?.toIntOrNull() ?: return null
        return half / 2f
    }

    /**
     * Converts a w:jc value to a visual alignment. In right-to-left paragraphs Word
     * interprets "left" and "right" relative to the paragraph direction (start and
     * end), so they are mirrored.
     */
    fun alignmentOf(jc: String?, rtl: Boolean): Alignment? = when (jc) {
        "left", "start" -> if (rtl) Alignment.RIGHT else Alignment.LEFT
        "right", "end" -> if (rtl) Alignment.LEFT else Alignment.RIGHT
        "center" -> Alignment.CENTER
        "both", "distribute", "thaiDistribute", "lowKashida", "mediumKashida", "highKashida" -> Alignment.JUSTIFY
        else -> null
    }

    /** Reads the direct character formatting stored in a w:rPr element. */
    fun readRunFormat(rPr: XmlElement?, text: String): RunFormat {
        if (rPr == null) return RunFormat.NONE
        val rtl = toggle(rPr, "rtl")
        // Runs of complex scripts (Arabic, Hebrew, ...) use the *Cs variants of the properties.
        val complex = rtl == true || TextDirection.containsRtl(text)

        val bold = if (complex) toggle(rPr, "bCs") ?: toggle(rPr, "b") else toggle(rPr, "b")
        val italic = if (complex) toggle(rPr, "iCs") ?: toggle(rPr, "i") else toggle(rPr, "i")
        val size = if (complex) sizePt(rPr, "szCs") ?: sizePt(rPr, "sz") else sizePt(rPr, "sz")

        val u = rPr.child("u")
        val underline: Boolean? = if (u == null) null else (u.attr("val") != "none")

        val fonts = rPr.child("rFonts")
        val fontName = if (fonts == null) {
            null
        } else {
            fonts.attr("ascii") ?: fonts.attr("hAnsi") ?: fonts.attr("cs") ?: fonts.attr("eastAsia")
        }

        val colorValue = rPr.child("color")?.attr("val")
        val color = if (colorValue == null || colorValue.equals("auto", ignoreCase = true)) {
            null
        } else {
            colorValue.uppercase()
        }

        return RunFormat(
            bold = bold,
            italic = italic,
            underline = underline,
            fontSizePt = size,
            fontName = fontName,
            colorHex = color,
            rtl = rtl,
        )
    }
}

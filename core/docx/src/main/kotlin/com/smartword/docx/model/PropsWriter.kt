package com.smartword.docx.model

import com.smartword.docx.xml.XmlElement
import kotlin.math.roundToInt

/** Writes formatting into WordprocessingML property elements (w:rPr and w:pPr). */
internal object PropsWriter {
    private val HEX_COLOR = Regex("^[0-9A-Fa-f]{6}$")
    private val THEME_FONT_ATTRS = listOf("asciiTheme", "hAnsiTheme", "cstheme", "eastAsiaTheme")
    private val THEME_COLOR_ATTRS = listOf("themeColor", "themeTint", "themeShade")

    /** Applies every non-null field of [patch] to [rPr]. */
    fun applyRun(rPr: XmlElement, patch: RunFormat, ctx: WContext) {
        val order = PropOrder.RPR

        patch.bold?.let {
            setToggle(rPr, "b", it, order, ctx)
            setToggle(rPr, "bCs", it, order, ctx)
        }
        patch.italic?.let {
            setToggle(rPr, "i", it, order, ctx)
            setToggle(rPr, "iCs", it, order, ctx)
        }
        patch.underline?.let {
            val u = order.getOrCreate(rPr, "u", ctx)
            u.setAttr(ctx.qn("val"), if (it) "single" else "none")
        }
        patch.fontSizePt?.let {
            require(it > 0f) { "Font size must be positive" }
            val half = (it * 2f).roundToInt().coerceIn(2, 3276).toString()
            order.getOrCreate(rPr, "sz", ctx).setAttr(ctx.qn("val"), half)
            order.getOrCreate(rPr, "szCs", ctx).setAttr(ctx.qn("val"), half)
        }
        patch.fontName?.let { name ->
            require(name.isNotBlank()) { "Font name must not be blank" }
            val fonts = order.getOrCreate(rPr, "rFonts", ctx)
            // Theme attributes take precedence over explicit names, so they must go.
            for (attr in THEME_FONT_ATTRS) fonts.removeAttr(attr)
            for (attr in listOf("ascii", "hAnsi", "cs", "eastAsia")) fonts.setAttr(ctx.qn(attr), name)
        }
        patch.colorHex?.let { hex ->
            require(HEX_COLOR.matches(hex)) { "Color must be six hexadecimal digits (RRGGBB)" }
            val color = order.getOrCreate(rPr, "color", ctx)
            for (attr in THEME_COLOR_ATTRS) color.removeAttr(attr)
            color.setAttr(ctx.qn("val"), hex.uppercase())
        }
        patch.rtl?.let { setToggle(rPr, "rtl", it, order, ctx) }
    }

    /** Writes an on/off property: present means on, val="0" means explicitly off. */
    private fun setToggle(parent: XmlElement, name: String, on: Boolean, order: PropOrder, ctx: WContext) {
        val el = order.getOrCreate(parent, name, ctx)
        if (on) el.removeAttr("val") else el.setAttr(ctx.qn("val"), "0")
    }

    fun setBidi(pPr: XmlElement, on: Boolean, ctx: WContext) {
        setToggle(pPr, "bidi", on, PropOrder.PPR, ctx)
    }

    /** Sets w:jc to [value], or removes it when [value] is null. */
    fun setJustification(pPr: XmlElement, value: String?, ctx: WContext) {
        if (value == null) {
            pPr.children.removeAll { it is XmlElement && it.isW("jc") }
            return
        }
        PropOrder.PPR.getOrCreate(pPr, "jc", ctx).setAttr(ctx.qn("val"), value)
    }

    /** The w:jc value that shows [alignment] visually in a paragraph of the given direction. */
    fun justificationFor(alignment: Alignment, rtl: Boolean): String = when (alignment) {
        // In right-to-left paragraphs Word reads "left" and "right" as start and end.
        Alignment.LEFT -> if (rtl) "right" else "left"
        Alignment.RIGHT -> if (rtl) "left" else "right"
        Alignment.CENTER -> "center"
        Alignment.JUSTIFY -> "both"
    }
}

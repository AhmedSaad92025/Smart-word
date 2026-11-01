package com.smartword.docx.style

import com.smartword.docx.model.WordProps
import com.smartword.docx.xml.XmlElement

/**
 * A text color as written in w:color: automatic, a plain RRGGBB value, or a theme color with an
 * optional tint or shade. When a theme color is given it wins over the cached value (Word does the same).
 */
internal class ColorRef(
    val hex: String?,
    val themeColor: String?,
    /** w:themeTint, 0..255, or null. */
    val tint: Int?,
    /** w:themeShade, 0..255, or null. */
    val shade: Int?,
) {
    companion object {
        fun from(el: XmlElement?): ColorRef? {
            if (el == null) return null
            val value = el.attr("val")
            val theme = el.attr("themeColor")
            if (value == null && theme == null) return null
            val hex = when {
                value == null || value.equals("auto", ignoreCase = true) -> null
                else -> value.uppercase()
            }
            return ColorRef(
                hex, theme,
                el.attr("themeTint")?.toIntOrNull(16),
                el.attr("themeShade")?.toIntOrNull(16),
            )
        }
    }
}

/** A font as written in w:rFonts: an explicit name, a theme font reference, or both (the theme wins). */
internal class FontRef(val name: String?, val theme: String?)

/**
 * The character properties found in one layer (defaults, a style, or direct formatting).
 * A null field means "this layer says nothing"; layers are combined with [overlay].
 */
internal class RunBag {
    var bold: Boolean? = null
    var boldCs: Boolean? = null
    var italic: Boolean? = null
    var italicCs: Boolean? = null
    var underline: Boolean? = null
    var sizeHalf: Int? = null
    var sizeCsHalf: Int? = null

    /** Null: not set. Otherwise the color as written, which may be automatic or a theme reference. */
    var color: ColorRef? = null
    var rtl: Boolean? = null
    var fontAscii: FontRef? = null
    var fontHAnsi: FontRef? = null
    var fontCs: FontRef? = null
    var fontEa: FontRef? = null

    /** Applies every property that [o] sets on top of this bag. */
    fun overlay(o: RunBag) {
        o.bold?.let { bold = it }
        o.boldCs?.let { boldCs = it }
        o.italic?.let { italic = it }
        o.italicCs?.let { italicCs = it }
        o.underline?.let { underline = it }
        o.sizeHalf?.let { sizeHalf = it }
        o.sizeCsHalf?.let { sizeCsHalf = it }
        o.color?.let { color = it }
        o.rtl?.let { rtl = it }
        o.fontAscii?.let { fontAscii = it }
        o.fontHAnsi?.let { fontHAnsi = it }
        o.fontCs?.let { fontCs = it }
        o.fontEa?.let { fontEa = it }
    }

    fun copy(): RunBag {
        val c = RunBag()
        c.overlay(this)
        return c
    }

    companion object {
        fun from(rPr: XmlElement?): RunBag {
            val bag = RunBag()
            if (rPr == null) return bag
            bag.bold = WordProps.onOff(rPr.child("b"))
            bag.boldCs = WordProps.onOff(rPr.child("bCs"))
            bag.italic = WordProps.onOff(rPr.child("i"))
            bag.italicCs = WordProps.onOff(rPr.child("iCs"))
            val u = rPr.child("u")
            if (u != null) bag.underline = u.attr("val") != "none"
            bag.sizeHalf = rPr.child("sz")?.attr("val")?.toIntOrNull()
            bag.sizeCsHalf = rPr.child("szCs")?.attr("val")?.toIntOrNull()
            bag.color = ColorRef.from(rPr.child("color"))
            bag.rtl = WordProps.onOff(rPr.child("rtl"))
            val fonts = rPr.child("rFonts")
            if (fonts != null) {
                bag.fontAscii = fontRef(fonts.attr("ascii"), fonts.attr("asciiTheme"))
                bag.fontHAnsi = fontRef(fonts.attr("hAnsi"), fonts.attr("hAnsiTheme"))
                bag.fontCs = fontRef(fonts.attr("cs"), fonts.attr("cstheme"))
                bag.fontEa = fontRef(fonts.attr("eastAsia"), fonts.attr("eastAsiaTheme"))
            }
            return bag
        }

        private fun fontRef(name: String?, theme: String?): FontRef? =
            if (name == null && theme == null) null else FontRef(name, theme)

        fun styleIdOf(rPr: XmlElement?): String? = rPr?.child("rStyle")?.attr("val")
    }
}

/** The paragraph properties found in one layer. Values are in twips unless noted. */
internal class ParaBag {
    /** Raw w:jc value. */
    var jc: String? = null
    var bidi: Boolean? = null
    var before: Int? = null
    var after: Int? = null
    var line: Int? = null
    var lineRule: String? = null

    /** Indent from the leading edge (w:start, or w:left which Word treats as the same). */
    var indStart: Int? = null
    var indEnd: Int? = null

    /** First-line indent; negative for a hanging indent. */
    var firstLine: Int? = null
    var numId: Int? = null
    var ilvl: Int? = null
    var outline: Int? = null

    fun overlay(o: ParaBag) {
        o.jc?.let { jc = it }
        o.bidi?.let { bidi = it }
        o.before?.let { before = it }
        o.after?.let { after = it }
        o.line?.let { line = it }
        o.lineRule?.let { lineRule = it }
        o.indStart?.let { indStart = it }
        o.indEnd?.let { indEnd = it }
        o.firstLine?.let { firstLine = it }
        o.numId?.let { numId = it }
        o.ilvl?.let { ilvl = it }
        o.outline?.let { outline = it }
    }

    fun copy(): ParaBag {
        val c = ParaBag()
        c.overlay(this)
        return c
    }

    companion object {
        fun from(pPr: XmlElement?): ParaBag {
            val bag = ParaBag()
            if (pPr == null) return bag
            bag.jc = pPr.child("jc")?.attr("val")
            bag.bidi = WordProps.onOff(pPr.child("bidi"))

            val spacing = pPr.child("spacing")
            if (spacing != null) {
                bag.before = spacing.attr("before")?.toIntOrNull()
                bag.after = spacing.attr("after")?.toIntOrNull()
                bag.line = spacing.attr("line")?.toIntOrNull()
                bag.lineRule = spacing.attr("lineRule")
            }

            val ind = pPr.child("ind")
            if (ind != null) {
                bag.indStart = (ind.attr("start") ?: ind.attr("left"))?.toIntOrNull()
                bag.indEnd = (ind.attr("end") ?: ind.attr("right"))?.toIntOrNull()
                val hanging = ind.attr("hanging")?.toIntOrNull()
                bag.firstLine = if (hanging != null) -hanging else ind.attr("firstLine")?.toIntOrNull()
            }

            val numPr = pPr.child("numPr")
            if (numPr != null) {
                bag.numId = numPr.child("numId")?.attr("val")?.toIntOrNull()
                bag.ilvl = numPr.child("ilvl")?.attr("val")?.toIntOrNull()
            }
            bag.outline = pPr.child("outlineLvl")?.attr("val")?.toIntOrNull()
            return bag
        }

        fun styleIdOf(pPr: XmlElement?): String? = pPr?.child("pStyle")?.attr("val")
    }
}

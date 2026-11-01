package com.smartword.docx.model

import com.smartword.docx.style.FontRef
import com.smartword.docx.style.NumberingDefs
import com.smartword.docx.style.ParaBag
import com.smartword.docx.style.RunBag
import com.smartword.docx.style.StyleSheet
import com.smartword.docx.style.StyleType
import com.smartword.docx.style.ColorRef
import com.smartword.docx.style.ThemeFonts

/** Combines styles, numbering and direct formatting into the formatting the reader sees. */
internal class StyleResolver(
    val styles: StyleSheet,
    val numbering: NumberingDefs,
    private val theme: ThemeFonts,
) {
    /**
     * Paragraph properties from the layers in Word's order: document defaults, style chain,
     * list level, direct formatting. A new bag the caller may modify.
     */
    fun paragraphBag(p: DocxParagraph): ParaBag {
        val direct = ParaBag.from(p.pPr)
        val base = styles.paragraphBag(p.styleId)
        val bag = base.copy()
        val numId = direct.numId ?: base.numId
        if (numId != null && numId > 0) {
            val ilvl = direct.ilvl ?: base.ilvl ?: styleLinkedLevel(numId, p) ?: 0
            numbering.level(numId, ilvl)?.pPr?.let { bag.overlay(it) }
        }
        bag.overlay(direct)
        return bag
    }

    private fun styleLinkedLevel(numId: Int, p: DocxParagraph): Int? =
        numbering.levelForStyle(numId, styles.paragraphStyleOrDefault(p.styleId)?.id)

    fun isRtl(p: DocxParagraph): Boolean = paragraphBag(p).bidi == true

    /** The list definition and level of [p], or null if it is not a list item. */
    fun listRef(p: DocxParagraph): Pair<Int, Int>? {
        val bag = paragraphBag(p)
        val numId = bag.numId ?: return null
        if (numId <= 0) return null
        val ilvl = bag.ilvl ?: styleLinkedLevel(numId, p) ?: 0
        return Pair(numId, ilvl)
    }

    fun paragraphFormat(p: DocxParagraph): ParagraphFormat {
        val bag = paragraphBag(p)
        val rtl = bag.bidi == true
        val style = styles.paragraphStyleOrDefault(p.styleId)
        val outline = bag.outline?.takeIf { it in 0..8 }?.plus(1)
        val ref = listRef(p)
        return ParagraphFormat(
            alignment = WordProps.alignmentOf(bag.jc, rtl) ?: if (rtl) Alignment.RIGHT else Alignment.LEFT,
            isRtl = rtl,
            spaceBeforePt = (bag.before ?: 0) / TWIPS_PER_POINT,
            spaceAfterPt = (bag.after ?: 0) / TWIPS_PER_POINT,
            lineSpacing = lineSpacing(bag),
            indentStartPt = (bag.indStart ?: 0) / TWIPS_PER_POINT,
            indentEndPt = (bag.indEnd ?: 0) / TWIPS_PER_POINT,
            firstLineIndentPt = (bag.firstLine ?: 0) / TWIPS_PER_POINT,
            outlineLevel = outline,
            headingLevel = outline ?: headingLevelByName(style?.name ?: style?.id),
            styleId = p.styleId,
            styleName = style?.name,
            listId = ref?.first,
            listLevel = ref?.second,
        )
    }

    private fun lineSpacing(bag: ParaBag): LineSpacing {
        val line = bag.line ?: return LineSpacing.SINGLE
        return when (bag.lineRule) {
            "exact" -> LineSpacing(line / TWIPS_PER_POINT, LineRule.EXACT)
            "atLeast" -> LineSpacing(line / TWIPS_PER_POINT, LineRule.AT_LEAST)
            else -> LineSpacing(line / TWIPS_PER_LINE, LineRule.AUTO)
        }
    }

    private fun headingLevelByName(name: String?): Int? {
        if (name == null) return null
        val match = HEADING_NAME.matchEntire(name.trim()) ?: return null
        return match.groupValues[1].toInt()
    }

    fun runFormat(p: DocxParagraph, run: TextRun): EffectiveRunFormat {
        val bag = styles.runBag(p.styleId, run.styleId)
        bag.overlay(RunBag.from(run.rPr))

        // Complex-script text (Arabic, Hebrew, ...) uses the *Cs variants of the properties.
        val complex = bag.rtl == true || TextDirection.containsRtl(run.text)
        val bold = if (complex) bag.boldCs ?: bag.bold else bag.bold
        val italic = if (complex) bag.italicCs ?: bag.italic else bag.italic
        val sizeHalf = if (complex) bag.sizeCsHalf ?: bag.sizeHalf else bag.sizeHalf
        val font = if (complex) {
            fontName(bag.fontCs) ?: fontName(bag.fontAscii) ?: fontName(bag.fontHAnsi)
        } else {
            fontName(bag.fontAscii) ?: fontName(bag.fontHAnsi) ?: fontName(bag.fontEa)
        }
        val color = resolveColor(bag.color)
        return EffectiveRunFormat(
            bold = bold ?: false,
            italic = italic ?: false,
            underline = bag.underline ?: false,
            fontSizePt = (sizeHalf ?: DEFAULT_SIZE_HALF_POINTS) / 2f,
            fontName = font,
            colorHex = color,
            rtl = bag.rtl ?: false,
        )
    }

    /** Uppercase RRGGBB, or null for automatic. A resolvable theme color wins over the cached value. */
    private fun resolveColor(ref: ColorRef?): String? {
        if (ref == null) return null
        if (ref.themeColor != null) {
            theme.colors.resolve(ref.themeColor, ref.tint, ref.shade)?.let { return it }
        }
        return ref.hex
    }

    /** A theme reference wins over the explicit name; an unresolvable theme reference falls back to the name. */
    private fun fontName(ref: FontRef?): String? {
        if (ref == null) return null
        val theme = ref.theme
        if (theme != null) {
            val resolved = this.theme.resolve(theme)
            if (resolved != null) return resolved
        }
        return ref.name
    }

    /** True when the paragraph's style (or the default) is a real paragraph style of this document. */
    fun hasParagraphStyle(id: String): Boolean = styles[id]?.type == StyleType.PARAGRAPH

    companion object {
        private const val TWIPS_PER_POINT = 20f
        private const val TWIPS_PER_LINE = 240f
        private const val DEFAULT_SIZE_HALF_POINTS = 20
        private val HEADING_NAME = Regex("(?i)heading\\s*([1-9])")

        val EMPTY = StyleResolver(StyleSheet.EMPTY, NumberingDefs.EMPTY, ThemeFonts.EMPTY)
    }
}

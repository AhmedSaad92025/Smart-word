package com.smartword.docx.style

import com.smartword.docx.xml.XmlElement

/** Major (headings) and minor (body) fonts of the document theme, used to resolve theme font references. */
internal class ThemeFonts(
    private val majorLatin: String?,
    private val minorLatin: String?,
    private val majorEa: String?,
    private val minorEa: String?,
    private val majorCs: String?,
    private val minorCs: String?,
) {
    /** Resolves a w:rFonts theme name such as "minorHAnsi" or "majorBidi"; null if unknown or not defined. */
    fun resolve(themeName: String): String? = when (themeName) {
        "majorHAnsi", "majorAscii" -> majorLatin
        "minorHAnsi", "minorAscii" -> minorLatin
        "majorEastAsia" -> majorEa
        "minorEastAsia" -> minorEa
        "majorBidi" -> majorCs
        "minorBidi" -> minorCs
        else -> null
    }

    companion object {
        val EMPTY = ThemeFonts(null, null, null, null, null, null)

        fun parse(root: XmlElement): ThemeFonts {
            val scheme = findDescendant(root, "fontScheme") ?: return EMPTY
            val major = scheme.elements().firstOrNull { it.localName == "majorFont" }
            val minor = scheme.elements().firstOrNull { it.localName == "minorFont" }
            return ThemeFonts(
                latin(major), latin(minor),
                typeface(major, "ea"), typeface(minor, "ea"),
                complexScript(major), complexScript(minor),
            )
        }

        private fun latin(font: XmlElement?): String? = typeface(font, "latin")

        private fun typeface(font: XmlElement?, local: String): String? {
            val name = font?.elements()?.firstOrNull { it.localName == local }?.attr("typeface")
            return if (name.isNullOrBlank()) null else name
        }

        /** The complex-script font: the cs entry, or the Arabic entry of the per-script list. */
        private fun complexScript(font: XmlElement?): String? {
            typeface(font, "cs")?.let { return it }
            val arabic = font?.elements()?.firstOrNull {
                it.localName == "font" && it.attr("script") == "Arab"
            }
            val name = arabic?.attr("typeface")
            return if (name.isNullOrBlank()) null else name
        }

        private fun findDescendant(el: XmlElement, local: String): XmlElement? {
            for (c in el.elements()) {
                if (c.localName == local) return c
                val deeper = findDescendant(c, local)
                if (deeper != null) return deeper
            }
            return null
        }
    }
}

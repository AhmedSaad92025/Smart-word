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
    /** The colors of the theme, for theme color references. */
    val colors: ThemeColors = ThemeColors.EMPTY,
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
            val colors = ThemeColors.parse(findDescendant(root, "clrScheme"))
            val scheme = findDescendant(root, "fontScheme") ?: return ThemeFonts(null, null, null, null, null, null, colors)
            val major = scheme.elements().firstOrNull { it.localName == "majorFont" }
            val minor = scheme.elements().firstOrNull { it.localName == "minorFont" }
            return ThemeFonts(
                latin(major), latin(minor),
                typeface(major, "ea"), typeface(minor, "ea"),
                complexScript(major), complexScript(minor),
                colors,
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

/** The color scheme of the document theme (dk1, lt1, accent1, ...), with Word's tint and shade rules. */
internal class ThemeColors(private val byName: Map<String, String>) {

    /**
     * The RRGGBB value of the theme color [name] as used in w:color/@w:themeColor, with
     * [tint] (lighter) or [shade] (darker), both 0..255 as stored; null if the theme lacks it.
     */
    fun resolve(name: String, tint: Int?, shade: Int?): String? {
        val key = when (name) {
            "dark1", "text1" -> "dk1"
            "light1", "background1" -> "lt1"
            "dark2", "text2" -> "dk2"
            "light2", "background2" -> "lt2"
            "hyperlink" -> "hlink"
            "followedHyperlink" -> "folHlink"
            else -> name
        }
        val base = byName[key] ?: return null
        if (tint == null && shade == null) return base
        val r = base.substring(0, 2).toInt(16)
        val g = base.substring(2, 4).toInt(16)
        val b = base.substring(4, 6).toInt(16)
        val hsl = rgbToHsl(r, g, b)
        var lum = hsl[2]
        if (shade != null) lum *= shade / 255.0
        if (tint != null) lum = lum * (tint / 255.0) + (1.0 - tint / 255.0)
        return hslToHex(hsl[0], hsl[1], lum.coerceIn(0.0, 1.0))
    }

    companion object {
        val EMPTY = ThemeColors(emptyMap())
        private val NAMES = listOf("dk1", "lt1", "dk2", "lt2", "accent1", "accent2", "accent3", "accent4", "accent5", "accent6", "hlink", "folHlink")

        fun parse(scheme: XmlElement?): ThemeColors {
            if (scheme == null) return EMPTY
            val map = HashMap<String, String>()
            for (el in scheme.elements()) {
                if (el.localName !in NAMES) continue
                val inner = el.elements().firstOrNull() ?: continue
                val hex = when (inner.localName) {
                    "srgbClr" -> inner.attr("val")
                    "sysClr" -> inner.attr("lastClr")
                    else -> null
                }
                if (hex != null && hex.length == 6 && hex.all { it.isLetterOrDigit() }) map[el.localName] = hex.uppercase()
            }
            return ThemeColors(map)
        }

        private fun rgbToHsl(r: Int, g: Int, b: Int): DoubleArray {
            val rf = r / 255.0
            val gf = g / 255.0
            val bf = b / 255.0
            val max = maxOf(rf, gf, bf)
            val min = minOf(rf, gf, bf)
            val l = (max + min) / 2
            if (max == min) return doubleArrayOf(0.0, 0.0, l)
            val d = max - min
            val s = if (l > 0.5) d / (2 - max - min) else d / (max + min)
            val h = when (max) {
                rf -> (gf - bf) / d + (if (gf < bf) 6 else 0)
                gf -> (bf - rf) / d + 2
                else -> (rf - gf) / d + 4
            } / 6
            return doubleArrayOf(h, s, l)
        }

        private fun hslToHex(h: Double, s: Double, l: Double): String {
            fun channel(t0: Double, p: Double, q: Double): Int {
                var t = t0
                if (t < 0) t += 1
                if (t > 1) t -= 1
                val v = when {
                    t < 1.0 / 6 -> p + (q - p) * 6 * t
                    t < 0.5 -> q
                    t < 2.0 / 3 -> p + (q - p) * (2.0 / 3 - t) * 6
                    else -> p
                }
                return Math.round(v * 255).toInt().coerceIn(0, 255)
            }
            val rgb = if (s == 0.0) {
                val v = Math.round(l * 255).toInt()
                intArrayOf(v, v, v)
            } else {
                val q = if (l < 0.5) l * (1 + s) else l + s - l * s
                val p = 2 * l - q
                intArrayOf(channel(h + 1.0 / 3, p, q), channel(h, p, q), channel(h - 1.0 / 3, p, q))
            }
            return "%02X%02X%02X".format(rgb[0], rgb[1], rgb[2])
        }
    }
}

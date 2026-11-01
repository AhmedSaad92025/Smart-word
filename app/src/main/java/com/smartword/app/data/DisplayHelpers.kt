package com.smartword.app.data

/** Converts the engine's RRGGBB colors to the ARGB numbers the screen uses. */
object HexColor {
    /** `FF8800` becomes `0xFFFF8800`; null for automatic colors and anything that is not six hex digits. */
    fun toArgb(hex: String?): Long? {
        if (hex == null || hex.length != 6) return null
        val rgb = hex.toLongOrNull(16) ?: return null
        if (hex.any { !(it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F') }) return null
        return 0xFF000000L or rgb
    }
}

/** The font families the screen can draw; document fonts are mapped to the nearest one. */
enum class FontKind { DEFAULT, SANS, SERIF, MONO }

object FontMapping {
    private val MONO = listOf("courier", "consolas", "mono", "menlo", "lucida console")
    private val SERIF = listOf("times", "georgia", "cambria", "garamond", "palatino", "book antiqua", "traditional arabic", "simplified arabic")
    private val SANS = listOf("arial", "calibri", "helvetica", "segoe", "tahoma", "verdana", "roboto", "dejavu sans", "sans")

    fun kindOf(fontName: String?): FontKind {
        val name = fontName?.trim()?.lowercase() ?: return FontKind.DEFAULT
        if (name.isEmpty()) return FontKind.DEFAULT
        return when {
            MONO.any { name.contains(it) } -> FontKind.MONO
            SANS.any { name.contains(it) } -> FontKind.SANS
            SERIF.any { name.contains(it) } -> FontKind.SERIF
            else -> FontKind.DEFAULT
        }
    }
}

package com.smartword.docx.style

/** Formats list numbers the way Word's w:numFmt values do, including the Arabic ones. */
internal object NumberFormats {
    private val LOWER = ('a'..'z').map { it.toString() }
    private val UPPER = ('A'..'Z').map { it.toString() }

    // w:numFmt="arabicAlpha": the alphabetical order of the Arabic letters.
    private val ARABIC_ALPHA = listOf(
        "أ", "ب", "ت", "ث", "ج", "ح", "خ", "د", "ذ", "ر", "ز", "س", "ش", "ص",
        "ض", "ط", "ظ", "ع", "غ", "ف", "ق", "ك", "ل", "م", "ن", "هـ", "و", "ي",
    )

    // w:numFmt="arabicAbjad": the traditional abjad order.
    private val ARABIC_ABJAD = listOf(
        "أ", "ب", "ج", "د", "هـ", "و", "ز", "ح", "ط", "ي", "ك", "ل", "م", "ن",
        "س", "ع", "ف", "ص", "ق", "ر", "ش", "ت", "ث", "خ", "ذ", "ض", "ظ", "غ",
    )

    private const val ARABIC_INDIC_DIGITS = "٠١٢٣٤٥٦٧٨٩"

    private val ROMAN_VALUES = intArrayOf(1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1)
    private val ROMAN_SYMBOLS = arrayOf("M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I")

    /** Formats [n] with the given w:numFmt value. Unknown formats fall back to decimal. */
    fun format(n: Int, numFmt: String): String = when (numFmt) {
        "decimal" -> n.toString()
        "decimalZero" -> if (n in 0..9) "0$n" else n.toString()
        "lowerLetter" -> letters(n, LOWER)
        "upperLetter" -> letters(n, UPPER)
        "lowerRoman" -> roman(n).lowercase()
        "upperRoman" -> roman(n)
        "arabicAlpha" -> letters(n, ARABIC_ALPHA)
        "arabicAbjad" -> letters(n, ARABIC_ABJAD)
        "hindiNumbers" -> arabicIndic(n)
        "ordinal" -> ordinal(n)
        "none" -> ""
        else -> n.toString()
    }

    /** a, b, ... z, aa, bb, ... zz, aaa ... as Word counts with letters. */
    private fun letters(n: Int, alphabet: List<String>): String {
        if (n <= 0) return n.toString()
        val index = (n - 1) % alphabet.size
        val repeat = (n - 1) / alphabet.size + 1
        return alphabet[index].repeat(repeat)
    }

    private fun roman(n: Int): String {
        if (n !in 1..3999) return n.toString()
        var rest = n
        val sb = StringBuilder()
        for (i in ROMAN_VALUES.indices) {
            while (rest >= ROMAN_VALUES[i]) {
                sb.append(ROMAN_SYMBOLS[i])
                rest -= ROMAN_VALUES[i]
            }
        }
        return sb.toString()
    }

    private fun arabicIndic(n: Int): String {
        val sb = StringBuilder()
        for (c in n.toString()) {
            sb.append(if (c in '0'..'9') ARABIC_INDIC_DIGITS[c - '0'] else c)
        }
        return sb.toString()
    }

    private fun ordinal(n: Int): String {
        val lastTwo = n % 100
        val suffix = if (lastTwo in 11..13) {
            "th"
        } else {
            when (n % 10) {
                1 -> "st"
                2 -> "nd"
                3 -> "rd"
                else -> "th"
            }
        }
        return "$n$suffix"
    }

    /** The visible bullet for a bullet level's w:lvlText, mapping symbol-font code points to real characters. */
    fun bullet(raw: String): String {
        if (raw.isEmpty()) return "•"
        val code = raw[0].code
        return when {
            code in 0xF000..0xF0FF -> when (code) {
                0xF0B7 -> "•"
                0xF0A7 -> "▪"
                0xF0D8 -> "➢"
                0xF0FC -> "✓"
                0xF076 -> "❖"
                else -> "•"
            }
            raw == "o" -> "◦"
            else -> raw
        }
    }
}

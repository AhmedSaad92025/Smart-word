package com.smartword.docx.style

import com.smartword.docx.model.ListLabel
import com.smartword.docx.model.WordProps
import com.smartword.docx.xml.XmlElement

/** One level of a list definition (w:lvl). */
internal class LevelDef(
    val start: Int,
    val numFmt: String,
    val text: String,
    val isLegal: Boolean,
    /** The paragraph style that is linked to this level, if any. */
    val pStyle: String?,
    val pPr: ParaBag?,
)

internal class LevelOverride(val startOverride: Int?, val level: LevelDef?)

/** A list instance (w:num): which definition it uses, with per-level overrides. */
internal class NumDef(val abstractId: Int, val overrides: Map<Int, LevelOverride>)

/** The list definitions of numbering.xml. */
internal class NumberingDefs(
    val abstracts: Map<Int, Map<Int, LevelDef>>,
    val nums: Map<Int, NumDef>,
) {
    fun level(numId: Int, ilvl: Int): LevelDef? {
        val num = nums[numId] ?: return null
        return num.overrides[ilvl]?.level ?: abstracts[num.abstractId]?.get(ilvl)
    }

    /** The level that a paragraph style is linked to (w:lvl/w:pStyle), or null. */
    fun levelForStyle(numId: Int, styleId: String?): Int? {
        if (styleId == null) return null
        val num = nums[numId] ?: return null
        val levels = abstracts[num.abstractId] ?: return null
        for (ilvl in levels.keys.sorted()) {
            if (levels[ilvl]?.pStyle == styleId) return ilvl
        }
        return null
    }

    companion object {
        val EMPTY = NumberingDefs(emptyMap(), emptyMap())

        fun parse(root: XmlElement): NumberingDefs {
            val abstracts = HashMap<Int, Map<Int, LevelDef>>()
            val nums = HashMap<Int, NumDef>()
            for (el in root.elements()) {
                if (el.isW("abstractNum")) {
                    val id = el.attr("abstractNumId")?.toIntOrNull() ?: continue
                    val levels = HashMap<Int, LevelDef>()
                    for (lvl in el.elements()) {
                        if (!lvl.isW("lvl")) continue
                        val ilvl = lvl.attr("ilvl")?.toIntOrNull() ?: continue
                        levels[ilvl] = parseLevel(lvl)
                    }
                    if (!abstracts.containsKey(id)) abstracts[id] = levels
                } else if (el.isW("num")) {
                    val numId = el.attr("numId")?.toIntOrNull() ?: continue
                    val abstractId = el.child("abstractNumId")?.attr("val")?.toIntOrNull() ?: continue
                    val overrides = HashMap<Int, LevelOverride>()
                    for (o in el.elements()) {
                        if (!o.isW("lvlOverride")) continue
                        val ilvl = o.attr("ilvl")?.toIntOrNull() ?: continue
                        overrides[ilvl] = LevelOverride(
                            o.child("startOverride")?.attr("val")?.toIntOrNull(),
                            o.child("lvl")?.let { parseLevel(it) },
                        )
                    }
                    if (!nums.containsKey(numId)) nums[numId] = NumDef(abstractId, overrides)
                }
            }
            return NumberingDefs(abstracts, nums)
        }

        private fun parseLevel(lvl: XmlElement): LevelDef = LevelDef(
            start = lvl.child("start")?.attr("val")?.toIntOrNull() ?: 1,
            numFmt = lvl.child("numFmt")?.attr("val") ?: "decimal",
            text = lvl.child("lvlText")?.attr("val") ?: "",
            isLegal = WordProps.onOff(lvl.child("isLgl")) == true,
            pStyle = lvl.child("pStyle")?.attr("val"),
            pPr = lvl.child("pPr")?.let { ParaBag.from(it) },
        )
    }
}

/**
 * Walks list paragraphs in document order and produces their labels ("1.", "a)", "•", ...).
 *
 * Like Word, list instances that share one definition continue each other's numbering, a
 * higher level restarts the levels below it, and a start override applies the first time
 * its list instance is used.
 */
internal class NumberingCounter(private val defs: NumberingDefs) {
    private val counters = HashMap<Int, Array<Int?>>()
    private val appliedOverrides = HashSet<Long>()

    fun next(numId: Int, level: Int): ListLabel? {
        val num = defs.nums[numId] ?: return null
        val levels = defs.abstracts[num.abstractId] ?: return null
        val lv = level.coerceIn(0, MAX_LEVEL)
        val def = num.overrides[lv]?.level ?: levels[lv] ?: return null

        if (def.numFmt == "bullet") {
            return ListLabel(numId, lv, NumberFormats.bullet(def.text), true)
        }

        val state = counters.getOrPut(num.abstractId) { arrayOfNulls<Int>(MAX_LEVEL + 1) }
        val override = num.overrides[lv]?.startOverride
        val start = override ?: def.start
        val firstUse = override != null && appliedOverrides.add(numId.toLong() * 16 + lv)
        val previous = state[lv]
        state[lv] = if (firstUse || previous == null) start else previous + 1
        for (deeper in lv + 1..MAX_LEVEL) state[deeper] = null

        val sb = StringBuilder()
        var i = 0
        val text = def.text
        while (i < text.length) {
            val c = text[i]
            if (c == '%' && i + 1 < text.length && text[i + 1] in '1'..'9') {
                val k = text[i + 1] - '1'
                val kDef = num.overrides[k]?.level ?: levels[k]
                val value = state[k] ?: kDef?.start ?: 1
                val fmt = if (def.isLegal && k < lv) "decimal" else kDef?.numFmt ?: "decimal"
                sb.append(NumberFormats.format(value, fmt))
                i += 2
            } else {
                sb.append(c)
                i++
            }
        }
        return ListLabel(numId, lv, sb.toString(), false)
    }

    companion object {
        private const val MAX_LEVEL = 8
    }
}

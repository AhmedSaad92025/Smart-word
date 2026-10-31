package com.smartword.docx.style

import com.smartword.docx.xml.XmlElement

enum class StyleType { PARAGRAPH, CHARACTER, TABLE, NUMBERING, OTHER }

/** One style from styles.xml. Its formatting is resolved through [StyleSheet], not read directly. */
class DocxStyle internal constructor(
    val id: String,
    /** The style's name as stored in the file; built-in styles use English names such as "heading 1". */
    val name: String?,
    val type: StyleType,
    val basedOn: String?,
    val isDefault: Boolean,
    /** True for styles Word lists in its gallery (w:qFormat). */
    val isQuickStyle: Boolean,
    internal val pPr: XmlElement?,
    internal val rPr: XmlElement?,
)

/**
 * The styles of a document: document defaults plus every style, with inheritance
 * (w:basedOn) resolved. Reading only; styles.xml is never rewritten.
 *
 * Not thread-safe: resolved results are cached on first use.
 */
class StyleSheet internal constructor(
    val styles: List<DocxStyle>,
    private val defaultRun: RunBag,
    private val defaultPara: ParaBag,
) {
    private val byId = HashMap<String, DocxStyle>()
    private val paraCache = HashMap<String, ParaBag>()
    private val paraRunCache = HashMap<String, RunBag>()

    init {
        for (s in styles) {
            if (!byId.containsKey(s.id)) byId[s.id] = s
        }
    }

    val paragraphStyles: List<DocxStyle> get() = styles.filter { it.type == StyleType.PARAGRAPH }

    val characterStyles: List<DocxStyle> get() = styles.filter { it.type == StyleType.CHARACTER }

    /** The style applied to paragraphs that name none (usually "Normal"). */
    val defaultParagraphStyle: DocxStyle? =
        styles.firstOrNull { it.type == StyleType.PARAGRAPH && it.isDefault }

    operator fun get(id: String?): DocxStyle? = if (id == null) null else byId[id]

    /** The paragraph style that really applies for [id]: itself if it exists, otherwise the default style. */
    fun paragraphStyleOrDefault(id: String?): DocxStyle? {
        val s = get(id)
        return if (s != null && s.type == StyleType.PARAGRAPH) s else defaultParagraphStyle
    }

    /** Defaults plus the style chain of the paragraph style. The result is shared: copy it before changing it. */
    internal fun paragraphBag(styleId: String?): ParaBag {
        val style = paragraphStyleOrDefault(styleId)
        return paraCache.getOrPut(style?.id ?: "") {
            val bag = defaultPara.copy()
            for (s in chain(style)) bag.overlay(ParaBag.from(s.pPr))
            bag
        }
    }

    private fun paragraphRunBag(styleId: String?): RunBag {
        val style = paragraphStyleOrDefault(styleId)
        return paraRunCache.getOrPut(style?.id ?: "") {
            val bag = defaultRun.copy()
            for (s in chain(style)) bag.overlay(RunBag.from(s.rPr))
            bag
        }
    }

    /**
     * Character properties from defaults, the paragraph style chain and the character
     * style chain, in that order. A new bag the caller may modify.
     */
    internal fun runBag(paragraphStyleId: String?, characterStyleId: String?): RunBag {
        val bag = paragraphRunBag(paragraphStyleId).copy()
        val character = get(characterStyleId)
        if (character != null && character.type == StyleType.CHARACTER) {
            for (s in chain(character)) bag.overlay(RunBag.from(s.rPr))
        }
        return bag
    }

    /** The style and its ancestors, most distant ancestor first. Cycles and very long chains are cut off. */
    private fun chain(start: DocxStyle?): List<DocxStyle> {
        val result = ArrayList<DocxStyle>()
        val seen = HashSet<String>()
        var current = start
        while (current != null && result.size < MAX_CHAIN && seen.add(current.id)) {
            result.add(current)
            current = get(current.basedOn)
        }
        result.reverse()
        return result
    }

    companion object {
        private const val MAX_CHAIN = 64

        val EMPTY = StyleSheet(emptyList(), RunBag(), ParaBag())

        internal fun parse(root: XmlElement): StyleSheet {
            val defaults = root.child("docDefaults")
            val run = RunBag.from(defaults?.child("rPrDefault")?.child("rPr"))
            val para = ParaBag.from(defaults?.child("pPrDefault")?.child("pPr"))

            val list = ArrayList<DocxStyle>()
            for (el in root.elements()) {
                if (!el.isW("style")) continue
                val id = el.attr("styleId") ?: continue
                val type = when (el.attr("type")) {
                    null, "paragraph" -> StyleType.PARAGRAPH
                    "character" -> StyleType.CHARACTER
                    "table" -> StyleType.TABLE
                    "numbering" -> StyleType.NUMBERING
                    else -> StyleType.OTHER
                }
                val default = el.attr("default")?.lowercase()
                list.add(
                    DocxStyle(
                        id = id,
                        name = el.child("name")?.attr("val"),
                        type = type,
                        basedOn = el.child("basedOn")?.attr("val"),
                        isDefault = default == "1" || default == "true" || default == "on",
                        isQuickStyle = el.child("qFormat") != null,
                        pPr = el.child("pPr"),
                        rPr = el.child("rPr"),
                    ),
                )
            }
            return StyleSheet(list, run, para)
        }
    }
}

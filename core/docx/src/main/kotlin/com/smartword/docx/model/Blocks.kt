package com.smartword.docx.model

import com.smartword.docx.xml.XmlElement
import com.smartword.docx.xml.XmlWriter

/** A top-level item of the document body, in document order. */
sealed class DocxBlock

/**
 * A paragraph made of editable text runs and untouched opaque items.
 *
 * Offsets used by the editing functions are UTF-16 positions in [text]. Read-only
 * content (hyperlink text, fields, ...) occupies its visible length in that text but
 * is never altered: deleting or formatting a range skips it, and inserting inside it
 * is rejected.
 */
class DocxParagraph internal constructor(
    internal val ctx: WContext,
    /** The original w:p element while the paragraph is unchanged. */
    internal var source: XmlElement?,
    internal var pPr: XmlElement?,
    inlines: List<DocxInline>,
) : DocxBlock() {
    /** True when the paragraph must be rebuilt from the model when saving. */
    internal var dirty: Boolean = source == null

    private val inlineList = ArrayList<DocxInline>(inlines)

    val inlines: List<DocxInline> get() = inlineList

    /** All visible text of the paragraph, including read-only parts such as hyperlink text. */
    val text: String
        get() {
            val sb = StringBuilder()
            for (item in inlineList) sb.append(item.visibleText)
            return sb.toString()
        }

    /** True if the paragraph is right-to-left, whether set directly (w:bidi) or by its style or the defaults. */
    val isRtl: Boolean
        get() = ctx.resolver.isRtl(this)

    /**
     * The visual alignment set directly on this paragraph, or null when it sets none (the
     * style or the default then decides; see [format] for the effective value).
     */
    val alignment: Alignment?
        get() = WordProps.alignmentOf(pPr?.child("jc")?.attr("val"), isRtl)

    // ---- styles ----

    /** The paragraph style named by the paragraph (w:pStyle), or null if it names none. */
    val styleId: String?
        get() = pPr?.child("pStyle")?.attr("val")

    /** Paragraph formatting as the reader sees it, with styles, list level and defaults applied. */
    val format: ParagraphFormat
        get() = ctx.resolver.paragraphFormat(this)

    /** 1..9 if this paragraph is a heading, otherwise null. */
    val headingLevel: Int?
        get() = format.headingLevel

    /** Character formatting of [run] as the reader sees it, with styles and defaults applied. */
    fun effectiveFormat(run: TextRun): EffectiveRunFormat = ctx.resolver.runFormat(this, run)

    /**
     * Applies the paragraph style [styleId], or removes the paragraph's own style when null.
     * Only styles that exist in the document can be applied.
     */
    fun setStyle(styleId: String?) {
        require(styleId == null || ctx.resolver.hasParagraphStyle(styleId)) {
            "Unknown paragraph style '$styleId'"
        }
        touch()
        if (styleId == null) {
            pPr?.children?.removeAll { it is XmlElement && it.isW("pStyle") }
            return
        }
        PropOrder.PPR.getOrCreate(ensurePPr(), "pStyle", ctx).setAttr(ctx.qn("val"), styleId)
    }

    // ---- text editing ----

    /**
     * Inserts [text] at [offset]. The new text continues the formatting of the run
     * before the offset (or the run after it at the start of the paragraph). When
     * [format] is given it is applied on top of that formatting for the new text only.
     */
    fun insertText(offset: Int, text: String, format: RunFormat? = null) {
        val clean = normalize(text)
        checkOffset(offset, this.text)
        if (clean.isEmpty()) return

        var pos = 0
        var target: TextRun? = null
        var targetStart = 0
        var startRun: TextRun? = null
        var insertIndex = inlineList.size
        var indexFound = false
        for (i in inlineList.indices) {
            val item = inlineList[i]
            val len = item.visibleText.length
            if (item is TextRun) {
                if (offset > pos && offset <= pos + len) {
                    target = item
                    targetStart = pos
                    break
                }
                if (offset == pos && startRun == null) startRun = item
            } else if (len > 0 && offset > pos && offset < pos + len) {
                throw IllegalArgumentException("Offset $offset is inside read-only content")
            }
            if (!indexFound && pos >= offset) {
                insertIndex = i
                indexFound = true
            }
            pos += len
        }

        touch()
        val patch = format ?: RunFormat.NONE
        val neighbour = target ?: startRun

        if (neighbour == null) {
            val newRPr = ctx.el("rPr")
            var effective = patch
            if (isRtl && TextDirection.containsRtl(clean) && effective.rtl == null) {
                effective = effective.copy(rtl = true)
            }
            PropsWriter.applyRun(newRPr, effective, ctx)
            val run = TextRun(ctx, clean, WordProps.readRunFormat(newRPr, clean), newRPr, null)
            inlineList.add(insertIndex, run)
            return
        }

        val local = if (target != null) offset - targetStart else 0
        if (patch.isEmpty || neighbour.format.overlay(patch) == neighbour.format) {
            neighbour.setContent(neighbour.text.substring(0, local) + clean + neighbour.text.substring(local))
            return
        }

        // The new text is formatted differently: split the neighbour around it.
        val index = inlineList.indexOf(neighbour)
        val before = neighbour.text.substring(0, local)
        val after = neighbour.text.substring(local)
        val midRPr = neighbour.rPr?.deepCopy() ?: ctx.el("rPr")
        PropsWriter.applyRun(midRPr, patch, ctx)
        val mid = TextRun(ctx, clean, WordProps.readRunFormat(midRPr, clean), midRPr, null)
        var tail: TextRun? = null
        if (after.isNotEmpty()) {
            val tailRPr = neighbour.rPr?.deepCopy()
            tail = TextRun(ctx, after, WordProps.readRunFormat(tailRPr, after), tailRPr, null)
        }
        if (before.isEmpty()) {
            inlineList.removeAt(index)
            inlineList.add(index, mid)
            if (tail != null) inlineList.add(index + 1, tail)
        } else {
            neighbour.setContent(before)
            inlineList.add(index + 1, mid)
            if (tail != null) inlineList.add(index + 2, tail)
        }
    }

    /** Deletes the editable text in [start, end). Read-only content inside the range stays. */
    fun deleteText(start: Int, end: Int) {
        val full = text
        checkOffset(start, full)
        checkOffset(end, full)
        require(start <= end) { "start must not be after end" }
        if (start == end) return

        touch()
        splitAt(start)
        splitAt(end)
        var pos = 0
        val iter = inlineList.iterator()
        while (iter.hasNext()) {
            val item = iter.next()
            val len = item.visibleText.length
            if (item is TextRun && len > 0 && pos >= start && pos + len <= end) iter.remove()
            pos += len
        }
        mergeAdjacentRuns()
    }

    /** Applies [patch] to the editable text in [start, end). Null fields of the patch change nothing. */
    fun applyFormat(start: Int, end: Int, patch: RunFormat) {
        val full = text
        checkOffset(start, full)
        checkOffset(end, full)
        require(start <= end) { "start must not be after end" }
        if (start == end || patch.isEmpty) return

        touch()
        splitAt(start)
        splitAt(end)
        var pos = 0
        for (item in inlineList) {
            val len = item.visibleText.length
            if (item is TextRun && len > 0 && pos >= start && pos + len <= end) item.applyPatch(patch)
            pos += len
        }
        mergeAdjacentRuns()
    }

    // ---- paragraph formatting ----

    /** Sets the visual alignment; null removes the setting so the paragraph uses its default. */
    fun setAlignment(alignment: Alignment?) {
        touch()
        if (alignment == null) {
            pPr?.let { PropsWriter.setJustification(it, null, ctx) }
            return
        }
        PropsWriter.setJustification(ensurePPr(), PropsWriter.justificationFor(alignment, isRtl), ctx)
    }

    /**
     * Sets the paragraph direction, keeping the alignment the reader sees, including an
     * alignment that the paragraph's style sets (it is then written onto the paragraph).
     */
    fun setRtl(rtl: Boolean) {
        val before = ctx.resolver.paragraphBag(this)
        val visual = WordProps.alignmentOf(before.jc, before.bidi == true)
        touch()
        PropsWriter.setBidi(ensurePPr(), rtl, ctx)
        if (visual != null) setAlignment(visual)
    }

    // ---- internals ----

    internal fun touch() {
        dirty = true
    }

    private fun ensurePPr(): XmlElement {
        val existing = pPr
        if (existing != null) return existing
        val created = ctx.el("pPr")
        pPr = created
        return created
    }

    private fun normalize(text: String): String = text.replace("\r\n", "\n").replace('\r', '\n')

    private fun checkOffset(offset: Int, full: String) {
        require(offset in 0..full.length) { "Offset $offset is outside the paragraph (length ${full.length})" }
        if (offset in 1 until full.length &&
            Character.isHighSurrogate(full[offset - 1]) &&
            Character.isLowSurrogate(full[offset])
        ) {
            throw IllegalArgumentException("Offset $offset splits a surrogate pair")
        }
    }

    /** Ensures that no text run straddles [offset] by splitting the run that does. */
    private fun splitAt(offset: Int) {
        var pos = 0
        for (i in inlineList.indices) {
            val item = inlineList[i]
            val len = item.visibleText.length
            if (item is TextRun && offset > pos && offset < pos + len) {
                val k = offset - pos
                val tailText = item.text.substring(k)
                val tailRPr = item.rPr?.deepCopy()
                item.setContent(item.text.substring(0, k))
                inlineList.add(i + 1, TextRun(ctx, tailText, WordProps.readRunFormat(tailRPr, tailText), tailRPr, null))
                return
            }
            pos += len
        }
    }

    /** Joins neighbouring text runs that have identical character properties. */
    private fun mergeAdjacentRuns() {
        var i = 0
        while (i < inlineList.size - 1) {
            val a = inlineList[i]
            val b = inlineList[i + 1]
            if (a is TextRun && b is TextRun && propsKey(a) == propsKey(b)) {
                a.setContent(a.text + b.text)
                inlineList.removeAt(i + 1)
            } else {
                i++
            }
        }
    }

    private fun propsKey(run: TextRun): String {
        val props = run.rPr
        if (props == null || props.elements().isEmpty()) return ""
        return XmlWriter.writeElementToString(props)
    }

    /**
     * Moves everything from [offset] on into a new paragraph that copies this paragraph's
     * properties. A section break stays with the last of the two paragraphs.
     */
    internal fun splitOff(offset: Int): DocxParagraph {
        checkOffset(offset, text)
        touch()
        splitAt(offset)

        var pos = 0
        var cut = inlineList.size
        for (i in inlineList.indices) {
            if (pos >= offset) {
                cut = i
                break
            }
            pos += inlineList[i].visibleText.length
        }
        val tail = ArrayList<DocxInline>(inlineList.subList(cut, inlineList.size))
        inlineList.subList(cut, inlineList.size).clear()

        val copy = pPr?.deepCopy()
        pPr?.children?.removeAll { it is XmlElement && it.isW("sectPr") }
        return DocxParagraph(ctx, null, copy, tail)
    }

    /** Appends the content of [next] to this paragraph; a section break of [next] is kept. */
    internal fun absorb(next: DocxParagraph) {
        touch()
        inlineList.addAll(next.inlineList)
        val sect = next.pPr?.child("sectPr")
        if (sect != null) {
            val props = ensurePPr()
            props.children.removeAll { it is XmlElement && it.isW("sectPr") }
            PropOrder.PPR.insert(props, sect)
        }
        mergeAdjacentRuns()
    }
}

/** What kind of block-level content an [OpaqueBlock] holds. */
enum class OpaqueKind { TABLE, STRUCTURED_TAG, OTHER }

/** Block-level content the editor keeps but does not edit (tables, content controls, ...). */
class OpaqueBlock internal constructor(
    internal val element: XmlElement,
    val kind: OpaqueKind,
    val previewText: String,
) : DocxBlock()

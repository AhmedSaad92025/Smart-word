package com.smartword.app.preview

import com.smartword.docx.model.DocxDocument
import com.smartword.docx.model.DocxParagraph
import com.smartword.docx.model.OpaqueBlock
import com.smartword.docx.model.TextRun

/**
 * Turns a [DocxDocument] into what the screen draws: for every paragraph its text split
 * into spans with the formatting the reader sees (styles, lists and theme colors included).
 * Reading only; the document is not changed.
 */
object PreviewBuilder {

    fun build(doc: DocxDocument): List<PreviewBlock> {
        val labels = doc.listLabels()
        return doc.blocks.map { block ->
            when (block) {
                is DocxParagraph -> paragraph(block, labels[block]?.text)
                is OpaqueBlock -> PreviewPlaceholder(block.previewText)
            }
        }
    }

    private fun paragraph(p: DocxParagraph, label: String?): PreviewParagraph {
        val format = p.format
        val spans = ArrayList<PreviewSpan>()
        for (item in p.inlines) {
            if (item.visibleText.isEmpty()) continue
            if (item is TextRun) {
                val f = p.effectiveFormat(item)
                spans.add(PreviewSpan(item.text, f.bold, f.italic, f.underline, f.fontSizePt, f.colorHex, f.fontName))
            } else {
                // Read-only content such as link text keeps the paragraph's plain look.
                val first = p.inlines.filterIsInstance<TextRun>().firstOrNull()
                val f = if (first != null) p.effectiveFormat(first) else null
                spans.add(
                    PreviewSpan(
                        item.visibleText, f?.bold ?: false, f?.italic ?: false, false,
                        f?.fontSizePt ?: DEFAULT_SIZE_PT, null, f?.fontName,
                    ),
                )
            }
        }
        return PreviewParagraph(
            spans = spans,
            alignment = format.alignment,
            rtl = format.isRtl,
            headingLevel = format.headingLevel,
            listLabel = label,
            indentStartPt = format.indentStartPt,
            indentEndPt = format.indentEndPt,
            spaceBeforePt = format.spaceBeforePt,
            spaceAfterPt = format.spaceAfterPt,
        )
    }

    private const val DEFAULT_SIZE_PT = 11f
}

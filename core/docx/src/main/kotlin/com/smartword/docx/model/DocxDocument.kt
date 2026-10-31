package com.smartword.docx.model

import com.smartword.docx.InvalidDocxException
import com.smartword.docx.pkg.DocxPackage
import com.smartword.docx.pkg.Opc
import com.smartword.docx.pkg.ReadLimits
import com.smartword.docx.xml.XmlDocument
import com.smartword.docx.xml.XmlElement
import com.smartword.docx.xml.XmlNode
import com.smartword.docx.xml.XmlParseException
import com.smartword.docx.xml.XmlParser
import com.smartword.docx.xml.XmlWriter
import java.io.InputStream
import java.io.OutputStream

/**
 * An opened DOCX document.
 *
 * Only the main document part is interpreted. Every other part of the archive and
 * every element of the body that is not understood is kept and written back unchanged.
 * A document that was not edited is saved byte-for-byte like the original.
 */
class DocxDocument private constructor(
    private val pkg: DocxPackage,
    private val mainPartName: String,
    private val xml: XmlDocument,
    private val body: XmlElement,
    private val ctx: WContext,
    blocks: List<DocxBlock>,
    private val trailing: List<XmlElement>,
) {
    private val blockList = ArrayList<DocxBlock>(blocks)
    private var structureChanged = false

    val blocks: List<DocxBlock> get() = blockList

    val paragraphs: List<DocxParagraph> get() = blockList.filterIsInstance<DocxParagraph>()

    /** Text of the whole document, one line per paragraph. */
    val plainText: String
        get() = blockList.joinToString("\n") { block ->
            when (block) {
                is DocxParagraph -> block.text
                is OpaqueBlock -> block.previewText
            }
        }

    /** True if the document was changed since it was opened or last serialized. */
    val hasUnsavedChanges: Boolean
        get() = structureChanged || blockList.any { it is DocxParagraph && it.dirty }

    // ---- structure editing ----

    /** Creates a paragraph that is not yet part of the document; add it with [insertBlock]. */
    fun createParagraph(
        text: String = "",
        format: RunFormat = RunFormat.NONE,
        rtl: Boolean? = null,
        alignment: Alignment? = null,
    ): DocxParagraph {
        val p = DocxParagraph(ctx, null, null, emptyList())
        val isRtl = rtl ?: TextDirection.startsWithRtl(text)
        if (isRtl) p.setRtl(true)
        if (alignment != null) p.setAlignment(alignment)
        if (text.isNotEmpty()) {
            val effective = if (isRtl && TextDirection.containsRtl(text) && format.rtl == null) {
                format.copy(rtl = true)
            } else {
                format
            }
            p.insertText(0, text, effective)
        }
        return p
    }

    fun insertBlock(index: Int, block: DocxBlock) {
        require(index in 0..blockList.size) { "Index $index is outside the document" }
        require(block !in blockList) { "The block is already part of the document" }
        if (block is DocxParagraph) {
            require(block.ctx === ctx) { "The paragraph belongs to another document" }
        }
        blockList.add(index, block)
        structureChanged = true
    }

    /** Appends a new paragraph at the end of the document and returns it. */
    fun addParagraph(
        text: String = "",
        format: RunFormat = RunFormat.NONE,
        rtl: Boolean? = null,
        alignment: Alignment? = null,
    ): DocxParagraph {
        val p = createParagraph(text, format, rtl, alignment)
        insertBlock(blockList.size, p)
        return p
    }

    fun removeBlock(index: Int): DocxBlock {
        require(index in blockList.indices) { "Index $index is outside the document" }
        structureChanged = true
        return blockList.removeAt(index)
    }

    /** Splits [paragraph] at [offset] (the Enter key) and returns the new paragraph that follows it. */
    fun splitParagraph(paragraph: DocxParagraph, offset: Int): DocxParagraph {
        val index = blockList.indexOf(paragraph)
        require(index >= 0) { "The paragraph is not part of the document" }
        val second = paragraph.splitOff(offset)
        blockList.add(index + 1, second)
        structureChanged = true
        return second
    }

    /**
     * Joins the paragraph that follows [paragraph] onto it (Backspace at a paragraph start).
     * Returns false when the next block is not a paragraph, e.g. a table, which is never merged.
     */
    fun mergeWithNext(paragraph: DocxParagraph): Boolean {
        val index = blockList.indexOf(paragraph)
        require(index >= 0) { "The paragraph is not part of the document" }
        val next = blockList.getOrNull(index + 1) as? DocxParagraph ?: return false
        paragraph.absorb(next)
        blockList.removeAt(index + 1)
        structureChanged = true
        return true
    }

    // ---- saving ----

    /** Serializes the document as a DOCX file. */
    fun toByteArray(): ByteArray {
        syncToPackage()
        return pkg.toByteArray()
    }

    fun writeTo(out: OutputStream) {
        syncToPackage()
        pkg.writeTo(out)
    }

    /** Writes edits back into the main part; untouched documents keep their original bytes. */
    private fun syncToPackage() {
        if (!hasUnsavedChanges) return
        val children = ArrayList<XmlNode>()
        for (block in blockList) {
            when (block) {
                is DocxParagraph -> children.add(DocxWriter.paragraphElement(block))
                is OpaqueBlock -> children.add(block.element)
            }
        }
        children.addAll(trailing)
        body.children.clear()
        body.children.addAll(children)
        pkg.put(mainPartName, XmlWriter.write(xml))
        structureChanged = false
    }

    companion object {
        /** Creates an empty A4 document; with [rtl] its first paragraph and page are right-to-left. */
        fun create(rtl: Boolean = false, now: java.time.Instant = java.time.Instant.now()): DocxDocument =
            fromPackage(DocxPackage.read(DocxTemplates.newDocument(rtl, now)))

        fun open(bytes: ByteArray, limits: ReadLimits = ReadLimits()): DocxDocument =
            fromPackage(DocxPackage.read(bytes, limits))

        fun open(input: InputStream, limits: ReadLimits = ReadLimits()): DocxDocument =
            fromPackage(DocxPackage.read(input, limits))

        private fun fromPackage(pkg: DocxPackage): DocxDocument {
            val mainName = Opc.mainDocumentPartName(pkg)
            val part = pkg.find(mainName)
                ?: throw InvalidDocxException("The main document part is missing")
            val xml = try {
                XmlParser.parse(part.bytes)
            } catch (e: XmlParseException) {
                throw InvalidDocxException("The document content is damaged: ${e.message}", e)
            }
            if (!xml.root.isW("document")) {
                throw InvalidDocxException("The main part is not a WordprocessingML document")
            }
            val body = xml.root.child("body")
                ?: throw InvalidDocxException("The document has no body")
            val ctx = WContext.forRoot(xml.root)

            val blocks = ArrayList<DocxBlock>()
            val trailing = ArrayList<XmlElement>()
            for (child in body.children) {
                if (child !is XmlElement) continue
                if (child.isW("sectPr")) {
                    trailing.add(child)
                    continue
                }
                val block = DocxReader.readBlock(child, ctx)
                if (block != null) blocks.add(block)
            }
            return DocxDocument(pkg, part.name, xml, body, ctx, blocks, trailing)
        }
    }
}

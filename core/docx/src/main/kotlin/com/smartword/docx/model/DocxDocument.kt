package com.smartword.docx.model

import com.smartword.docx.InvalidDocxException
import com.smartword.docx.pkg.DocxPackage
import com.smartword.docx.pkg.Opc
import com.smartword.docx.pkg.ReadLimits
import com.smartword.docx.xml.XmlDocument
import com.smartword.docx.xml.XmlElement
import com.smartword.docx.xml.XmlParseException
import com.smartword.docx.xml.XmlParser
import java.io.InputStream
import java.io.OutputStream

/**
 * An opened DOCX document.
 *
 * Only the main document part is interpreted. Every other part of the archive and
 * every element of the body that is not understood is kept and written back unchanged.
 */
class DocxDocument private constructor(
    private val pkg: DocxPackage,
    private val mainPartName: String,
    private val xml: XmlDocument,
    private val body: XmlElement,
    blocks: List<DocxBlock>,
    private val trailing: List<XmlElement>,
) {
    private val blockList = ArrayList<DocxBlock>(blocks)

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

    /** Serializes the document as a DOCX file. */
    fun toByteArray(): ByteArray = pkg.toByteArray()

    fun writeTo(out: OutputStream) = pkg.writeTo(out)

    companion object {
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

            val blocks = ArrayList<DocxBlock>()
            val trailing = ArrayList<XmlElement>()
            for (child in body.children) {
                if (child !is XmlElement) continue
                if (child.isW("sectPr")) {
                    trailing.add(child)
                    continue
                }
                val block = DocxReader.readBlock(child)
                if (block != null) blocks.add(block)
            }
            return DocxDocument(pkg, part.name, xml, body, blocks, trailing)
        }
    }
}

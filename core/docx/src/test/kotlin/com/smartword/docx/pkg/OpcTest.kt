package com.smartword.docx.pkg

import com.smartword.docx.InvalidDocxException
import com.smartword.docx.ZipFixtures
import com.smartword.docx.ZipFixtures.text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class OpcTest {

    private fun rels(target: String, type: String = Opc.REL_OFFICE_DOCUMENT) =
        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"$type\" Target=\"$target\"/></Relationships>"

    private fun pkg(vararg entries: Pair<String, String>): DocxPackage =
        DocxPackage.read(ZipFixtures.zip(entries.map { it.first to text(it.second) }))

    @Test
    fun followsOfficeDocumentRelationship() {
        val p = pkg(
            "[Content_Types].xml" to "<Types/>",
            "_rels/.rels" to rels("word/document.xml"),
            "word/document.xml" to "<d/>",
        )
        assertEquals("word/document.xml", Opc.mainDocumentPartName(p))
    }

    @Test
    fun supportsAbsoluteTargetsAndCustomPartNames() {
        val p = pkg(
            "[Content_Types].xml" to "<Types/>",
            "_rels/.rels" to rels("/word/document2.xml"),
            "word/document.xml" to "<old/>",
            "word/document2.xml" to "<d/>",
        )
        assertEquals("word/document2.xml", Opc.mainDocumentPartName(p))
    }

    @Test
    fun ignoresUnrelatedRelationships() {
        val p = pkg(
            "[Content_Types].xml" to "<Types/>",
            "_rels/.rels" to rels("docProps/core.xml", "http://example.com/other"),
            "word/document.xml" to "<d/>",
        )
        assertEquals("word/document.xml", Opc.mainDocumentPartName(p))
    }

    @Test
    fun fallsBackToDefaultNameWithoutRels() {
        val p = pkg("[Content_Types].xml" to "<Types/>", "word/document.xml" to "<d/>")
        assertEquals("word/document.xml", Opc.mainDocumentPartName(p))
    }

    @Test
    fun rejectsPackageWithoutContentTypes() {
        val p = pkg("word/document.xml" to "<d/>")
        assertThrows(InvalidDocxException::class.java) { Opc.mainDocumentPartName(p) }
    }

    @Test
    fun rejectsMissingMainPart() {
        val p = pkg("[Content_Types].xml" to "<Types/>", "_rels/.rels" to rels("word/document.xml"))
        assertThrows(InvalidDocxException::class.java) { Opc.mainDocumentPartName(p) }
    }

    @Test
    fun rejectsDamagedRelationships() {
        val p = pkg(
            "[Content_Types].xml" to "<Types/>",
            "_rels/.rels" to "<Relationships><oops>",
            "word/document.xml" to "<d/>",
        )
        assertThrows(InvalidDocxException::class.java) { Opc.mainDocumentPartName(p) }
    }
}

package com.smartword.docx.pkg

import com.smartword.docx.DocxTooLargeException
import com.smartword.docx.EncryptedDocxException
import com.smartword.docx.InvalidDocxException
import com.smartword.docx.ZipFixtures
import com.smartword.docx.ZipFixtures.text
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DocxPackageTest {

    private fun sample(): ByteArray = ZipFixtures.zip(
        listOf(
            "[Content_Types].xml" to text("<Types/>"),
            "_rels/.rels" to text("<Relationships/>"),
            "word/document.xml" to text("<w:document/>"),
            "word/media/image1.png" to ZipFixtures.binary(3000, 1L),
            "customXml/item1.xml" to text("<item>مرحبا</item>"),
        ),
    )

    @Test
    fun roundTripKeepsNamesOrderAndBytes() {
        val original = DocxPackage.read(sample())
        val copy = DocxPackage.read(original.toByteArray())
        assertEquals(original.parts.map { it.name }, copy.parts.map { it.name })
        for (i in original.parts.indices) {
            assertArrayEquals(original.parts[i].bytes, copy.parts[i].bytes)
        }
        assertEquals(5, copy.parts.size)
    }

    @Test
    fun readsFromInputStream() {
        val pkg = DocxPackage.read(ByteArrayInputStream(sample()))
        assertEquals(5, pkg.parts.size)
        assertNotNull(pkg.find("word/document.xml"))
    }

    @Test
    fun findPrefersExactMatchThenIgnoresCase() {
        val pkg = DocxPackage.read(sample())
        assertEquals("word/document.xml", pkg.find("word/document.xml")?.name)
        assertEquals("word/document.xml", pkg.find("WORD/Document.xml")?.name)
        assertNull(pkg.find("word/missing.xml"))
    }

    @Test
    fun putReplacesExistingAndAppendsNew() {
        val pkg = DocxPackage.read(sample())
        pkg.put("word/document.xml", text("<changed/>"))
        pkg.put("word/new.xml", text("<new/>"))
        assertEquals(6, pkg.parts.size)
        assertEquals("<changed/>", String(pkg.find("word/document.xml")!!.bytes, Charsets.UTF_8))
        assertEquals("word/new.xml", pkg.parts.last().name)
    }

    @Test
    fun removeDeletesPart() {
        val pkg = DocxPackage.read(sample())
        assertTrue(pkg.remove("customXml/item1.xml"))
        assertFalse(pkg.remove("customXml/item1.xml"))
        assertEquals(4, pkg.parts.size)
    }

    @Test
    fun skipsDirectoryEntries() {
        val bos = java.io.ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            z.putNextEntry(ZipEntry("word/"))
            z.closeEntry()
            z.putNextEntry(ZipEntry("word/document.xml"))
            z.write(text("<a/>"))
            z.closeEntry()
        }
        val pkg = DocxPackage.read(bos.toByteArray())
        assertEquals(listOf("word/document.xml"), pkg.parts.map { it.name })
    }

    @Test
    fun rejectsNonZipData() {
        assertThrows(InvalidDocxException::class.java) {
            DocxPackage.read(text("hello world, this is not a zip"))
        }
    }

    @Test
    fun rejectsEmptyData() {
        assertThrows(InvalidDocxException::class.java) { DocxPackage.read(ByteArray(0)) }
    }

    @Test
    fun rejectsTruncatedArchive() {
        val bytes = sample().copyOf(40)
        assertThrows(InvalidDocxException::class.java) { DocxPackage.read(bytes) }
    }

    @Test
    fun reportsOleContainersAsEncryptedOrLegacy() {
        val ole = byteArrayOf(
            0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(),
            0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte(), 0, 0, 0, 0,
        )
        assertThrows(EncryptedDocxException::class.java) { DocxPackage.read(ole) }
    }

    @Test
    fun enforcesFileSizeLimit() {
        val limits = ReadLimits(maxFileBytes = 100)
        assertThrows(DocxTooLargeException::class.java) { DocxPackage.read(sample(), limits) }
        assertThrows(DocxTooLargeException::class.java) {
            DocxPackage.read(ByteArrayInputStream(sample()), limits)
        }
    }

    @Test
    fun enforcesDecompressedEntrySizeLimit() {
        val bomb = ZipFixtures.zip(listOf("word/document.xml" to ByteArray(200_000)))
        assertTrue(bomb.size < 2_000)
        val limits = ReadLimits(maxEntryBytes = 10_000)
        assertThrows(DocxTooLargeException::class.java) { DocxPackage.read(bomb, limits) }
    }

    @Test
    fun enforcesTotalDecompressedSizeLimit() {
        val data = ZipFixtures.zip(
            listOf("a.bin" to ByteArray(6_000), "b.bin" to ByteArray(6_000)),
        )
        val limits = ReadLimits(maxEntryBytes = 10_000, maxTotalBytes = 10_000)
        assertThrows(DocxTooLargeException::class.java) { DocxPackage.read(data, limits) }
    }

    @Test
    fun enforcesEntryCountLimit() {
        val data = ZipFixtures.zip((1..5).map { "f$it.xml" to text("<a/>") })
        assertThrows(DocxTooLargeException::class.java) {
            DocxPackage.read(data, ReadLimits(maxEntries = 3))
        }
    }

    @Test
    fun writingIsDeterministic() {
        val pkg = DocxPackage.read(sample())
        assertArrayEquals(pkg.toByteArray(), pkg.toByteArray())
    }
}

package com.smartword.docx.pkg

import com.smartword.docx.DocxTooLargeException
import com.smartword.docx.EncryptedDocxException
import com.smartword.docx.InvalidDocxException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** One file inside the DOCX archive. The bytes are kept exactly as read unless replaced. */
class DocxPart(val name: String, var bytes: ByteArray)

/** Upper bounds that protect the app from oversized files and ZIP bombs. */
data class ReadLimits(
    val maxFileBytes: Long = 100L * 1024 * 1024,
    val maxEntries: Int = 20_000,
    val maxEntryBytes: Long = 100L * 1024 * 1024,
    val maxTotalBytes: Long = 300L * 1024 * 1024,
)

/**
 * The raw ZIP container of a DOCX file.
 *
 * Parts keep their original order and bytes, so everything the editor does not
 * touch (images, themes, headers, custom XML, ...) is written back unchanged.
 */
class DocxPackage(initialParts: List<DocxPart> = emptyList()) {
    private val partList = ArrayList<DocxPart>(initialParts)

    val parts: List<DocxPart> get() = partList

    /** Finds a part by name; an exact match wins, otherwise the match ignores case. */
    fun find(name: String): DocxPart? {
        for (p in partList) if (p.name == name) return p
        for (p in partList) if (p.name.equals(name, ignoreCase = true)) return p
        return null
    }

    /** Replaces the bytes of an existing part or appends a new one. */
    fun put(name: String, bytes: ByteArray) {
        val existing = find(name)
        if (existing != null) existing.bytes = bytes else partList.add(DocxPart(name, bytes))
    }

    fun remove(name: String): Boolean {
        val existing = find(name) ?: return false
        return partList.remove(existing)
    }

    fun toByteArray(): ByteArray {
        val out = ByteArrayOutputStream()
        writeTo(out)
        return out.toByteArray()
    }

    /** Writes the archive to [out] without closing it. */
    fun writeTo(out: OutputStream) {
        val zip = ZipOutputStream(out)
        zip.setLevel(Deflater.DEFAULT_COMPRESSION)
        for (p in partList) {
            val entry = ZipEntry(p.name)
            entry.time = FIXED_TIME
            zip.putNextEntry(entry)
            zip.write(p.bytes)
            zip.closeEntry()
        }
        zip.finish()
        zip.flush()
    }

    companion object {
        private const val FIXED_TIME = 1577836800000L // 2020-01-01T00:00:00Z

        private val OLE_MAGIC = intArrayOf(0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1)

        fun read(input: InputStream, limits: ReadLimits = ReadLimits()): DocxPackage {
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0L
            while (true) {
                val n = try {
                    input.read(buffer)
                } catch (e: IOException) {
                    throw InvalidDocxException("Could not read the file", e)
                }
                if (n < 0) break
                total += n
                if (total > limits.maxFileBytes) {
                    throw DocxTooLargeException("The file is larger than ${limits.maxFileBytes} bytes")
                }
                out.write(buffer, 0, n)
            }
            return read(out.toByteArray(), limits)
        }

        fun read(bytes: ByteArray, limits: ReadLimits = ReadLimits()): DocxPackage {
            if (bytes.size.toLong() > limits.maxFileBytes) {
                throw DocxTooLargeException("The file is larger than ${limits.maxFileBytes} bytes")
            }
            if (isOleContainer(bytes)) {
                throw EncryptedDocxException(
                    "The file is a password-protected document or a legacy .doc file",
                )
            }
            if (bytes.size < 4 || bytes[0] != 'P'.code.toByte() || bytes[1] != 'K'.code.toByte()) {
                throw InvalidDocxException("The file is not a ZIP archive")
            }

            val parts = ArrayList<DocxPart>()
            val seen = HashSet<String>()
            var total = 0L
            var count = 0
            try {
                ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        if (entry.isDirectory) continue
                        count++
                        if (count > limits.maxEntries) {
                            throw DocxTooLargeException("The archive has too many entries")
                        }
                        val allowed = minOf(limits.maxEntryBytes, limits.maxTotalBytes - total)
                        val data = readEntry(zip, allowed)
                        total += data.size
                        if (seen.add(entry.name)) parts.add(DocxPart(entry.name, data))
                    }
                }
            } catch (e: ZipException) {
                throw InvalidDocxException("The ZIP archive is damaged", e)
            } catch (e: IOException) {
                throw InvalidDocxException("The ZIP archive could not be read", e)
            }
            if (parts.isEmpty()) throw InvalidDocxException("The archive contains no files")
            return DocxPackage(parts)
        }

        private fun readEntry(zip: ZipInputStream, maxBytes: Long): ByteArray {
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0L
            while (true) {
                val n = zip.read(buffer)
                if (n < 0) break
                total += n
                if (total > maxBytes) {
                    throw DocxTooLargeException("A part of the document is too large when decompressed")
                }
                out.write(buffer, 0, n)
            }
            return out.toByteArray()
        }

        private fun isOleContainer(bytes: ByteArray): Boolean {
            if (bytes.size < OLE_MAGIC.size) return false
            for (k in OLE_MAGIC.indices) {
                if ((bytes[k].toInt() and 0xFF) != OLE_MAGIC[k]) return false
            }
            return true
        }
    }
}

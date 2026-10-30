package com.smartword.docx

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Builds small ZIP archives for tests. */
object ZipFixtures {
    fun text(value: String): ByteArray = value.toByteArray(Charsets.UTF_8)

    fun zip(entries: List<Pair<String, ByteArray>>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            for ((name, data) in entries) {
                z.putNextEntry(ZipEntry(name))
                z.write(data)
                z.closeEntry()
            }
        }
        return bos.toByteArray()
    }

    /** Deterministic pseudo-random bytes, standing in for an embedded image. */
    fun binary(size: Int, seed: Long): ByteArray {
        val random = java.util.Random(seed)
        val data = ByteArray(size)
        random.nextBytes(data)
        return data
    }
}

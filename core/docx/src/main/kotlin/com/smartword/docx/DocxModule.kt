package com.smartword.docx

/**
 * Entry point marker for the DOCX engine module.
 *
 * This module is pure Kotlin/JVM (no Android dependencies) so that it can be
 * unit-tested quickly. Phase 1 adds the package reader/writer, the document
 * model, and round-trip preservation of parts the editor does not understand.
 */
object DocxModule {
    const val NAME = "docx-engine"
}

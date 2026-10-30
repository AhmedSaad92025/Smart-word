package com.smartword.docx

/** Base type for every error the DOCX engine reports to callers. */
open class DocxException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** The data is not a readable DOCX: not a ZIP, damaged, missing required parts, or invalid XML. */
class InvalidDocxException(message: String, cause: Throwable? = null) : DocxException(message, cause)

/** A password-protected DOCX, or a legacy binary .doc file (both use an OLE2 container). */
class EncryptedDocxException(message: String) : DocxException(message)

/** The file or one of its parts exceeds the configured size limits. */
class DocxTooLargeException(message: String) : DocxException(message)

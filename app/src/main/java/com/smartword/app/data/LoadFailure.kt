package com.smartword.app.data

import com.smartword.docx.DocxException
import com.smartword.docx.DocxTooLargeException
import com.smartword.docx.EncryptedDocxException
import com.smartword.docx.InvalidDocxException
import java.io.FileNotFoundException

/** Why a document could not be opened or saved, in terms the screen can explain. */
enum class FailureKind { ENCRYPTED, TOO_LARGE, INVALID, NOT_FOUND, NO_ACCESS, OTHER }

object LoadFailure {
    fun classify(error: Throwable): FailureKind = when (error) {
        is EncryptedDocxException -> FailureKind.ENCRYPTED
        is DocxTooLargeException -> FailureKind.TOO_LARGE
        is InvalidDocxException -> FailureKind.INVALID
        is DocxException -> FailureKind.INVALID
        is FileNotFoundException -> FailureKind.NOT_FOUND
        is SecurityException -> FailureKind.NO_ACCESS
        is OutOfMemoryError -> FailureKind.TOO_LARGE
        else -> FailureKind.OTHER
    }
}

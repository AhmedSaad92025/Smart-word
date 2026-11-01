package com.smartword.app.data

import com.smartword.docx.DocxTooLargeException
import com.smartword.docx.EncryptedDocxException
import com.smartword.docx.InvalidDocxException
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.FileNotFoundException
import java.io.IOException

class LoadFailureTest {
    @Test
    fun errorsAreClassifiedForTheScreen() {
        assertEquals(FailureKind.ENCRYPTED, LoadFailure.classify(EncryptedDocxException("x")))
        assertEquals(FailureKind.TOO_LARGE, LoadFailure.classify(DocxTooLargeException("x")))
        assertEquals(FailureKind.INVALID, LoadFailure.classify(InvalidDocxException("x")))
        assertEquals(FailureKind.NOT_FOUND, LoadFailure.classify(FileNotFoundException()))
        assertEquals(FailureKind.NO_ACCESS, LoadFailure.classify(SecurityException()))
        assertEquals(FailureKind.TOO_LARGE, LoadFailure.classify(OutOfMemoryError()))
        assertEquals(FailureKind.OTHER, LoadFailure.classify(IOException("disk")))
    }
}

package com.smartword.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FileNamesTest {
    @Test
    fun titleDropsTheExtensionInAnyCase() {
        assertEquals("تقرير", FileNames.title("تقرير.docx"))
        assertEquals("Report", FileNames.title("Report.DOCX"))
        assertEquals("notes.txt", FileNames.title("notes.txt"))
        assertEquals(".docx", FileNames.title(".docx"))
    }

    @Test
    fun savingNamesAddTheExtensionOnceAndRemoveForbiddenCharacters() {
        assertEquals("تقرير.docx", FileNames.forSaving("تقرير"))
        assertEquals("تقرير.docx", FileNames.forSaving("تقرير.docx"))
        assertEquals("a_b_c.docx", FileNames.forSaving("a/b:c"))
        assertEquals("مستند جديد.docx", FileNames.forSaving("   "))
        assertEquals("مستند جديد.docx", FileNames.forSaving("..."))
    }
}

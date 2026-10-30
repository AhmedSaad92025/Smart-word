package com.smartword.docx

import org.junit.Assert.assertEquals
import org.junit.Test

class DocxModuleTest {
    @Test
    fun moduleName_isStable() {
        assertEquals("docx-engine", DocxModule.NAME)
    }
}

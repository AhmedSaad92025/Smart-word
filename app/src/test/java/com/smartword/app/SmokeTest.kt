package com.smartword.app

import org.junit.Assert.assertEquals
import org.junit.Test

class SmokeTest {
    @Test
    fun packageName_isExpected() {
        assertEquals("com.smartword.app", MainActivity::class.java.`package`?.name)
    }
}

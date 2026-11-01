package com.smartword.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DisplayHelpersTest {

    @Test
    fun colorsBecomeOpaqueArgb() {
        assertEquals(0xFFFF8800L, HexColor.toArgb("FF8800"))
        assertEquals(0xFF2F5496L, HexColor.toArgb("2f5496"))
        assertEquals(0xFF000000L, HexColor.toArgb("000000"))
    }

    @Test
    fun automaticOrBrokenColorsGiveNull() {
        assertNull(HexColor.toArgb(null))
        assertNull(HexColor.toArgb(""))
        assertNull(HexColor.toArgb("FFF"))
        assertNull(HexColor.toArgb("GG0000"))
        assertNull(HexColor.toArgb("+12345"))
        assertNull(HexColor.toArgb("1234567"))
    }

    @Test
    fun fontsMapToTheNearestFamily() {
        assertEquals(FontKind.SANS, FontMapping.kindOf("Calibri"))
        assertEquals(FontKind.SANS, FontMapping.kindOf("Arial"))
        assertEquals(FontKind.SERIF, FontMapping.kindOf("Times New Roman"))
        assertEquals(FontKind.SERIF, FontMapping.kindOf("Traditional Arabic"))
        assertEquals(FontKind.MONO, FontMapping.kindOf("Courier New"))
        assertEquals(FontKind.MONO, FontMapping.kindOf("Consolas"))
    }

    @Test
    fun unknownOrMissingFontsUseTheDefault() {
        assertEquals(FontKind.DEFAULT, FontMapping.kindOf(null))
        assertEquals(FontKind.DEFAULT, FontMapping.kindOf("  "))
        assertEquals(FontKind.DEFAULT, FontMapping.kindOf("Some Rare Font"))
    }
}

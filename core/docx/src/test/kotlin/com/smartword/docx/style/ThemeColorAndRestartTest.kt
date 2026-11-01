package com.smartword.docx.style

import com.smartword.docx.DocxAssertions
import com.smartword.docx.DocxFixtures.para
import com.smartword.docx.DocxFixtures.run
import com.smartword.docx.StyledFixtures
import com.smartword.docx.StyledFixtures.abstractNum
import com.smartword.docx.StyledFixtures.lvl
import com.smartword.docx.StyledFixtures.normal
import com.smartword.docx.StyledFixtures.num
import com.smartword.docx.StyledFixtures.numPr
import com.smartword.docx.StyledFixtures.numberingXml
import com.smartword.docx.StyledFixtures.style
import com.smartword.docx.StyledFixtures.stylesXml
import com.smartword.docx.model.DocxDocument
import com.smartword.docx.model.DocxParagraph
import com.smartword.docx.model.RunFormat
import com.smartword.docx.model.TextRun
import com.smartword.docx.pkg.DocxPackage
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Theme colors (with tint and shade) and the lvlRestart rule of multi-level lists. */
class ThemeColorAndRestartTest {

    private val theme = StyledFixtures.themeXml()

    private fun docOf(body: String, styles: String, numbering: String? = null) =
        DocxDocument.open(StyledFixtures.docx(body, styles = styles, numbering = numbering, theme = theme))

    private fun colorOf(p: DocxParagraph, index: Int = 0): String? =
        p.effectiveFormat(p.inlines.filterIsInstance<TextRun>()[index]).colorHex

    private fun coloredRun(attrs: String) =
        "<w:r><w:rPr><w:color $attrs/></w:rPr><w:t>x</w:t></w:r>"

    private fun channelsClose(expected: String, actual: String?, tolerance: Int = 2) {
        assertTrue("color is $actual", actual != null && actual.length == 6)
        for (i in 0 until 3) {
            val e = expected.substring(i * 2, i * 2 + 2).toInt(16)
            val a = actual!!.substring(i * 2, i * 2 + 2).toInt(16)
            assertTrue("$actual is not close to $expected", Math.abs(e - a) <= tolerance)
        }
    }

    // ---- theme colors ----

    @Test
    fun themeColorsAreResolvedFromTheTheme() {
        val doc = docOf(
            para(coloredRun("w:val=\"000000\" w:themeColor=\"accent2\"")) +
                para(coloredRun("w:themeColor=\"text2\"")) +
                para(coloredRun("w:val=\"auto\" w:themeColor=\"hyperlink\"")),
            stylesXml(normal()),
        )
        assertEquals("ED7D31", colorOf(doc.paragraphs[0]))
        assertEquals("44546A", colorOf(doc.paragraphs[1]))
        assertEquals("0563C1", colorOf(doc.paragraphs[2]))
    }

    @Test
    fun aPlainValueIsUsedWhenThereIsNoThemeReferenceOrNoTheme() {
        val doc = docOf(para(coloredRun("w:val=\"ff0000\"")), stylesXml(normal()))
        assertEquals("FF0000", colorOf(doc.paragraphs[0]))
        val noTheme = DocxDocument.open(
            StyledFixtures.docx(
                para(coloredRun("w:val=\"2F5496\" w:themeColor=\"accent1\"")),
                styles = stylesXml(normal()),
            ),
        )
        assertEquals("2F5496", colorOf(noTheme.paragraphs[0]))
    }

    @Test
    fun automaticColorStaysNull() {
        val doc = docOf(para(coloredRun("w:val=\"auto\"")), stylesXml(normal()))
        assertNull(colorOf(doc.paragraphs[0]))
    }

    @Test
    fun shadeDarkensLikeWordsHeadingColor() {
        // Word's built-in "Heading 1" color: accent1 (4472C4) with themeShade BF is 2F5496.
        val doc = docOf(
            para(coloredRun("w:val=\"2F5496\" w:themeColor=\"accent1\" w:themeShade=\"BF\"")),
            stylesXml(normal()),
        )
        channelsClose("2F5496", colorOf(doc.paragraphs[0]))
    }

    @Test
    fun tintLightensLikeWordsLighterPalette() {
        // accent1 with themeTint 99 is the "lighter 40 percent" swatch, 8FAADC.
        val doc = docOf(
            para(coloredRun("w:themeColor=\"accent1\" w:themeTint=\"99\"")),
            stylesXml(normal()),
        )
        channelsClose("8FAADC", colorOf(doc.paragraphs[0]), 4)
    }

    @Test
    fun themeColorsInStylesAreResolvedAndDirectColorWins() {
        val styles = stylesXml(
            normal(),
            style(
                "Heading1", "heading 1", basedOn = "Normal",
                rPr = "<w:color w:val=\"2F5496\" w:themeColor=\"accent1\" w:themeShade=\"BF\"/>",
            ),
        )
        val doc = docOf(
            para(run("a"), "<w:pStyle w:val=\"Heading1\"/>") +
                para(coloredRun("w:val=\"00FF00\""), "<w:pStyle w:val=\"Heading1\"/>"),
            styles,
        )
        channelsClose("2F5496", colorOf(doc.paragraphs[0]))
        assertEquals("00FF00", colorOf(doc.paragraphs[1]))
    }

    @Test
    fun settingAColorOverAThemeColorReplacesTheThemeReference() {
        val doc = docOf(para(coloredRun("w:val=\"000000\" w:themeColor=\"accent2\"")), stylesXml(normal()))
        val p = doc.paragraphs[0]
        p.applyFormat(0, 1, RunFormat(colorHex = "112233"))
        val bytes = doc.toByteArray()
        DocxAssertions.assertSchemaOrder(bytes)
        val saved = DocxDocument.open(bytes).paragraphs[0]
        assertEquals("112233", colorOf(saved))
    }

    @Test
    fun themeColorsAndFormattingSurviveEditingElsewhere() {
        val original = StyledFixtures.docx(
            para(coloredRun("w:val=\"ED7D31\" w:themeColor=\"accent2\"")) + para(run("other")),
            styles = stylesXml(normal()), theme = theme,
        )
        val doc = DocxDocument.open(original)
        doc.paragraphs[1].insertText(0, "edited ")
        val saved = doc.toByteArray()
        val before = DocxPackage.read(original)
        val after = DocxPackage.read(saved)
        for (part in before.parts) {
            if (part.name == "word/document.xml") continue
            assertArrayEquals(part.name, part.bytes, after.find(part.name)!!.bytes)
        }
        // The untouched paragraph keeps its original theme reference byte for byte.
        assertTrue(DocxAssertions.mainXml(DocxDocument.open(saved)).contains("w:themeColor=\"accent2\""))
        assertEquals("ED7D31", colorOf(DocxDocument.open(saved).paragraphs[0]))
    }

    @Test
    fun aBrokenThemeDoesNotBreakColors() {
        val doc = DocxDocument.open(
            StyledFixtures.docx(
                para(coloredRun("w:val=\"123456\" w:themeColor=\"accent1\"")),
                styles = stylesXml(normal()),
                theme = "<not-a-theme/>",
            ),
        )
        assertEquals("123456", colorOf(doc.paragraphs[0]))
    }

    // ---- lvlRestart ----

    private fun item(ilvl: Int, numId: Int = 1) = para(run("t"), numPr(numId, ilvl))

    private fun labels(numbering: String, vararg levels: Int): List<String?> {
        val doc = docOf(levels.joinToString("") { item(it) }, stylesXml(normal()), numbering)
        val map = doc.listLabels()
        return doc.paragraphs.map { map[it]?.text }
    }

    @Test
    fun levelsRestartAfterAHigherLevelByDefault() {
        val numbering = numberingXml(
            abstractNum(0, lvl(0, "decimal", "%1."), lvl(1, "decimal", "%1.%2")),
            num(1, 0),
        )
        assertEquals(listOf("1.", "1.1", "1.2", "2.", "2.1"), labels(numbering, 0, 1, 1, 0, 1))
    }

    @Test
    fun lvlRestartZeroNeverRestarts() {
        val numbering = numberingXml(
            abstractNum(0, lvl(0, "decimal", "%1."), lvl(1, "lowerLetter", "%2)", restart = 0)),
            num(1, 0),
        )
        assertEquals(listOf("1.", "a)", "b)", "2.", "c)"), labels(numbering, 0, 1, 1, 0, 1))
    }

    @Test
    fun lvlRestartCanNameTheLevelAfterWhichToRestart() {
        // Level 3 restarts only after level 1, not after level 2.
        val numbering = numberingXml(
            abstractNum(
                0,
                lvl(0, "decimal", "%1."),
                lvl(1, "lowerLetter", "%2)"),
                lvl(2, "lowerRoman", "%3.", restart = 1),
            ),
            num(1, 0),
        )
        assertEquals(
            listOf("1.", "a)", "i.", "b)", "ii.", "2.", "a)", "i."),
            labels(numbering, 0, 1, 2, 1, 2, 0, 1, 2),
        )
    }

    @Test
    fun lvlRestartOneStillRestartsTheLevelAfterTheTopLevel() {
        val numbering = numberingXml(
            abstractNum(0, lvl(0, "decimal", "%1."), lvl(1, "decimal", "%2.", restart = 1)),
            num(1, 0),
        )
        assertEquals(listOf("1.", "1.", "2.", "2.", "1.", "3."), labels(numbering, 0, 1, 1, 0, 1, 0))
    }
}

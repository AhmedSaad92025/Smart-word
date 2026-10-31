package com.smartword.docx.style

import com.smartword.docx.DocxFixtures.para
import com.smartword.docx.DocxFixtures.run
import com.smartword.docx.StyledFixtures
import com.smartword.docx.StyledFixtures.abstractNum
import com.smartword.docx.StyledFixtures.lvl
import com.smartword.docx.StyledFixtures.normal
import com.smartword.docx.StyledFixtures.num
import com.smartword.docx.StyledFixtures.numPr
import com.smartword.docx.StyledFixtures.numberingXml
import com.smartword.docx.StyledFixtures.startOverride
import com.smartword.docx.StyledFixtures.style
import com.smartword.docx.StyledFixtures.stylesXml
import com.smartword.docx.model.DocxDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NumberingTest {

    private fun docOf(body: String, numbering: String, styles: String = stylesXml(normal())) =
        DocxDocument.open(StyledFixtures.docx(body, styles = styles, numbering = numbering))

    private fun item(text: String, numId: Int, ilvl: Int? = null, extra: String = "") =
        para(run(text), numPr(numId, ilvl) + extra)

    // ---- number formats ----

    @Test
    fun decimalAndZeroPaddedNumbers() {
        assertEquals("7", NumberFormats.format(7, "decimal"))
        assertEquals("05", NumberFormats.format(5, "decimalZero"))
        assertEquals("12", NumberFormats.format(12, "decimalZero"))
    }

    @Test
    fun latinLettersRepeatAfterZ() {
        assertEquals("a", NumberFormats.format(1, "lowerLetter"))
        assertEquals("z", NumberFormats.format(26, "lowerLetter"))
        assertEquals("aa", NumberFormats.format(27, "lowerLetter"))
        assertEquals("zz", NumberFormats.format(52, "lowerLetter"))
        assertEquals("aaa", NumberFormats.format(53, "lowerLetter"))
        assertEquals("C", NumberFormats.format(3, "upperLetter"))
    }

    @Test
    fun romanNumerals() {
        assertEquals("IV", NumberFormats.format(4, "upperRoman"))
        assertEquals("IX", NumberFormats.format(9, "upperRoman"))
        assertEquals("XIV", NumberFormats.format(14, "upperRoman"))
        assertEquals("MCMXCIV", NumberFormats.format(1994, "upperRoman"))
        assertEquals("iv", NumberFormats.format(4, "lowerRoman"))
        assertEquals("5000", NumberFormats.format(5000, "upperRoman"))
    }

    @Test
    fun arabicAlphabeticalLetters() {
        assertEquals("أ", NumberFormats.format(1, "arabicAlpha"))
        assertEquals("ب", NumberFormats.format(2, "arabicAlpha"))
        assertEquals("ت", NumberFormats.format(3, "arabicAlpha"))
        assertEquals("ي", NumberFormats.format(28, "arabicAlpha"))
        assertEquals("أأ", NumberFormats.format(29, "arabicAlpha"))
    }

    @Test
    fun arabicAbjadLetters() {
        assertEquals("أ", NumberFormats.format(1, "arabicAbjad"))
        assertEquals("ب", NumberFormats.format(2, "arabicAbjad"))
        assertEquals("ج", NumberFormats.format(3, "arabicAbjad"))
        assertEquals("د", NumberFormats.format(4, "arabicAbjad"))
    }

    @Test
    fun easternArabicDigits() {
        assertEquals("١٢٣", NumberFormats.format(123, "hindiNumbers"))
        assertEquals("٠", NumberFormats.format(0, "hindiNumbers"))
    }

    @Test
    fun englishOrdinals() {
        val expected = mapOf(1 to "1st", 2 to "2nd", 3 to "3rd", 4 to "4th", 11 to "11th", 12 to "12th", 13 to "13th", 21 to "21st", 112 to "112th")
        for ((n, text) in expected) assertEquals(text, NumberFormats.format(n, "ordinal"))
    }

    @Test
    fun unknownFormatsFallBackToDecimalAndNoneIsEmpty() {
        assertEquals("3", NumberFormats.format(3, "someFutureFormat"))
        assertEquals("", NumberFormats.format(3, "none"))
    }

    @Test
    fun bulletCharactersFromSymbolFontsAreMapped() {
        assertEquals("•", NumberFormats.bullet(""))
        assertEquals("▪", NumberFormats.bullet(""))
        assertEquals("•", NumberFormats.bullet(""))
        assertEquals("•", NumberFormats.bullet(""))
        assertEquals("◦", NumberFormats.bullet("o"))
        assertEquals("–", NumberFormats.bullet("–"))
    }

    // ---- labels in documents ----

    private val numbering = numberingXml(
        abstractNum(
            0,
            lvl(0, "decimal", "%1.", pPr = "<w:ind w:left=\"720\" w:hanging=\"360\"/>"),
            lvl(1, "lowerLetter", "%2)"),
            lvl(2, "lowerRoman", "%3."),
        ),
        abstractNum(1, lvl(0, "bullet", "")),
        abstractNum(2, lvl(0, "upperRoman", "%1."), lvl(1, "decimal", "%1.%2")),
        num(1, 0),
        num(2, 1),
        num(3, 0, startOverride(0, 5)),
        num(4, 2),
    )

    @Test
    fun numbersCountAndRestartDeeperLevels() {
        val body = item("a", 1, 0) + item("b", 1, 0) + item("c", 1, 1) + item("d", 1, 1) +
            item("e", 1, 2) + item("f", 1, 0) + item("g", 1, 1)
        val doc = docOf(body, numbering)
        val labels = doc.listLabels()
        assertEquals(
            listOf("1.", "2.", "a)", "b)", "i.", "3.", "a)"),
            doc.paragraphs.map { labels[it]?.text },
        )
        assertEquals(listOf(0, 0, 1, 1, 2, 0, 1), doc.paragraphs.map { labels[it]?.level })
    }

    @Test
    fun bulletsAreMarkedAndMapped() {
        val doc = docOf(item("x", 2, 0), numbering)
        val label = doc.listLabels()[doc.paragraphs[0]]
        assertNotNull(label)
        assertTrue(label!!.isBullet)
        assertEquals("•", label.text)
        assertEquals(2, label.numId)
    }

    @Test
    fun plainParagraphsAndNumberZeroAreNotListItems() {
        val body = para(run("plain")) + item("off", 0, 0) + item("on", 1, 0) + item("missing", 99, 0)
        val doc = docOf(body, numbering)
        val labels = doc.listLabels()
        assertNull(labels[doc.paragraphs[0]])
        assertNull(labels[doc.paragraphs[1]])
        assertEquals("1.", labels[doc.paragraphs[2]]?.text)
        assertNull(labels[doc.paragraphs[3]])
        assertNull(doc.paragraphs[0].format.listId)
        assertNull(doc.paragraphs[1].format.listId)
        assertEquals(1, doc.paragraphs[2].format.listId)
        assertEquals(0, doc.paragraphs[2].format.listLevel)
    }

    @Test
    fun listInstancesSharingADefinitionContinueTheSequenceUnlessOverridden() {
        val body = item("a", 1, 0) + item("b", 1, 0) + item("c", 3, 0) + item("d", 1, 0) + item("e", 3, 0)
        val doc = docOf(body, numbering)
        val labels = doc.listLabels()
        // numId 3 starts at 5 the first time it is used; afterwards all instances keep counting.
        assertEquals(listOf("1.", "2.", "5.", "6.", "7."), doc.paragraphs.map { labels[it]?.text })
    }

    @Test
    fun bulletsDoNotDisturbNumbering() {
        val body = item("a", 1, 0) + item("bullet", 2, 0) + item("b", 1, 0)
        val doc = docOf(body, numbering)
        val labels = doc.listLabels()
        assertEquals(listOf("1.", "•", "2."), doc.paragraphs.map { labels[it]?.text })
    }

    @Test
    fun multiLevelLabelsCombineParentNumbers() {
        val body = item("a", 4, 0) + item("b", 4, 1) + item("c", 4, 1) + item("d", 4, 0) + item("e", 4, 1)
        val doc = docOf(body, numbering)
        val labels = doc.listLabels()
        assertEquals(listOf("I.", "I.1", "I.2", "II.", "II.1"), doc.paragraphs.map { labels[it]?.text })
    }

    @Test
    fun legalNumberingShowsParentLevelsAsDecimals() {
        val legal = numberingXml(
            abstractNum(0, lvl(0, "upperRoman", "%1."), lvl(1, "decimal", "%1.%2", isLgl = true)),
            num(1, 0),
        )
        val doc = docOf(item("a", 1, 0) + item("b", 1, 0) + item("c", 1, 1), legal)
        val labels = doc.listLabels()
        assertEquals("2.1", labels[doc.paragraphs[2]]?.text)
    }

    @Test
    fun startValuesOtherThanOneAreHonoured() {
        val custom = numberingXml(abstractNum(0, lvl(0, "decimal", "%1.", start = 10)), num(1, 0))
        val doc = docOf(item("a", 1, 0) + item("b", 1, 0), custom)
        val labels = doc.listLabels()
        assertEquals(listOf("10.", "11."), doc.paragraphs.map { labels[it]?.text })
    }

    @Test
    fun levelOverridesCanReplaceTheLevelDefinition() {
        val overridden = numberingXml(
            abstractNum(0, lvl(0, "decimal", "%1.")),
            num(
                1, 0,
                "<w:lvlOverride w:ilvl=\"0\">" + lvl(0, "upperLetter", "%1:") + "</w:lvlOverride>",
            ),
        )
        val doc = docOf(item("a", 1, 0) + item("b", 1, 0), overridden)
        val labels = doc.listLabels()
        assertEquals(listOf("A:", "B:"), doc.paragraphs.map { labels[it]?.text })
    }

    @Test
    fun levelsDefaultToZeroWhenTheParagraphGivesNone() {
        val doc = docOf(item("a", 1), numbering)
        assertEquals(0, doc.listLabels()[doc.paragraphs[0]]?.level)
    }

    @Test
    fun theIndentOfTheListLevelAppliesAndDirectIndentOverridesIt() {
        val body = item("a", 1, 0) + item("b", 1, 0, "<w:ind w:left=\"1440\"/>")
        val doc = docOf(body, numbering)
        val first = doc.paragraphs[0].format
        assertEquals(36f, first.indentStartPt, 0.001f)
        assertEquals(-18f, first.firstLineIndentPt, 0.001f)
        val second = doc.paragraphs[1].format
        assertEquals(72f, second.indentStartPt, 0.001f)
        assertEquals(-18f, second.firstLineIndentPt, 0.001f)
    }

    // ---- lists defined through styles ----

    @Test
    fun aParagraphStyleCanCarryTheNumbering() {
        val styles = stylesXml(
            normal(),
            style("ListNumber", "List Number", basedOn = "Normal", pPr = numPr(5)),
            style("ListNumber2", "List Number 2", basedOn = "Normal", pPr = numPr(5)),
        )
        val styled = numberingXml(
            abstractNum(
                3,
                lvl(0, "decimal", "%1.", pStyle = "ListNumber"),
                lvl(1, "lowerLetter", "%2)", pStyle = "ListNumber2"),
            ),
            num(5, 3),
        )
        val body = para(run("one"), "<w:pStyle w:val=\"ListNumber\"/>") +
            para(run("two"), "<w:pStyle w:val=\"ListNumber\"/>") +
            para(run("sub"), "<w:pStyle w:val=\"ListNumber2\"/>")
        val doc = docOf(body, styled, styles)
        val labels = doc.listLabels()
        assertEquals(listOf("1.", "2.", "a)"), doc.paragraphs.map { labels[it]?.text })
        assertEquals(1, doc.paragraphs[2].format.listLevel)
    }

    @Test
    fun directNumberingZeroTurnsOffNumberingFromTheStyle() {
        val styles = stylesXml(normal(), style("ListNumber", "List Number", basedOn = "Normal", pPr = numPr(5, 0)))
        val styled = numberingXml(abstractNum(3, lvl(0, "decimal", "%1.")), num(5, 3))
        val body = para(run("off"), "<w:pStyle w:val=\"ListNumber\"/>" + numPr(0))
        val doc = docOf(body, styled, styles)
        assertTrue(doc.listLabels().isEmpty())
    }

    // ---- Arabic lists ----

    @Test
    fun arabicListsUseArabicLetterAndDigitFormats() {
        val arabic = numberingXml(
            abstractNum(0, lvl(0, "arabicAlpha", "%1-")),
            abstractNum(1, lvl(0, "hindiNumbers", "%1.")),
            abstractNum(2, lvl(0, "arabicAbjad", "(%1)")),
            num(1, 0), num(2, 1), num(3, 2),
        )
        val body = item("أ", 1, 0, "<w:bidi/>") + item("ب", 1, 0, "<w:bidi/>") +
            item("ج", 2, 0, "<w:bidi/>") + item("د", 2, 0, "<w:bidi/>") +
            item("هـ", 3, 0, "<w:bidi/>") + item("و", 3, 0, "<w:bidi/>")
        val doc = docOf(body, arabic)
        val labels = doc.listLabels()
        assertEquals(listOf("أ-", "ب-", "١.", "٢.", "(أ)", "(ب)"), doc.paragraphs.map { labels[it]?.text })
        assertTrue(doc.paragraphs.all { it.isRtl })
    }

    // ---- editing keeps list membership ----

    @Test
    fun splittingAListItemKeepsBothPartsInTheList() {
        val doc = docOf(item("hello world", 1, 0), numbering)
        doc.splitParagraph(doc.paragraphs[0], 5)
        val saved = DocxDocument.open(doc.toByteArray())
        val labels = saved.listLabels()
        assertEquals(listOf("1.", "2."), saved.paragraphs.map { labels[it]?.text })
    }

    @Test
    fun noListsMeansNoLabels() {
        val doc = DocxDocument.open(StyledFixtures.docx(para(run("x"))))
        assertTrue(doc.listLabels().isEmpty())
        assertFalse(doc.styles.styles.isEmpty().not())
    }
}

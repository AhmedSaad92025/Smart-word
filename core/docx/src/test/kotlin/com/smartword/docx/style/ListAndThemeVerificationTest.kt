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
import com.smartword.docx.StyledFixtures.startOverride
import com.smartword.docx.StyledFixtures.style
import com.smartword.docx.StyledFixtures.stylesXml
import com.smartword.docx.model.DocxDocument
import com.smartword.docx.model.TextRun
import com.smartword.docx.pkg.DocxPackage
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Extra verification: deep multi-level lists, Arabic RTL lists, theme palettes and untouched originals. */
class ListAndThemeVerificationTest {

    private val ind0 = "<w:ind w:left=\"720\" w:hanging=\"360\"/>"
    private val ind1 = "<w:ind w:left=\"1440\" w:hanging=\"360\"/>"

    private fun docOf(body: String, numbering: String, styles: String = stylesXml(normal()), theme: String? = null) =
        DocxDocument.open(StyledFixtures.docx(body, styles = styles, numbering = numbering, theme = theme))

    private fun item(numId: Int, ilvl: Int, extra: String = "") = para(run("t"), numPr(numId, ilvl) + extra)

    private fun labelsOf(doc: DocxDocument): List<String?> {
        val map = doc.listLabels()
        return doc.paragraphs.map { map[it]?.text }
    }

    // ---- deep multi-level lists ----

    private val threeLevels = abstractNum(
        0,
        lvl(0, "decimal", "%1."),
        lvl(1, "decimal", "%1.%2"),
        lvl(2, "decimal", "%1.%2.%3"),
    )

    @Test
    fun threeLevelOutlineNumbersAndRestarts() {
        val doc = docOf(
            listOf(0, 1, 2, 2, 1, 2, 0, 1, 2).joinToString("") { item(1, it) },
            numberingXml(threeLevels, num(1, 0)),
        )
        assertEquals(
            listOf("1.", "1.1", "1.1.1", "1.1.2", "1.2", "1.2.1", "2.", "2.1", "2.1.1"),
            labelsOf(doc),
        )
    }

    @Test
    fun skippingALevelStartsTheSkippedLevelAtItsStartValue() {
        val doc = docOf(
            listOf(0, 2, 2).joinToString("") { item(1, it) },
            numberingXml(threeLevels, num(1, 0)),
        )
        assertEquals(listOf("1.", "1.1.1", "1.1.2"), labelsOf(doc))
    }

    @Test
    fun aLevelStartValueAppliesAfterEveryRestart() {
        val numbering = numberingXml(
            abstractNum(0, lvl(0, "decimal", "%1."), lvl(1, "decimal", "%2.", start = 5)),
            num(1, 0),
        )
        val doc = docOf(listOf(0, 1, 1, 0, 1).joinToString("") { item(1, it) }, numbering)
        assertEquals(listOf("1.", "5.", "6.", "2.", "5."), labelsOf(doc))
    }

    @Test
    fun restartAfterTheSecondLevelBehavesLikeTheDefaultInAThreeLevelList() {
        val numbering = numberingXml(
            abstractNum(
                0,
                lvl(0, "decimal", "%1."),
                lvl(1, "decimal", "%1.%2"),
                lvl(2, "decimal", "%1.%2.%3", restart = 2),
            ),
            num(1, 0),
        )
        val doc = docOf(listOf(0, 1, 2, 2, 1, 2, 0, 1, 2).joinToString("") { item(1, it) }, numbering)
        assertEquals(
            listOf("1.", "1.1", "1.1.1", "1.1.2", "1.2", "1.2.1", "2.", "2.1", "2.1.1"),
            labelsOf(doc),
        )
    }

    @Test
    fun lvlRestartZeroOnTheThirdLevelKeepsCountingAcrossBothHigherLevels() {
        val numbering = numberingXml(
            abstractNum(
                0,
                lvl(0, "decimal", "%1."),
                lvl(1, "lowerLetter", "%2)"),
                lvl(2, "decimal", "%3.", restart = 0),
            ),
            num(1, 0),
        )
        val doc = docOf(listOf(0, 1, 2, 1, 2, 0, 1, 2).joinToString("") { item(1, it) }, numbering)
        assertEquals(listOf("1.", "a)", "1.", "b)", "2.", "2.", "a)", "3."), labelsOf(doc))
    }

    @Test
    fun aLevelOverrideCanCarryItsOwnRestartRule() {
        val override = "<w:lvlOverride w:ilvl=\"1\">" +
            lvl(1, "decimal", "%2.", restart = 0) + "</w:lvlOverride>"
        val numbering = numberingXml(
            abstractNum(0, lvl(0, "decimal", "%1."), lvl(1, "decimal", "%2.")),
            num(1, 0),
            num(2, 0, override),
        )
        val plain = labelsOf(docOf(listOf(0, 1, 0, 1).joinToString("") { item(1, it) }, numbering))
        assertEquals(listOf("1.", "1.", "2.", "1."), plain)
        val overridden = labelsOf(docOf(listOf(0, 1, 0, 1).joinToString("") { item(2, it) }, numbering))
        assertEquals(listOf("1.", "1.", "2.", "2."), overridden)
    }

    @Test
    fun aSecondInstanceContinuesTheHigherLevelAndRestartsTheLowerOnes() {
        val numbering = numberingXml(
            abstractNum(0, lvl(0, "decimal", "%1."), lvl(1, "lowerLetter", "%2)")),
            num(1, 0),
            num(2, 0),
        )
        val body = item(1, 0) + item(1, 1) + item(1, 1) + item(2, 0) + item(2, 1)
        assertEquals(listOf("1.", "a)", "b)", "2.", "a)"), labelsOf(docOf(body, numbering)))
    }

    @Test
    fun aStartOverrideOnALowerLevelAppliesOnFirstUse() {
        val numbering = numberingXml(
            abstractNum(0, lvl(0, "decimal", "%1."), lvl(1, "decimal", "%2.")),
            num(1, 0, startOverride(1, 7)),
        )
        val doc = docOf(listOf(0, 1, 1).joinToString("") { item(1, it) }, numbering)
        assertEquals(listOf("1.", "7.", "8."), labelsOf(doc))
    }

    @Test
    fun bulletsMixedIntoANumberedOutlineDoNotChangeTheNumbers() {
        val numbering = numberingXml(
            abstractNum(0, lvl(0, "decimal", "%1."), lvl(1, "decimal", "%1.%2")),
            abstractNum(1, lvl(0, "bullet", "•")),
            num(1, 0),
            num(2, 1),
        )
        val body = item(1, 0) + item(2, 0) + item(1, 1) + item(2, 0) + item(1, 1) + item(1, 0)
        val labels = labelsOf(docOf(body, numbering))
        assertEquals(listOf("1.", "•", "1.1", "•", "1.2", "2."), labels)
    }

    @Test
    fun levelsLinkedToParagraphStylesNumberWithoutAnyDirectNumPr() {
        val styles = stylesXml(
            normal(),
            style("Chapter", "Chapter", basedOn = "Normal", pPr = "<w:numPr><w:numId w:val=\"1\"/></w:numPr>"),
            style(
                "Section", "Section", basedOn = "Normal",
                pPr = "<w:numPr><w:ilvl w:val=\"1\"/><w:numId w:val=\"1\"/></w:numPr>",
            ),
        )
        val numbering = numberingXml(
            abstractNum(
                0,
                lvl(0, "decimal", "%1.", pStyle = "Chapter"),
                lvl(1, "decimal", "%1.%2", pStyle = "Section"),
            ),
            num(1, 0),
        )
        val body = para(run("a"), "<w:pStyle w:val=\"Chapter\"/>") +
            para(run("b"), "<w:pStyle w:val=\"Section\"/>") +
            para(run("c"), "<w:pStyle w:val=\"Section\"/>") +
            para(run("d"), "<w:pStyle w:val=\"Chapter\"/>")
        assertEquals(listOf("1.", "1.1", "1.2", "2."), labelsOf(docOf(body, numbering, styles)))
    }

    // ---- Arabic right-to-left lists ----

    private val arabicOutline = abstractNum(
        0,
        lvl(0, "arabicAbjad", "%1-", pPr = ind0),
        lvl(1, "hindiNumbers", "%1.%2", pPr = ind1),
    )

    @Test
    fun arabicOutlineLabelsInRightToLeftParagraphs() {
        val body = listOf(0, 1, 1, 0, 1).joinToString("") { item(1, it, "<w:bidi/>") }
        val doc = docOf(body, numberingXml(arabicOutline, num(1, 0)))
        assertEquals(listOf("أ-", "أ.١", "أ.٢", "ب-", "ب.١"), labelsOf(doc))
        assertTrue(doc.paragraphs.all { it.isRtl })
    }

    @Test
    fun arabicListsReportTheStartSideIndentOfTheLevel() {
        val body = item(1, 0, "<w:bidi/>") + item(1, 1, "<w:bidi/>")
        val doc = docOf(body, numberingXml(arabicOutline, num(1, 0)))
        assertEquals(36f, doc.paragraphs[0].format.indentStartPt, 0.001f)
        assertEquals(0f, doc.paragraphs[0].format.indentEndPt, 0.001f)
        assertEquals(72f, doc.paragraphs[1].format.indentStartPt, 0.001f)
    }

    @Test
    fun arabicAlphaAndAbjadListsContinueThroughManyItems() {
        val numbering = numberingXml(
            abstractNum(0, lvl(0, "arabicAlpha", "%1.")),
            abstractNum(1, lvl(0, "arabicAbjad", "%1.")),
            num(1, 0),
            num(2, 1),
        )
        val doc = docOf(List(5) { item(1, 0, "<w:bidi/>") }.joinToString("") + List(5) { item(2, 0, "<w:bidi/>") }.joinToString(""), numbering)
        assertEquals(
            listOf("أ.", "ب.", "ت.", "ث.", "ج.", "أ.", "ب.", "ج.", "د.", "هـ."),
            labelsOf(doc),
        )
    }

    @Test
    fun arabicListTextCanBeEditedWithoutChangingItsLabelOrDirection() {
        val body = para(run("عنصر"), numPr(1, 0) + "<w:bidi/>") + para(run("آخر"), numPr(1, 0) + "<w:bidi/>")
        val doc = docOf(body, numberingXml(arabicOutline, num(1, 0)))
        doc.paragraphs[0].insertText(4, " أول")
        val saved = DocxDocument.open(doc.toByteArray())
        assertEquals("عنصر أول", saved.paragraphs[0].text)
        assertEquals(listOf("أ-", "ب-"), labelsOf(saved))
        assertTrue(saved.paragraphs.all { it.isRtl })
    }

    @Test
    fun splittingAListParagraphKeepsTheListOnBothParts() {
        val doc = docOf(
            para(run("عنصر كبير"), numPr(1, 0) + "<w:bidi/>"),
            numberingXml(arabicOutline, num(1, 0)),
        )
        doc.splitParagraph(doc.paragraphs[0], 4)
        val saved = DocxDocument.open(doc.toByteArray())
        assertEquals(listOf("أ-", "ب-"), labelsOf(saved))
    }

    // ---- theme palettes ----

    private fun themeWith(accent1: String, dk2: String = "44546A"): String =
        StyledFixtures.themeXml().replace("4472C4", accent1).replace("44546A", dk2)

    private fun colorOfFirst(doc: DocxDocument): String? =
        doc.paragraphs[0].let { it.effectiveFormat(it.inlines.filterIsInstance<TextRun>()[0]).colorHex }

    private val accentRun = "<w:r><w:rPr><w:color w:val=\"4472C4\" w:themeColor=\"accent1\"/></w:rPr><w:t>x</w:t></w:r>"

    @Test
    fun theSameDocumentShowsTheColorOfItsOwnTheme() {
        val body = para(accentRun)
        val styles = stylesXml(normal())
        val office = DocxDocument.open(StyledFixtures.docx(body, styles = styles, theme = themeWith("4472C4")))
        val green = DocxDocument.open(StyledFixtures.docx(body, styles = styles, theme = themeWith("00B050")))
        assertEquals("4472C4", colorOfFirst(office))
        // The cached 4472C4 is stale for a document whose theme says otherwise; the theme wins.
        assertEquals("00B050", colorOfFirst(green))
    }

    private fun swatch(attrs: String): String? {
        val doc = DocxDocument.open(
            StyledFixtures.docx(
                para("<w:r><w:rPr><w:color $attrs/></w:rPr><w:t>x</w:t></w:r>"),
                styles = stylesXml(normal()), theme = StyledFixtures.themeXml(),
            ),
        )
        return colorOfFirst(doc)
    }

    private fun close(expected: String, actual: String?, tolerance: Int = 4) {
        assertTrue("color is $actual", actual != null && actual.length == 6)
        for (i in 0 until 3) {
            val e = expected.substring(i * 2, i * 2 + 2).toInt(16)
            val a = actual!!.substring(i * 2, i * 2 + 2).toInt(16)
            assertTrue("$actual is not close to $expected", Math.abs(e - a) <= tolerance)
        }
    }

    @Test
    fun wordsStandardSwatchesOfAccentOne() {
        // The palette Word shows for the "Blue, Accent 1" column (Office theme).
        close("DAE3F3", swatch("w:themeColor=\"accent1\" w:themeTint=\"33\""))
        close("B4C7E7", swatch("w:themeColor=\"accent1\" w:themeTint=\"66\""))
        close("8FAADC", swatch("w:themeColor=\"accent1\" w:themeTint=\"99\""))
        close("2F5496", swatch("w:themeColor=\"accent1\" w:themeShade=\"BF\""))
        close("203864", swatch("w:themeColor=\"accent1\" w:themeShade=\"80\""), 6)
    }

    @Test
    fun tintAndShadeEdgesAndGrays() {
        assertEquals("FFFFFF", swatch("w:themeColor=\"accent1\" w:themeTint=\"00\""))
        assertEquals("000000", swatch("w:themeColor=\"accent1\" w:themeShade=\"00\""))
        close("4472C4", swatch("w:themeColor=\"accent1\" w:themeTint=\"FF\""), 1)
        close("4472C4", swatch("w:themeColor=\"accent1\" w:themeShade=\"FF\""), 1)
        // Gray stays gray (no hue): a tint of dark gray text.
        val gray = swatch("w:themeColor=\"accent3\" w:themeShade=\"BF\"")!!
        assertEquals(gray.substring(0, 2), gray.substring(2, 4))
        assertEquals(gray.substring(2, 4), gray.substring(4, 6))
    }

    @Test
    fun systemColorsAndAliasNamesResolve() {
        assertEquals("000000", swatch("w:themeColor=\"text1\""))
        assertEquals("FFFFFF", swatch("w:themeColor=\"background1\""))
        assertEquals("000000", swatch("w:themeColor=\"dark1\""))
        assertEquals("E7E6E6", swatch("w:themeColor=\"light2\""))
        assertEquals("954F72", swatch("w:themeColor=\"followedHyperlink\""))
    }

    @Test
    fun anUnknownThemeColorNameFallsBackToTheCachedValue() {
        assertEquals("123456", swatch("w:val=\"123456\" w:themeColor=\"nonsense\""))
        assertNull(swatch("w:themeColor=\"nonsense\""))
    }

    @Test
    fun malformedTintValuesDoNotCrash() {
        assertEquals("ED7D31", swatch("w:val=\"ED7D31\" w:themeColor=\"accent2\" w:themeTint=\"zz\""))
    }

    @Test
    fun characterStyleThemeColorWinsOverTheParagraphStyleColor() {
        val styles = stylesXml(
            normal(),
            style("Heading1", "heading 1", basedOn = "Normal", rPr = "<w:color w:val=\"FF0000\"/>"),
            style("Accent", "Accent", type = "character", rPr = "<w:color w:themeColor=\"accent6\"/>"),
        )
        val body = para(
            "<w:r><w:rPr><w:rStyle w:val=\"Accent\"/></w:rPr><w:t>x</w:t></w:r>" + run("y"),
            "<w:pStyle w:val=\"Heading1\"/>",
        )
        val doc = DocxDocument.open(StyledFixtures.docx(body, styles = styles, theme = StyledFixtures.themeXml()))
        val p = doc.paragraphs[0]
        val runs = p.inlines.filterIsInstance<TextRun>()
        assertEquals("70AD47", p.effectiveFormat(runs[0]).colorHex)
        assertEquals("FF0000", p.effectiveFormat(runs[1]).colorHex)
    }

    // ---- originals stay untouched ----

    private fun richOriginal(): ByteArray = StyledFixtures.docx(
        para(run("عنصر"), numPr(1, 0) + "<w:bidi/>") +
            item(1, 1, "<w:bidi/>") + item(1, 1, "<w:bidi/>") + item(1, 0, "<w:bidi/>") +
            para(accentRun) + para(run("plain")),
        styles = stylesXml(normal()),
        numbering = numberingXml(arabicOutline, num(1, 0)),
        theme = themeWith("4472C4"),
    )

    @Test
    fun readingEverythingLeavesTheOriginalPackageUnchanged() {
        val original = richOriginal()
        val doc = DocxDocument.open(original)
        doc.listLabels()
        for (p in doc.paragraphs) {
            p.format
            p.headingLevel
            for (r in p.inlines.filterIsInstance<TextRun>()) p.effectiveFormat(r)
        }
        assertFalse(doc.hasUnsavedChanges)
        val before = DocxPackage.read(original)
        val after = DocxPackage.read(doc.toByteArray())
        assertEquals(before.parts.map { it.name }, after.parts.map { it.name })
        for (part in before.parts) assertArrayEquals(part.name, part.bytes, after.find(part.name)!!.bytes)
    }

    @Test
    fun editingOneParagraphKeepsEveryOtherPartAndNeighbourUntouched() {
        val original = richOriginal()
        val doc = DocxDocument.open(original)
        doc.paragraphs.last().insertText(0, "edited ")
        val saved = doc.toByteArray()
        DocxAssertions.assertPackageIntegrity(saved)
        DocxAssertions.assertSchemaOrder(saved)
        val before = DocxPackage.read(original)
        val after = DocxPackage.read(saved)
        for (part in before.parts) {
            if (part.name == "word/document.xml") continue
            assertArrayEquals(part.name, part.bytes, after.find(part.name)!!.bytes)
        }
        val xml = DocxAssertions.mainXml(DocxDocument.open(saved))
        assertTrue(xml.contains("w:themeColor=\"accent1\""))
        val reopened = DocxDocument.open(saved)
        assertEquals(labelsOf(DocxDocument.open(original)), labelsOf(reopened))
    }
}

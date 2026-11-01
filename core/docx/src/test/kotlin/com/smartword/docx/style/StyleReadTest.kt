package com.smartword.docx.style

import com.smartword.docx.DocxFixtures.para
import com.smartword.docx.DocxFixtures.run
import com.smartword.docx.StyledFixtures
import com.smartword.docx.StyledFixtures.docDefaults
import com.smartword.docx.StyledFixtures.normal
import com.smartword.docx.StyledFixtures.style
import com.smartword.docx.StyledFixtures.stylesXml
import com.smartword.docx.model.Alignment
import com.smartword.docx.model.DocxDocument
import com.smartword.docx.model.DocxParagraph
import com.smartword.docx.model.EffectiveRunFormat
import com.smartword.docx.model.LineRule
import com.smartword.docx.model.TextRun
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StyleReadTest {

    private fun docOf(body: String, styles: String, theme: String? = null) =
        DocxDocument.open(StyledFixtures.docx(body, styles = styles, theme = theme))

    private fun fmt(p: DocxParagraph, index: Int = 0): EffectiveRunFormat =
        p.effectiveFormat(p.inlines.filterIsInstance<TextRun>()[index])

    private fun pStyle(id: String) = "<w:pStyle w:val=\"$id\"/>"

    // ---- headings and defaults ----

    private val headingStyles = stylesXml(
        docDefaults(rPr = "<w:sz w:val=\"22\"/><w:szCs w:val=\"22\"/>"),
        normal(),
        style(
            "Heading1", "heading 1", basedOn = "Normal",
            pPr = "<w:keepNext/><w:spacing w:before=\"240\" w:after=\"0\"/><w:outlineLvl w:val=\"0\"/>",
            rPr = "<w:b/><w:bCs/><w:color w:val=\"2F5496\"/><w:sz w:val=\"32\"/><w:szCs w:val=\"32\"/>",
        ),
    )

    @Test
    fun headingStyleGivesFormattingAndLevel() {
        val doc = docOf(para(run("Chapter"), pStyle("Heading1")) + para(run("body")), headingStyles)
        val heading = doc.paragraphs[0]
        val f = fmt(heading)
        assertTrue(f.bold)
        assertEquals(16f, f.fontSizePt, 0.001f)
        assertEquals("2F5496", f.colorHex)
        assertFalse(f.italic)
        assertEquals(1, heading.headingLevel)
        assertEquals("Heading1", heading.styleId)
        assertEquals("heading 1", heading.format.styleName)
        assertEquals(1, heading.format.outlineLevel)
        assertEquals(12f, heading.format.spaceBeforePt, 0.001f)
        assertEquals(0f, heading.format.spaceAfterPt, 0.001f)
    }

    @Test
    fun paragraphWithoutStyleUsesDefaultsAndTheDefaultStyle() {
        val doc = docOf(para(run("body")), headingStyles)
        val p = doc.paragraphs[0]
        val f = fmt(p)
        assertFalse(f.bold)
        assertEquals(11f, f.fontSizePt, 0.001f)
        assertNull(f.colorHex)
        assertNull(p.headingLevel)
        assertNull(p.styleId)
        assertEquals("Normal", p.format.styleName)
        assertNull(p.format.outlineLevel)
    }

    @Test
    fun sizeDefaultsToTenPointsWhenNothingSetsIt() {
        val doc = docOf(para(run("x")), stylesXml(normal()))
        assertEquals(10f, fmt(doc.paragraphs[0]).fontSizePt, 0.001f)
    }

    @Test
    fun directFormattingBeatsStyles() {
        val body = para(
            run("a", "<w:b w:val=\"0\"/>") + run("b", "<w:sz w:val=\"40\"/>") + run("c", "<w:color w:val=\"ff0000\"/>"),
            pStyle("Heading1"),
        )
        val p = docOf(body, headingStyles).paragraphs[0]
        assertFalse(fmt(p, 0).bold)
        assertEquals(20f, fmt(p, 1).fontSizePt, 0.001f)
        assertEquals("FF0000", fmt(p, 2).colorHex)
        assertTrue(fmt(p, 2).bold)
    }

    @Test
    fun automaticColorOverridesAStyleColor() {
        val p = docOf(para(run("a", "<w:color w:val=\"auto\"/>"), pStyle("Heading1")), headingStyles).paragraphs[0]
        assertNull(fmt(p).colorHex)
    }

    @Test
    fun characterStyleOverridesTheParagraphStyle() {
        val styles = stylesXml(
            normal(rPr = "<w:i/>"),
            style("Strong", "Strong", type = "character", rPr = "<w:b/><w:bCs/><w:i w:val=\"0\"/><w:color w:val=\"C00000\"/>"),
        )
        val body = para(run("plain") + run("strong", "<w:rStyle w:val=\"Strong\"/>"))
        val p = docOf(body, styles).paragraphs[0]
        assertTrue(fmt(p, 0).italic)
        assertFalse(fmt(p, 0).bold)
        assertTrue(fmt(p, 1).bold)
        assertFalse(fmt(p, 1).italic)
        assertEquals("C00000", fmt(p, 1).colorHex)
        assertEquals("Strong", (p.inlines[1] as TextRun).styleId)
    }

    @Test
    fun aParagraphStyleIdIsNotAcceptedAsACharacterStyle() {
        val styles = stylesXml(normal(), style("Heading1", "heading 1", rPr = "<w:b/>"))
        val p = docOf(para(run("x", "<w:rStyle w:val=\"Heading1\"/>")), styles).paragraphs[0]
        assertFalse(fmt(p).bold)
    }

    // ---- inheritance ----

    @Test
    fun basedOnChainsAreFollowedFromTheMostDistantAncestor() {
        val styles = stylesXml(
            normal(),
            style("A", basedOn = "Normal", rPr = "<w:color w:val=\"112233\"/><w:sz w:val=\"20\"/>"),
            style("B", basedOn = "A", rPr = "<w:sz w:val=\"30\"/>"),
            style("C", basedOn = "B", rPr = "<w:b/>"),
        )
        val f = fmt(docOf(para(run("x"), pStyle("C")), styles).paragraphs[0])
        assertEquals("112233", f.colorHex)
        assertEquals(15f, f.fontSizePt, 0.001f)
        assertTrue(f.bold)
    }

    @Test
    fun aBasedOnCycleDoesNotHang() {
        val styles = stylesXml(
            normal(),
            style("X", basedOn = "Y", rPr = "<w:b/>"),
            style("Y", basedOn = "X", rPr = "<w:i/>"),
        )
        val f = fmt(docOf(para(run("x"), pStyle("X")), styles).paragraphs[0])
        assertTrue(f.bold)
        assertTrue(f.italic)
    }

    @Test
    fun aMissingBaseStyleIsIgnored() {
        val styles = stylesXml(normal(), style("Orphan", basedOn = "Nope", rPr = "<w:b/>"))
        assertTrue(fmt(docOf(para(run("x"), pStyle("Orphan")), styles).paragraphs[0]).bold)
    }

    @Test
    fun aVeryLongChainIsHandled() {
        val parts = ArrayList<String>()
        parts.add(normal())
        parts.add(style("S0", basedOn = "Normal", rPr = "<w:b/>"))
        for (i in 1..200) parts.add(style("S$i", basedOn = "S${i - 1}"))
        val styles = stylesXml(*parts.toTypedArray())
        val p = docOf(para(run("x"), pStyle("S200")), styles).paragraphs[0]
        assertNotNull(fmt(p))
    }

    @Test
    fun anUnknownParagraphStyleFallsBackToTheDefaultStyle() {
        val styles = stylesXml(normal(rPr = "<w:color w:val=\"111111\"/>"))
        val p = docOf(para(run("x"), pStyle("DoesNotExist")), styles).paragraphs[0]
        assertEquals("111111", fmt(p).colorHex)
        assertEquals("DoesNotExist", p.styleId)
    }

    @Test
    fun aCustomStyleBasedOnAHeadingIsAHeading() {
        val styles = stylesXml(
            normal(),
            style("Heading2", "heading 2", basedOn = "Normal", pPr = "<w:outlineLvl w:val=\"1\"/>"),
            style("MyHeading", "My Heading", basedOn = "Heading2"),
        )
        assertEquals(2, docOf(para(run("x"), pStyle("MyHeading")), styles).paragraphs[0].headingLevel)
    }

    @Test
    fun headingNamesAreRecognisedEvenWithoutAnOutlineLevel() {
        val styles = stylesXml(normal(), style("Heading3", "Heading 3", basedOn = "Normal"), style("Title", "Title"))
        val doc = docOf(para(run("a"), pStyle("Heading3")) + para(run("b"), pStyle("Title")), styles)
        assertEquals(3, doc.paragraphs[0].headingLevel)
        assertNull(doc.paragraphs[1].headingLevel)
    }

    @Test
    fun theStyleSheetListsStylesByType() {
        val styles = stylesXml(
            normal(),
            style("Heading1", "heading 1", basedOn = "Normal"),
            style("Strong", "Strong", type = "character"),
            style("Grid", "Table Grid", type = "table"),
        )
        val sheet = docOf(para(run("x")), styles).styles
        assertEquals(listOf("Normal", "Heading1"), sheet.paragraphStyles.map { it.id })
        assertEquals(listOf("Strong"), sheet.characterStyles.map { it.id })
        assertEquals("Normal", sheet.defaultParagraphStyle?.id)
        assertEquals("heading 1", sheet["Heading1"]?.name)
        assertEquals(StyleType.TABLE, sheet["Grid"]?.type)
        assertNull(sheet["Missing"])
        assertNull(sheet[null])
    }

    // ---- fonts and the theme ----

    private val themeStyles = stylesXml(
        docDefaults(
            rPr = "<w:rFonts w:asciiTheme=\"minorHAnsi\" w:hAnsiTheme=\"minorHAnsi\" w:cstheme=\"minorBidi\"/>" +
                "<w:sz w:val=\"22\"/><w:szCs w:val=\"22\"/>",
        ),
        normal(),
        style("Heading1", "heading 1", basedOn = "Normal", rPr = "<w:rFonts w:asciiTheme=\"majorHAnsi\" w:hAnsiTheme=\"majorHAnsi\"/>"),
    )

    @Test
    fun themeFontsAreResolved() {
        val doc = docOf(
            para(run("Hello") + run("مرحبا")) + para(run("Title"), pStyle("Heading1")),
            themeStyles, StyledFixtures.themeXml(),
        )
        assertEquals("Calibri", fmt(doc.paragraphs[0], 0).fontName)
        assertEquals("Arial", fmt(doc.paragraphs[0], 1).fontName)
        assertEquals("Calibri Light", fmt(doc.paragraphs[1]).fontName)
    }

    @Test
    fun anExplicitComplexScriptThemeFontBeatsTheScriptList() {
        val doc = docOf(para(run("مرحبا")), themeStyles, StyledFixtures.themeXml(minorCs = "Cairo"))
        assertEquals("Cairo", fmt(doc.paragraphs[0]).fontName)
    }

    @Test
    fun explicitFontNamesAreUsedWhenThereIsNoTheme() {
        val doc = docOf(
            para(run("a", "<w:rFonts w:ascii=\"Cairo\" w:hAnsi=\"Cairo\" w:cs=\"Amiri\"/>") + run("ب", "<w:rFonts w:ascii=\"Cairo\" w:cs=\"Amiri\"/>")),
            stylesXml(normal()),
        )
        assertEquals("Cairo", fmt(doc.paragraphs[0], 0).fontName)
        assertEquals("Amiri", fmt(doc.paragraphs[0], 1).fontName)
    }

    @Test
    fun aThemeReferenceWithoutAThemePartFallsBackToTheNameOrNull() {
        val doc = docOf(
            para(run("a", "<w:rFonts w:ascii=\"Named\" w:asciiTheme=\"minorHAnsi\"/>") + run("b", "<w:rFonts w:asciiTheme=\"minorHAnsi\"/>")),
            stylesXml(normal()),
        )
        assertEquals("Named", fmt(doc.paragraphs[0], 0).fontName)
        assertNull(fmt(doc.paragraphs[0], 1).fontName)
    }

    @Test
    fun aFontSetByTheStyleIsReplacedByAnExplicitDirectFont() {
        val doc = docOf(
            para(run("Title", "<w:rFonts w:ascii=\"Courier New\"/>"), pStyle("Heading1")),
            themeStyles, StyledFixtures.themeXml(),
        )
        assertEquals("Courier New", fmt(doc.paragraphs[0]).fontName)
    }

    // ---- Arabic and right-to-left ----

    private val arabicStyles = stylesXml(
        docDefaults(rPr = "<w:sz w:val=\"20\"/><w:szCs w:val=\"20\"/>"),
        normal(),
        style(
            "ArabicBody", "Arabic Body", basedOn = "Normal",
            pPr = "<w:bidi/>",
            rPr = "<w:rtl/><w:bCs/><w:szCs w:val=\"32\"/>",
        ),
    )

    @Test
    fun aStyleCanMakeParagraphsRightToLeft() {
        val p = docOf(para(run("نص عربي"), pStyle("ArabicBody")), arabicStyles).paragraphs[0]
        assertTrue(p.isRtl)
        assertTrue(p.format.isRtl)
        assertNull(p.alignment)
        assertEquals(Alignment.RIGHT, p.format.alignment)
    }

    @Test
    fun alignmentIsMirroredForRightToLeftParagraphsFromStyles() {
        val body = para(run("أ"), pStyle("ArabicBody") + "<w:jc w:val=\"center\"/>") +
            para(run("ب"), pStyle("ArabicBody") + "<w:jc w:val=\"right\"/>") +
            para(run("ج"), pStyle("ArabicBody") + "<w:jc w:val=\"left\"/>") +
            para(run("د"), pStyle("ArabicBody") + "<w:jc w:val=\"both\"/>")
        val doc = docOf(body, arabicStyles)
        assertEquals(
            listOf(Alignment.CENTER, Alignment.LEFT, Alignment.RIGHT, Alignment.JUSTIFY),
            doc.paragraphs.map { it.format.alignment },
        )
    }

    @Test
    fun complexScriptPropertiesApplyToArabicRuns() {
        val p = docOf(para(run("مرحبا") + run("hello"), pStyle("ArabicBody")), arabicStyles).paragraphs[0]
        // The style marks its runs as right-to-left, so both runs use the complex-script properties.
        for (index in 0..1) {
            assertTrue(fmt(p, index).bold)
            assertEquals(16f, fmt(p, index).fontSizePt, 0.001f)
            assertTrue(fmt(p, index).rtl)
        }
    }

    @Test
    fun arabicTextInAPlainParagraphUsesComplexScriptSizes() {
        val styles = stylesXml(docDefaults(rPr = "<w:sz w:val=\"20\"/><w:szCs w:val=\"28\"/>"), normal())
        val p = docOf(para(run("مرحبا") + run("hello")), styles).paragraphs[0]
        assertEquals(14f, fmt(p, 0).fontSizePt, 0.001f)
        assertEquals(10f, fmt(p, 1).fontSizePt, 0.001f)
    }

    @Test
    fun aDocumentDefaultCanMakeEverythingRightToLeft() {
        val styles = stylesXml(docDefaults(pPr = "<w:bidi/>"), normal())
        val p = docOf(para(run("x")), styles).paragraphs[0]
        assertTrue(p.isRtl)
        assertEquals(Alignment.RIGHT, p.format.alignment)
    }

    @Test
    fun directBidiOffBeatsTheStyle() {
        val p = docOf(para(run("x"), pStyle("ArabicBody") + "<w:bidi w:val=\"0\"/>"), arabicStyles).paragraphs[0]
        assertFalse(p.isRtl)
        assertEquals(Alignment.LEFT, p.format.alignment)
    }

    // ---- spacing, alignment and indentation ----

    @Test
    fun spacingAndLineSpacingInherit() {
        val styles = stylesXml(
            docDefaults(pPr = "<w:spacing w:after=\"160\" w:line=\"259\" w:lineRule=\"auto\"/>"),
            normal(),
            style("Loose", basedOn = "Normal", pPr = "<w:spacing w:before=\"120\"/>"),
        )
        val f = docOf(para(run("x"), pStyle("Loose")), styles).paragraphs[0].format
        assertEquals(6f, f.spaceBeforePt, 0.001f)
        assertEquals(8f, f.spaceAfterPt, 0.001f)
        assertEquals(LineRule.AUTO, f.lineSpacing.rule)
        assertEquals(259f / 240f, f.lineSpacing.value, 0.001f)
    }

    @Test
    fun exactAndMinimumLineSpacing() {
        val body = para(run("a"), "<w:spacing w:line=\"400\" w:lineRule=\"exact\"/>") +
            para(run("b"), "<w:spacing w:line=\"300\" w:lineRule=\"atLeast\"/>")
        val doc = docOf(body, stylesXml(normal()))
        assertEquals(LineRule.EXACT, doc.paragraphs[0].format.lineSpacing.rule)
        assertEquals(20f, doc.paragraphs[0].format.lineSpacing.value, 0.001f)
        assertEquals(LineRule.AT_LEAST, doc.paragraphs[1].format.lineSpacing.rule)
        assertEquals(15f, doc.paragraphs[1].format.lineSpacing.value, 0.001f)
    }

    @Test
    fun unspecifiedParagraphFormattingIsSingleSpacedAndLeftAligned() {
        val f = docOf(para(run("x")), stylesXml(normal())).paragraphs[0].format
        assertEquals(Alignment.LEFT, f.alignment)
        assertEquals(0f, f.spaceBeforePt, 0.001f)
        assertEquals(1f, f.lineSpacing.value, 0.001f)
        assertEquals(0f, f.indentStartPt, 0.001f)
        assertEquals(0f, f.firstLineIndentPt, 0.001f)
    }

    @Test
    fun alignmentComesFromTheStyleAndDirectFormattingOverridesIt() {
        val styles = stylesXml(normal(), style("Centered", basedOn = "Normal", pPr = "<w:jc w:val=\"center\"/>"))
        val body = para(run("a"), pStyle("Centered")) + para(run("b"), pStyle("Centered") + "<w:jc w:val=\"right\"/>")
        val doc = docOf(body, styles)
        assertEquals(Alignment.CENTER, doc.paragraphs[0].format.alignment)
        assertEquals(Alignment.RIGHT, doc.paragraphs[1].format.alignment)
        assertNull(doc.paragraphs[0].alignment)
        assertEquals(Alignment.RIGHT, doc.paragraphs[1].alignment)
    }

    @Test
    fun indentsAreMeasuredFromTheLeadingEdgeInBothDirections() {
        val body = para(run("a"), "<w:ind w:left=\"720\" w:right=\"360\" w:hanging=\"360\"/>") +
            para(run("ب"), "<w:bidi/><w:ind w:left=\"720\" w:right=\"360\" w:firstLine=\"200\"/>")
        val doc = docOf(body, stylesXml(normal()))
        val ltr = doc.paragraphs[0].format
        assertEquals(36f, ltr.indentStartPt, 0.001f)
        assertEquals(18f, ltr.indentEndPt, 0.001f)
        assertEquals(-18f, ltr.firstLineIndentPt, 0.001f)
        val rtl = doc.paragraphs[1].format
        assertEquals(36f, rtl.indentStartPt, 0.001f)
        assertEquals(18f, rtl.indentEndPt, 0.001f)
        assertEquals(10f, rtl.firstLineIndentPt, 0.001f)
    }

    @Test
    fun startAndEndAttributesBeatLeftAndRight() {
        val body = para(run("a"), "<w:ind w:start=\"100\" w:left=\"999\" w:end=\"200\" w:right=\"888\"/>")
        val f = docOf(body, stylesXml(normal())).paragraphs[0].format
        assertEquals(5f, f.indentStartPt, 0.001f)
        assertEquals(10f, f.indentEndPt, 0.001f)
    }

    @Test
    fun indentAttributesInheritIndividually() {
        val styles = stylesXml(normal(), style("Indented", basedOn = "Normal", pPr = "<w:ind w:left=\"720\" w:hanging=\"360\"/>"))
        val body = para(run("a"), pStyle("Indented") + "<w:ind w:right=\"100\"/>") +
            para(run("b"), pStyle("Indented") + "<w:ind w:firstLine=\"200\"/>")
        val doc = docOf(body, styles)
        val first = doc.paragraphs[0].format
        assertEquals(36f, first.indentStartPt, 0.001f)
        assertEquals(5f, first.indentEndPt, 0.001f)
        assertEquals(-18f, first.firstLineIndentPt, 0.001f)
        // A direct first-line indent replaces the inherited hanging indent.
        assertEquals(10f, doc.paragraphs[1].format.firstLineIndentPt, 0.001f)
    }

    @Test
    fun directOutlineLevelMakesAParagraphAHeading() {
        val doc = docOf(para(run("x"), "<w:outlineLvl w:val=\"2\"/>") + para(run("y"), "<w:outlineLvl w:val=\"9\"/>"), stylesXml(normal()))
        assertEquals(3, doc.paragraphs[0].headingLevel)
        assertNull(doc.paragraphs[1].headingLevel)
    }
}

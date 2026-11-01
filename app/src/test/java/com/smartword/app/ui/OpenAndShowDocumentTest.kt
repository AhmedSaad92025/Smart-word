package com.smartword.app.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.drawToBitmap
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.test.core.app.ApplicationProvider
import com.smartword.app.MainViewModel
import com.smartword.app.OpenDocument
import com.smartword.app.ui.document.DocumentScreen
import com.smartword.app.ui.theme.SmartWordTheme
import com.smartword.docx.model.DocxDocument
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Opens real DOCX files the way the app does (view model, content resolver, engine, preview,
 * screen) and checks what is actually drawn: semantics nodes and pixels.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h2400dp-xhdpi")
open class OpenAndShowDocumentTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    private fun testFile(name: String): File {
        val file = File("../docs/test-files/$name")
        assertTrue("test file missing: ${file.absolutePath}", file.exists())
        return file
    }

    /** The screen as pixels: the activity's views drawn into a bitmap (no window capture needed). */
    private fun render(): Bitmap {
        rule.waitForIdle()
        return rule.activity.window.decorView.drawToBitmap()
    }

    private fun screenshot(name: String): Bitmap {
        val bitmap = render()
        val dir = File("build/screenshots").also { it.mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("SCREEN $name ${bitmap.width}x${bitmap.height}\n" + asciiMap(bitmap))
        return bitmap
    }

    private fun classify(p: Int): Char {
        val r = (p shr 16) and 0xFF
        val g = (p shr 8) and 0xFF
        val b = p and 0xFF
        return when {
            (p ushr 24) == 0 -> '?'
            r < 70 && g < 70 && b < 70 -> '#'
            r > 245 && g > 245 && b > 245 -> '.'
            b > r + 60 -> 'B'
            r > 200 && g > 200 && b > 200 -> ','
            else -> 'x'
        }
    }

    /** A coarse picture of the screen: # dark text, . white page, , gray desk, B blue, x other, ? transparent. */
    private fun asciiMap(bitmap: Bitmap, columns: Int = 40, rows: Int = 60): String {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val sb = StringBuilder()
        val cellW = bitmap.width / columns
        val cellH = bitmap.height / rows
        for (row in 0 until rows) {
            for (col in 0 until columns) {
                val counts = HashMap<Char, Int>()
                var y = row * cellH
                while (y < (row + 1) * cellH) {
                    var x = col * cellW
                    while (x < (col + 1) * cellW) {
                        val c = classify(pixels[y * bitmap.width + x])
                        counts[c] = (counts[c] ?: 0) + 1
                        x += 3
                    }
                    y += 3
                }
                // Dark text is thin, so any real amount of it wins over the background.
                val dark = counts['#'] ?: 0
                val total = counts.values.sum().coerceAtLeast(1)
                sb.append(if (dark * 40 > total) '#' else counts.maxByOrNull { it.value }?.key ?: ' ')
            }
            sb.append('\n')
        }
        return sb.toString()
    }

    private fun count(bitmap: Bitmap, c: Char): Int {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.count { classify(it) == c }
    }

    private fun openThroughViewModel(file: File): MainViewModel {
        val viewModel = MainViewModel(app)
        viewModel.open(Uri.fromFile(file), persist = false)
        rule.setContent { SmartWordTheme { SmartWordApp(viewModel) } }
        return viewModel
    }

    private fun waitForText(text: String) {
        rule.waitUntil(timeoutMillis = 15_000) {
            rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun anArabicDocumentOpenedFromAFileShowsItsHeadingsAndParagraphs() {
        openThroughViewModel(testFile("01-arabic-rtl-headings.docx"))
        waitForText("المقدمة")
        println(rule.onRoot().printToString())
        rule.onNodeWithText("تقرير تجريبي بالعربية", substring = true).assertIsDisplayed()
        rule.onNodeWithText("المقدمة", substring = true).assertIsDisplayed()
        rule.onNodeWithText("هذه فقرة عربية عادية", substring = true).assertIsDisplayed()
        // The status line says the content was read (17 paragraphs) even before looking at pixels.
        assertTrue(rule.onAllNodesWithText("فقرة ·", substring = true).fetchSemanticsNodes().isNotEmpty())
        rule.waitForIdle()
        val shot = screenshot("arabic")
        assertTrue("no text pixels were drawn: ${count(shot, '#')}", count(shot, '#') > 500)
        assertTrue("no white page was drawn", count(shot, '.') > 5000)
        assertTrue("no toolbar was drawn", count(shot, 'B') > 1000)
    }

    @Test
    fun listLabelsAreShownWithTheirItems() {
        openThroughViewModel(testFile("02-multilevel-lists.docx"))
        waitForText("البند الأول")
        assertTrue(rule.onAllNodesWithText("أ-", substring = true).fetchSemanticsNodes().isNotEmpty())
        rule.onNodeWithText("Chapter one", substring = true).assertIsDisplayed()
        assertTrue(rule.onAllNodesWithText("1.1.1  Subsection", substring = true).fetchSemanticsNodes().isNotEmpty())
        screenshot("lists")
    }

    @Test
    fun formattedDocumentShowsAllSections() {
        openThroughViewModel(testFile("03-colors-fonts-alignment.docx"))
        waitForText("Colors, fonts and alignment")
        rule.onNodeWithText("Courier New (monospace)", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Centered paragraph text.", substring = true).assertIsDisplayed()
        screenshot("formatting")
    }

    @Test
    fun aNewDocumentShowsTheEmptyPageWithItsToolbar() {
        val viewModel = MainViewModel(app)
        rule.setContent { SmartWordTheme { SmartWordApp(viewModel) } }
        rule.onNodeWithText("مستند جديد", substring = true).assertIsDisplayed()
        viewModel.createNew()
        rule.waitForIdle()
        println(rule.onRoot().printToString())
        rule.onNodeWithText("رجوع").assertIsDisplayed()
        rule.onNodeWithText("عرض للقراءة فقط", substring = true).assertIsDisplayed()
        rule.onNodeWithText("المستند فارغ").assertIsDisplayed()
        rule.onNodeWithText("هذه صفحة فارغة", substring = true).assertIsDisplayed()
        assertTrue(rule.onAllNodesWithText("حرفًا", substring = true).fetchSemanticsNodes().isNotEmpty())
        screenshot("new-document")
    }

    @Test
    fun theDocumentScreenAloneDrawsTextFromTheEngine() {
        val doc = DocxDocument.create(rtl = true)
        doc.paragraphs[0].insertText(0, "عنوان التقرير")
        doc.paragraphs[0].setStyle("Heading1")
        doc.addParagraph("فقرة عربية عادية للاختبار")
        val reopened = DocxDocument.open(doc.toByteArray())
        rule.setContent { SmartWordTheme { DocumentScreen(OpenDocument(null, "تجربة", reopened), onBack = {}) } }
        rule.onNodeWithText("عنوان التقرير", substring = true).assertIsDisplayed()
        rule.onNodeWithText("فقرة عربية عادية للاختبار", substring = true).assertIsDisplayed()
    }
}

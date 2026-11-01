package com.smartword.app.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.graphics.asAndroidBitmap
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
class OpenAndShowDocumentTest {

    @get:Rule
    val rule = createComposeRule()

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    private fun testFile(name: String): File {
        val file = File("../docs/test-files/$name")
        assertTrue("test file missing: ${file.absolutePath}", file.exists())
        return file
    }

    private fun screenshot(name: String) {
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/screenshots").also { it.mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Counts nearly black pixels (text on the white page) in the rendered screen. */
    private fun darkPixels(): Int {
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        var count = 0
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        for (p in pixels) {
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            if (r < 60 && g < 60 && b < 60) count++
        }
        return count
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
        rule.waitForIdle()
        screenshot("arabic")
        assertTrue("no text pixels were drawn", darkPixels() > 500)
    }

    @Test
    fun listLabelsAreShownWithTheirItems() {
        openThroughViewModel(testFile("02-multilevel-lists.docx"))
        waitForText("البند الأول")
        rule.onNodeWithText("أ-", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Chapter one", substring = true).assertIsDisplayed()
        rule.onNodeWithText("1.1.1", substring = true).assertIsDisplayed()
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

package com.smartword.app.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.drawToBitmap
import androidx.test.core.app.ApplicationProvider
import com.smartword.app.MainViewModel
import com.smartword.app.preview.PreviewParagraph
import com.smartword.app.ui.theme.SmartWordTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Every sample document in docs/test-files (hand-written ones and ones saved by LibreOffice) is opened
 * through the view model on a phone-sized screen, and the screen must show text on the page.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "ar-rEG-w360dp-h800dp-xhdpi")
class EveryTestFileRendersTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    private fun darkPixels(bitmap: Bitmap): Int {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.count {
            ((it shr 16) and 0xFF) < 70 && ((it shr 8) and 0xFF) < 70 && (it and 0xFF) < 70
        }
    }

    private fun check(relativePath: String) {
        val file = File("../docs/test-files/$relativePath")
        assertTrue("missing ${file.absolutePath}", file.exists())
        val viewModel = MainViewModel(app)
        viewModel.open(Uri.fromFile(file), persist = false)
        rule.setContent { SmartWordTheme { SmartWordApp(viewModel) } }
        rule.waitUntil(20_000) { viewModel.state.value.open != null || viewModel.state.value.error != null }
        rule.waitForIdle()
        val state = viewModel.state.value
        val open = state.open
        assertTrue("$relativePath did not open: ${state.error}", open != null)
        val text = open!!.blocks.filterIsInstance<PreviewParagraph>().sumOf { p -> p.spans.sumOf { it.text.length } }
        val dark = darkPixels(rule.activity.window.decorView.drawToBitmap())
        println("FILE $relativePath: blocks=${open.blocks.size} textChars=$text darkPixels=$dark")
        assertTrue("$relativePath: no text found in the document", text > 0)
        assertTrue("$relativePath: only $dark dark pixels were drawn", dark > 300)
    }

    @Test fun handWrittenArabic() = check("01-arabic-rtl-headings.docx")
    @Test fun handWrittenLists() = check("02-multilevel-lists.docx")
    @Test fun handWrittenFormatting() = check("03-colors-fonts-alignment.docx")
    @Test fun libreOfficeReportWithTableAndLink() = check("realistic/libreoffice-report.docx")
    @Test fun libreOfficeResavedArabic() = check("realistic/libreoffice-resaved-01-arabic-rtl-headings.docx")
    @Test fun libreOfficeResavedLists() = check("realistic/libreoffice-resaved-02-multilevel-lists.docx")
    @Test fun libreOfficeResavedFormatting() = check("realistic/libreoffice-resaved-03-colors-fonts-alignment.docx")

    @Test
    fun noSampleDocumentIsMissingFromTheseTests() {
        val files = File("../docs/test-files").walkTopDown().filter { it.extension == "docx" }.count()
        assertTrue("the list of tests above must cover every sample ($files found)", files == 7)
    }
}

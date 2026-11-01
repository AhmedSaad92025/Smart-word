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

    @Test
    fun everySampleDocumentShowsTextOnThePage() {
        val files = File("../docs/test-files").walkTopDown().filter { it.extension == "docx" }.sortedBy { it.path }.toList()
        assertTrue("no sample documents found", files.size >= 7)
        val problems = ArrayList<String>()
        for (file in files) {
            val viewModel = MainViewModel(app)
            viewModel.open(Uri.fromFile(file), persist = false)
            rule.setContent { SmartWordTheme { SmartWordApp(viewModel) } }
            rule.waitUntil(15_000) { viewModel.state.value.open != null || viewModel.state.value.error != null }
            rule.waitForIdle()
            val state = viewModel.state.value
            val open = state.open
            if (open == null) {
                problems.add("${file.name}: did not open (${state.error})")
                continue
            }
            val text = open.blocks.filterIsInstance<PreviewParagraph>().sumOf { p -> p.spans.sumOf { it.text.length } }
            val dark = darkPixels(rule.activity.window.decorView.drawToBitmap())
            println("FILE ${file.name}: blocks=${open.blocks.size} textChars=$text darkPixels=$dark")
            if (text == 0) problems.add("${file.name}: no text found in the document")
            if (dark < 300) problems.add("${file.name}: only $dark dark pixels were drawn")
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }
}

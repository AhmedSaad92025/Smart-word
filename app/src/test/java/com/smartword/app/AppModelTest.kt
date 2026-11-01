package com.smartword.app

import com.smartword.app.data.FailureKind
import com.smartword.app.data.RecentFile
import com.smartword.app.data.RecentFiles
import com.smartword.app.preview.PreviewParagraph
import com.smartword.docx.model.DocxDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class AppModelTest {

    private val now = Instant.parse("2026-01-01T00:00:00Z")

    private fun doc(text: String = "نص"): DocxDocument {
        val d = DocxDocument.create(rtl = true, now = now)
        d.paragraphs[0].insertText(0, text)
        return DocxDocument.open(d.toByteArray())
    }

    @Test
    fun startsOnTheHomeScreenWithNothingOpen() {
        val state = AppModel().state.value
        assertNull(state.open)
        assertTrue(state.recents.isEmpty())
        assertFalse(state.loading)
        assertNull(state.error)
    }

    @Test
    fun loadingShowsAndOpeningClearsIt() {
        val model = AppModel()
        model.loadingStarted()
        assertTrue(model.state.value.loading)
        model.opened("content://a", "تقرير.docx", doc(), 100)
        val state = model.state.value
        assertFalse(state.loading)
        assertEquals("تقرير", state.open?.name)
        assertEquals("content://a", state.open?.uri)
    }

    @Test
    fun openingAddsTheFileToTheRecentsOnce() {
        val model = AppModel()
        model.opened("content://a", "a.docx", doc(), 1)
        model.opened("content://b", "b.docx", doc(), 2)
        model.opened("content://a", "a.docx", doc(), 3)
        assertEquals(listOf("content://a", "content://b"), model.state.value.recents.map { it.uri })
        assertEquals(listOf("content://a", "content://b"), model.recentsToStore().items.map { it.uri })
    }

    @Test
    fun theOpenDocumentCarriesItsPreview() {
        val model = AppModel()
        model.opened("content://a", "a.docx", doc("مرحبا"), 1)
        val blocks = model.state.value.open!!.blocks
        val p = blocks.filterIsInstance<PreviewParagraph>().first()
        assertEquals("مرحبا", p.spans.joinToString("") { it.text })
        assertTrue(p.rtl)
    }

    @Test
    fun aNewDocumentHasNoUriAndStaysOutOfTheRecents() {
        val model = AppModel()
        model.created(DocxDocument.create(rtl = true, now = now), "مستند جديد")
        val state = model.state.value
        assertNull(state.open?.uri)
        assertEquals("مستند جديد", state.open?.name)
        assertTrue(state.recents.isEmpty())
    }

    @Test
    fun failuresStopLoadingAndCanBeDismissed() {
        val model = AppModel()
        model.loadingStarted()
        model.failed(FailureKind.ENCRYPTED, "سري.docx")
        assertFalse(model.state.value.loading)
        assertEquals(FailureKind.ENCRYPTED, model.state.value.error?.kind)
        assertEquals("سري.docx", model.state.value.error?.name)
        assertNull(model.state.value.open)
        model.errorDismissed()
        assertNull(model.state.value.error)
    }

    @Test
    fun aFailureDoesNotCloseTheDocumentThatIsOpen() {
        val model = AppModel()
        model.opened("content://a", "a.docx", doc(), 1)
        model.failed(FailureKind.NO_ACCESS, null)
        assertNotNull(model.state.value.open)
    }

    @Test
    fun closingReturnsHomeAndKeepsTheRecents() {
        val model = AppModel()
        model.opened("content://a", "a.docx", doc(), 1)
        model.closed()
        assertNull(model.state.value.open)
        assertEquals(1, model.state.value.recents.size)
    }

    @Test
    fun storedRecentsAreShownAndFilesCanBeForgotten() {
        val model = AppModel()
        model.recentsLoaded(RecentFiles().with(RecentFile("content://a", "a.docx", 1)).with(RecentFile("content://b", "b.docx", 2)))
        assertEquals(listOf("content://b", "content://a"), model.state.value.recents.map { it.uri })
        model.forgotten("content://b")
        assertEquals(listOf("content://a"), model.state.value.recents.map { it.uri })
        assertEquals(listOf("content://a"), model.recentsToStore().items.map { it.uri })
    }

    @Test
    fun aNewLoadClearsAnEarlierError() {
        val model = AppModel()
        model.failed(FailureKind.INVALID, "x.docx")
        model.loadingStarted()
        assertNull(model.state.value.error)
    }
}

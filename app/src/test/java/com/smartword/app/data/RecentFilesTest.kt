package com.smartword.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentFilesTest {

    private fun file(n: Int, at: Long = n.toLong()) = RecentFile("content://doc/$n", "ملف $n.docx", at)

    @Test
    fun newestFileComesFirst() {
        val list = RecentFiles().with(file(1)).with(file(2)).with(file(3))
        assertEquals(listOf(3, 2, 1), list.items.map { it.uri.substringAfterLast('/').toInt() })
    }

    @Test
    fun openingAFileAgainMovesItToTheTopWithoutDuplicates() {
        val list = RecentFiles().with(file(1)).with(file(2)).with(file(1, at = 10))
        assertEquals(listOf("content://doc/1", "content://doc/2"), list.items.map { it.uri })
        assertEquals(10L, list.items[0].lastOpenedMillis)
    }

    @Test
    fun theListIsCapped() {
        var list = RecentFiles(limit = 3)
        for (i in 1..6) list = list.with(file(i))
        assertEquals(listOf(6, 5, 4), list.items.map { it.uri.substringAfterLast('/').toInt() })
    }

    @Test
    fun removingAFile() {
        val list = RecentFiles().with(file(1)).with(file(2)).without("content://doc/1")
        assertEquals(listOf("content://doc/2"), list.items.map { it.uri })
        assertEquals(list.items, list.without("content://doc/missing").items)
    }

    @Test
    fun serializingAndParsingKeepsEverythingIncludingArabicNames() {
        val list = RecentFiles().with(file(1)).with(file(2))
        val back = RecentFiles.parse(list.serialize())
        assertEquals(list.items, back.items)
    }

    @Test
    fun namesWithTabsAndLineBreaksSurviveAsSingleLines() {
        val list = RecentFiles().with(RecentFile("content://a", "a\tb\nc.docx", 5))
        val back = RecentFiles.parse(list.serialize())
        assertEquals(1, back.items.size)
        assertEquals("a b c.docx", back.items[0].name)
    }

    @Test
    fun damagedOrEmptyTextGivesAnEmptyOrPartialList() {
        assertTrue(RecentFiles.parse(null).items.isEmpty())
        assertTrue(RecentFiles.parse("").items.isEmpty())
        val back = RecentFiles.parse("garbage\n12\tcontent://ok\tname.docx\nx\ty\tz\n\t\t")
        assertEquals(listOf("content://ok"), back.items.map { it.uri })
    }

    @Test
    fun parsingSortsByTimeAndDropsDuplicateUris() {
        val text = "1\tcontent://a\tA\n9\tcontent://b\tB\n5\tcontent://a\tA2"
        val back = RecentFiles.parse(text)
        assertEquals(listOf("content://b", "content://a"), back.items.map { it.uri })
        assertEquals("A2", back.items[1].name)
    }
}

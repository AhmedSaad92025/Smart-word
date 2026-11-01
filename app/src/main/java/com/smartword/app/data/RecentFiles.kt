package com.smartword.app.data

/** A document the user opened or saved before. [uri] is the persistable content URI as text. */
data class RecentFile(val uri: String, val name: String, val lastOpenedMillis: Long)

/**
 * The list of recent documents: newest first, one entry per document, at most [limit] entries.
 * Immutable; every change returns a new list. Plain Kotlin so it is tested without Android.
 */
class RecentFiles private constructor(val items: List<RecentFile>, private val limit: Int) {

    constructor(limit: Int = DEFAULT_LIMIT) : this(emptyList(), limit)

    /** Adds or refreshes [file], moving it to the top. */
    fun with(file: RecentFile): RecentFiles {
        val rest = items.filter { it.uri != file.uri }
        return RecentFiles((listOf(file) + rest).take(limit), limit)
    }

    fun without(uri: String): RecentFiles = RecentFiles(items.filter { it.uri != uri }, limit)

    /** One line per entry: `millis<TAB>uri<TAB>name`, with tabs and line breaks in names replaced by spaces. */
    fun serialize(): String =
        items.joinToString("\n") { "${it.lastOpenedMillis}\t${it.uri}\t${clean(it.name)}" }

    companion object {
        const val DEFAULT_LIMIT = 20

        private fun clean(text: String) = text.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')

        /** Reads text written by [serialize]; damaged lines are skipped. */
        fun parse(text: String?, limit: Int = DEFAULT_LIMIT): RecentFiles {
            if (text.isNullOrEmpty()) return RecentFiles(limit)
            val parsed = text.split('\n').mapNotNull { line ->
                val parts = line.split('\t', limit = 3)
                val millis = parts.getOrNull(0)?.toLongOrNull()
                val uri = parts.getOrNull(1)
                val name = parts.getOrNull(2)
                if (millis == null || uri.isNullOrEmpty() || name == null) null else RecentFile(uri, name, millis)
            }
            val unique = LinkedHashMap<String, RecentFile>()
            for (file in parsed.sortedByDescending { it.lastOpenedMillis }) unique.putIfAbsent(file.uri, file)
            return RecentFiles(unique.values.take(limit), limit)
        }
    }
}

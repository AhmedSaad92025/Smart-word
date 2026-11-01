package com.smartword.app.preview

/** What a document contains, for the status line: it tells "nothing was read" apart from "read but not drawn". */
data class PreviewStats(val paragraphs: Int, val characters: Int, val otherBlocks: Int) {
    val hasText: Boolean get() = characters > 0

    companion object {
        fun of(blocks: List<PreviewBlock>): PreviewStats {
            var paragraphs = 0
            var characters = 0
            var other = 0
            for (block in blocks) {
                when (block) {
                    is PreviewParagraph -> {
                        paragraphs++
                        characters += block.spans.sumOf { it.text.length }
                        if (block.listLabel != null) characters += block.listLabel.length
                    }
                    is PreviewPlaceholder -> other++
                }
            }
            return PreviewStats(paragraphs, characters, other)
        }
    }
}

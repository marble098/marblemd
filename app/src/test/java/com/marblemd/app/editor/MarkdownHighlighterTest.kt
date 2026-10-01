package com.marblemd.app.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownHighlighterTest {

    private fun textOf(markdown: String, token: MarkdownHighlighter.Token): List<String> =
        MarkdownHighlighter.highlight(markdown)
            .filter { it.token == token }
            .map { markdown.substring(it.start, it.end.coerceAtMost(markdown.length)) }

    @Test
    fun headingLinesAreDetected() {
        val markdown = "# Title\n\nBody text\n"
        assertTrue(textOf(markdown, MarkdownHighlighter.Token.HEADING).any { it.startsWith("# Title") })
    }

    @Test
    fun fencedCodeIsHighlightedAsCodeAndPreventsHeadingDetection() {
        val markdown = "```md\n# not a heading\n```\n\n# real heading\n"
        val code = textOf(markdown, MarkdownHighlighter.Token.CODE)
        val headings = textOf(markdown, MarkdownHighlighter.Token.HEADING)

        assertTrue(code.any { it.contains("not a heading") })
        assertEquals(listOf("# real heading"), headings)
    }

    @Test
    fun inlineMarkersAndLinksAreDetected() {
        val markdown = "Some **bold** and *italic* with `code` and [link](https://example.com)"
        assertTrue(textOf(markdown, MarkdownHighlighter.Token.STRONG).contains("**bold**"))
        assertTrue(textOf(markdown, MarkdownHighlighter.Token.EMPHASIS).contains("*italic*"))
        assertTrue(textOf(markdown, MarkdownHighlighter.Token.CODE).contains("`code`"))
        assertTrue(
            textOf(markdown, MarkdownHighlighter.Token.LINK)
                .contains("[link](https://example.com)")
        )
    }

    @Test
    fun quotesListsTasksAndRulesAreDetected() {
        val markdown = """
            > quoted line

            - item one

            1. numbered

            - [x] done

            ---
        """.trimIndent()

        assertTrue(textOf(markdown, MarkdownHighlighter.Token.QUOTE).any { it.startsWith(">") })
        assertTrue(textOf(markdown, MarkdownHighlighter.Token.LIST).any { it.trim() == "-" })
        assertTrue(textOf(markdown, MarkdownHighlighter.Token.RULE).isNotEmpty())
    }

    @Test
    fun emptyAndHugeDocumentsAreSkippedCheaply() {
        assertEquals(emptyList<MarkdownHighlighter.Range>(), MarkdownHighlighter.highlight(""))
        val huge = "# heading\n".repeat(MarkdownHighlighter.MAX_HIGHLIGHT_CHARS)
        assertEquals(emptyList<MarkdownHighlighter.Range>(), MarkdownHighlighter.highlight(huge))
    }

    @Test
    fun rangesStayInsideTheDocument() {
        val markdown = "# هدر فارسی با **بولد**\n\n- آیتم\n"
        MarkdownHighlighter.highlight(markdown).forEach { range ->
            assertTrue(range.start >= 0)
            assertTrue(range.end <= markdown.length)
            assertTrue(range.start < range.end)
        }
    }
}

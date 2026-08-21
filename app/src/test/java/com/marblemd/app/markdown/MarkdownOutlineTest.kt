package com.marblemd.app.markdown

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownOutlineTest {
    @Test
    fun parsesAtxAndSetextAndIgnoresFencedCode() {
        val markdown = """
            # One

            ## Two

            ```md
            # Not a heading
            ```

            Three
            =====

            Four
            ----
        """.trimIndent()

        val headings = MarkdownOutline.parse(markdown)

        assertEquals(listOf("One", "Two", "Three", "Four"), headings.map { it.title })
        assertEquals(listOf(1, 2, 1, 2), headings.map { it.level })
    }
}

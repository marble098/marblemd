package com.marblemd.app.editor

import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownActionsTest {

    private fun at(text: String, start: Int, end: Int = start) =
        TextFieldValue(text, TextRange(start, end))

    @Test
    fun boldWrapsTheSelectionAndSelectsTheText() {
        val value = at("hello world", 0, 5)
        val result = applyMarkdownAction(value, MarkdownAction.BOLD)

        assertEquals("**hello** world", result.text)
        assertEquals("hello", result.text.substring(result.selection.start, result.selection.end))
    }

    @Test
    fun boldWithoutSelectionInsertsPlaceholder() {
        val result = applyMarkdownAction(at("", 0), MarkdownAction.BOLD)
        assertEquals("**bold text**", result.text)
    }

    @Test
    fun headingReplacesAnExistingHeadingLevel() {
        val text = "## Old title"
        val result = applyMarkdownAction(at(text, text.length), MarkdownAction.H1)
        assertEquals("# Old title", result.text)
    }

    @Test
    fun bulletListPrefixesEverySelectedLine() {
        val value = at("one\ntwo\nthree", 0, 11)
        val result = applyMarkdownAction(value, MarkdownAction.BULLET_LIST)

        assertEquals("- one\n- two\n- three", result.text)
    }

    @Test
    fun numberedListNumbersEachLine() {
        val value = at("alpha\nbeta", 0, 10)
        val result = applyMarkdownAction(value, MarkdownAction.NUMBERED_LIST)

        assertEquals("1. alpha\n2. beta", result.text)
    }

    @Test
    fun taskListCreatesCheckboxes() {
        val value = at("todo", 0, 4)
        val result = applyMarkdownAction(value, MarkdownAction.TASK_LIST)
        assertEquals("- [ ] todo", result.text)
    }

    @Test
    fun codeBlockWrapsTheSelectionInFences() {
        val value = at("val x = 1", 0, 9)
        val result = applyMarkdownAction(value, MarkdownAction.CODE_BLOCK)
        assertEquals("```\nval x = 1\n```", result.text)
    }

    @Test
    fun tableInsertsACompleteTemplate() {
        val result = applyMarkdownAction(at("", 0), MarkdownAction.TABLE)
        assertTrue(result.text.contains("| Column 1 | Column 2 |"))
        assertTrue(result.text.contains("| --- | --- |"))
        assertTrue(result.text.endsWith("\n"))
    }

    @Test
    fun findMatchesIsLiteralAndCaseInsensitive() {
        val text = "Markdown markdown MARKDOWN"
        val matches = findMatches(text, "markdown")
        assertEquals(3, matches.size)
        assertEquals(0, matches.first().first)
        assertEquals(1, findMatches(text, "markdown", matchCase = true).size)
        assertEquals(emptyList<IntRange>(), findMatches(text, ""))
    }

    @Test
    fun findMatchesHandlesOverlappingNeedles() {
        val matches = findMatches("aaaa", "aa")
        assertEquals(listOf(0..1, 2..3), matches)
    }
}

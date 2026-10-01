package com.marblemd.app.editor

import androidx.compose.ui.text.TextFieldValue
import androidx.compose.ui.text.TextRange

/**
 * Markdown authoring helpers used by the editor toolbar.
 *
 * Pure functions over [TextFieldValue]: they wrap the selection, prefix whole
 * lines or insert block level elements, then place the caret where the user is
 * expected to type next.
 */
enum class MarkdownAction(
    val shortLabel: String,
    val label: String,
    val description: String
) {
    H1("H1", "Heading 1", "Main heading"),
    H2("H2", "Heading 2", "Second-level heading"),
    H3("H3", "Heading 3", "Third-level heading"),
    H4("H4", "Heading 4", "Fourth-level heading"),
    H5("H5", "Heading 5", "Fifth-level heading"),
    H6("H6", "Heading 6", "Sixth-level heading"),
    BOLD("B", "Bold", "Wrap selection with **bold**"),
    ITALIC("I", "Italic", "Wrap selection with *italic*"),
    STRIKE("S", "Strikethrough", "Wrap selection with ~~strikethrough~~"),
    INLINE_CODE("`", "Inline code", "Wrap selection in backticks"),
    LINK("↗", "Link", "Insert [text](url)"),
    IMAGE("Img", "Image", "Insert ![alt](url)"),
    QUOTE(">", "Block quote", "Prefix selected lines with >"),
    BULLET_LIST("•", "Bullet list", "Create an unordered list"),
    NUMBERED_LIST("1.", "Numbered list", "Create an ordered list"),
    TASK_LIST("☐", "Task list", "Create - [ ] checklist items"),
    CODE_BLOCK("```", "Code block", "Insert a fenced code block"),
    HORIZONTAL_RULE("—", "Horizontal rule", "Insert ---"),
    TABLE("Tbl", "Table", "Insert a Markdown table template")
}

fun applyMarkdownAction(value: TextFieldValue, action: MarkdownAction): TextFieldValue =
    when (action) {
        MarkdownAction.H1 -> heading(value, 1)
        MarkdownAction.H2 -> heading(value, 2)
        MarkdownAction.H3 -> heading(value, 3)
        MarkdownAction.H4 -> heading(value, 4)
        MarkdownAction.H5 -> heading(value, 5)
        MarkdownAction.H6 -> heading(value, 6)
        MarkdownAction.BOLD -> wrap(value, "**", "**", "bold text")
        MarkdownAction.ITALIC -> wrap(value, "*", "*", "italic text")
        MarkdownAction.STRIKE -> wrap(value, "~~", "~~", "strikethrough")
        MarkdownAction.INLINE_CODE -> wrap(value, "`", "`", "code")
        MarkdownAction.LINK -> link(value)
        MarkdownAction.IMAGE -> image(value)
        MarkdownAction.QUOTE -> prefixLines(value) { "> " }
        MarkdownAction.BULLET_LIST -> prefixLines(value) { "- " }
        MarkdownAction.NUMBERED_LIST -> prefixLines(value) { index -> "${index + 1}. " }
        MarkdownAction.TASK_LIST -> prefixLines(value) { "- [ ] " }
        MarkdownAction.CODE_BLOCK -> fencedCode(value)
        MarkdownAction.HORIZONTAL_RULE -> insertBlock(value, "---")
        MarkdownAction.TABLE -> insertBlock(
            value,
            """
            | Column 1 | Column 2 |
            | --- | --- |
            | Value 1 | Value 2 |
            """.trimIndent()
        )
    }

private fun normalizedSelection(value: TextFieldValue): IntRange {
    val start = minOf(value.selection.start, value.selection.end).coerceIn(0, value.text.length)
    val end = maxOf(value.selection.start, value.selection.end).coerceIn(0, value.text.length)
    return start..end
}

private fun wrap(
    value: TextFieldValue,
    prefix: String,
    suffix: String,
    placeholder: String
): TextFieldValue {
    val range = normalizedSelection(value)
    val start = range.first
    val end = range.last
    val selected = value.text.substring(start, end)
    val inner = selected.ifEmpty { placeholder }
    val replacement = prefix + inner + suffix
    val next = value.text.replaceRange(start, end, replacement)
    val innerStart = start + prefix.length
    return TextFieldValue(
        text = next,
        selection = TextRange(innerStart, innerStart + inner.length)
    )
}

private fun heading(value: TextFieldValue, level: Int): TextFieldValue {
    val cursor = minOf(value.selection.start, value.selection.end)
        .coerceIn(0, value.text.length)
    val lineStart = if (cursor <= 0) {
        0
    } else {
        value.text.lastIndexOf('\n', cursor - 1).let { if (it < 0) 0 else it + 1 }
    }
    val lineEnd = value.text.indexOf('\n', cursor)
        .let { if (it < 0) value.text.length else it }
    val current = value.text.substring(lineStart, lineEnd)
    val clean = current.replace(Regex("""^#{1,6}\s+"""), "")
    val replacement = "#".repeat(level) + " " + clean
    val next = value.text.replaceRange(lineStart, lineEnd, replacement)
    val newCursor = (lineStart + replacement.length).coerceAtMost(next.length)
    return TextFieldValue(next, TextRange(newCursor))
}

private fun prefixLines(
    value: TextFieldValue,
    prefix: (Int) -> String
): TextFieldValue {
    val range = normalizedSelection(value)
    val start = range.first
    val end = range.last
    val blockStart = if (start <= 0) {
        0
    } else {
        value.text.lastIndexOf('\n', start - 1).let { if (it < 0) 0 else it + 1 }
    }
    val blockEnd = value.text.indexOf('\n', end)
        .let { if (it < 0) value.text.length else it }

    val lines = value.text.substring(blockStart, blockEnd).split('\n')
    val replacement = lines.mapIndexed { index, line ->
        if (line.isBlank()) line else prefix(index) + line
    }.joinToString("\n")

    val next = value.text.replaceRange(blockStart, blockEnd, replacement)
    return TextFieldValue(
        text = next,
        selection = TextRange(blockStart, blockStart + replacement.length)
    )
}

private fun link(value: TextFieldValue): TextFieldValue {
    val range = normalizedSelection(value)
    val start = range.first
    val end = range.last
    val selected = value.text.substring(start, end)
    val label = selected.ifEmpty { "link text" }
    val url = "https://"
    val replacement = "[$label]($url)"
    val next = value.text.replaceRange(start, end, replacement)
    val urlStart = start + 1 + label.length + 2
    return TextFieldValue(
        text = next,
        selection = TextRange(urlStart, urlStart + url.length)
    )
}

private fun image(value: TextFieldValue): TextFieldValue {
    val range = normalizedSelection(value)
    val start = range.first
    val end = range.last
    val selected = value.text.substring(start, end)
    val alt = selected.ifEmpty { "image description" }
    val url = "https://"
    val replacement = "![$alt]($url)"
    val next = value.text.replaceRange(start, end, replacement)
    val urlStart = start + 2 + alt.length + 2
    return TextFieldValue(
        text = next,
        selection = TextRange(urlStart, urlStart + url.length)
    )
}

private fun fencedCode(value: TextFieldValue): TextFieldValue {
    val range = normalizedSelection(value)
    val start = range.first
    val end = range.last
    val selected = value.text.substring(start, end)
    val inner = selected.ifEmpty { "code" }
    val replacement = "```\n$inner\n```"
    val next = value.text.replaceRange(start, end, replacement)
    val innerStart = start + 4
    return TextFieldValue(
        text = next,
        selection = TextRange(innerStart, innerStart + inner.length)
    )
}

private fun insertBlock(
    value: TextFieldValue,
    block: String
): TextFieldValue {
    val range = normalizedSelection(value)
    val start = range.first
    val end = range.last
    val beforeNeedsBreak = start > 0 && value.text[start - 1] != '\n'
    val afterNeedsBreak = end < value.text.length && value.text[end] != '\n'
    val replacement = buildString {
        if (beforeNeedsBreak) append("\n\n")
        append(block)
        if (afterNeedsBreak) append("\n\n") else append('\n')
    }
    val next = value.text.replaceRange(start, end, replacement)
    return TextFieldValue(
        text = next,
        selection = TextRange((start + replacement.length).coerceAtMost(next.length))
    )
}

/** Case-insensitive (optionally case-sensitive) literal search for the find bar. */
fun findMatches(text: String, query: String, matchCase: Boolean = false): List<IntRange> {
    if (query.isEmpty()) return emptyList()
    val haystack = if (matchCase) text else text.lowercase()
    val needle = if (matchCase) query else query.lowercase()
    val matches = mutableListOf<IntRange>()
    var index = haystack.indexOf(needle)
    var guard = 0
    while (index >= 0 && guard < MAX_MATCHES) {
        matches += index until index + needle.length
        index = haystack.indexOf(needle, index + needle.length)
        guard++
    }
    return matches
}

private const val MAX_MATCHES = 5_000

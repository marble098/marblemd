package com.marblemd.app.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.verticalScroll

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MarkdownEditor(
    markdown: String,
    onMarkdownChange: (String) -> Unit,
    scrollState: ScrollState,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    var value by remember { mutableStateOf(TextFieldValue(markdown)) }
    var insertSheet by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val caretOffset = value.selection.end.coerceIn(0, value.text.length)
    val lineStart = if (caretOffset == 0) {
        0
    } else {
        value.text.lastIndexOf('\n', caretOffset - 1).let { if (it < 0) 0 else it + 1 }
    }
    val lineNumber = value.text.take(caretOffset).count { it == '\n' } + 1
    val columnNumber = caretOffset - lineStart + 1
    val wordCount = remember(value.text) { Regex("\\S+").findAll(value.text).count() }

    LaunchedEffect(markdown) {
        if (markdown != value.text) {
            val cursor = value.selection.end.coerceIn(0, markdown.length)
            value = TextFieldValue(
                text = markdown,
                selection = TextRange(cursor)
            )
        }
    }

    fun apply(action: MarkdownAction) {
        val next = applyMarkdownAction(value, action)
        value = next
        onMarkdownChange(next.text)
        focusRequester.requestFocus()
    }

    if (insertSheet) {
        MarkdownInsertSheet(
            onDismiss = { insertSheet = false },
            onAction = { action ->
                insertSheet = false
                apply(action)
            }
        )
    }

    Surface(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(
                color = colors.surfaceContainerLow,
                tonalElevation = 1.dp
            ) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(QUICK_ACTIONS) { action ->
                        AssistChip(
                            onClick = { apply(action) },
                            label = { Text(action.shortLabel) }
                        )
                    }
                    item {
                        AssistChip(
                            onClick = { insertSheet = true },
                            label = { Text("+ Insert") }
                        )
                    }
                }
            }

            HorizontalDivider()

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = { next ->
                        value = next
                        onMarkdownChange(next.text)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = colors.onSurface,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 16.sp,
                        lineHeight = 24.sp
                    ),
                    cursorBrush = SolidColor(colors.primary)
                )
            }

            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 7.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(
                    "Ln $lineNumber, Col $columnNumber",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant
                )
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                Text(
                    "$wordCount words • ${value.text.length} chars",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MarkdownInsertSheet(
    onDismiss: () -> Unit,
    onAction: (MarkdownAction) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            "Insert Markdown",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        Text(
            "Select text first to format it, or place the cursor where you want a new element.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )

        LazyColumn(
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(MarkdownAction.entries) { action ->
                ListItem(
                    headlineContent = { Text(action.label) },
                    supportingContent = { Text(action.description) },
                    leadingContent = {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                action.shortLabel,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAction(action) }
                        .padding(horizontal = 8.dp)
                )
            }
        }
    }
}

private enum class MarkdownAction(
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

private val QUICK_ACTIONS = listOf(
    MarkdownAction.H1,
    MarkdownAction.BOLD,
    MarkdownAction.ITALIC,
    MarkdownAction.BULLET_LIST,
    MarkdownAction.NUMBERED_LIST,
    MarkdownAction.LINK
)

private fun applyMarkdownAction(
    value: TextFieldValue,
    action: MarkdownAction
): TextFieldValue = when (action) {
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


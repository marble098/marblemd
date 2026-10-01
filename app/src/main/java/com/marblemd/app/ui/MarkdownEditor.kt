package com.marblemd.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Redo
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TransformedText
import androidx.compose.ui.text.VisualTransformation
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marblemd.app.editor.EditorHistory
import com.marblemd.app.editor.MarkdownAction
import com.marblemd.app.editor.MarkdownHighlighter
import com.marblemd.app.editor.applyMarkdownAction
import com.marblemd.app.editor.findMatches
import kotlin.math.roundToInt

/**
 * The MarbleMD source editor.
 *
 * Professional feel without giving up the app's typography: line numbers,
 * Markdown syntax colouring, undo/redo, find & replace, quick-insert tools and
 * a live document status bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MarkdownEditor(
    markdown: String,
    onMarkdownChange: (String) -> Unit,
    scrollState: ScrollState,
    saveStateLabel: String,
    canSaveAs: Boolean,
    onSave: () -> Unit,
    onRequestSaveAs: () -> Unit,
    onExitToPreview: () -> Unit,
    modifier: Modifier = Modifier,
    fontFamily: FontFamily? = null
) {
    val colors = MaterialTheme.colorScheme
    var value by remember { mutableStateOf(TextFieldValue(markdown)) }
    var insertSheet by remember { mutableStateOf(false) }
    var findVisible by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }
    var matchCase by remember { mutableStateOf(false) }
    var activeMatch by remember { mutableStateOf(0) }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val history = remember { EditorHistory() }
    val focusRequester = remember { FocusRequester() }
    val findFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    val editorStyle = MaterialTheme.typography.bodyLarge.copy(
        color = colors.onSurface,
        fontFamily = fontFamily ?: FontFamily.Monospace,
        fontSize = 16.sp,
        lineHeight = 25.sp
    )

    LaunchedEffect(markdown) {
        if (markdown != value.text) {
            val cursor = value.selection.end.coerceIn(0, markdown.length)
            value = TextFieldValue(markdown, TextRange(cursor))
            layout = null
        }
    }

    fun commit(next: TextFieldValue, recordHistory: Boolean = true) {
        if (next.text == value.text && next.selection == value.selection) return
        if (recordHistory) {
            history.record(
                EditorHistory.Snapshot(
                    text = value.text,
                    selectionStart = value.selection.start,
                    selectionEnd = value.selection.end
                )
            )
        }
        value = next
        onMarkdownChange(next.text)
    }

    fun restore(snapshot: EditorHistory.Snapshot?) {
        snapshot ?: return
        val start = snapshot.selectionStart.coerceIn(0, snapshot.text.length)
        val end = snapshot.selectionEnd.coerceIn(0, snapshot.text.length)
        value = TextFieldValue(snapshot.text, TextRange(start, end))
        onMarkdownChange(snapshot.text)
        focusRequester.requestFocus()
    }

    fun snapshot() = EditorHistory.Snapshot(
        text = value.text,
        selectionStart = value.selection.start,
        selectionEnd = value.selection.end
    )

    fun apply(action: MarkdownAction) {
        commit(applyMarkdownAction(value, action))
        focusRequester.requestFocus()
    }

    val matches = remember(value.text, findQuery, matchCase) {
        findMatches(value.text, findQuery, matchCase)
    }
    val currentMatch = matches.getOrNull(activeMatch.coerceIn(0, (matches.size - 1).coerceAtLeast(0)))

    fun scrollToMatch(range: IntRange, target: TextLayoutResult?) {
        val targetLayout = target ?: return
        val line = targetLayout.getLineForOffset(range.first.coerceIn(0, value.text.length))
        val destination = (targetLayout.getLineTop(line) - 24f).coerceAtLeast(0f).roundToInt()
        scope.launch {
            scrollState.scrollTo(destination.coerceIn(0, scrollState.maxValue))
        }
    }

    fun goToMatch(index: Int) {
        if (matches.isEmpty()) return
        val bounded = ((index % matches.size) + matches.size) % matches.size
        activeMatch = bounded
        val range = matches[bounded]
        value = TextFieldValue(
            text = value.text,
            selection = TextRange(range.first, range.last + 1)
        )
        scrollToMatch(range, layout)
    }

    fun replaceCurrent() {
        val range = currentMatch ?: return
        if (range.first < 0 || range.last + 1 > value.text.length) return
        val next = value.text.replaceRange(range.first, range.last + 1, replaceQuery)
        commit(TextFieldValue(next, TextRange((range.first + replaceQuery.length).coerceAtMost(next.length))))
    }

    fun replaceAll() {
        if (matches.isEmpty()) return
        val builder = StringBuilder()
        var cursor = 0
        matches.forEach { range ->
            if (range.first >= cursor) {
                builder.append(value.text, cursor, range.first)
                builder.append(replaceQuery)
                cursor = range.last + 1
            }
        }
        builder.append(value.text, cursor, value.text.length)
        commit(TextFieldValue(builder.toString(), TextRange(0)))
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
            EditorToolbar(
                canUndo = history.canUndo,
                canRedo = history.canRedo,
                saveStateLabel = saveStateLabel,
                findVisible = findVisible,
                onUndo = { restore(history.undo(snapshot())) },
                onRedo = { restore(history.redo(snapshot())) },
                onAction = ::apply,
                onInsertSheet = { insertSheet = true },
                onToggleFind = {
                    findVisible = !findVisible
                    if (findVisible) findFocus.requestFocus()
                },
                onSave = onSave,
                onRequestSaveAs = onRequestSaveAs,
                canSaveAs = canSaveAs,
                onExitToPreview = onExitToPreview
            )

            if (findVisible) {
                FindBar(
                    query = findQuery,
                    replaceQuery = replaceQuery,
                    matchCase = matchCase,
                    matchCount = matches.size,
                    activeIndex = if (matches.isEmpty()) 0 else activeMatch + 1,
                    focusRequester = findFocus,
                    onQueryChange = {
                        findQuery = it
                        activeMatch = 0
                    },
                    onReplaceChange = { replaceQuery = it },
                    onToggleCase = { matchCase = !matchCase },
                    onPrevious = { goToMatch(activeMatch - 1) },
                    onNext = { goToMatch(activeMatch + 1) },
                    onReplace = { replaceCurrent() },
                    onReplaceAll = { replaceAll() },
                    onClose = { findVisible = false }
                )
            }

            HorizontalDivider()

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp)
                ) {
                    LineNumberGutter(
                        layout = layout,
                        modifier = Modifier.padding(start = 10.dp, end = 6.dp)
                    )

                    BasicTextField(
                        value = value,
                        onValueChange = { next -> commit(next) },
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 16.dp)
                            .focusRequester(focusRequester),
                        textStyle = editorStyle,
                        cursorBrush = SolidColor(colors.primary),
                        visualTransformation = rememberHighlightTransformation(value.text),
                        onTextLayout = { result -> layout = result }
                    )
                }
            }

            HorizontalDivider()
            EditorStatusBar(
                text = value.text,
                cursor = value.selection.end,
                saveStateLabel = saveStateLabel,
                matchCount = if (findVisible) matches.size else null
            )
        }
    }
}

@Composable
private fun EditorToolbar(
    canUndo: Boolean,
    canRedo: Boolean,
    saveStateLabel: String,
    findVisible: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onAction: (MarkdownAction) -> Unit,
    onInsertSheet: () -> Unit,
    onToggleFind: () -> Unit,
    onSave: () -> Unit,
    onRequestSaveAs: () -> Unit,
    canSaveAs: Boolean,
    onExitToPreview: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 1.dp) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onUndo, enabled = canUndo) {
                    Icon(Icons.Outlined.Undo, contentDescription = "Undo")
                }
                IconButton(onClick = onRedo, enabled = canRedo) {
                    Icon(Icons.Outlined.Redo, contentDescription = "Redo")
                }
                ToolbarDivider()

                IconButton(onClick = { onAction(MarkdownAction.H1) }) {
                    Icon(Icons.Outlined.Title, contentDescription = "Heading")
                }
                IconButton(onClick = { onAction(MarkdownAction.BOLD) }) {
                    Icon(Icons.Outlined.FormatBold, contentDescription = "Bold")
                }
                IconButton(onClick = { onAction(MarkdownAction.ITALIC) }) {
                    Icon(Icons.Outlined.FormatItalic, contentDescription = "Italic")
                }
                IconButton(onClick = { onAction(MarkdownAction.INLINE_CODE) }) {
                    Icon(Icons.Outlined.Code, contentDescription = "Inline code")
                }
                IconButton(onClick = { onAction(MarkdownAction.LINK) }) {
                    Icon(Icons.Outlined.Link, contentDescription = "Link")
                }
                IconButton(onClick = { onAction(MarkdownAction.BULLET_LIST) }) {
                    Icon(Icons.Outlined.FormatListBulleted, contentDescription = "Bullet list")
                }
                IconButton(onClick = { onAction(MarkdownAction.TABLE) }) {
                    Icon(Icons.Outlined.TableChart, contentDescription = "Table")
                }
                ToolbarDivider()

                AssistChip(
                    onClick = onInsertSheet,
                    label = { Text("+ Insert") }
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onToggleFind, enabled = !findVisible) {
                    Icon(Icons.Outlined.Search, contentDescription = "Find and replace")
                }
                IconButton(onClick = onExitToPreview) {
                    Icon(Icons.Outlined.Visibility, contentDescription = "Preview")
                }
                IconButton(onClick = onSave) {
                    Icon(Icons.Outlined.Save, contentDescription = "Save")
                }
                if (canSaveAs) {
                    TextButton(onClick = onRequestSaveAs) { Text("Save as") }
                    Spacer(Modifier.width(6.dp))
                }
            }
            Text(
                text = "Editing • $saveStateLabel",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 14.dp, bottom = 4.dp)
            )
        }
    }
}

@Composable
private fun ToolbarDivider() {
    Box(
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .width(1.dp)
            .height(26.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

@Composable
private fun FindBar(
    query: String,
    replaceQuery: String,
    matchCase: Boolean,
    matchCount: Int,
    activeIndex: Int,
    focusRequester: FocusRequester,
    onQueryChange: (String) -> Unit,
    onReplaceChange: (String) -> Unit,
    onToggleCase: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onReplace: () -> Unit,
    onReplaceAll: () -> Unit,
    onClose: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    singleLine = true,
                    label = { Text("Find") },
                    trailingIcon = {
                        Text(
                            text = if (query.isEmpty()) "" else if (matchCount == 0) "0/0" else "$activeIndex/$matchCount",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(end = 10.dp)
                        )
                    }
                )
                IconButton(onClick = onToggleCase) {
                    Text(
                        text = "Aa",
                        fontWeight = if (matchCase) FontWeight.Bold else FontWeight.Normal,
                        color = if (matchCase) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                IconButton(onClick = onPrevious) { Text("↑") }
                IconButton(onClick = onNext) { Text("↓") }
                IconButton(onClick = onClose) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close find")
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = replaceQuery,
                    onValueChange = onReplaceChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("Replace with") }
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    TextButton(onClick = onReplace, enabled = matchCount > 0) { Text("Replace") }
                    TextButton(onClick = onReplaceAll, enabled = matchCount > 0) { Text("All") }
                }
            }
        }
    }
}

@Composable
private fun LineNumberGutter(
    layout: TextLayoutResult?,
    modifier: Modifier = Modifier
) {
    val target = layout ?: return
    if (target.lineCount <= 1 || target.lineCount > MAX_GUTTER_LINES) return

    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    val style = TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = color)
    val digits = target.lineCount.toString().length

    // Numbers are measured once per document state, never per frame.
    val numberLayouts = remember(target.lineCount, style, measurer) {
        List(target.lineCount) { index -> measurer.measure(AnnotatedString("${index + 1}"), style) }
    }
    val widest = remember(digits, style, measurer) {
        measurer.measure(AnnotatedString("0".repeat(digits)), style).size.width
    }

    val gutterWidth = with(density) { (widest + 12).toDp() }
    val height = with(density) { target.size.height.toDp() }

    Canvas(
        modifier = modifier
            .width(gutterWidth)
            .height(height)
    ) {
        numberLayouts.forEachIndexed { line, number ->
            val baseline = target.getLineBaseline(line)
            if (baseline - number.size.height > size.height) return@forEachIndexed
            drawText(
                textLayoutResult = number,
                topLeft = androidx.compose.ui.geometry.Offset(
                    x = (size.width - number.size.width - 2f).coerceAtLeast(0f),
                    y = baseline - number.getLineBaseline(0)
                )
            )
        }
    }
}

/** Past this many lines the gutter is skipped to keep scrolling smooth. */
private const val MAX_GUTTER_LINES = 3_000

@Composable
private fun EditorStatusBar(
    text: String,
    cursor: Int,
    saveStateLabel: String,
    matchCount: Int?
) {
    val words = remember(text) { countWords(text) }
    val lines = remember(text) { text.count { it == '\n' } + 1 }
    val column = remember(text, cursor) {
        val safe = cursor.coerceIn(0, text.length)
        safe - (text.lastIndexOf('\n', (safe - 1).coerceAtLeast(0)).takeIf { safe > 0 } ?: -1)
    }
    val line = remember(text, cursor) {
        text.take(cursor.coerceIn(0, text.length)).count { it == '\n' } + 1
    }

    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$words words • ${text.length} chars • $lines lines",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = buildString {
                    append("Ln $line, Col $column")
                    if (matchCount != null) append(" • $matchCount matches")
                    append(" • ")
                    append(saveStateLabel)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End
            )
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

        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
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

/** Colours used while editing Markdown source. */
internal data class HighlightColors(
    val heading: Color,
    val emphasis: Color,
    val strong: Color,
    val code: Color,
    val link: Color,
    val quote: Color,
    val list: Color,
    val rule: Color,
    val comment: Color
)

@Composable
private fun highlightColors(): HighlightColors {
    val scheme = MaterialTheme.colorScheme
    return HighlightColors(
        heading = scheme.primary,
        emphasis = scheme.tertiary,
        strong = scheme.tertiary,
        code = scheme.secondary,
        link = scheme.primary,
        quote = scheme.onSurfaceVariant,
        list = scheme.secondary,
        rule = scheme.outline,
        comment = scheme.outline
    )
}

@Composable
private fun rememberHighlightTransformation(text: String): VisualTransformation {
    val palette = highlightColors()
    val ranges = remember(text, palette) { MarkdownHighlighter.highlight(text) }
    return remember(ranges, palette) { MarkdownRangeTransformation(ranges, palette) }
}

private class MarkdownRangeTransformation(
    private val ranges: List<MarkdownHighlighter.Range>,
    private val palette: HighlightColors
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        if (ranges.isEmpty()) return TransformedText(text, OffsetMappingIdentity)
        val builder = AnnotatedString.Builder(text)
        ranges.forEach { range ->
            if (range.start >= 0 && range.end <= text.length && range.start < range.end) {
                builder.addStyle(
                    SpanStyle(color = palette.colorFor(range.token)),
                    range.start,
                    range.end
                )
            }
        }
        return TransformedText(builder.toAnnotatedString(), OffsetMappingIdentity)
    }
}

private fun HighlightColors.colorFor(token: MarkdownHighlighter.Token): Color = when (token) {
    MarkdownHighlighter.Token.HEADING -> heading
    MarkdownHighlighter.Token.EMPHASIS -> emphasis
    MarkdownHighlighter.Token.STRONG -> strong
    MarkdownHighlighter.Token.CODE -> code
    MarkdownHighlighter.Token.LINK -> link
    MarkdownHighlighter.Token.URL -> link
    MarkdownHighlighter.Token.QUOTE -> quote
    MarkdownHighlighter.Token.LIST -> list
    MarkdownHighlighter.Token.RULE -> rule
    MarkdownHighlighter.Token.COMMENT -> comment
}

private val OffsetMappingIdentity = androidx.compose.ui.text.input.OffsetMapping.Identity

private fun countWords(text: String): Int =
    Regex("""\S+""").findAll(text).count()

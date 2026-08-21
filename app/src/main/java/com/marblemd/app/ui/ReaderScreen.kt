package com.marblemd.app.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marblemd.app.markdown.MarkdownHeading
import com.marblemd.app.markdown.MarkdownOutline
import com.marblemd.app.model.DirectionMode
import com.marblemd.app.model.MarkdownDocument
import com.marblemd.app.model.SaveState
import com.marblemd.app.update.UpdateStatus
import com.marblemd.app.update.UpdateUiState
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    documents: List<MarkdownDocument>,
    activeDocument: MarkdownDocument,
    fontSizeSp: Float,
    directionMode: DirectionMode,
    onOpen: () -> Unit,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onContentChange: (String) -> Unit,
    onRequestSaveAs: () -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onDirectionChange: (DirectionMode) -> Unit,
    updateState: UpdateUiState,
    onCheckForUpdates: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onInstallUpdate: () -> Unit
) {
    var directionMenu by remember { mutableStateOf(false) }
    var fontMenu by remember { mutableStateOf(false) }
    var pendingFontSize by remember(fontMenu, fontSizeSp) { mutableFloatStateOf(fontSizeSp) }
    var tabsSheet by remember { mutableStateOf(false) }
    var outlineSheet by remember { mutableStateOf(false) }
    var editMode by remember(activeDocument.id) { mutableStateOf(false) }
    var updateSheet by remember { mutableStateOf(false) }

    val outline = remember(activeDocument.content) {
        MarkdownOutline.parse(activeDocument.content)
    }
    val totalLines = remember(activeDocument.content) {
        activeDocument.content.count { it == '\n' } + 1
    }

    val readerScrollStates = remember { mutableMapOf<String, ScrollState>() }
    val editorScrollStates = remember { mutableMapOf<String, ScrollState>() }
    val readerScroll = readerScrollStates.getOrPut(activeDocument.id) { ScrollState(0) }
    val editorScroll = editorScrollStates.getOrPut(activeDocument.id) { ScrollState(0) }
    val scope = rememberCoroutineScope()

    if (tabsSheet) {
        TabsSheet(
            documents = documents,
            activeId = activeDocument.id,
            onDismiss = { tabsSheet = false },
            onSelect = {
                tabsSheet = false
                onSelectTab(it)
            },
            onClose = onCloseTab,
            onOpen = {
                tabsSheet = false
                onOpen()
            }
        )
    }

    if (outlineSheet) {
        OutlineSheet(
            headings = outline,
            onDismiss = { outlineSheet = false },
            onSelect = { heading ->
                outlineSheet = false
                val denominator = (totalLines - 1).coerceAtLeast(1)
                val fraction = heading.lineIndex.toFloat() / denominator.toFloat()
                val target = (readerScroll.maxValue * fraction).roundToInt()
                scope.launch { readerScroll.animateScrollTo(target) }
            }
        )
    }


    if (updateSheet) {
        UpdateSheet(
            state = updateState,
            onDismiss = { updateSheet = false },
            onCheck = onCheckForUpdates,
            onDownload = onDownloadUpdate,
            onInstall = onInstallUpdate
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = activeDocument.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = activeDocument.saveState.label(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onOpen) {
                        Icon(Icons.Outlined.FolderOpen, contentDescription = "Open Markdown files")
                    }
                },
                actions = {
                    BadgedBox(
                        badge = {
                            if (updateState.status == UpdateStatus.AVAILABLE ||
                                updateState.status == UpdateStatus.READY
                            ) {
                                Badge()
                            }
                        }
                    ) {
                        IconButton(onClick = { updateSheet = true }) {
                            Icon(
                                Icons.Outlined.SystemUpdateAlt,
                                contentDescription = "MarbleMD updates"
                            )
                        }
                    }
                    IconButton(
                        onClick = { outlineSheet = true },
                        enabled = outline.isNotEmpty() && !editMode
                    ) {
                        Icon(Icons.Outlined.FormatListBulleted, contentDescription = "Document outline")
                    }
                    IconButton(onClick = { editMode = !editMode }) {
                        Icon(
                            imageVector = if (editMode) Icons.Outlined.Visibility else Icons.Outlined.Edit,
                            contentDescription = if (editMode) "Preview Markdown" else "Edit Markdown"
                        )
                    }
                    BadgedBox(
                        badge = {
                            Badge {
                                Text(documents.size.toString())
                            }
                        }
                    ) {
                        IconButton(onClick = { tabsSheet = true }) {
                            Icon(Icons.Outlined.Description, contentDescription = "Open tabs")
                        }
                    }
                }
            )
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
        ) {
            Surface(tonalElevation = 2.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box {
                        AssistChip(
                            onClick = {
                                pendingFontSize = fontSizeSp
                                fontMenu = true
                            },
                            label = { Text("${fontSizeSp.toInt()} sp") },
                            leadingIcon = {
                                Icon(Icons.Outlined.TextFields, contentDescription = null)
                            }
                        )
                        DropdownMenu(
                            expanded = fontMenu,
                            onDismissRequest = { fontMenu = false },
                            modifier = Modifier.width(300.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    "Text size  ${pendingFontSize.roundToInt()} sp",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Slider(
                                    value = pendingFontSize,
                                    onValueChange = { pendingFontSize = it },
                                    valueRange = 12f..34f,
                                    steps = 21,
                                    onValueChangeFinished = {
                                        onFontSizeChange(pendingFontSize.roundToInt().toFloat())
                                        fontMenu = false
                                    }
                                )
                                Text(
                                    "Applied once when you release the slider",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Box {
                        AssistChip(
                            onClick = { directionMenu = true },
                            label = { Text(directionMode.label) },
                            leadingIcon = {
                                Icon(Icons.Outlined.SwapHoriz, contentDescription = null)
                            }
                        )
                        DropdownMenu(
                            expanded = directionMenu,
                            onDismissRequest = { directionMenu = false }
                        ) {
                            DirectionMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(mode.label) },
                                    onClick = {
                                        onDirectionChange(mode)
                                        directionMenu = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    if (
                        activeDocument.uri == null ||
                        !activeDocument.writable ||
                        activeDocument.saveState == SaveState.ERROR
                    ) {
                        TextButton(onClick = onRequestSaveAs) {
                            Icon(Icons.Outlined.Save, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Save as")
                        }
                    }
                }
            }

            HorizontalDivider()

            if (editMode) {
                MarkdownEditor(
                    markdown = activeDocument.content,
                    onMarkdownChange = onContentChange,
                    scrollState = editorScroll,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(readerScroll)
                ) {
                    MarkdownText(
                        markdown = activeDocument.content,
                        fontSizeSp = fontSizeSp,
                        directionMode = directionMode,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun MarkdownEditor(
    markdown: String,
    onMarkdownChange: (String) -> Unit,
    scrollState: ScrollState,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Surface(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            BasicTextField(
                value = markdown,
                onValueChange = onMarkdownChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = colors.onSurface,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 16.sp,
                    lineHeight = 24.sp
                ),
                cursorBrush = SolidColor(colors.primary)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabsSheet(
    documents: List<MarkdownDocument>,
    activeId: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    onClose: (String) -> Unit,
    onOpen: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = "Open Markdown tabs",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )

        LazyColumn {
            items(documents, key = { it.id }) { document ->
                ListItem(
                    modifier = Modifier.clickable { onSelect(document.id) },
                    leadingContent = {
                        Icon(
                            if (document.id == activeId) Icons.Outlined.CheckCircle else Icons.Outlined.Description,
                            contentDescription = null
                        )
                    },
                    headlineContent = {
                        Text(
                            document.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    supportingContent = {
                        Text(document.saveState.label())
                    },
                    trailingContent = {
                        IconButton(onClick = { onClose(document.id) }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Close tab")
                        }
                    }
                )
            }

            item {
                TextButton(
                    onClick = onOpen,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Open more Markdown files")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OutlineSheet(
    headings: List<MarkdownHeading>,
    onDismiss: () -> Unit,
    onSelect: (MarkdownHeading) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = "Smart outline",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        Text(
            text = "${headings.size} headings detected",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        LazyColumn {
            items(headings) { heading ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(heading) }
                        .padding(
                            start = (16 + (heading.level - 1) * 14).dp,
                            end = 18.dp,
                            top = 12.dp,
                            bottom = 12.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "H${heading.level}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = heading.title,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UpdateSheet(
    state: UpdateUiState,
    onDismiss: () -> Unit,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text("MarbleMD Update", style = MaterialTheme.typography.titleLarge)
            Text(
                "Installed: ${state.currentVersion}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            state.latestVersion?.let { latest ->
                Text(
                    "Latest: $latest • ${state.architecture}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.width(8.dp))
            Text(
                state.message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 12.dp)
            )

            if (state.status == UpdateStatus.CHECKING) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else if (state.status == UpdateStatus.DOWNLOADING) {
                LinearProgressIndicator(
                    progress = { state.progress / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "${state.progress}%",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (state.status) {
                    UpdateStatus.AVAILABLE -> {
                        TextButton(onClick = onDownload) { Text("Download update") }
                        TextButton(onClick = onCheck) { Text("Check again") }
                    }
                    UpdateStatus.READY -> {
                        TextButton(onClick = onInstall) { Text("Install update") }
                        TextButton(onClick = onCheck) { Text("Check again") }
                    }
                    UpdateStatus.CHECKING,
                    UpdateStatus.DOWNLOADING -> Unit
                    else -> {
                        TextButton(onClick = onCheck) { Text("Check for updates") }
                    }
                }
            }
        }
    }
}


private fun SaveState.label(): String = when (this) {
    SaveState.SAVED -> "Saved automatically"
    SaveState.SAVING -> "Saving…"
    SaveState.UNSAVED -> "Unsaved • choose Save as"
    SaveState.READ_ONLY -> "Read-only • Save as to edit a copy"
    SaveState.ERROR -> "Save failed • choose Save as"
}

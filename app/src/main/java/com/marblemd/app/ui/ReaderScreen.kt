package com.marblemd.app.ui

import android.text.format.DateUtils
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material.icons.outlined.TextFields
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.marblemd.app.markdown.MarkdownPlanHeading
import com.marblemd.app.markdown.MarkdownRenderPlan
import com.marblemd.app.model.DirectionMode
import com.marblemd.app.model.MarkdownDocument
import com.marblemd.app.model.ReaderFont
import com.marblemd.app.model.ReadingPosition
import com.marblemd.app.model.RecentDocumentInfo
import com.marblemd.app.model.SaveState
import com.marblemd.app.update.UpdateStatus
import com.marblemd.app.update.UpdateUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    documents: List<MarkdownDocument>,
    activeDocument: MarkdownDocument,
    fontSizeSp: Float,
    directionMode: DirectionMode,
    readerFont: ReaderFont,
    customFontPath: String?,
    customFontVersion: Int,
    customFontName: String?,
    fontActionMessage: String?,
    recentDocuments: List<RecentDocumentInfo>,
    activeReadingPosition: ReadingPosition,
    newDocumentId: String?,
    onOpen: () -> Unit,
    onNewDocument: () -> Unit,
    onOpenRecentDocument: (String) -> Unit,
    onForgetRecentDocument: (String) -> Unit,
    onReadingPositionChange: (String, ReadingPosition) -> Unit,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onContentChange: (String) -> Unit,
    onRequestSaveAs: () -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onDirectionChange: (DirectionMode) -> Unit,
    onReaderFontChange: (ReaderFont) -> Unit,
    onImportCustomFont: () -> Unit,
    onRemoveCustomFont: () -> Unit,
    updateState: UpdateUiState,
    onCheckForUpdates: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onInstallUpdate: () -> Unit
) {
    var tabsSheet by remember { mutableStateOf(false) }
    var recentSheet by remember { mutableStateOf(false) }
    var outlineSheet by remember { mutableStateOf(false) }
    var readingSettingsSheet by remember { mutableStateOf(false) }
    var updateSheet by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    val editModes = remember { mutableStateMapOf<String, Boolean>() }
    val editMode = editModes[activeDocument.id] ?: (activeDocument.id == newDocumentId)
    val renderPlans = remember { mutableStateMapOf<String, MarkdownRenderPlan>() }

    val readerListStates = remember { mutableMapOf<String, LazyListState>() }
    val editorScrollStates = remember { mutableMapOf<String, ScrollState>() }
    val restoredPosition = activeReadingPosition.normalized()
    val readerListState = readerListStates.getOrPut(activeDocument.id) {
        LazyListState(
            initialFirstVisibleItemIndex = restoredPosition.firstVisibleItemIndex,
            initialFirstVisibleItemScrollOffset = restoredPosition.firstVisibleItemScrollOffset
        )
    }
    val editorScroll = editorScrollStates.getOrPut(activeDocument.id) { ScrollState(0) }
    val scope = rememberCoroutineScope()
    val markdownEngine = rememberMarkdownEngine(readerFont, customFontPath, customFontVersion)
    val documentIds = documents.map { it.id }

    LaunchedEffect(documentIds) {
        val keep = documentIds.toSet()
        renderPlans.keys.toList().filterNot { it in keep }.forEach(renderPlans::remove)
        readerListStates.keys.retainAll(keep)
        editorScrollStates.keys.retainAll(keep)
        editModes.keys.retainAll(keep)
    }

    LaunchedEffect(activeDocument.id, readerListState, editMode) {
        if (!editMode) {
            snapshotFlow {
                ReadingPosition(
                    firstVisibleItemIndex = readerListState.firstVisibleItemIndex,
                    firstVisibleItemScrollOffset = readerListState.firstVisibleItemScrollOffset
                )
            }.distinctUntilChanged().collectLatest { position ->
                delay(READING_POSITION_DEBOUNCE_MS)
                onReadingPositionChange(activeDocument.id, position.normalized())
            }
        }
    }

    DisposableEffect(activeDocument.id, readerListState) {
        onDispose {
            onReadingPositionChange(
                activeDocument.id,
                ReadingPosition(
                    readerListState.firstVisibleItemIndex,
                    readerListState.firstVisibleItemScrollOffset
                ).normalized()
            )
        }
    }

    LaunchedEffect(activeDocument.id, activeDocument.revision, editMode, markdownEngine) {
        val cached = renderPlans[activeDocument.id]
        if (!editMode && cached?.revision != activeDocument.revision) {
            renderPlans.remove(activeDocument.id)
            val contentSnapshot = activeDocument.content
            val revisionSnapshot = activeDocument.revision
            renderPlans[activeDocument.id] = withContext(Dispatchers.Default) {
                markdownEngine.prepare(contentSnapshot, revisionSnapshot)
            }
        }
    }

    val activePlan = renderPlans[activeDocument.id]
        ?.takeIf { it.revision == activeDocument.revision }

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

    if (recentSheet) {
        RecentDocumentsSheet(
            documents = recentDocuments,
            onDismiss = { recentSheet = false },
            onSelect = { id ->
                recentSheet = false
                onOpenRecentDocument(id)
            },
            onForget = onForgetRecentDocument,
            onOpenFile = {
                recentSheet = false
                onOpen()
            }
        )
    }

    if (outlineSheet && activePlan != null) {
        OutlineSheet(
            headings = activePlan.headings,
            onDismiss = { outlineSheet = false },
            onSelect = { heading ->
                outlineSheet = false
                scope.launch { readerListState.animateScrollToItem(heading.blockIndex) }
            }
        )
    }

    if (readingSettingsSheet) {
        ReadingSettingsSheet(
            fontSizeSp = fontSizeSp,
            readerFont = readerFont,
            customFontName = customFontName,
            fontActionMessage = fontActionMessage,
            directionMode = directionMode,
            onDismiss = { readingSettingsSheet = false },
            onFontSizeChange = onFontSizeChange,
            onReaderFontChange = onReaderFontChange,
            onImportCustomFont = onImportCustomFont,
            onRemoveCustomFont = onRemoveCustomFont,
            onDirectionChange = onDirectionChange
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
                            text = if (editMode) {
                                "Editing • ${activeDocument.saveState.compactLabel()}"
                            } else {
                                activeDocument.saveState.compactLabel()
                            },
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
                            if (documents.size > 1) {
                                Badge { Text(documents.size.toString()) }
                            }
                        }
                    ) {
                        IconButton(onClick = { tabsSheet = true }) {
                            Icon(Icons.Outlined.Description, contentDescription = "Open tabs")
                        }
                    }

                    Box {
                        BadgedBox(
                            badge = {
                                if (updateState.status == UpdateStatus.AVAILABLE ||
                                    updateState.status == UpdateStatus.READY
                                ) {
                                    Badge()
                                }
                            }
                        ) {
                            IconButton(onClick = { moreMenu = true }) {
                                Icon(Icons.Outlined.MoreVert, contentDescription = "More options")
                            }
                        }

                        DropdownMenu(
                            expanded = moreMenu,
                            onDismissRequest = { moreMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (editMode) "Preview document" else "Edit Markdown") },
                                leadingIcon = {
                                    Icon(
                                        if (editMode) Icons.Outlined.Visibility else Icons.Outlined.Edit,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    moreMenu = false
                                    editModes[activeDocument.id] = !editMode
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("New Markdown document") },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Add, contentDescription = null)
                                },
                                onClick = {
                                    moreMenu = false
                                    onNewDocument()
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Recent Markdown files") },
                                leadingIcon = {
                                    Icon(Icons.Outlined.History, contentDescription = null)
                                },
                                onClick = {
                                    moreMenu = false
                                    recentSheet = true
                                }
                            )

                            HorizontalDivider()

                            DropdownMenuItem(
                                text = { Text("Smart outline") },
                                leadingIcon = {
                                    Icon(Icons.Outlined.FormatListBulleted, contentDescription = null)
                                },
                                enabled = activePlan?.headings?.isNotEmpty() == true && !editMode,
                                onClick = {
                                    moreMenu = false
                                    outlineSheet = true
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Reading settings") },
                                leadingIcon = {
                                    Icon(Icons.Outlined.TextFields, contentDescription = null)
                                },
                                onClick = {
                                    moreMenu = false
                                    readingSettingsSheet = true
                                }
                            )

                            if (
                                activeDocument.uri == null ||
                                !activeDocument.writable ||
                                activeDocument.saveState == SaveState.ERROR
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Save as") },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.Save, contentDescription = null)
                                    },
                                    onClick = {
                                        moreMenu = false
                                        onRequestSaveAs()
                                    }
                                )
                            }

                            HorizontalDivider()

                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (updateState.status == UpdateStatus.AVAILABLE ||
                                            updateState.status == UpdateStatus.READY
                                        ) {
                                            "Update available"
                                        } else {
                                            "Updates"
                                        }
                                    )
                                },
                                leadingIcon = {
                                    Icon(Icons.Outlined.SystemUpdateAlt, contentDescription = null)
                                },
                                onClick = {
                                    moreMenu = false
                                    updateSheet = true
                                }
                            )
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
            if (editMode) {
                MarkdownEditor(
                    markdown = activeDocument.content,
                    onMarkdownChange = onContentChange,
                    scrollState = editorScroll,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (activePlan == null) {
                PreparingDocument(
                    title = activeDocument.title,
                    characterCount = activeDocument.content.length,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                LazyMarkdownDocument(
                    plan = activePlan,
                    fontSizeSp = fontSizeSp,
                    directionMode = directionMode,
                    listState = readerListState,
                    engine = markdownEngine,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadingSettingsSheet(
    fontSizeSp: Float,
    readerFont: ReaderFont,
    customFontName: String?,
    fontActionMessage: String?,
    directionMode: DirectionMode,
    onDismiss: () -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onReaderFontChange: (ReaderFont) -> Unit,
    onImportCustomFont: () -> Unit,
    onRemoveCustomFont: () -> Unit,
    onDirectionChange: (DirectionMode) -> Unit
) {
    var pendingSize by remember(fontSizeSp) { mutableFloatStateOf(fontSizeSp) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Settings, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Reading settings", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Kept off the reading canvas until you need it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Text(
                        "Text size  ${pendingSize.roundToInt()} sp",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Slider(
                        value = pendingSize,
                        onValueChange = { pendingSize = it },
                        valueRange = 12f..34f,
                        steps = 21,
                        onValueChangeFinished = {
                            onFontSizeChange(pendingSize.roundToInt().toFloat())
                        }
                    )
                    Text(
                        "The document relayout happens once when you release the slider.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text("Font", style = MaterialTheme.typography.labelLarge)
            val availableFonts = ReaderFont.entries.filter {
                it != ReaderFont.CUSTOM || customFontName != null
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(availableFonts) { font ->
                    AssistChip(
                        onClick = { onReaderFontChange(font) },
                        label = {
                            Text(
                                if (font == readerFont) "✓ ${font.shortLabel}"
                                else font.shortLabel
                            )
                        }
                    )
                }
            }
            if (customFontName != null) {
                Text(
                    text = "Custom font: $customFontName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = onImportCustomFont) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (customFontName == null) "Add custom TTF" else "Change TTF")
                }
                if (customFontName != null) {
                    TextButton(onClick = onRemoveCustomFont) { Text("Remove") }
                }
            }
            fontActionMessage?.let { message ->
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text("Direction", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DirectionMode.entries.forEach { mode ->
                    AssistChip(
                        onClick = { onDirectionChange(mode) },
                        label = {
                            Text(
                                if (mode == directionMode) "✓ ${mode.label}"
                                else mode.label
                            )
                        }
                    )
                }
            }

            Spacer(Modifier.width(1.dp))
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 18.dp)
            ) {
                Text("Done")
            }
        }
    }
}

@Composable
private fun LazyMarkdownDocument(
    plan: MarkdownRenderPlan,
    fontSizeSp: Float,
    directionMode: DirectionMode,
    listState: LazyListState,
    engine: com.marblemd.app.markdown.MarkdownEngine,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        state = listState,
        modifier = modifier
    ) {
        itemsIndexed(
            items = plan.blocks,
            key = { _, block -> block.index }
        ) { index, block ->
            MarkdownText(
                node = block.node,
                fontSizeSp = fontSizeSp,
                directionMode = directionMode,
                engine = engine,
                isFirstBlock = index == 0,
                isLastBlock = index == plan.blocks.lastIndex,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun PreparingDocument(
    title: String,
    characterCount: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Preparing $title",
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Text(
            "Large documents are parsed into lazy blocks off the UI thread • ${formatCharacters(characterCount)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecentDocumentsSheet(
    documents: List<RecentDocumentInfo>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    onForget: (String) -> Unit,
    onOpenFile: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.History, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Recent Markdown", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Pick up where you left off.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (documents.isEmpty()) {
            Text(
                "Your recently opened files and local drafts will appear here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                items(documents, key = { it.id }) { document ->
                    val location = when {
                        document.uriString == null -> "Local draft"
                        document.hasUnsavedChanges && document.writable -> "Unsaved edits • editable file"
                        document.hasUnsavedChanges -> "Unsaved edits • local copy"
                        document.writable -> "Markdown file • editable"
                        else -> "Markdown file • read-only"
                    }
                    val opened = if (document.lastOpenedAt > 0L) {
                        DateUtils.getRelativeTimeSpanString(
                            document.lastOpenedAt,
                            System.currentTimeMillis(),
                            DateUtils.MINUTE_IN_MILLIS
                        ).toString()
                    } else {
                        "Recently opened"
                    }
                    ListItem(
                        modifier = Modifier.clickable { onSelect(document.id) },
                        leadingContent = {
                            Icon(Icons.Outlined.Description, contentDescription = null)
                        },
                        headlineContent = {
                            Text(
                                document.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        supportingContent = { Text("$location • $opened") },
                        trailingContent = {
                            IconButton(onClick = { onForget(document.id) }) {
                                Icon(Icons.Outlined.Close, contentDescription = "Forget recent file")
                            }
                        }
                    )
                }
            }
        }

        TextButton(
            onClick = onOpenFile,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Icon(Icons.Outlined.FolderOpen, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Browse Markdown files")
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
                            if (document.id == activeId) Icons.Outlined.CheckCircle
                            else Icons.Outlined.Description,
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
                    supportingContent = { Text(document.saveState.compactLabel()) },
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
    headings: List<MarkdownPlanHeading>,
    onDismiss: () -> Unit,
    onSelect: (MarkdownPlanHeading) -> Unit
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
                    else -> TextButton(onClick = onCheck) { Text("Check for updates") }
                }
            }
        }
    }
}

private fun SaveState.compactLabel(): String = when (this) {
    SaveState.SAVED -> "Saved"
    SaveState.SAVING -> "Saving…"
    SaveState.UNSAVED -> "Unsaved"
    SaveState.READ_ONLY -> "Read-only"
    SaveState.ERROR -> "Save failed"
}

private const val READING_POSITION_DEBOUNCE_MS = 300L

private fun formatCharacters(count: Int): String = when {
    count >= 1_000_000 -> String.format(Locale.US, "%.1fM chars", count / 1_000_000f)
    count >= 1_000 -> String.format(Locale.US, "%.1fK chars", count / 1_000f)
    else -> "$count chars"
}


package com.marblemd.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.marblemd.app.R
import com.marblemd.app.library.MarkdownTemplate
import com.marblemd.app.library.RecentDocument
import com.marblemd.app.markdown.MarkdownPlanHeading
import com.marblemd.app.markdown.MarkdownRenderPlan
import com.marblemd.app.model.DirectionMode
import com.marblemd.app.model.MarkdownDocument
import com.marblemd.app.model.ReaderFont
import com.marblemd.app.model.SaveState
import com.marblemd.app.model.ScrollTarget
import com.marblemd.app.text.CustomFont
import com.marblemd.app.update.UpdateStatus
import com.marblemd.app.update.UpdateUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    documents: List<MarkdownDocument>,
    activeDocument: MarkdownDocument?,
    editing: Boolean,
    fontSizeSp: Float,
    directionMode: DirectionMode,
    readerFont: ReaderFont,
    customFonts: List<CustomFont>,
    uiFontId: String?,
    recents: List<RecentDocument>,
    editorFontFamily: FontFamily?,
    scrollTarget: ScrollTarget?,
    updateState: UpdateUiState,
    onScrollTargetHandled: () -> Unit,
    onScrollPositionChange: (String, Int) -> Unit,
    onOpen: () -> Unit,
    onNewFile: (MarkdownTemplate) -> Unit,
    onOpenRecent: (RecentDocument) -> Unit,
    onRemoveRecent: (RecentDocument) -> Unit,
    onClearRecents: () -> Unit,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onContentChange: (String) -> Unit,
    onSaveNow: () -> Unit,
    onRequestSaveAs: () -> Unit,
    onEditingChange: (Boolean) -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onDirectionChange: (DirectionMode) -> Unit,
    onReaderFontChange: (ReaderFont) -> Unit,
    onImportFont: () -> Unit,
    onDeleteFont: (CustomFont) -> Unit,
    onUseFontInUi: (CustomFont?) -> Unit,
    onCheckForUpdates: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onInstallUpdate: () -> Unit
) {
    var librarySheet by remember { mutableStateOf(false) }
    var templateSheet by remember { mutableStateOf(false) }
    var outlineSheet by remember { mutableStateOf(false) }
    var readingSettingsSheet by remember { mutableStateOf(false) }
    var updateSheet by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var fontPendingDelete by remember { mutableStateOf<CustomFont?>(null) }

    val renderPlans = remember { mutableStateMapOf<String, MarkdownRenderPlan>() }
    val readerListStates = remember { mutableMapOf<String, LazyListState>() }
    val editorScrollStates = remember { mutableMapOf<String, ScrollState>() }
    val activeId = activeDocument?.id
    val readerListState = activeId?.let { readerListStates.getOrPut(it) { LazyListState() } }
    val editorScroll = activeId?.let { editorScrollStates.getOrPut(it) { ScrollState(0) } }
    val scope = rememberCoroutineScope()
    val fallbackListState = remember { LazyListState() }
    val fallbackScrollState = remember { ScrollState(0) }
    val markdownEngine = rememberMarkdownEngine(readerFont)
    val documentIds = documents.map { it.id }

    LaunchedEffect(documentIds) {
        val keep = documentIds.toSet()
        renderPlans.keys.toList().filterNot { it in keep }.forEach(renderPlans::remove)
        readerListStates.keys.retainAll(keep)
        editorScrollStates.keys.retainAll(keep)
    }

    LaunchedEffect(activeId, activeDocument?.revision, editing, markdownEngine) {
        val document = activeDocument ?: return@LaunchedEffect
        val cached = renderPlans[document.id]
        if (!editing && cached?.revision != document.revision) {
            renderPlans.remove(document.id)
            val contentSnapshot = document.content
            val revisionSnapshot = document.revision
            renderPlans[document.id] = withContext(Dispatchers.Default) {
                markdownEngine.prepare(contentSnapshot, revisionSnapshot)
            }
        }
    }

    // Remember the reading position of the active document.
    LaunchedEffect(activeId, readerListState) {
        val id = activeId ?: return@LaunchedEffect
        val state = readerListState ?: return@LaunchedEffect
        snapshotFlow { state.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { index -> onScrollPositionChange(id, index) }
    }

    val activePlan = activeDocument?.let { document ->
        renderPlans[document.id]?.takeIf { it.revision == document.revision }
    }

    // Restore a saved position once the document blocks are ready.
    LaunchedEffect(scrollTarget, activePlan, activeId) {
        val target = scrollTarget ?: return@LaunchedEffect
        if (target.documentId != activeId) return@LaunchedEffect
        val plan = activePlan ?: return@LaunchedEffect
        val state = readerListStates.getOrPut(target.documentId) { LazyListState() }
        state.scrollToItem(target.blockIndex.coerceIn(0, plan.blocks.lastIndex.coerceAtLeast(0)))
        onScrollTargetHandled()
    }

    if (librarySheet) {
        LibrarySheet(
            documents = documents,
            activeId = activeId,
            recents = recents,
            onDismiss = { librarySheet = false },
            onSelectDocument = {
                librarySheet = false
                onSelectTab(it)
            },
            onCloseDocument = onCloseTab,
            onOpenFiles = {
                librarySheet = false
                onOpen()
            },
            onNewFile = {
                librarySheet = false
                templateSheet = true
            },
            onOpenRecent = {
                librarySheet = false
                onOpenRecent(it)
            },
            onRemoveRecent = onRemoveRecent,
            onClearRecents = onClearRecents
        )
    }

    if (templateSheet) {
        TemplateSheet(
            onDismiss = { templateSheet = false },
            onSelect = { template ->
                templateSheet = false
                onNewFile(template)
            }
        )
    }

    if (outlineSheet && activePlan != null) {
        OutlineSheet(
            headings = activePlan.headings,
            onDismiss = { outlineSheet = false },
            onSelect = { heading ->
                outlineSheet = false
                scope.launch { readerListState?.animateScrollToItem(heading.blockIndex) }
            }
        )
    }

    if (readingSettingsSheet) {
        ReadingSettingsSheet(
            fontSizeSp = fontSizeSp,
            readerFont = readerFont,
            directionMode = directionMode,
            customFonts = customFonts,
            uiFontId = uiFontId,
            onDismiss = { readingSettingsSheet = false },
            onFontSizeChange = onFontSizeChange,
            onReaderFontChange = onReaderFontChange,
            onDirectionChange = onDirectionChange,
            onImportFont = onImportFont,
            onDeleteFont = { font -> fontPendingDelete = font },
            onUseFontInUi = onUseFontInUi
        )
    }

    fontPendingDelete?.let { font ->
        AlertDialog(
            onDismissRequest = { fontPendingDelete = null },
            title = { Text("Delete ${font.displayName}?") },
            text = { Text("The imported font file will be removed from the app.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        fontPendingDelete = null
                        onDeleteFont(font)
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { fontPendingDelete = null }) { Text("Cancel") }
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
                            text = activeDocument?.title ?: "MarbleMD",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = activeDocument?.saveState?.compactLabel() ?: "Markdown reader • editor",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { librarySheet = true }) {
                        BadgedBox(
                            badge = {
                                if (documents.size > 1) {
                                    Badge { Text(documents.size.toString()) }
                                }
                            }
                        ) {
                            Icon(Icons.Outlined.Description, contentDescription = "Library")
                        }
                    }
                },
                actions = {
                    if (activeDocument != null) {
                        IconButton(onClick = { onEditingChange(!editing) }) {
                            Icon(
                                if (editing) Icons.Outlined.Visibility else Icons.Outlined.Edit,
                                contentDescription = if (editing) "Preview" else "Edit"
                            )
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
                                text = { Text("Library & recents") },
                                leadingIcon = { Icon(Icons.Outlined.History, contentDescription = null) },
                                onClick = {
                                    moreMenu = false
                                    librarySheet = true
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("New Markdown file") },
                                leadingIcon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                                onClick = {
                                    moreMenu = false
                                    templateSheet = true
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Open from device") },
                                leadingIcon = { Icon(Icons.Outlined.FolderOpen, contentDescription = null) },
                                onClick = {
                                    moreMenu = false
                                    onOpen()
                                }
                            )

                            if (activePlan != null && !editing) {
                                DropdownMenuItem(
                                    text = { Text("Smart outline") },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.FormatListBulleted, contentDescription = null)
                                    },
                                    enabled = activePlan.headings.isNotEmpty(),
                                    onClick = {
                                        moreMenu = false
                                        outlineSheet = true
                                    }
                                )
                            }

                            DropdownMenuItem(
                                text = { Text("Reading settings") },
                                leadingIcon = { Icon(Icons.Outlined.TextFields, contentDescription = null) },
                                onClick = {
                                    moreMenu = false
                                    readingSettingsSheet = true
                                }
                            )

                            if (activeDocument != null && activeDocument.uri == null) {
                                DropdownMenuItem(
                                    text = { Text("Save to a file") },
                                    leadingIcon = { Icon(Icons.Outlined.Save, contentDescription = null) },
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
                                leadingIcon = { Icon(Icons.Outlined.SystemUpdateAlt, contentDescription = null) },
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
            when {
                activeDocument == null -> WelcomeContent(
                    recents = recents,
                    onOpen = onOpen,
                    onNewFile = { templateSheet = true },
                    onOpenRecent = onOpenRecent
                )

                editing -> MarkdownEditor(
                    markdown = activeDocument.content,
                    onMarkdownChange = onContentChange,
                    scrollState = editorScroll ?: fallbackScrollState,
                    saveStateLabel = activeDocument.saveState.compactLabel(),
                    canSaveAs = activeDocument.uri == null || !activeDocument.writable,
                    onSave = onSaveNow,
                    onRequestSaveAs = onRequestSaveAs,
                    onExitToPreview = { onEditingChange(false) },
                    fontFamily = editorFontFamily,
                    modifier = Modifier.fillMaxSize()
                )

                activePlan == null -> PreparingDocument(
                    title = activeDocument.title,
                    characterCount = activeDocument.content.length,
                    modifier = Modifier.fillMaxSize()
                )

                else -> LazyMarkdownDocument(
                    plan = activePlan,
                    fontSizeSp = fontSizeSp,
                    directionMode = directionMode,
                    listState = readerListState ?: fallbackListState,
                    engine = markdownEngine,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun WelcomeContent(
    recents: List<RecentDocument>,
    onOpen: () -> Unit,
    onNewFile: () -> Unit,
    onOpenRecent: (RecentDocument) -> Unit
) {
    val latest = recents.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Image(
            painter = painterResource(R.drawable.ic_marblemd_mark),
            contentDescription = null,
            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
                MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier
                .height(56.dp)
                .width(56.dp)
        )
        Text("MarbleMD", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = "A multilingual Markdown reader and editor that keeps RTL and LTR text on the right side of the page.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilledTonalButton(onClick = onOpen) {
                Icon(Icons.Outlined.FolderOpen, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Open .md")
            }
            FilledTonalButton(onClick = onNewFile) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("New file")
            }
        }

        if (latest != null) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Continue reading",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = latest.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = buildString {
                            append(formatWhen(latest.lastOpenedAt))
                            append(" • ")
                            append(latest.charCount.formatCount())
                            if (latest.scrollIndex > 0) append(" • resumes where you stopped")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (latest.snippet.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = latest.snippet,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    FilledTonalButton(onClick = { onOpenRecent(latest) }) {
                        Text("Continue")
                    }
                }
            }
        }

        if (recents.size > 1) {
            Text("Recent Markdown", style = MaterialTheme.typography.titleSmall)
            recents.drop(1).take(6).forEach { recent ->
                ListItem(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenRecent(recent) }
                        .padding(horizontal = 0.dp),
                    leadingContent = { Icon(Icons.Outlined.History, contentDescription = null) },
                    headlineContent = {
                        Text(recent.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    supportingContent = {
                        Text(
                            text = "${formatWhen(recent.lastOpenedAt)} • ${recent.charCount.formatCount()}",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ReadingSettingsSheet(
    fontSizeSp: Float,
    readerFont: ReaderFont,
    directionMode: DirectionMode,
    customFonts: List<CustomFont>,
    uiFontId: String?,
    onDismiss: () -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onReaderFontChange: (ReaderFont) -> Unit,
    onDirectionChange: (DirectionMode) -> Unit,
    onImportFont: () -> Unit,
    onDeleteFont: (CustomFont) -> Unit,
    onUseFontInUi: (CustomFont?) -> Unit
) {
    var pendingSize by remember(fontSizeSp) { mutableFloatStateOf(fontSizeSp) }
    val fonts = ReaderFont.all(customFonts)
    val activeCustomId = ReaderFont.customFontId(readerFont.key)

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
                        "Typography, direction and your own fonts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
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

            Text("Reading font", style = MaterialTheme.typography.labelLarge)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                fonts.forEach { font ->
                    val selected = font.key == readerFont.key
                    if (font.isCustom) {
                        val custom = customFonts.firstOrNull {
                            ReaderFont.CUSTOM_PREFIX + it.id == font.key
                        }
                        InputChip(
                            selected = selected,
                            onClick = { onReaderFontChange(font) },
                            label = { Text(if (selected) "✓ ${font.shortLabel}" else font.shortLabel) },
                            trailingIcon = {
                                if (custom != null) {
                                    Icon(
                                        Icons.Outlined.Close,
                                        contentDescription = "Delete ${custom.displayName}",
                                        modifier = Modifier
                                            .width(18.dp)
                                            .height(18.dp)
                                            .clickable { onDeleteFont(custom) }
                                    )
                                }
                            }
                        )
                    } else {
                        AssistChip(
                            onClick = { onReaderFontChange(font) },
                            label = { Text(if (selected) "✓ ${font.shortLabel}" else font.shortLabel) }
                        )
                    }
                }
            }

            OutlinedButton(onClick = onImportFont, modifier = Modifier.fillMaxWidth()) {
                Text("＋  Import .ttf / .otf font")
            }
            Text(
                text = if (customFonts.isEmpty()) {
                    "Add any TrueType font you like — it is copied into the app and used for reading, editing and (optionally) the whole interface."
                } else {
                    "${customFonts.size} custom font(s) installed. Tap ✕ on a chip to remove one."
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (activeCustomId != null) {
                val custom = customFonts.firstOrNull { it.id == activeCustomId }
                if (custom != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Use for the app interface", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Apply ${custom.displayName} to menus, buttons and titles too.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = uiFontId == custom.id,
                            onCheckedChange = { checked ->
                                onUseFontInUi(if (checked) custom else null)
                            }
                        )
                    }
                }
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
                            Text(if (mode == directionMode) "✓ ${mode.label}" else mode.label)
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
            "Large documents are parsed into lazy blocks off the UI thread • ${characterCount.formatCount()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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

internal fun SaveState.compactLabel(): String = when (this) {
    SaveState.SAVED -> "Saved"
    SaveState.SAVING -> "Saving…"
    SaveState.UNSAVED -> "Unsaved"
    SaveState.READ_ONLY -> "Read-only"
    SaveState.ERROR -> "Save failed"
}

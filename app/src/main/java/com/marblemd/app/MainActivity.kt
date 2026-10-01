package com.marblemd.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontFamily
import androidx.core.content.IntentCompat
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import com.marblemd.app.library.DocumentMemory
import com.marblemd.app.library.MarkdownTemplate
import com.marblemd.app.library.RecentDocument
import com.marblemd.app.model.DirectionMode
import com.marblemd.app.model.MarkdownDocument
import com.marblemd.app.model.ReaderFont
import com.marblemd.app.model.SaveState
import com.marblemd.app.model.ScrollTarget
import com.marblemd.app.text.CustomFont
import com.marblemd.app.text.CustomFontStore
import com.marblemd.app.text.FontRegistry
import com.marblemd.app.ui.ReaderScreen
import com.marblemd.app.ui.theme.MarbleMDTheme
import com.marblemd.app.update.UpdateCheckResult
import com.marblemd.app.update.UpdateInfo
import com.marblemd.app.update.UpdateManager
import com.marblemd.app.update.UpdateStatus
import com.marblemd.app.update.UpdateUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets

class MainActivity : ComponentActivity() {
    private var tabs by mutableStateOf<List<MarkdownDocument>>(emptyList())
    private var activeTabId by mutableStateOf<String?>(null)
    private var editingIds by mutableStateOf<Set<String>>(emptySet())
    private var pendingCloseId by mutableStateOf<String?>(null)
    private var fontSizeSp by mutableFloatStateOf(18f)
    private var directionMode by mutableStateOf(DirectionMode.AUTO)
    private var readerFont by mutableStateOf(ReaderFont.SMART)
    private var customFonts by mutableStateOf<List<CustomFont>>(emptyList())
    private var uiFontId by mutableStateOf<String?>(null)
    private var recents by mutableStateOf<List<RecentDocument>>(emptyList())
    private var scrollTarget by mutableStateOf<ScrollTarget?>(null)
    private var uiTypography by mutableStateOf<Typography?>(null)
    private var uiFontFamily by mutableStateOf<FontFamily?>(null)

    private val readerPreferences by lazy {
        getSharedPreferences("reader_preferences", MODE_PRIVATE)
    }
    private val documentMemory by lazy { DocumentMemory(this) }
    private val fontStore by lazy { CustomFontStore(this) }
    private val fontRegistry by lazy { FontRegistry(this) }
    private val scrollPositions = mutableMapOf<String, Int>()
    private val saveJobs = mutableMapOf<String, Job>()
    private val positionJobs = mutableMapOf<String, Job>()
    private var saveAsTargetId: String? = null
    private var pendingRecentScroll = 0
    private val updateManager by lazy { UpdateManager(this) }
    private var updateUiState by mutableStateOf(UpdateUiState())
    private var availableUpdate: UpdateInfo? = null
    private var downloadedUpdate: java.io.File? = null
    private var pendingInstallAfterPermission: java.io.File? = null

    private val openDocuments =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
            val data = result.data ?: return@registerForActivityResult
            val uris = buildList {
                data.data?.let(::add)
                data.clipData?.let { clip ->
                    repeat(clip.itemCount) { index ->
                        val uri = clip.getItemAt(index).uri
                        if (uri != null && uri !in this) add(uri)
                    }
                }
            }
            if (uris.isNotEmpty()) {
                openUris(uris, persist = true, grantFlags = data.flags)
            }
        }

    private val createDocument = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri ->
        val targetId = saveAsTargetId
        saveAsTargetId = null
        if (uri != null && targetId != null) {
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            val current = tabs.firstOrNull { it.id == targetId } ?: return@registerForActivityResult
            val renamed = current.copy(
                title = displayName(uri) ?: current.title.ensureMarkdownExtension(),
                uri = uri,
                writable = true,
                saveState = SaveState.SAVING
            )
            replaceTab(renamed)
            scheduleAutoSave(renamed, immediate = true)
        }
    }

    private val importFont = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                fontStore.import(uri, displayName(uri))
            }
            result.onSuccess { font ->
                customFonts = fontStore.fonts()
                setReaderFont(ReaderFont.custom(font))
                toast("«${font.displayName}» added")
            }.onFailure { error ->
                toast(error.message ?: "That font could not be imported")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        restoreReaderPreferences()
        customFonts = fontStore.fonts()
        readerFont = ReaderFont.fromKey(
            readerPreferences.getString(PREF_READER_FONT, null),
            customFonts
        )
        uiFontId = fontStore.uiFontId()
        refreshUiFont()
        recents = documentMemory.recents()
        restoreSession()

        setContent {
            MarbleMDTheme(typography = uiTypography) {
                val active = tabs.firstOrNull { it.id == activeTabId }
                ReaderScreen(
                    documents = tabs,
                    activeDocument = active,
                    editing = active != null && active.id in editingIds,
                    fontSizeSp = fontSizeSp,
                    directionMode = directionMode,
                    readerFont = readerFont,
                    customFonts = customFonts,
                    uiFontId = uiFontId,
                    recents = recents,
                    editorFontFamily = uiFontFamily,
                    scrollTarget = scrollTarget,
                    updateState = updateUiState,
                    onScrollTargetHandled = { scrollTarget = null },
                    onScrollPositionChange = ::onScrollPositionChanged,
                    onOpen = ::launchDocumentPicker,
                    onNewFile = ::createDocumentFromTemplate,
                    onOpenRecent = ::openRecent,
                    onRemoveRecent = ::removeRecent,
                    onClearRecents = ::clearRecents,
                    onSelectTab = { id ->
                        activeTabId = id
                        val uri = tabs.firstOrNull { it.id == id }?.uri
                        rememberDocument(tabs.firstOrNull { it.id == id })
                        if (uri != null) recents = documentMemory.recents()
                    },
                    onCloseTab = ::requestCloseTab,
                    onContentChange = { content -> active?.let { updateContent(it.id, content) } },
                    onSaveNow = { active?.let { saveNow(it.id) } },
                    onRequestSaveAs = { active?.let { requestSaveAs(it.id) } },
                    onEditingChange = { editing ->
                        active?.let { document ->
                            editingIds = if (editing) {
                                editingIds + document.id
                            } else {
                                editingIds - document.id
                            }
                        }
                    },
                    onFontSizeChange = ::setReaderFontSize,
                    onDirectionChange = ::setReaderDirection,
                    onReaderFontChange = ::setReaderFont,
                    onImportFont = { importFont.launch(arrayOf("*/*")) },
                    onDeleteFont = ::deleteCustomFont,
                    onUseFontInUi = ::setUiFont,
                    onCheckForUpdates = { checkForUpdates(force = true) },
                    onDownloadUpdate = { downloadAvailableUpdate() },
                    onInstallUpdate = { installDownloadedUpdate() }
                )

                pendingCloseDialog()
            }
        }

        consumeIntent(intent)
        lifecycleScope.launch {
            delay(1_500L)
            checkForUpdates(force = false)
        }
    }

    override fun onResume() {
        super.onResume()
        val pending = pendingInstallAfterPermission
        if (pending != null && updateManager.canInstallPackages()) {
            pendingInstallAfterPermission = null
            updateManager.launchInstaller(this, pending)
        }
    }

    override fun onPause() {
        super.onPause()
        persistSession()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeIntent(intent)
    }

    // ------------------------------------------------------------- documents --

    private fun launchDocumentPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf("text/markdown", "text/x-markdown", "text/plain")
            )
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
        }
        openDocuments.launch(intent)
    }

    private fun createDocumentFromTemplate(template: MarkdownTemplate) {
        val document = MarkdownDocument(
            title = template.suggestedName,
            content = template.body
        )
        tabs = tabs + document
        activeTabId = document.id
        editingIds = editingIds + document.id
        requestSaveAs(document.id)
    }

    private fun openRecent(recent: RecentDocument) {
        pendingRecentScroll = recent.scrollIndex
        openUris(
            uris = listOf(Uri.parse(recent.uri)),
            persist = true,
            grantFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            fallbackTitle = recent.title
        )
    }

    private fun removeRecent(recent: RecentDocument) {
        documentMemory.forget(recent.uri)
        recents = documentMemory.recents()
    }

    private fun clearRecents() {
        documentMemory.clearRecents()
        recents = documentMemory.recents()
    }

    private fun consumeIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_VIEW -> {
                intent.data?.let { uri ->
                    openUris(listOf(uri), persist = false, grantFlags = intent.flags)
                }
            }

            Intent.ACTION_SEND -> {
                val stream = IntentCompat.getParcelableExtra(
                    intent,
                    Intent.EXTRA_STREAM,
                    Uri::class.java
                )
                when {
                    stream != null -> openUris(listOf(stream), persist = false, grantFlags = intent.flags)
                    !intent.getStringExtra(Intent.EXTRA_TEXT).isNullOrBlank() -> {
                        addOrActivate(
                            MarkdownDocument(
                                title = "Shared text.md",
                                content = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
                            )
                        )
                    }
                }
            }
        }
    }

    private fun openUris(
        uris: List<Uri>,
        persist: Boolean,
        grantFlags: Int,
        fallbackTitle: String? = null
    ) {
        val permissionFlags = grantFlags and (
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        val writeGranted = permissionFlags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0

        if (persist && permissionFlags != 0) {
            uris.forEach { uri ->
                runCatching {
                    contentResolver.takePersistableUriPermission(uri, permissionFlags)
                }
            }
        }

        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                uris.map { uri ->
                    runCatching {
                        val title = displayName(uri)
                            ?: fallbackTitle
                            ?: uri.lastPathSegment
                            ?: "document.md"
                        val content = contentResolver.openInputStream(uri)?.use { input ->
                            input.bufferedReader(StandardCharsets.UTF_8).readText()
                        } ?: error("Unable to open file")

                        val canWrite = writeGranted || canOpenForWrite(uri)
                        MarkdownDocument(
                            title = title,
                            content = content,
                            uri = uri,
                            writable = canWrite,
                            saveState = if (canWrite) SaveState.SAVED else SaveState.READ_ONLY
                        )
                    }
                }
            }

            loaded.forEach { result ->
                result.onSuccess { document ->
                    val alreadyOpen = document.uri != null && tabs.any { it.uri == document.uri }
                    addOrActivate(document)
                    val restoreIndex = pendingRecentScroll
                    rememberDocument(document, scrollIndex = restoreIndex)
                    if (!alreadyOpen && restoreIndex > 0) {
                        val tabId = tabs.firstOrNull { it.uri == document.uri }?.id
                        if (tabId != null) scrollTarget = ScrollTarget(tabId, restoreIndex)
                    }
                    pendingRecentScroll = 0
                }.onFailure { error ->
                    addOrActivate(
                        MarkdownDocument(
                            title = "Could not open file",
                            content = "# Error\n\n${error.message ?: "Unknown read error"}\n\nTry opening a UTF-8 Markdown file again."
                        )
                    )
                }
            }
            recents = documentMemory.recents()
        }
    }

    private fun addOrActivate(document: MarkdownDocument) {
        val existing = document.uri?.let { uri -> tabs.firstOrNull { it.uri == uri } }
        if (existing != null) {
            activeTabId = existing.id
            return
        }
        tabs = tabs + document
        activeTabId = document.id
    }

    private fun updateContent(id: String, content: String) {
        val current = tabs.firstOrNull { it.id == id } ?: return
        if (current.content == content) return

        val nextState = when {
            current.uri == null -> SaveState.UNSAVED
            current.writable -> SaveState.SAVING
            else -> SaveState.READ_ONLY
        }
        val updated = current.copy(
            content = content,
            saveState = nextState,
            revision = current.revision + 1L
        )
        replaceTab(updated)

        if (updated.uri != null && updated.writable) {
            scheduleAutoSave(updated)
        }
    }

    private fun scheduleAutoSave(document: MarkdownDocument, immediate: Boolean = false) {
        if (document.uri == null || !document.writable) return
        saveJobs.remove(document.id)?.cancel()
        saveJobs[document.id] = lifecycleScope.launch {
            if (!immediate) delay(AUTOSAVE_DEBOUNCE_MS)
            val snapshot = tabs.firstOrNull { it.id == document.id } ?: return@launch
            val saved = withContext(Dispatchers.IO) { writeDocument(snapshot) }
            val latest = tabs.firstOrNull { it.id == snapshot.id } ?: return@launch

            if (latest.content == snapshot.content) {
                replaceTab(
                    latest.copy(saveState = if (saved) SaveState.SAVED else SaveState.ERROR)
                )
            }
            if (saved) rememberDocument(snapshot)
        }
    }

    private fun saveNow(id: String) {
        val document = tabs.firstOrNull { it.id == id } ?: return
        if (document.uri == null || !document.writable) {
            requestSaveAs(id)
            return
        }
        saveJobs.remove(id)?.cancel()
        lifecycleScope.launch {
            val saved = withContext(Dispatchers.IO) { writeDocument(document) }
            replaceTab(document.copy(saveState = if (saved) SaveState.SAVED else SaveState.ERROR))
            if (saved) {
                rememberDocument(document)
                toast("Saved")
            } else {
                toast("Could not save this file")
            }
        }
    }

    private fun writeDocument(document: MarkdownDocument): Boolean {
        val uri = document.uri ?: return false
        return runCatching {
            val output = runCatching {
                contentResolver.openOutputStream(uri, "wt")
            }.getOrNull() ?: contentResolver.openOutputStream(uri, "w")
                ?: error("Unable to open file for writing")
            output.bufferedWriter(StandardCharsets.UTF_8).use { writer ->
                writer.write(document.content)
            }
            true
        }.getOrDefault(false)
    }

    private fun canOpenForWrite(uri: Uri): Boolean =
        runCatching {
            contentResolver.openFileDescriptor(uri, "rw")?.use { true } ?: false
        }.getOrDefault(false)

    private fun requestSaveAs(id: String) {
        val document = tabs.firstOrNull { it.id == id } ?: return
        saveAsTargetId = id
        createDocument.launch(document.title.ensureMarkdownExtension())
    }

    private fun requestCloseTab(id: String) {
        val document = tabs.firstOrNull { it.id == id } ?: return
        val hasUnsavedBuffer = document.uri == null && document.content.isNotBlank()
        if (hasUnsavedBuffer) {
            pendingCloseId = id
            return
        }
        closeTab(id)
    }

    private fun closeTab(id: String) {
        val index = tabs.indexOfFirst { it.id == id }
        if (index < 0) return

        val closing = tabs[index]
        saveJobs.remove(id)?.cancel()
        if (closing.writable && closing.uri != null && closing.saveState == SaveState.SAVING) {
            lifecycleScope.launch(Dispatchers.IO) { writeDocument(closing) }
        }

        val remaining = tabs.filterNot { it.id == id }
        editingIds = editingIds - id
        tabs = remaining
        if (tabs.isEmpty()) {
            activeTabId = null
        } else if (activeTabId == id) {
            activeTabId = remaining[index.coerceAtMost(remaining.lastIndex)].id
        }
    }

    private fun replaceTab(document: MarkdownDocument) {
        tabs = tabs.map { if (it.id == document.id) document else it }
    }

    // ------------------------------------------------------------ library -----

    private fun onScrollPositionChanged(documentId: String, index: Int) {
        scrollPositions[documentId] = index
        val document = tabs.firstOrNull { it.id == documentId } ?: return
        val uri = document.uri ?: return
        positionJobs.remove(documentId)?.cancel()
        positionJobs[documentId] = lifecycleScope.launch {
            delay(POSITION_DEBOUNCE_MS)
            withContext(Dispatchers.IO) { documentMemory.updatePosition(uri.toString(), index) }
        }
    }

    private fun rememberDocument(document: MarkdownDocument?, scrollIndex: Int = -1) {
        val uri = document?.uri ?: return
        val index = if (scrollIndex >= 0) scrollIndex else scrollPositions[document.id] ?: 0
        scrollPositions[document.id] = index
        documentMemory.remember(
            uri = uri.toString(),
            title = document.title,
            writable = document.writable,
            charCount = document.content.length,
            snippet = snippetOf(document.content),
            scrollIndex = index
        )
        recents = documentMemory.recents()
    }

    private fun restoreSession() {
        val restored = documentMemory.session()
        if (restored.isNotEmpty()) {
            tabs = restored
            val requested = documentMemory.activeSessionId()
            activeTabId = requested?.takeIf { id -> restored.any { it.id == id } } ?: restored.first().id
            scrollPositions.putAll(documentMemory.sessionScrollPositions())
            val active = restored.firstOrNull { it.id == activeTabId }
            val index = active?.let { scrollPositions[it.id] } ?: 0
            if (active != null && index > 0) {
                scrollTarget = ScrollTarget(active.id, index)
            }
        }
    }

    private fun persistSession() {
        if (tabs.isEmpty()) {
            documentMemory.clearSession()
            return
        }
        documentMemory.saveSession(tabs, activeTabId.orEmpty(), scrollPositions)
        tabs.forEach { document -> rememberDocument(document) }
    }

    // --------------------------------------------------------------- fonts ----

    private fun setUiFont(font: CustomFont?) {
        uiFontId = font?.id
        fontStore.setUiFontId(font?.id)
        refreshUiFont()
        val name = font?.displayName ?: "system"
        toast("Interface font: $name")
    }

    private fun refreshUiFont() {
        val typeface = uiFontId?.let { id -> fontRegistry.customTypeface(id) }
        val family = typeface?.let { FontFamily(it) }
        uiFontFamily = family
        uiTypography = family?.let { Typography(defaultFontFamily = it) }
    }

    private fun deleteCustomFont(font: CustomFont) {
        if (readerFont.key == ReaderFont.CUSTOM_PREFIX + font.id) {
            setReaderFont(ReaderFont.SMART)
        }
        if (uiFontId == font.id) {
            uiFontId = null
            fontStore.setUiFontId(null)
            refreshUiFont()
        }
        fontRegistry.invalidate(font.id)
        fontStore.delete(font)
        customFonts = fontStore.fonts()
        toast("«${font.displayName}» removed")
    }

    // ------------------------------------------------------------- settings ---

    private fun restoreReaderPreferences() {
        fontSizeSp = readerPreferences.getFloat(PREF_FONT_SIZE, 18f).coerceIn(12f, 34f)
        directionMode = runCatching {
            DirectionMode.valueOf(readerPreferences.getString(PREF_DIRECTION, null).orEmpty())
        }.getOrDefault(DirectionMode.AUTO)
    }

    private fun setReaderFontSize(value: Float) {
        fontSizeSp = value.coerceIn(12f, 34f)
        readerPreferences.edit { putFloat(PREF_FONT_SIZE, fontSizeSp) }
    }

    private fun setReaderDirection(value: DirectionMode) {
        directionMode = value
        readerPreferences.edit { putString(PREF_DIRECTION, value.name) }
    }

    private fun setReaderFont(value: ReaderFont) {
        readerFont = value
        readerPreferences.edit { putString(PREF_READER_FONT, value.key) }
        toast("Reading font: ${value.label}")
    }

    // -------------------------------------------------------------- updates ---

    private fun checkForUpdates(force: Boolean) {
        if (updateUiState.status == UpdateStatus.CHECKING ||
            updateUiState.status == UpdateStatus.DOWNLOADING
        ) {
            return
        }
        if (!force && !updateManager.shouldAutoCheck()) return

        updateUiState = UpdateUiState(
            status = UpdateStatus.CHECKING,
            message = "Checking GitHub Releases…"
        )

        lifecycleScope.launch {
            when (val result = updateManager.checkForUpdate(force = force)) {
                is UpdateCheckResult.Available -> {
                    availableUpdate = result.info
                    downloadedUpdate = null
                    updateUiState = UpdateUiState(
                        status = UpdateStatus.AVAILABLE,
                        latestVersion = result.info.versionName,
                        architecture = result.info.asset.abi,
                        message = if (updateManager.isUnmeteredNetwork()) {
                            "Update found. Downloading the verified APK automatically…"
                        } else {
                            "Update found. Automatic download is paused on a metered network."
                        }
                    )
                    if (updateManager.isUnmeteredNetwork()) {
                        downloadAvailableUpdate()
                    }
                }

                UpdateCheckResult.UpToDate -> {
                    updateUiState = UpdateUiState(
                        status = UpdateStatus.UP_TO_DATE,
                        message = "MarbleMD is up to date."
                    )
                }

                is UpdateCheckResult.Error -> {
                    updateUiState = UpdateUiState(
                        status = UpdateStatus.ERROR,
                        message = result.message
                    )
                }
            }
        }
    }

    private fun downloadAvailableUpdate() {
        val info = availableUpdate ?: return
        if (updateUiState.status == UpdateStatus.DOWNLOADING) return

        updateUiState = updateUiState.copy(
            status = UpdateStatus.DOWNLOADING,
            progress = 0,
            message = "Downloading ${info.asset.abi} APK…"
        )

        lifecycleScope.launch {
            val result = updateManager.download(info) { progress ->
                runOnUiThread {
                    if (updateUiState.status == UpdateStatus.DOWNLOADING) {
                        updateUiState = updateUiState.copy(progress = progress)
                    }
                }
            }

            result.onSuccess { file ->
                downloadedUpdate = file
                updateUiState = UpdateUiState(
                    status = UpdateStatus.READY,
                    latestVersion = info.versionName,
                    architecture = info.asset.abi,
                    progress = 100,
                    message = "Download verified. Ready to install."
                )
            }.onFailure { error ->
                downloadedUpdate = null
                updateUiState = UpdateUiState(
                    status = UpdateStatus.ERROR,
                    latestVersion = info.versionName,
                    architecture = info.asset.abi,
                    message = error.message ?: "Update download failed"
                )
            }
        }
    }

    private fun installDownloadedUpdate() {
        val file = downloadedUpdate ?: return
        if (!file.exists()) {
            updateUiState = updateUiState.copy(
                status = UpdateStatus.ERROR,
                message = "Downloaded update file is no longer available."
            )
            return
        }

        if (!updateManager.canInstallPackages()) {
            pendingInstallAfterPermission = file
            updateUiState = updateUiState.copy(
                message = "Allow MarbleMD to install updates, then installation will continue."
            )
            updateManager.openInstallPermission(this)
            return
        }

        pendingInstallAfterPermission = null
        updateManager.launchInstaller(this, file)
    }

    // ------------------------------------------------------------ utilities ---

    private fun displayName(uri: Uri): String? {
        if (uri.scheme != "content") return null
        return contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    @Composable
    private fun pendingCloseDialog() {
        val id = pendingCloseId ?: return
        val document = tabs.firstOrNull { it.id == id } ?: return
        AlertDialog(
            onDismissRequest = { pendingCloseId = null },
            title = { Text("Close ${document.title}?") },
            text = { Text("This document was never saved to a file. Closing it will discard its text.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingCloseId = null
                        closeTab(id)
                    }
                ) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = { pendingCloseId = null }) { Text("Keep editing") }
            }
        )
    }

    private fun String.ensureMarkdownExtension(): String =
        if (endsWith(".md", ignoreCase = true) || endsWith(".markdown", ignoreCase = true)) {
            this
        } else {
            "$this.md"
        }

    private fun snippetOf(content: String): String = content
        .lineSequence()
        .map { line ->
            line.trim()
                .removePrefix("#")
                .trim()
                .replace(Regex("""^[>\-*+\d.\s\[\]x]+"""), "")
                .replace(Regex("""[`*_~]"""), "")
                .trim()
        }
        .filter { it.isNotEmpty() }
        .take(3)
        .joinToString(" ")
        .take(SNIPPET_CHARS)

    companion object {
        private const val AUTOSAVE_DEBOUNCE_MS = 550L
        private const val POSITION_DEBOUNCE_MS = 1_500L
        private const val SNIPPET_CHARS = 180
        private const val PREF_FONT_SIZE = "font_size"
        private const val PREF_DIRECTION = "direction"
        private const val PREF_READER_FONT = "reader_font"
    }
}

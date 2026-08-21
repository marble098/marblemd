package com.marblemd.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import com.marblemd.app.model.DirectionMode
import com.marblemd.app.model.MarkdownDocument
import com.marblemd.app.model.SaveState
import com.marblemd.app.ui.ReaderScreen
import com.marblemd.app.ui.theme.MarbleMDTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets

class MainActivity : ComponentActivity() {
    private var tabs by mutableStateOf(listOf(sampleDocument()))
    private var activeTabId by mutableStateOf(tabs.first().id)
    private var fontSizeSp by mutableFloatStateOf(18f)
    private var directionMode by mutableStateOf(DirectionMode.AUTO)
    private val saveJobs = mutableMapOf<String, Job>()
    private var saveAsTargetId: String? = null

    private val openDocuments = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MarbleMDTheme {
                val active = tabs.firstOrNull { it.id == activeTabId } ?: tabs.first()
                ReaderScreen(
                    documents = tabs,
                    activeDocument = active,
                    fontSizeSp = fontSizeSp,
                    directionMode = directionMode,
                    onOpen = ::launchDocumentPicker,
                    onSelectTab = { activeTabId = it },
                    onCloseTab = ::closeTab,
                    onContentChange = { content -> updateContent(active.id, content) },
                    onRequestSaveAs = { requestSaveAs(active.id) },
                    onFontSizeChange = { fontSizeSp = it },
                    onDirectionChange = { directionMode = it }
                )
            }
        }

        consumeIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeIntent(intent)
    }

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

    private fun openUris(uris: List<Uri>, persist: Boolean, grantFlags: Int) {
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
                        val title = displayName(uri) ?: uri.lastPathSegment ?: "document.md"
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
                result.onSuccess(::addOrActivate)
                    .onFailure { error ->
                        addOrActivate(
                            MarkdownDocument(
                                title = "Could not open file",
                                content = "# Error\n\n${error.message ?: "Unknown read error"}\n\nTry opening a UTF-8 Markdown file again."
                            )
                        )
                    }
            }
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
        val updated = current.copy(content = content, saveState = nextState)
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

    private fun closeTab(id: String) {
        val index = tabs.indexOfFirst { it.id == id }
        if (index < 0) return

        val closing = tabs[index]
        saveJobs.remove(id)?.cancel()
        if (closing.writable && closing.uri != null && closing.saveState == SaveState.SAVING) {
            lifecycleScope.launch(Dispatchers.IO) { writeDocument(closing) }
        }

        val remaining = tabs.filterNot { it.id == id }
        if (remaining.isEmpty()) {
            val welcome = sampleDocument()
            tabs = listOf(welcome)
            activeTabId = welcome.id
            return
        }

        tabs = remaining
        if (activeTabId == id) {
            activeTabId = remaining[index.coerceAtMost(remaining.lastIndex)].id
        }
    }

    private fun replaceTab(document: MarkdownDocument) {
        tabs = tabs.map { if (it.id == document.id) document else it }
    }

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

    private fun String.ensureMarkdownExtension(): String =
        if (endsWith(".md", ignoreCase = true) || endsWith(".markdown", ignoreCase = true)) {
            this
        } else {
            "$this.md"
        }

    private fun sampleDocument() = MarkdownDocument(
        title = "Welcome.md",
        content = SAMPLE_MARKDOWN.trimIndent()
    )

    companion object {
        private const val AUTOSAVE_DEBOUNCE_MS = 550L

        private const val SAMPLE_MARKDOWN = """
# MarbleMD

**یک Markdown Reader مدرن برای متن‌های فارسی و چندزبانه.**

این پاراگراف فارسی است و داخل آن English words, `inline code` و عدد 2026 بدون به‌هم‌ریختگی نمایش داده می‌شوند.

## Mixed direction / جهت ترکیبی

English paragraph with فارسی داخل همان خط and **bold**, *italic*, ~~strike~~, [links](https://www.android.com), and emoji ✨.

> نقل‌قول فارسی باید از سمت صحیح پاراگراف نمایش داده شود.

- آیتم فارسی
- English item
- [x] task completed
- [ ] task pending

| ویژگی | Status |
|---|---|
| RTL/LTR | ✅ |
| Tables | ✅ |
| HTML | ✅ |

```kotlin
fun main() {
    println("MarbleMD")
}
```

---

### زبان‌های دیگر

العربية — اردو — کوردی — Türkçe — Azərbaycan dili — English — Русский — Ελληνικά — 日本語 — 中文
"""
    }
}

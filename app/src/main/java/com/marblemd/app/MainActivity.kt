package com.marblemd.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.IntentCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.marblemd.app.model.DirectionMode
import com.marblemd.app.model.MarkdownDocument
import com.marblemd.app.ui.ReaderScreen
import com.marblemd.app.ui.theme.MarbleMDTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.charset.Charset

class MainActivity : ComponentActivity() {
    private var document by mutableStateOf(sampleDocument())
    private var fontSizeSp by mutableFloatStateOf(18f)
    private var directionMode by mutableStateOf(DirectionMode.AUTO)

    private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) openUri(uri, persist = true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MarbleMDTheme {
                ReaderScreen(
                    document = document,
                    fontSizeSp = fontSizeSp,
                    directionMode = directionMode,
                    onOpen = { openDocument.launch(arrayOf("text/markdown", "text/x-markdown", "text/plain", "*/*")) },
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

    private fun consumeIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data?.let { openUri(it, persist = false) }
            Intent.ACTION_SEND -> {
                val stream = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                when {
                    stream != null -> openUri(stream, persist = false)
                    !intent.getStringExtra(Intent.EXTRA_TEXT).isNullOrBlank() -> {
                        document = MarkdownDocument("Shared text.md", intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty())
                    }
                }
            }
        }
    }

    private fun openUri(uri: Uri, persist: Boolean) {
        if (persist) {
            runCatching {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                runCatching {
                    val title = displayName(uri) ?: uri.lastPathSegment ?: "document.md"
                    val content = contentResolver.openInputStream(uri)?.use { input ->
                        input.bufferedReader(Charset.forName("UTF-8")).readText()
                    } ?: error("Unable to open file")
                    MarkdownDocument(title, content, uri)
                }
            }
            loaded.onSuccess { document = it }
                .onFailure { error ->
                    document = MarkdownDocument(
                        "Could not open file",
                        "# Error\n\n${error.message ?: "Unknown read error"}\n\nTry opening a UTF-8 Markdown file again."
                    )
                }
        }
    }

    private fun displayName(uri: Uri): String? {
        if (uri.scheme != "content") return null
        return contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }

    private fun sampleDocument() = MarkdownDocument(
        title = "Welcome.md",
        content = SAMPLE_MARKDOWN.trimIndent()
    )

    companion object {
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

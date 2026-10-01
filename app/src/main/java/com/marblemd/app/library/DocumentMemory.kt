package com.marblemd.app.library

import android.content.Context
import androidx.core.content.edit
import com.marblemd.app.model.MarkdownDocument
import com.marblemd.app.model.SaveState

/**
 * Remembers what the user was reading.
 *
 * Two things are persisted:
 *
 *  * **recents** - every Markdown file opened before, with the scroll position
 *    it was left at, so "Continue reading" (and the recents list) returns to
 *    the exact same place;
 *  * **session** - the tabs that were open when the app went away, including
 *    unsaved text, so a crash or a swipe-away never loses work.
 */
class DocumentMemory(private val context: Context) {

    private val preferences =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    // ------------------------------------------------------------- recents ---

    fun recents(): List<RecentDocument> =
        DocumentMemoryCodec.decodeRecords(preferences.getString(KEY_RECENTS, null))
            .mapNotNull(RecentDocument::fromRecord)
            .sortedByDescending { it.lastOpenedAt }
            .take(MAX_RECENTS)

    fun recent(uri: String): RecentDocument? = recents().firstOrNull { it.uri == uri }

    fun remember(
        uri: String,
        title: String,
        writable: Boolean,
        charCount: Int,
        snippet: String,
        scrollIndex: Int = 0
    ) {
        val existing = recent(uri)
        val updated = RecentDocument(
            uri = uri,
            title = title,
            lastOpenedAt = System.currentTimeMillis(),
            scrollIndex = if (scrollIndex > 0) scrollIndex else existing?.scrollIndex ?: 0,
            writable = writable,
            charCount = charCount,
            snippet = snippet.take(SNIPPET_CHARS)
        )
        val merged = listOf(updated) + recents().filterNot { it.uri == uri }
        writeRecents(merged.take(MAX_RECENTS))
    }

    /** Lightweight heartbeat while reading; never reorders the list. */
    fun updatePosition(uri: String, scrollIndex: Int) {
        val current = recents()
        val target = current.firstOrNull { it.uri == uri } ?: return
        if (target.scrollIndex == scrollIndex) return
        writeRecents(current.map { if (it.uri == uri) it.copy(scrollIndex = scrollIndex) else it })
    }

    fun forget(uri: String) {
        writeRecents(recents().filterNot { it.uri == uri })
    }

    fun clearRecents() {
        preferences.edit { remove(KEY_RECENTS) }
    }

    // ------------------------------------------------------------- session ---

    fun session(): List<MarkdownDocument> =
        DocumentMemoryCodec.decodeRecords(preferences.getString(KEY_SESSION, null))
            .mapNotNull(SessionDocument::fromRecord)
            .map { document ->
                MarkdownDocument(
                    id = document.id,
                    title = document.title,
                    content = document.content,
                    uri = document.uri?.let(android.net.Uri::parse),
                    writable = document.writable,
                    saveState = when {
                        document.uri == null -> SaveState.UNSAVED
                        document.writable -> SaveState.SAVED
                        else -> SaveState.READ_ONLY
                    }
                )
            }

    fun activeSessionId(): String? =
        DocumentMemoryCodec.decodeRecords(preferences.getString(KEY_SESSION, null))
            .mapNotNull(SessionDocument::fromRecord)
            .firstOrNull { it.active }
            ?.id

    fun sessionScrollPositions(): Map<String, Int> =
        DocumentMemoryCodec.decodeRecords(preferences.getString(KEY_SESSION, null))
            .mapNotNull(SessionDocument::fromRecord)
            .associate { it.id to it.scrollIndex }

    /**
     * Persists the open tabs. Document bodies are kept only up to a per-tab
     * limit and skipped entirely in extreme cases, so preferences can never
     * explode on a huge document.
     */
    fun saveSession(documents: List<MarkdownDocument>, activeId: String, scrollPositions: Map<String, Int>) {
        val records = documents.take(MAX_SESSION_TABS).map { document ->
            SessionDocument(
                id = document.id,
                title = document.title,
                uri = document.uri?.toString(),
                content = if (document.content.length <= MAX_SNAPSHOT_CHARS) document.content else "",
                writable = document.writable,
                scrollIndex = scrollPositions[document.id] ?: 0,
                active = document.id == activeId
            ).toRecord()
        }
        preferences.edit {
            putString(KEY_SESSION, DocumentMemoryCodec.encodeRecords(records))
            putLong(KEY_SESSION_SAVED_AT, System.currentTimeMillis())
        }
    }

    fun clearSession() {
        preferences.edit {
            remove(KEY_SESSION)
            remove(KEY_SESSION_SAVED_AT)
        }
    }

    // ------------------------------------------------------------ internals --

    private fun writeRecents(recents: List<RecentDocument>) {
        preferences.edit {
            putString(
                KEY_RECENTS,
                DocumentMemoryCodec.encodeRecords(recents.map(RecentDocument::toRecord))
            )
        }
    }

    private companion object {
        const val PREFERENCES = "marblemd_library"
        const val KEY_RECENTS = "recents"
        const val KEY_SESSION = "session"
        const val KEY_SESSION_SAVED_AT = "session_saved_at"

        const val MAX_RECENTS = 50
        const val MAX_SESSION_TABS = 12
        const val SNIPPET_CHARS = 180
        const val MAX_SNAPSHOT_CHARS = 400_000
    }
}

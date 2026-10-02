package com.marblemd.app.data

import android.content.Context
import android.net.Uri
import com.marblemd.app.model.MarkdownDocument
import com.marblemd.app.model.ReadingPosition
import com.marblemd.app.model.RecentDocumentInfo
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Keeps a small, private recent-document index and source snapshots in app storage.
 * The source snapshot lets local drafts survive process death and provides a
 * fallback when a document provider revokes a temporary URI grant.
 */
internal class RecentDocumentsStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val cacheDirectory = File(context.filesDir, CACHE_DIRECTORY).apply { mkdirs() }
    private val lock = Any()
    private val contentLock = Any()

    fun list(): List<RecentDocumentInfo> = synchronized(lock) { readIndex() }

    fun findById(id: String): RecentDocumentInfo? =
        synchronized(lock) { readIndex().firstOrNull { it.id == id } }

    fun findByUri(uri: Uri): RecentDocumentInfo? =
        synchronized(lock) { readIndex().firstOrNull { it.uriString == uri.toString() } }

    /** Adds/promotes a document when opened; existing reading position is retained. */
    fun remember(document: MarkdownDocument, touched: Boolean = true): List<RecentDocumentInfo> =
        synchronized(lock) {
            val current = readIndex()
            val uriString = document.uri?.toString()
            val old = current.firstOrNull { it.id == document.id }
                ?: uriString?.let { uri -> current.firstOrNull { it.uriString == uri } }
            val info = RecentDocumentInfo(
                id = document.id,
                title = document.title,
                uriString = uriString,
                writable = document.writable,
                lastOpenedAt = if (touched || old == null) System.currentTimeMillis() else old.lastOpenedAt,
                position = old?.position ?: ReadingPosition(),
                hasUnsavedChanges = document.hasUnsavedChanges
            )
            val next = if (touched) {
                listOf(info) + current.filterNot { it.id == info.id || (uriString != null && it.uriString == uriString) }
            } else {
                val replaced = current.map { existing ->
                    if (existing.id == info.id) info else existing
                }
                if (replaced.any { it.id == info.id }) replaced else listOf(info) + replaced
            }
            val bounded = writeIndex(next)
            val retainedIds = bounded.mapTo(mutableSetOf()) { it.id }
            (current + next)
                .distinctBy { it.id }
                .filterNot { it.id in retainedIds }
                .forEach { deleteContent(it.id) }
            bounded
        }

    fun savePosition(id: String, position: ReadingPosition): List<RecentDocumentInfo> =
        synchronized(lock) {
            val current = readIndex()
            val next = current.map { info ->
                if (info.id == id) info.copy(position = position.normalized()) else info
            }
            if (next != current) writeIndex(next) else current
        }

    fun saveContent(id: String, content: String) {
        if (!isSafeId(id)) return
        synchronized(contentLock) {
            cacheDirectory.mkdirs()
            val destination = contentFile(id)
            val temporary = File(cacheDirectory, "$id.tmp")
            temporary.bufferedWriter(StandardCharsets.UTF_8).use { writer -> writer.write(content) }
            if (destination.exists() && !destination.delete()) {
                temporary.delete()
                return
            }
            if (!temporary.renameTo(destination)) {
                temporary.copyTo(destination, overwrite = true)
                temporary.delete()
            }
        }
    }

    fun loadContent(id: String): String? = synchronized(contentLock) {
        if (!isSafeId(id)) return@synchronized null
        contentFile(id).takeIf { it.isFile }?.let { file ->
            runCatching { file.readText(StandardCharsets.UTF_8) }.getOrNull()
        }
    }

    fun remove(id: String): List<RecentDocumentInfo> = synchronized(lock) {
        val next = readIndex().filterNot { it.id == id }
        deleteContent(id)
        writeIndex(next)
    }

    private fun readIndex(): List<RecentDocumentInfo> {
        val raw = preferences.getString(KEY_RECENTS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val json = array.optJSONObject(index) ?: continue
                    val id = json.optString("id").takeIf(::isSafeId) ?: continue
                    val uri = if (json.has("uri") && !json.isNull("uri")) json.getString("uri") else null
                    add(
                        RecentDocumentInfo(
                            id = id,
                            title = json.optString("title", "Untitled.md"),
                            uriString = uri,
                            writable = json.optBoolean("writable", false),
                            lastOpenedAt = json.optLong("lastOpenedAt", 0L),
                            position = ReadingPosition(
                                firstVisibleItemIndex = json.optInt("positionIndex", 0).coerceAtLeast(0),
                                firstVisibleItemScrollOffset = json.optInt("positionOffset", 0).coerceAtLeast(0)
                            ),
                            hasUnsavedChanges = json.optBoolean("hasUnsavedChanges", false)
                        )
                    )
                }
            }.distinctBy { it.id }.take(MAX_RECENTS)
        }.getOrDefault(emptyList())
    }

    /** Writes the bounded recent-document index. */
    private fun writeIndex(entries: List<RecentDocumentInfo>): List<RecentDocumentInfo> {
        val bounded = entries.distinctBy { it.id }.take(MAX_RECENTS)
        val array = JSONArray()
        bounded.forEach { info ->
            array.put(
                JSONObject()
                    .put("id", info.id)
                    .put("title", info.title)
                    .put("uri", info.uriString)
                    .put("writable", info.writable)
                    .put("hasUnsavedChanges", info.hasUnsavedChanges)
                    .put("lastOpenedAt", info.lastOpenedAt)
                    .put("positionIndex", info.position.firstVisibleItemIndex.coerceAtLeast(0))
                    .put("positionOffset", info.position.firstVisibleItemScrollOffset.coerceAtLeast(0))
            )
        }
        preferences.edit().putString(KEY_RECENTS, array.toString()).apply()
        return bounded
    }

    private fun deleteContent(id: String) {
        if (!isSafeId(id)) return
        synchronized(contentLock) { contentFile(id).delete() }
    }

    private fun contentFile(id: String) = File(cacheDirectory, "$id.md")

    private fun isSafeId(id: String): Boolean = id.matches(ID_PATTERN)

    private companion object {
        const val PREFERENCES_NAME = "recent_markdown_documents"
        const val KEY_RECENTS = "documents"
        const val CACHE_DIRECTORY = "recent-markdown"
        const val MAX_RECENTS = 20
        val ID_PATTERN = Regex("[a-zA-Z0-9-]{1,80}")
    }
}

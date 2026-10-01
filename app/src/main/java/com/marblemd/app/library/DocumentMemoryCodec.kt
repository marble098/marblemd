package com.marblemd.app.library

/**
 * Pure Kotlin codec for MarbleMD's document memory (recents + open session).
 *
 * Kept free of Android types so the escaping rules can be unit tested.
 */
internal object DocumentMemoryCodec {

    const val VERSION = "v1"
    private const val FIELD_SEPARATOR = '\t'
    private const val RECORD_SEPARATOR = '\n'

    fun encodeRecords(records: List<List<String?>>): String = buildString {
        append(VERSION)
        records.forEach { record ->
            append(RECORD_SEPARATOR)
            append(record.joinToString(FIELD_SEPARATOR.toString()) { escape(it.orEmpty()) })
        }
    }

    fun decodeRecords(raw: String?): List<List<String>> {
        if (raw.isNullOrBlank()) return emptyList()
        val lines = raw.split(RECORD_SEPARATOR)
        if (lines.isEmpty() || lines.first().trim() != VERSION) return emptyList()
        return lines.drop(1)
            .filter { it.isNotEmpty() }
            .map { line -> line.split(FIELD_SEPARATOR).map(::unescape) }
    }

    fun escape(value: String): String = buildString(value.length + 8) {
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '\t' -> append("\\t")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                else -> append(character)
            }
        }
    }

    fun unescape(value: String): String {
        if ('\\' !in value) return value
        val builder = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            val character = value[index]
            if (character == '\\' && index + 1 < value.length) {
                when (val escaped = value[index + 1]) {
                    't' -> builder.append('\t')
                    'n' -> builder.append('\n')
                    'r' -> builder.append('\r')
                    '\\' -> builder.append('\\')
                    else -> builder.append(escaped)
                }
                index += 2
            } else {
                builder.append(character)
                index++
            }
        }
        return builder.toString()
    }
}

/** A document the user opened before, with the position they stopped at. */
data class RecentDocument(
    val uri: String,
    val title: String,
    val lastOpenedAt: Long,
    val scrollIndex: Int,
    val writable: Boolean,
    val charCount: Int,
    val snippet: String
) {
    fun toRecord(): List<String?> = listOf(
        uri,
        title,
        lastOpenedAt.toString(),
        scrollIndex.toString(),
        if (writable) "1" else "0",
        charCount.toString(),
        snippet
    )

    companion object {
        fun fromRecord(fields: List<String>): RecentDocument? {
            if (fields.size < 7) return null
            val uri = fields[0]
            if (uri.isBlank()) return null
            return RecentDocument(
                uri = uri,
                title = fields[1].ifBlank { uri.substringAfterLast('/') },
                lastOpenedAt = fields[2].toLongOrNull() ?: 0L,
                scrollIndex = fields[3].toIntOrNull()?.coerceAtLeast(0) ?: 0,
                writable = fields[4] == "1",
                charCount = fields[5].toIntOrNull() ?: 0,
                snippet = fields[6]
            )
        }
    }
}

/** An open tab, restored on the next launch. */
data class SessionDocument(
    val id: String,
    val title: String,
    val uri: String?,
    val content: String,
    val writable: Boolean,
    val scrollIndex: Int,
    val active: Boolean
) {
    fun toRecord(): List<String?> = listOf(
        id,
        title,
        uri,
        content,
        if (writable) "1" else "0",
        scrollIndex.toString(),
        if (active) "1" else "0"
    )

    companion object {
        fun fromRecord(fields: List<String>): SessionDocument? {
            if (fields.size < 7) return null
            val id = fields[0]
            if (id.isBlank()) return null
            return SessionDocument(
                id = id,
                title = fields[1].ifBlank { "Untitled.md" },
                uri = fields[2].takeIf { it.isNotBlank() },
                content = fields[3],
                writable = fields[4] == "1",
                scrollIndex = fields[5].toIntOrNull()?.coerceAtLeast(0) ?: 0,
                active = fields[6] == "1"
            )
        }
    }
}

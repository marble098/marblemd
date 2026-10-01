package com.marblemd.app.text

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import java.io.File
import java.util.UUID

/** A font the user imported into the app. */
data class CustomFont(
    val id: String,
    val displayName: String,
    val fileName: String,
    val importedAt: Long
) {
    val byteSize: Long?
        get() = null
}

/**
 * Stores user supplied `.ttf` / `.otf` fonts in the app's private storage and
 * keeps the metadata in preferences.
 *
 * Fonts are copied (not referenced by URI) so the choice keeps working after a
 * reboot, an SD-card removal or a revoked SAF permission.
 */
class CustomFontStore(private val context: Context) {

    private val preferences =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    private val directory: File
        get() = File(context.filesDir, FONT_DIRECTORY).apply { mkdirs() }

    fun fonts(): List<CustomFont> =
        decode(preferences.getString(KEY_FONTS, null))
            .filter { file(it).exists() }
            .sortedBy { it.displayName.lowercase() }

    fun font(id: String?): CustomFont? =
        id?.let { wanted -> fonts().firstOrNull { it.id == wanted } }

    fun file(font: CustomFont): File = File(directory, font.fileName)

    fun uiFontId(): String? = preferences.getString(KEY_UI_FONT, null)

    fun setUiFontId(id: String?) = preferences.edit {
        if (id == null) remove(KEY_UI_FONT) else putString(KEY_UI_FONT, id)
    }

    /**
     * Copies a picked document into private storage.
     *
     * The file must look like a real SFNT font; anything else (an HTML error
     * page, a renamed archive, a truncated download) is rejected before it can
     * break rendering.
     */
    fun import(uri: Uri, fallbackName: String?): Result<CustomFont> = runCatching {
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(MAX_FONT_BYTES + 1)
            var total = 0
            while (total < buffer.size) {
                val read = input.read(buffer, total, buffer.size - total)
                if (read <= 0) break
                total += read
            }
            buffer.copyOf(total)
        } ?: error("The selected file could not be read")

        if (bytes.isEmpty()) error("The selected file is empty")
        if (bytes.size > MAX_FONT_BYTES) error("Fonts larger than 24 MB are not supported")
        if (!TrueTypeName.looksLikeFont(bytes)) {
            error("That file is not a TrueType/OpenType font (.ttf or .otf)")
        }

        val displayName = TrueTypeName.familyName(bytes)
            ?: fallbackName?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
            ?: "Custom font"

        val font = CustomFont(
            id = UUID.randomUUID().toString(),
            displayName = displayName,
            fileName = "font-${UUID.randomUUID()}.${detectExtension(bytes)}",
            importedAt = System.currentTimeMillis()
        )

        val target = file(font)
        target.parentFile?.mkdirs()
        target.writeBytes(bytes)

        val updated = fonts() + font
        preferences.edit { putString(KEY_FONTS, encode(updated)) }
        font
    }

    fun delete(font: CustomFont): Boolean {
        val remaining = fonts().filterNot { it.id == font.id }
        preferences.edit {
            putString(KEY_FONTS, encode(remaining))
            if (uiFontId() == font.id) remove(KEY_UI_FONT)
        }
        return runCatching { file(font).delete() }.getOrDefault(false)
    }

    fun rename(font: CustomFont, newName: String): CustomFont {
        val cleaned = newName.trim().take(60).ifBlank { font.displayName }
        val updated = font.copy(displayName = cleaned)
        preferences.edit {
            putString(KEY_FONTS, encode(fonts().map { if (it.id == font.id) updated else it }))
        }
        return updated
    }

    private fun detectExtension(bytes: ByteArray): String =
        if (bytes.size > 3 && bytes[0] == 'O'.code.toByte() && bytes[1] == 'T'.code.toByte()) {
            "otf"
        } else {
            "ttf"
        }

    private fun encode(fonts: List<CustomFont>): String = fonts.joinToString("\n") { font ->
        listOf(
            font.id,
            font.displayName,
            font.fileName,
            font.importedAt.toString()
        ).joinToString("\t") { escape(it) }
    }

    private fun decode(raw: String?): List<CustomFont> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.lineSequence().mapNotNull { line ->
            if (line.isBlank()) return@mapNotNull null
            val parts = line.split('\t')
            if (parts.size < 4) return@mapNotNull null
            val importedAt = parts[3].toLongOrNull() ?: 0L
            CustomFont(
                id = unescape(parts[0]),
                displayName = unescape(parts[1]),
                fileName = unescape(parts[2]),
                importedAt = importedAt
            )
        }.toList()
    }

    private fun escape(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\t", "\\t")
        .replace("\n", "\\n")

    private fun unescape(value: String): String {
        val builder = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            val character = value[index]
            if (character == '\\' && index + 1 < value.length) {
                when (value[index + 1]) {
                    't' -> builder.append('\t')
                    'n' -> builder.append('\n')
                    '\\' -> builder.append('\\')
                    else -> builder.append(value[index + 1])
                }
                index += 2
            } else {
                builder.append(character)
                index++
            }
        }
        return builder.toString()
    }

    private companion object {
        const val PREFERENCES = "marblemd_fonts"
        const val KEY_FONTS = "fonts"
        const val KEY_UI_FONT = "ui_font"
        const val FONT_DIRECTORY = "custom_fonts"
        const val MAX_FONT_BYTES = 24 * 1024 * 1024
    }
}

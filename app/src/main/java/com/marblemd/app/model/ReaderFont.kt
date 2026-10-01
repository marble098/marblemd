package com.marblemd.app.model

import com.marblemd.app.text.CustomFont

/**
 * A selectable reading font.
 *
 * Built-in entries map to the OFL fonts shipped with the app; custom entries
 * point at a `.ttf`/`.otf` file the user imported into the app's private
 * storage. The [key] is what gets persisted in preferences.
 */
data class ReaderFont(
    val key: String,
    val label: String,
    val shortLabel: String,
    val isCustom: Boolean = false
) {
    companion object {
        const val KEY_SMART = "smart"
        const val KEY_VAZIRMATN = "vazirmatn"
        const val KEY_LALEZAR = "lalezar"
        const val CUSTOM_PREFIX = "custom:"

        val SMART = ReaderFont(KEY_SMART, "Smart multilingual", "Smart")
        val VAZIRMATN = ReaderFont(KEY_VAZIRMATN, "Vazirmatn (Persian)", "Vazir")
        val LALEZAR = ReaderFont(KEY_LALEZAR, "Lalezar (display titr)", "Lalezar")

        val builtIns: List<ReaderFont> = listOf(SMART, VAZIRMATN, LALEZAR)

        fun custom(font: CustomFont): ReaderFont = ReaderFont(
            key = CUSTOM_PREFIX + font.id,
            label = font.displayName,
            shortLabel = font.displayName.shortFontLabel(),
            isCustom = true
        )

        /** Every font the user can pick, built-ins first. */
        fun all(customFonts: List<CustomFont>): List<ReaderFont> =
            builtIns + customFonts.map(::custom)

        /** Resolves a persisted key, tolerating fonts that were deleted meanwhile. */
        fun fromKey(key: String?, customFonts: List<CustomFont>): ReaderFont {
            if (key.isNullOrBlank()) return SMART
            builtIns.firstOrNull { it.key == key }?.let { return it }
            if (key.startsWith(CUSTOM_PREFIX)) {
                val id = key.removePrefix(CUSTOM_PREFIX)
                customFonts.firstOrNull { it.id == id }?.let { return custom(it) }
            }
            return SMART
        }

        fun customFontId(key: String): String? =
            key.takeIf { it.startsWith(CUSTOM_PREFIX) }?.removePrefix(CUSTOM_PREFIX)
    }

    /** Reader fonts are stored as a compact key so preferences survive upgrades. */
    override fun toString(): String = key
}

internal fun String.shortFontLabel(limit: Int = 14): String {
    val trimmed = trim().replace(Regex("""\s+"""), " ")
    if (trimmed.isEmpty()) return "Custom"
    return if (trimmed.length <= limit) trimmed else trimmed.take(limit - 1) + "…"
}

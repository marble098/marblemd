package com.marblemd.app.text

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import com.marblemd.app.model.ReaderFont

/**
 * Resolves reading typefaces, including fonts the user imported.
 *
 * Built-in fonts come from `assets/fonts`; custom fonts come from the app's
 * private storage and are wrapped with a system fallback so non-Latin scripts
 * (CJK, Devanagari, ...) that the user font does not cover still render.
 */
internal class FontRegistry(private val context: Context) {

    private val bundled = mutableMapOf<String, Typeface>()
    private val custom = mutableMapOf<String, Typeface>()

    val vazirmatn: Typeface get() = bundled("fonts/Vazirmatn.ttf", Typeface.SANS_SERIF)
    val notoSans: Typeface get() = bundled("fonts/NotoSans.ttf", Typeface.SANS_SERIF)
    val lalezar: Typeface get() = bundled("fonts/Lalezar.ttf", vazirmatn)

    /** Typeface for a custom font id, or `null` when the file is gone. */
    fun customTypeface(id: String): Typeface? {
        custom[id]?.let { return it }
        val store = CustomFontStore(context)
        val font = store.font(id) ?: return null
        val file = store.file(font)
        if (!file.exists()) return null
        val typeface = loadFile(file)
        custom[id] = typeface
        return typeface
    }

    /** Drops a cached custom typeface (after delete/replace). */
    fun invalidate(id: String) {
        custom.remove(id)
    }

    /** Primary typeface for a reading font, or `null` to keep the system font. */
    fun primaryTypeface(font: ReaderFont): Typeface? =
        if (font.isCustom) {
            ReaderFont.customFontId(font.key)?.let(::customTypeface)
        } else {
            when (font.key) {
                ReaderFont.KEY_VAZIRMATN -> vazirmatn
                ReaderFont.KEY_LALEZAR -> lalezar
                else -> null
            }
        }

    /** Typeface used for a script bucket while [smart] multilingual mode is on. */
    fun smartTypeface(script: FontScript): Typeface? = when (script) {
        FontScript.ARABIC -> vazirmatn
        FontScript.GOOGLE_SANS -> notoSans
        FontScript.SYSTEM -> null
    }

    val defaultTypeface: Typeface get() = Typeface.SANS_SERIF

    private fun bundled(asset: String, fallback: Typeface): Typeface =
        bundled.getOrPut(asset) {
            runCatching { Typeface.createFromAsset(context.assets, asset) }.getOrDefault(fallback)
        }

    private fun loadFile(file: java.io.File): Typeface {
        val direct = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Typeface.Builder(file).build()
            } else {
                @Suppress("DEPRECATION")
                Typeface.createFromFile(file)
            }
        }.getOrNull()

        val base = direct ?: runCatching {
            @Suppress("DEPRECATION")
            Typeface.createFromFile(file)
        }.getOrNull() ?: return defaultTypeface

        // The user font is the primary face; Android's multilingual stack
        // covers whatever it does not contain.
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Typeface.CustomFallbackBuilder(base)
                    .setSystemFallback("sans-serif")
                    .build()
            } else {
                base
            }
        }.getOrDefault(base)
    }
}

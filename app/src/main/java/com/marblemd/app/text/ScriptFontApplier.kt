package com.marblemd.app.text

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.MetricAffectingSpan
import com.marblemd.app.model.ReaderFont

internal object ScriptFontApplier {
    fun apply(source: Spanned, fonts: FontRegistry, readerFont: ReaderFont): Spanned {
        val text = SpannableStringBuilder(source)
        if (text.isEmpty()) return text

        var start = 0
        var current = scriptAt(text, 0)
        var i = Character.charCount(Character.codePointAt(text, 0))

        while (i < text.length) {
            val script = scriptAt(text, i)
            if (script != current) {
                applyRun(text, start, i, current, fonts, readerFont)
                start = i
                current = script
            }
            i += Character.charCount(Character.codePointAt(text, i))
        }
        applyRun(text, start, text.length, current, fonts, readerFont)
        return text
    }

    private fun scriptAt(text: CharSequence, offset: Int): FontScript =
        UnicodeScript.classify(Character.codePointAt(text, offset))

    private fun applyRun(
        text: SpannableStringBuilder,
        start: Int,
        end: Int,
        script: FontScript,
        fonts: FontRegistry,
        readerFont: ReaderFont
    ) {
        if (start >= end) return
        if (!readerFont.isCustom && script == FontScript.SYSTEM) return
        if (overlapsCode(text, start, end)) return
        val typeface = selectTypeface(script, fonts, readerFont) ?: return
        text.setSpan(
            PreservingTypefaceSpan(typeface),
            start,
            end,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }

    private fun selectTypeface(
        script: FontScript,
        fonts: FontRegistry,
        readerFont: ReaderFont
    ): Typeface? {
        // A font the user imported is applied everywhere; Android's system
        // fallback (wired in FontRegistry) covers glyphs it does not contain.
        if (readerFont.isCustom) {
            return fonts.primaryTypeface(readerFont)
        }
        return when (readerFont.key) {
            ReaderFont.KEY_SMART -> fonts.smartTypeface(script)
            ReaderFont.KEY_VAZIRMATN -> fonts.vazirmatn
            ReaderFont.KEY_LALEZAR -> fonts.lalezar
            else -> fonts.smartTypeface(script)
        }
    }

    private fun overlapsCode(text: Spanned, start: Int, end: Int): Boolean =
        text.getSpans(start, end, MetricAffectingSpan::class.java)
            .any { span -> span.javaClass.simpleName.contains("Code", ignoreCase = true) }
}

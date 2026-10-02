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
        if (start >= end || overlapsCode(text, start, end)) return
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
    ): Typeface? = when (readerFont) {
        ReaderFont.SMART -> when (script) {
            FontScript.ARABIC -> fonts.vazirmatn
            FontScript.GOOGLE_SANS -> fonts.notoSans
            FontScript.SYSTEM -> null
        }
        ReaderFont.VAZIRMATN -> when (script) {
            FontScript.ARABIC, FontScript.GOOGLE_SANS -> fonts.vazirmatn
            FontScript.SYSTEM -> null
        }
        ReaderFont.LALEZAR -> when (script) {
            FontScript.ARABIC, FontScript.GOOGLE_SANS -> fonts.lalezar
            FontScript.SYSTEM -> null
        }
        ReaderFont.CUSTOM -> when (script) {
            FontScript.ARABIC, FontScript.GOOGLE_SANS -> fonts.custom
                ?: if (script == FontScript.ARABIC) fonts.vazirmatn else fonts.notoSans
            FontScript.SYSTEM -> fonts.custom
        }
    }

    private fun overlapsCode(text: Spanned, start: Int, end: Int): Boolean =
        text.getSpans(start, end, MetricAffectingSpan::class.java)
            .any { span -> span.javaClass.simpleName.contains("Code", ignoreCase = true) }
}

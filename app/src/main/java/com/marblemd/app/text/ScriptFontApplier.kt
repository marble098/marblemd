package com.marblemd.app.text

import android.text.Spanned
import android.text.SpannableStringBuilder
import android.text.style.MetricAffectingSpan

internal object ScriptFontApplier {
    fun apply(source: Spanned, fonts: FontRegistry): Spanned {
        val text = SpannableStringBuilder(source)
        if (text.isEmpty()) return text

        var start = 0
        var current = scriptAt(text, 0)
        var i = Character.charCount(Character.codePointAt(text, 0))

        while (i < text.length) {
            val script = scriptAt(text, i)
            if (script != current) {
                applyRun(text, start, i, current, fonts)
                start = i
                current = script
            }
            i += Character.charCount(Character.codePointAt(text, i))
        }
        applyRun(text, start, text.length, current, fonts)
        return text
    }

    private fun scriptAt(text: CharSequence, offset: Int): FontScript =
        UnicodeScript.classify(Character.codePointAt(text, offset))

    private fun applyRun(
        text: SpannableStringBuilder,
        start: Int,
        end: Int,
        script: FontScript,
        fonts: FontRegistry
    ) {
        if (start >= end || script == FontScript.SYSTEM || overlapsCode(text, start, end)) return
        val typeface = when (script) {
            FontScript.ARABIC -> fonts.vazirmatn
            FontScript.GOOGLE_SANS -> fonts.notoSans
            FontScript.SYSTEM -> return
        }
        text.setSpan(
            PreservingTypefaceSpan(typeface),
            start,
            end,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }

    private fun overlapsCode(text: Spanned, start: Int, end: Int): Boolean =
        text.getSpans(start, end, MetricAffectingSpan::class.java)
            .any { span -> span.javaClass.simpleName.contains("Code", ignoreCase = true) }
}

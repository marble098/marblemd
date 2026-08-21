package com.marblemd.app.text

import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.text.style.MetricAffectingSpan

internal class PreservingTypefaceSpan(
    private val baseTypeface: Typeface
) : MetricAffectingSpan() {
    override fun updateDrawState(tp: TextPaint) = apply(tp)
    override fun updateMeasureState(tp: TextPaint) = apply(tp)

    private fun apply(paint: Paint) {
        val previousStyle = paint.typeface?.style ?: Typeface.NORMAL
        paint.typeface = Typeface.create(baseTypeface, previousStyle)
    }
}

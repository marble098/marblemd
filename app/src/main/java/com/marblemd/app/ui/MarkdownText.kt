package com.marblemd.app.ui

import android.os.Build
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.marblemd.app.markdown.MarkdownEngine
import com.marblemd.app.model.DirectionMode

@Composable
fun MarkdownText(
    markdown: String,
    fontSizeSp: Float,
    directionMode: DirectionMode,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val textColor = colors.onSurface.toArgb()
    val linkColor = colors.primary.toArgb()
    val quoteColor = colors.secondary.toArgb()
    val codeBg = colors.surfaceVariant.toArgb()
    val engine = remember(textColor, linkColor, quoteColor, codeBg) {
        MarkdownEngine(context, textColor, linkColor, quoteColor, codeBg)
    }
    val rendered = remember(markdown, engine) { engine.render(markdown) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextView(ctx).apply {
                setTextIsSelectable(true)
                linksClickable = true
                includeFontPadding = false
                textAlignment = View.TEXT_ALIGNMENT_TEXT_START
                setLineSpacing(dp(3f), 1.12f)
                setPadding(dp(20f).toInt(), dp(18f).toInt(), dp(20f).toInt(), dp(64f).toInt())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    breakStrategy = android.text.Layout.BREAK_STRATEGY_HIGH_QUALITY
                    hyphenationFrequency = android.text.Layout.HYPHENATION_FREQUENCY_NORMAL
                }
            }
        },
        update = { view ->
            view.setTextColor(textColor)
            view.setLinkTextColor(linkColor)
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSizeSp)
            engine.applyTo(view, rendered)
            when (directionMode) {
                DirectionMode.AUTO -> {
                    view.layoutDirection = View.LAYOUT_DIRECTION_LOCALE
                    view.textDirection = View.TEXT_DIRECTION_FIRST_STRONG
                    view.textAlignment = View.TEXT_ALIGNMENT_TEXT_START
                }
                DirectionMode.RTL -> {
                    view.layoutDirection = View.LAYOUT_DIRECTION_RTL
                    view.textDirection = View.TEXT_DIRECTION_RTL
                    view.textAlignment = View.TEXT_ALIGNMENT_TEXT_START
                }
                DirectionMode.LTR -> {
                    view.layoutDirection = View.LAYOUT_DIRECTION_LTR
                    view.textDirection = View.TEXT_DIRECTION_LTR
                    view.textAlignment = View.TEXT_ALIGNMENT_TEXT_START
                }
            }
        }
    )
}

private fun TextView.dp(value: Float): Float = value * resources.displayMetrics.density

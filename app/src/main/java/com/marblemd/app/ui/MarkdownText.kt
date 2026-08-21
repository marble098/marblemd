package com.marblemd.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.text.LineBreaker
import android.os.Build
import android.text.Layout
import android.text.Spanned
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.marblemd.app.markdown.MarkdownEngine
import com.marblemd.app.model.DirectionMode
import com.marblemd.app.model.ReaderFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.commonmark.node.Node
import kotlin.math.abs

@Composable
internal fun rememberMarkdownEngine(readerFont: ReaderFont): MarkdownEngine {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val textColor = colors.onSurface.toArgb()
    val linkColor = colors.primary.toArgb()
    val quoteColor = colors.secondary.toArgb()
    val codeBg = colors.surfaceVariant.toArgb()

    return remember(textColor, linkColor, quoteColor, codeBg, readerFont) {
        MarkdownEngine(
            context = context.applicationContext,
            textColor = textColor,
            linkColor = linkColor,
            quoteColor = quoteColor,
            codeBackgroundColor = codeBg,
            readerFont = readerFont
        )
    }
}

@Composable
internal fun MarkdownText(
    node: Node,
    fontSizeSp: Float,
    directionMode: DirectionMode,
    engine: MarkdownEngine,
    isFirstBlock: Boolean,
    isLastBlock: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val textColor = colors.onSurface.toArgb()
    val linkColor = colors.primary.toArgb()

    val rendered by produceState<Spanned?>(
        initialValue = null,
        key1 = node,
        key2 = engine
    ) {
        value = withContext(Dispatchers.Default) {
            engine.render(node)
        }
    }

    val ready = rendered
    if (ready == null) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = 42.dp)
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        return
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            MarkdownRenderView(ctx).apply {
                setTextIsSelectable(true)
                linksClickable = true
                includeFontPadding = false
                textAlignment = View.TEXT_ALIGNMENT_TEXT_START
                setLineSpacing(dp(2.5f), 1.10f)

            }
        },
        update = { view ->
            val useFastLayout = ready.length >= FAST_LAYOUT_BLOCK_CHARS
            if (view.fastLayout != useFastLayout) {
                configureLineBreaking(view, useFastLayout)
                view.fastLayout = useFastLayout
            }

            view.setPadding(
                view.dp(20f).toInt(),
                view.dp(if (isFirstBlock) 16f else 1f).toInt(),
                view.dp(20f).toInt(),
                view.dp(if (isLastBlock) 64f else 1f).toInt()
            )
            view.setTextColor(textColor)
            view.setLinkTextColor(linkColor)

            val desiredPx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                fontSizeSp,
                view.resources.displayMetrics
            )
            if (abs(view.textSize - desiredPx) > 0.5f) {
                view.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSizeSp)
            }

            // Font-size and direction changes only relayout this visible block.
            if (view.appliedRender !== ready) {
                engine.applyTo(view, ready)
                view.appliedRender = ready
            }

            applyDirection(view, directionMode)
        }
    )
}

private fun applyDirection(view: TextView, directionMode: DirectionMode) {
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

private class MarkdownRenderView(context: Context) : TextView(context) {
    var appliedRender: Spanned? = null
    var fastLayout: Boolean? = null
}

private fun TextView.dp(value: Float): Float = value * resources.displayMetrics.density

private fun configureLineBreaking(view: TextView, fast: Boolean) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        view.breakStrategy = if (fast) {
            LineBreaker.BREAK_STRATEGY_SIMPLE
        } else {
            LineBreaker.BREAK_STRATEGY_HIGH_QUALITY
        }
        view.hyphenationFrequency = if (fast) {
            Layout.HYPHENATION_FREQUENCY_NONE
        } else {
            Layout.HYPHENATION_FREQUENCY_NORMAL
        }
    } else {
        view.configureLegacyLineBreaking(fast)
    }
}

@SuppressLint("WrongConstant")
private fun TextView.configureLegacyLineBreaking(fast: Boolean) {
    breakStrategy = if (fast) Layout.BREAK_STRATEGY_SIMPLE else Layout.BREAK_STRATEGY_HIGH_QUALITY
    hyphenationFrequency = if (fast) {
        Layout.HYPHENATION_FREQUENCY_NONE
    } else {
        Layout.HYPHENATION_FREQUENCY_NORMAL
    }
}

private const val FAST_LAYOUT_BLOCK_CHARS = 3_500

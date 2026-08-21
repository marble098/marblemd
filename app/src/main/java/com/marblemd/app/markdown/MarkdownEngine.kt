package com.marblemd.app.markdown

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.text.Layout
import android.text.Spanned
import android.text.style.LeadingMarginSpan
import android.util.LruCache
import android.widget.TextView
import coil.ImageLoader
import com.marblemd.app.model.ReaderFont
import com.marblemd.app.text.FontRegistry
import com.marblemd.app.text.ScriptFontApplier
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.MarkwonReducer
import io.noties.markwon.MarkwonSpansFactory
import io.noties.markwon.RenderProps
import io.noties.markwon.SpanFactory
import io.noties.markwon.core.CoreProps
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.core.spans.BulletListItemSpan
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TableAwareMovementMethod
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.coil.CoilImagesPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import io.noties.markwon.movement.MovementMethodPlugin
import io.noties.markwon.utils.LeadingMarginUtils
import org.commonmark.node.Code
import org.commonmark.node.Heading
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.Text

internal class MarkdownEngine(
    context: Context,
    textColor: Int,
    linkColor: Int,
    quoteColor: Int,
    codeBackgroundColor: Int,
    private val readerFont: ReaderFont
) {
    private val fonts = FontRegistry(context)
    private val imageLoader = ImageLoader.Builder(context).build()
    private val parseLock = Any()
    private val reducer = MarkwonReducer.directChildren()
    private val renderCache = object : LruCache<Node, Spanned>(RENDER_CACHE_CHARS) {
        override fun sizeOf(key: Node, value: Spanned): Int = value.length.coerceAtLeast(1)
    }

    private val markwon: Markwon = Markwon.builder(context)
        .usePlugin(HtmlPlugin.create())
        .usePlugin(TablePlugin.create(context))
        .usePlugin(StrikethroughPlugin.create())
        .usePlugin(TaskListPlugin.create(context))
        .usePlugin(LinkifyPlugin.create())
        .usePlugin(CoilImagesPlugin.create(context, imageLoader))
        .usePlugin(MovementMethodPlugin.create(TableAwareMovementMethod.create()))
        .usePlugin(object : AbstractMarkwonPlugin() {
            override fun configureTheme(builder: MarkwonTheme.Builder) {
                builder
                    .linkColor(linkColor)
                    .blockQuoteColor(quoteColor)
                    .codeTextColor(textColor)
                    .codeBlockTextColor(textColor)
                    .codeBlockBackgroundColor(codeBackgroundColor)
                    .codeBackgroundColor(codeBackgroundColor)
            }

            override fun configureSpansFactory(builder: MarkwonSpansFactory.Builder) {
                // Markwon 4.6.2 hardcodes ordered-list labels as ASCII "1. ".
                // Replace only the ListItem factory so each marker can follow the
                // actual script direction of its rendered list item.
                builder.setFactory(ListItem::class.java, BidiAwareListItemSpanFactory())
            }

            override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                // Parsing is shared with block-level lazy rendering below.
            }
        })
        .build()

    fun prepare(markdown: String, revision: Long): MarkdownRenderPlan = synchronized(parseLock) {
        val document = markwon.parse(markdown)
        val nodes = reducer.reduce(document)
        val blocks = nodes.mapIndexed { index, node -> MarkdownRenderBlock(index, node) }
        val headings = buildList {
            blocks.forEach { block ->
                collectHeadings(block.node, block.index, this)
            }
        }
        MarkdownRenderPlan(
            revision = revision,
            blocks = blocks,
            headings = headings
        )
    }

    fun render(node: Node): Spanned {
        renderCache.get(node)?.let { return it }
        val rendered = ScriptFontApplier.apply(markwon.render(node), fonts, readerFont)
        renderCache.put(node, rendered)
        return rendered
    }

    fun applyTo(textView: TextView, rendered: Spanned) {
        markwon.setParsedMarkdown(textView, rendered)
    }

    private fun collectHeadings(
        node: Node,
        blockIndex: Int,
        output: MutableList<MarkdownPlanHeading>
    ) {
        if (node is Heading) {
            val title = headingText(node).trim().replace(Regex("""\s+"""), " ")
            if (title.isNotBlank()) {
                output += MarkdownPlanHeading(
                    title = title,
                    level = node.level,
                    blockIndex = blockIndex
                )
            }
        }

        var child = node.firstChild
        while (child != null) {
            collectHeadings(child, blockIndex, output)
            child = child.next
        }
    }

    private fun headingText(node: Node): String = buildString {
        appendInlineText(node, this)
    }

    private fun appendInlineText(node: Node, output: StringBuilder) {
        when (node) {
            is Text -> output.append(node.literal)
            is Code -> output.append(node.literal)
            else -> {
                var child = node.firstChild
                while (child != null) {
                    appendInlineText(child, output)
                    child = child.next
                }
            }
        }
    }

    companion object {
        // Cache rendered visible/recent blocks, not one giant document Spanned.
        private const val RENDER_CACHE_CHARS = 320 * 1024
    }
}

private class BidiAwareListItemSpanFactory : SpanFactory {
    override fun getSpans(
        configuration: MarkwonConfiguration,
        props: RenderProps
    ): Any {
        return if (CoreProps.LIST_ITEM_TYPE.require(props) == CoreProps.ListItemType.BULLET) {
            BulletListItemSpan(
                configuration.theme(),
                CoreProps.BULLET_LIST_ITEM_LEVEL.require(props)
            )
        } else {
            BidiAwareOrderedListSpan(
                theme = configuration.theme(),
                number = CoreProps.ORDERED_LIST_ITEM_NUMBER.require(props)
            )
        }
    }
}

/**
 * Ordered-list marker that does not assume every document is LTR.
 *
 * Markwon 4.6.2 builds the default marker as an ASCII string. That works for
 * English but leaves Persian lists with Latin digits and punctuation on the
 * wrong visual side. This span determines the dominant strong direction of the
 * actual list-item text at draw time:
 *
 *  - Persian/Arabic-dominant item -> Persian digits and RTL punctuation
 *  - Latin-dominant item          -> Latin digits and normal LTR punctuation
 *
 * The Markdown source itself is never rewritten.
 */
private class BidiAwareOrderedListSpan(
    private val theme: MarkwonTheme,
    private val number: Int
) : LeadingMarginSpan {

    private val markerPaint = Paint()
    private var measuredMargin = 0

    override fun getLeadingMargin(first: Boolean): Int =
        maxOf(measuredMargin, theme.blockMargin)

    override fun drawLeadingMargin(
        canvas: Canvas,
        paint: Paint,
        x: Int,
        dir: Int,
        top: Int,
        baseline: Int,
        bottom: Int,
        text: CharSequence,
        start: Int,
        end: Int,
        first: Boolean,
        layout: Layout
    ) {
        if (!first || !LeadingMarginUtils.selfStart(start, text, this)) return

        markerPaint.set(paint)
        theme.applyListItemStyle(markerPaint)

        val rtl = isRtlDominantListItem(text, start, end)
        val digits = if (rtl) number.toPersianDigits() else number.toString()

        // Leading margins are drawn outside TextView's bidi paragraph engine.
        // For RTL the punctuation must therefore be physically placed before
        // the digits so it appears between the marker and the Persian text.
        val marker = if (rtl) {
            "\u00A0.$digits"
        } else {
            "$digits.\u00A0"
        }

        val markerWidth = (markerPaint.measureText(marker) + 0.5f).toInt()
        var width = theme.blockMargin
        if (markerWidth > width) {
            width = markerWidth
            measuredMargin = markerWidth
        } else {
            measuredMargin = 0
        }

        val left = if (dir > 0) {
            x + width - markerWidth
        } else {
            x - width + (width - markerWidth)
        }

        canvas.drawText(marker, left.toFloat(), baseline.toFloat(), markerPaint)
    }
}

private fun isRtlDominantListItem(
    text: CharSequence,
    start: Int,
    end: Int
): Boolean {
    var rtlScore = 0
    var ltrScore = 0
    var index = start.coerceAtLeast(0)
    val limit = minOf(end.coerceAtMost(text.length), index + 768)

    while (index < limit) {
        val codePoint = Character.codePointAt(text, index)
        if (codePoint == '\n'.code && index > start) break

        when (Character.getDirectionality(codePoint)) {
            Character.DIRECTIONALITY_RIGHT_TO_LEFT,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC -> rtlScore += 2

            Character.DIRECTIONALITY_LEFT_TO_RIGHT -> ltrScore += 1
        }

        index += Character.charCount(codePoint)
    }

    return rtlScore > 0 && rtlScore >= ltrScore
}

private fun Int.toPersianDigits(): String = buildString {
    this@toPersianDigits.toString().forEach { ch ->
        append(
            when (ch) {
                '0' -> '۰'
                '1' -> '۱'
                '2' -> '۲'
                '3' -> '۳'
                '4' -> '۴'
                '5' -> '۵'
                '6' -> '۶'
                '7' -> '۷'
                '8' -> '۸'
                '9' -> '۹'
                else -> ch
            }
        )
    }
}


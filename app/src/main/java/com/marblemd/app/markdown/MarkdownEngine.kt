package com.marblemd.app.markdown

import android.content.Context
import android.text.Spanned
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
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TableAwareMovementMethod
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.coil.CoilImagesPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import io.noties.markwon.movement.MovementMethodPlugin
import org.commonmark.node.Code
import org.commonmark.node.Heading
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

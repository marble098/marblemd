package com.marblemd.app.markdown

import android.content.Context
import android.text.Spanned
import android.widget.TextView
import com.bumptech.glide.Glide
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TableAwareMovementMethod
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.glide.GlideImagesPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import io.noties.markwon.movement.MovementMethodPlugin
import com.marblemd.app.text.FontRegistry
import com.marblemd.app.text.ScriptFontApplier

internal class MarkdownEngine(
    context: Context,
    textColor: Int,
    linkColor: Int,
    quoteColor: Int,
    codeBackgroundColor: Int
) {
    private val fonts = FontRegistry(context)
    private val markwon: Markwon = Markwon.builder(context)
        .usePlugin(HtmlPlugin.create())
        .usePlugin(TablePlugin.create(context))
        .usePlugin(StrikethroughPlugin.create())
        .usePlugin(TaskListPlugin.create(context))
        .usePlugin(LinkifyPlugin.create())
        .usePlugin(GlideImagesPlugin.create(Glide.with(context)))
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
                // Keep default CommonMark/GFM behavior; TextView supplies bidirectional layout.
            }
        })
        .build()

    fun render(markdown: String): Spanned = ScriptFontApplier.apply(markwon.toMarkdown(markdown), fonts)

    fun applyTo(textView: TextView, rendered: Spanned) {
        // setParsedMarkdown runs Markwon before/after TextView hooks, which are required by images/tables.
        markwon.setParsedMarkdown(textView, rendered)
    }
}

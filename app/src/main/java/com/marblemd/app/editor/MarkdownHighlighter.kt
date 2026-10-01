package com.marblemd.app.editor

/**
 * Markdown source highlighter for the editor.
 *
 * Works line by line so typing stays cheap even in large files, and it never
 * changes the text itself - only colours. Returns ranges so the caller can
 * build an `AnnotatedString`.
 */
object MarkdownHighlighter {

    enum class Token { HEADING, EMPHASIS, STRONG, CODE, LINK, URL, QUOTE, LIST, RULE, COMMENT }

    data class Range(val start: Int, val end: Int, val token: Token)

    private val heading = Regex("""^\s{0,3}#{1,6}\s.*$""")
    private val quote = Regex("""^\s{0,3}>.*$""")
    private val listItem = Regex("""^\s{0,8}([-*+]|\d{1,9}[.)])\s""")
    private val task = Regex("""^\s{0,8}[-*+]\s\[[ xX]]\s""")
    private val rule = Regex("""^\s{0,3}([-*_])(\s*\1){2,}\s*$""")
    private val fenced = Regex("""^\s{0,3}(```|~~~)""")
    private val inlineCode = Regex("""`[^`\n]+`""")
    private val strong = Regex("""\*\*[^*\n]+\*\*|__[^_\n]+__""")
    private val emphasis = Regex("""(?<![*\w])\*[^*\n]+\*(?!\*)|(?<![_\w])_[^_\n]+_(?!_)""")
    private val link = Regex("""!?\[[^\]\n]*]\([^)\n]*\)""")
    private val url = Regex("""(?:https?|mailto):[^\s)]+""")
    private val htmlComment = Regex("""<!--.*?-->""")

    /** Highlight up to this many characters; beyond it, plain text is used. */
    const val MAX_HIGHLIGHT_CHARS = 120_000

    fun highlight(text: String): List<Range> {
        if (text.isEmpty() || text.length > MAX_HIGHLIGHT_CHARS) return emptyList()

        val ranges = mutableListOf<Range>()
        var lineStart = 0
        var inFence = false

        while (lineStart <= text.length) {
            val newline = text.indexOf('\n', lineStart)
            val lineEnd = if (newline < 0) text.length else newline

            if (lineEnd > lineStart) {
                collectLine(text, lineStart, lineEnd, inFence, ranges)
                if (fenced.containsMatchIn(text.substring(lineStart, lineEnd))) inFence = !inFence
            }

            if (newline < 0) break
            lineStart = newline + 1
        }

        return ranges
    }

    private fun collectLine(
        text: String,
        start: Int,
        end: Int,
        inFence: Boolean,
        output: MutableList<Range>
    ) {
        val line = text.substring(start, end)

        if (inFence || fenced.containsMatchIn(line)) {
            output += Range(start, end, Token.CODE)
            return
        }
        if (rule.matches(line)) {
            output += Range(start, end, Token.RULE)
            return
        }

        heading.find(line)?.let { match ->
            output += Range(start + match.range.first, start + match.range.last + 1, Token.HEADING)
            // Headings keep their emphasis styling, so keep scanning inline.
        }
        quote.find(line)?.let { match ->
            output += Range(start + match.range.first, start + match.range.last + 1, Token.QUOTE)
        }
        task.find(line)?.let { match ->
            output += Range(start + match.range.first, start + match.range.last + 1, Token.LIST)
        } ?: listItem.find(line)?.let { match ->
            output += Range(start + match.range.first, start + match.range.last + 1, Token.LIST)
        }

        fun scan(regex: Regex, token: Token) {
            regex.findAll(line).forEach { match ->
                output += Range(start + match.range.first, start + match.range.last + 1, token)
            }
        }

        scan(htmlComment, Token.COMMENT)
        scan(link, Token.LINK)
        scan(inlineCode, Token.CODE)
        scan(strong, Token.STRONG)
        scan(emphasis, Token.EMPHASIS)
        scan(url, Token.URL)
    }
}

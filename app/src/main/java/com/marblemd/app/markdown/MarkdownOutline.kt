package com.marblemd.app.markdown

data class MarkdownHeading(
    val title: String,
    val level: Int,
    val lineIndex: Int
)

object MarkdownOutline {
    private val atx = Regex("""^\s{0,3}(#{1,6})\s+(.+?)\s*#*\s*$""")
    private val setext = Regex("""^\s{0,3}(=+|-+)\s*$""")
    private val fenceStart = Regex("""^\s{0,3}(`{3,}|~{3,}).*$""")

    fun parse(markdown: String): List<MarkdownHeading> {
        val lines = markdown.lines()
        if (lines.isEmpty()) return emptyList()

        val result = mutableListOf<MarkdownHeading>()
        var fenceMarker: Char? = null
        var fenceLength = 0
        var index = 0

        while (index < lines.size) {
            val line = lines[index]
            val trimmed = line.trimStart()

            val fence = fenceStart.matchEntire(line)
            if (fence != null) {
                val marker = fence.groupValues[1]
                if (fenceMarker == null) {
                    fenceMarker = marker.first()
                    fenceLength = marker.length
                } else if (
                    marker.first() == fenceMarker &&
                    marker.length >= fenceLength
                ) {
                    fenceMarker = null
                    fenceLength = 0
                }
                index++
                continue
            }

            if (fenceMarker != null) {
                index++
                continue
            }

            val atxMatch = atx.matchEntire(line)
            if (atxMatch != null) {
                val title = cleanTitle(atxMatch.groupValues[2])
                if (title.isNotBlank()) {
                    result += MarkdownHeading(
                        title = title,
                        level = atxMatch.groupValues[1].length,
                        lineIndex = index
                    )
                }
                index++
                continue
            }

            if (
                trimmed.isNotBlank() &&
                index + 1 < lines.size
            ) {
                val underline = setext.matchEntire(lines[index + 1])
                if (underline != null) {
                    val marker = underline.groupValues[1]
                    val title = cleanTitle(trimmed)
                    if (title.isNotBlank()) {
                        result += MarkdownHeading(
                            title = title,
                            level = if (marker.first() == '=') 1 else 2,
                            lineIndex = index
                        )
                    }
                    index += 2
                    continue
                }
            }

            index++
        }

        return result
    }

    private fun cleanTitle(raw: String): String =
        raw.trim()
            .removeSuffix("#")
            .trim()
            .replace(Regex("""\s+"""), " ")
}

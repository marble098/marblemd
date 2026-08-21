package com.marblemd.app.markdown

import org.commonmark.node.Node

data class MarkdownRenderBlock(
    val index: Int,
    val node: Node
)

data class MarkdownPlanHeading(
    val title: String,
    val level: Int,
    val blockIndex: Int
)

data class MarkdownRenderPlan(
    val revision: Long,
    val blocks: List<MarkdownRenderBlock>,
    val headings: List<MarkdownPlanHeading>
)

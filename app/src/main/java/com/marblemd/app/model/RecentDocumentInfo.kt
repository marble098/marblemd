package com.marblemd.app.model

/** A lightweight, persisted entry shown in the Recent Markdown list. */
data class RecentDocumentInfo(
    val id: String,
    val title: String,
    val uriString: String?,
    val writable: Boolean,
    val lastOpenedAt: Long,
    val position: ReadingPosition = ReadingPosition(),
    val hasUnsavedChanges: Boolean = false
)

/** Position of the first visible Markdown block and its pixel offset within that block. */
data class ReadingPosition(
    val firstVisibleItemIndex: Int = 0,
    val firstVisibleItemScrollOffset: Int = 0
) {
    fun normalized(): ReadingPosition = copy(
        firstVisibleItemIndex = firstVisibleItemIndex.coerceAtLeast(0),
        firstVisibleItemScrollOffset = firstVisibleItemScrollOffset.coerceAtLeast(0)
    )
}

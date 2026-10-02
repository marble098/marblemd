package com.marblemd.app.editor

/**
 * Undo/redo for the Markdown editor, free of Compose/Android types so it can be
 * unit tested. History entries are plain text snapshots with a caret position.
 */
class EditorHistory(
    private val limit: Int = 200,
    private val coalesceMillis: Long = 600L
) {
    data class Snapshot(val text: String, val selectionStart: Int, val selectionEnd: Int)

    private val undoStack = ArrayDeque<Snapshot>()
    private val redoStack = ArrayDeque<Snapshot>()
    private var lastRecordedAt = 0L

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun clear() {
        undoStack.clear()
        redoStack.clear()
        lastRecordedAt = 0L
    }

    /**
     * Records the state *before* it was replaced. Rapid keystrokes within
     * [coalesceMillis] fold into a single undo step, which is what users expect
     * from a text editor.
     */
    fun record(previous: Snapshot, now: Long = System.currentTimeMillis()) {
        if (undoStack.lastOrNull()?.text == previous.text) return

        val coalesce = now - lastRecordedAt < coalesceMillis && undoStack.isNotEmpty()
        lastRecordedAt = now
        if (coalesce) {
            redoStack.clear()
            return
        }
        undoStack.addLast(previous)
        while (undoStack.size > limit) undoStack.removeFirst()
        redoStack.clear()
    }

    /** Returns the state to show, or `null` when there is nothing to restore. */
    fun undo(current: Snapshot): Snapshot? {
        val previous = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(current)
        lastRecordedAt = 0L
        return previous
    }

    fun redo(current: Snapshot): Snapshot? {
        val next = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(current)
        lastRecordedAt = 0L
        return next
    }
}

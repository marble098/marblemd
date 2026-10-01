package com.marblemd.app.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorHistoryTest {

    private fun snapshot(text: String, caret: Int = text.length) =
        EditorHistory.Snapshot(text = text, selectionStart = caret, selectionEnd = caret)

    @Test
    fun undoAndRedoWalkThroughRecordedStates() {
        val history = EditorHistory()
        // Each record stores the text as it was *before* that edit.
        history.record(snapshot(""), now = 1_000)
        history.record(snapshot("a"), now = 5_000)
        history.record(snapshot("ab"), now = 9_000)

        assertTrue(history.canUndo)
        assertEquals("ab", history.undo(snapshot("abc"))?.text)
        assertEquals("a", history.undo(snapshot("ab"))?.text)
        assertEquals("", history.undo(snapshot("a"))?.text)
        assertNull(history.undo(snapshot("")))

        assertTrue(history.canRedo)
        assertEquals("a", history.redo(snapshot(""))?.text)
        assertEquals("ab", history.redo(snapshot("a"))?.text)
        assertEquals("abc", history.redo(snapshot("ab"))?.text)
        assertNull(history.redo(snapshot("abc")))
    }

    @Test
    fun rapidTypingFoldsIntoASingleUndoStep() {
        val history = EditorHistory(coalesceMillis = 600)
        history.record(snapshot(""), now = 1_000)
        history.record(snapshot("h"), now = 1_100)
        history.record(snapshot("he"), now = 1_200)
        history.record(snapshot("hel"), now = 1_300)

        assertEquals("", history.undo(snapshot("hel"))?.text)
        assertFalse(history.canUndo)
    }

    @Test
    fun typingAfterTheCoalesceWindowCreatesASecondStep() {
        val history = EditorHistory(coalesceMillis = 600)
        history.record(snapshot(""), now = 1_000)
        history.record(snapshot("hello"), now = 2_000)

        assertEquals("hello", history.undo(snapshot("hello world"))?.text)
        assertEquals("", history.undo(snapshot("hello"))?.text)
    }

    @Test
    fun recordingTheSameTextTwiceIsIgnored() {
        val history = EditorHistory()
        history.record(snapshot("same"), now = 1_000)
        history.record(snapshot("same"), now = 9_000)
        assertEquals("same", history.undo(snapshot("changed"))?.text)
        assertFalse(history.canUndo)
    }

    @Test
    fun historyIsTrimmedToItsLimit() {
        val history = EditorHistory(limit = 3, coalesceMillis = 0)
        repeat(10) { index ->
            history.record(snapshot("v$index"), now = index * 1_000L)
        }

        var current = snapshot("v9")
        var steps = 0
        while (true) {
            val previous = history.undo(current) ?: break
            current = previous
            steps++
        }
        assertEquals(3, steps)
    }

    @Test
    fun clearForgetsEverything() {
        val history = EditorHistory()
        history.record(snapshot("a"))
        history.clear()
        assertFalse(history.canUndo)
        assertFalse(history.canRedo)
    }
}

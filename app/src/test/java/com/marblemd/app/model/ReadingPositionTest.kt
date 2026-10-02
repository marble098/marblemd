package com.marblemd.app.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingPositionTest {
    @Test
    fun normalizeClampsNegativeListOffsets() {
        val normalized = ReadingPosition(
            firstVisibleItemIndex = -4,
            firstVisibleItemScrollOffset = -12
        ).normalized()

        assertEquals(ReadingPosition(0, 0), normalized)
    }

    @Test
    fun normalizeKeepsAValidReadingPosition() {
        val position = ReadingPosition(firstVisibleItemIndex = 8, firstVisibleItemScrollOffset = 132)

        assertEquals(position, position.normalized())
    }
}

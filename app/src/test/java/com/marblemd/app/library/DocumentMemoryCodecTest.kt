package com.marblemd.app.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentMemoryCodecTest {

    @Test
    fun recentDocumentSurvivesTabsNewlinesAndBackslashes() {
        val original = RecentDocument(
            uri = "content://com.example.docs/document/42",
            title = "یادداشت\t۱",
            lastOpenedAt = 1_726_000_000_000L,
            scrollIndex = 37,
            writable = true,
            charCount = 4821,
            snippet = "خط اول\nخط دوم با \\ بک‌اسلش"
        )

        val encoded = DocumentMemoryCodec.encodeRecords(listOf(original.toRecord()))
        val decoded = DocumentMemoryCodec.decodeRecords(encoded).single()

        assertEquals(original, RecentDocument.fromRecord(decoded))
    }

    @Test
    fun sessionDocumentKeepsMarkdownContent() {
        val original = SessionDocument(
            id = "tab-1",
            title = "README.md",
            uri = null,
            content = "# Title\n\n| a | b |\n| --- | --- |\n\n```kt\nval x = 1\n```\n",
            writable = false,
            scrollIndex = 5,
            active = true
        )

        val encoded = DocumentMemoryCodec.encodeRecords(listOf(original.toRecord()))
        val decoded = DocumentMemoryCodec.decodeRecords(encoded).single()

        assertEquals(original, SessionDocument.fromRecord(decoded))
    }

    @Test
    fun missingOrForeignPayloadDecodesToEmpty() {
        assertEquals(emptyList<List<String>>(), DocumentMemoryCodec.decodeRecords(null))
        assertEquals(emptyList<List<String>>(), DocumentMemoryCodec.decodeRecords(""))
        assertEquals(emptyList<List<String>>(), DocumentMemoryCodec.decodeRecords("v0\nfoo\tbar"))
        assertEquals(emptyList<List<String>>(), DocumentMemoryCodec.decodeRecords("random text"))
    }

    @Test
    fun malformedRecordsAreDroppedButValidOnesSurvive() {
        val encoded = DocumentMemoryCodec.encodeRecords(
            listOf(
                listOf("content://a", "A.md", "1", "0", "1", "10", "snippet"),
                listOf("", "Broken", "1")
            )
        )

        val decoded = DocumentMemoryCodec.decodeRecords(encoded).mapNotNull(RecentDocument::fromRecord)

        assertEquals(1, decoded.size)
        assertEquals("A.md", decoded.single().title)
        assertTrue(decoded.single().writable)
    }

    @Test
    fun recordWithTooFewFieldsIsRejected() {
        assertNull(RecentDocument.fromRecord(listOf("uri", "title")))
        assertNull(SessionDocument.fromRecord(listOf("id")))
    }
}

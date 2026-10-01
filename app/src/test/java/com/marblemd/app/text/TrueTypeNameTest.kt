package com.marblemd.app.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class TrueTypeNameTest {

    @Test
    fun rejectsFilesThatAreNotFonts() {
        assertFalse(TrueTypeName.looksLikeFont(ByteArray(0)))
        assertFalse(TrueTypeName.looksLikeFont("<!doctype html><html>error</html>".toByteArray()))
        assertFalse(TrueTypeName.looksLikeFont(ByteArray(64) { 0x7F }))
        assertNull(TrueTypeName.familyName("not a font at all".toByteArray()))
    }

    @Test
    fun readsTheFamilyNameFromANameTable() {
        val font = buildFont(
            listOf(
                NameRecord(platformId = 3, encodingId = 1, nameId = 1, value = "Test Family"),
                NameRecord(platformId = 3, encodingId = 1, nameId = 4, value = "Test Family Regular")
            )
        )

        assertTrue(TrueTypeName.looksLikeFont(font))
        assertEquals("Test Family", TrueTypeName.familyName(font))
    }

    @Test
    fun prefersTheTypographicFamilyWhenPresent() {
        val font = buildFont(
            listOf(
                NameRecord(3, 1, nameId = 1, value = "Legacy Name"),
                NameRecord(3, 1, nameId = 16, value = "Preferred Name")
            )
        )

        assertEquals("Preferred Name", TrueTypeName.familyName(font))
    }

    @Test
    fun readsNamesWhoseStringOffsetExceedsOneByte() {
        val font = buildFont(
            listOf(
                NameRecord(3, 1, nameId = 1, value = "Legacy family ".repeat(30)),
                NameRecord(3, 1, nameId = 16, value = "Preferred Family")
            )
        )

        assertEquals("Preferred Family", TrueTypeName.familyName(font))
    }

    @Test
    fun malformedNameTableDoesNotCrash() {
        val font = buildFont(
            listOf(NameRecord(3, 1, nameId = 1, value = "Broken")),
            declaredCountOverride = 250
        )

        assertTrue(TrueTypeName.looksLikeFont(font))
        // Out-of-range records must be skipped instead of throwing.
        TrueTypeName.familyName(font)
    }

    private data class NameRecord(
        val platformId: Int,
        val encodingId: Int,
        val nameId: Int,
        val value: String
    )

    /** Builds the smallest valid SFNT file carrying a `name` table. */
    private fun buildFont(
        records: List<NameRecord>,
        declaredCountOverride: Int? = null
    ): ByteArray {
        val strings = ByteArrayOutputStream()
        val stringOffsets = mutableListOf<Int>()
        records.forEach { record ->
            stringOffsets += strings.size()
            record.value.forEach { character ->
                strings.write((character.code shr 8) and 0xFF)
                strings.write(character.code and 0xFF)
            }
        }

        val count = declaredCountOverride ?: records.size
        val nameTableSize = 6 + count * 12 + strings.size()
        val nameTableOffset = 12 + 16

        val output = ByteArrayOutputStream()
        fun writeUInt16(value: Int) {
            output.write((value shr 8) and 0xFF)
            output.write(value and 0xFF)
        }

        fun writeUInt32(value: Int) {
            output.write((value shr 24) and 0xFF)
            output.write((value shr 16) and 0xFF)
            output.write((value shr 8) and 0xFF)
            output.write(value and 0xFF)
        }

        writeUInt32(0x00010000) // sfntVersion
        writeUInt16(1) // numTables
        writeUInt16(0) // searchRange
        writeUInt16(0) // entrySelector
        writeUInt16(0) // rangeShift

        writeUInt32(0x6E616D65) // 'name'
        writeUInt32(0) // checksum
        writeUInt32(nameTableOffset)
        writeUInt32(nameTableSize)

        writeUInt16(0) // format
        writeUInt16(count)
        writeUInt16(6 + count * 12) // stringOffset

        repeat(count) { index ->
            val record = records.getOrNull(index)
            writeUInt16(record?.platformId ?: 0)
            writeUInt16(record?.encodingId ?: 0)
            writeUInt16(0x0409)
            writeUInt16(record?.nameId ?: 0)
            val bytes = record?.value?.length?.times(2) ?: 0
            writeUInt16(if (index < stringOffsets.size) bytes else 0)
            writeUInt16(stringOffsets.getOrElse(index) { 0 })
        }

        output.write(strings.toByteArray())
        return output.toByteArray()
    }
}

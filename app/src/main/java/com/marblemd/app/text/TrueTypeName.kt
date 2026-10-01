package com.marblemd.app.text

/**
 * Minimal SFNT (TrueType / OpenType) reader.
 *
 * MarbleMD only needs two things from a user supplied font file: is it a real
 * font, and what is it called. Parsing just the `name` table keeps this tiny
 * and dependency free, and it is pure Kotlin so it can be unit tested.
 */
internal object TrueTypeName {

    /** Recognised SFNT signatures. */
    private val SIGNATURES = setOf(0x00010000, 0x4F54544F, 0x74727565, 0x74746366)

    fun looksLikeFont(data: ByteArray): Boolean {
        if (data.size < 12) return false
        val signature = readUInt32(data, 0) ?: return false
        return signature in SIGNATURES
    }

    /**
     * Best-effort family name. Prefers the typographic family (nameID 16) and
     * falls back to the legacy family (nameID 1), then the full name (nameID 4).
     */
    fun familyName(data: ByteArray): String? {
        if (!looksLikeFont(data)) return null
        if (readUInt32(data, 0) == 0x74746366) return null // TTC: use the file name

        val tableCount = readUInt16(data, 4) ?: return null
        var offset = 12
        var nameTable: Int? = null
        repeat(tableCount) {
            val tag = readUInt32(data, offset) ?: return null
            if (tag == 0x6E616D65) { // 'name'
                nameTable = readUInt32(data, offset + 8)
                return@repeat
            }
            offset += 16
        }
        val base = nameTable ?: return null

        val count = readUInt16(data, base + 2) ?: return null
        val stringOffset = readUInt16(data, base + 4) ?: return null
        val storage = base + stringOffset

        val candidates = sortedMapOf<Int, String>()
        for (index in 0 until count) {
            val record = base + 6 + index * 12
            val platformId = readUInt16(data, record) ?: continue
            val encodingId = readUInt16(data, record + 2) ?: continue
            val nameId = readUInt16(data, record + 6) ?: continue
            if (nameId !in 1..16) continue
            val length = readUInt16(data, record + 8) ?: continue
            // SFNT name records are 12 bytes; their string offset is a uint16.
            val valueOffset = readUInt16(data, record + 10) ?: continue
            val start = storage + valueOffset
            if (start < 0 || length <= 0 || start + length > data.size) continue

            val decoderIsUnicode = platformId == 0 || (platformId == 3 && encodingId in 0..10)
            val decoded: String? = when {
                decoderIsUnicode -> decodeUtf16Be(data, start, length)
                platformId == 1 -> decodeMacRoman(data, start, length)
                else -> null
            }
            val cleaned = decoded?.trim() ?: continue
            if (cleaned.isEmpty() || cleaned.any { it.isISOControl() }) continue

            // Later (higher nameId priority is handled below) - keep the first
            // readable candidate per id, preferring Unicode platforms.
            val priority = when (nameId) {
                16 -> 0
                1 -> if (platformId == 3) 1 else 2
                4 -> 3
                else -> 4
            }
            val existing = candidates[priority]
            if (existing == null || platformId == 3) candidates[priority] = cleaned
        }

        return candidates.values.firstOrNull()
    }

    private fun decodeUtf16Be(data: ByteArray, start: Int, length: Int): String? {
        val builder = StringBuilder()
        var index = start
        val end = start + length - 1
        while (index < end) {
            val unit = ((data[index].toInt() and 0xFF) shl 8) or (data[index + 1].toInt() and 0xFF)
            builder.append(unit.toChar())
            index += 2
        }
        return builder.toString()
    }

    private fun decodeMacRoman(data: ByteArray, start: Int, length: Int): String {
        val builder = StringBuilder()
        for (index in start until start + length) {
            val value = data[index].toInt() and 0xFF
            builder.append(if (value < 0x80) value.toChar() else MAC_ROMAN[value - 0x80])
        }
        return builder.toString()
    }

    private fun readUInt16(data: ByteArray, offset: Int): Int? {
        if (offset < 0 || offset + 2 > data.size) return null
        return ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
    }

    private fun readUInt32(data: ByteArray, offset: Int): Int? {
        if (offset < 0 || offset + 4 > data.size) return null
        return ((data[offset].toInt() and 0xFF) shl 24) or
            ((data[offset + 1].toInt() and 0xFF) shl 16) or
            ((data[offset + 2].toInt() and 0xFF) shl 8) or
            (data[offset + 3].toInt() and 0xFF)
    }

    private val MAC_ROMAN = charArrayOf(
        'Ä', 'Å', 'Ç', 'É', 'Ñ', 'Ö', 'Ü', 'á', 'à', 'â', 'ä', 'ã', 'å', 'ç', 'é', 'è',
        'ê', 'ë', 'í', 'ì', 'î', 'ï', 'ñ', 'ó', 'ò', 'ô', 'ö', 'õ', 'ú', 'ù', 'û', 'ü',
        '†', '°', '¢', '£', '§', '•', '¶', 'ß', '®', '©', '™', '´', '¨', '≠', 'Æ', 'Ø',
        '∞', '±', '≤', '≥', '¥', 'µ', '∂', '∑', '∏', 'π', '∫', 'ª', 'º', 'Ω', 'æ', 'ø',
        '¿', '¡', '¬', '√', 'ƒ', '≈', '∆', '«', '»', '…', '\u00A0', 'À', 'Ã', 'Õ', 'Œ', 'œ',
        '–', '—', '“', '”', '‘', '’', '÷', '◊', 'ÿ', 'Ÿ', '⁄', '€', '‹', '›', 'ﬁ', 'ﬂ',
        '‡', '·', '‚', '„', '‰', 'Â', 'Ê', 'Á', 'Ë', 'È', 'Í', 'Î', 'Ï', 'Ì', 'Ó', 'Ô',
        '\uF8FF', 'Ò', 'Ú', 'Û', 'Ù', 'ı', 'ˆ', '˜', '¯', '˘', '˙', '˚', '¸', '˝', '˛', 'ˇ'
    )
}

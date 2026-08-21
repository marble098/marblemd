package com.marblemd.app.text

internal enum class FontScript { ARABIC, GOOGLE_SANS, SYSTEM }

internal object UnicodeScript {
    fun classify(codePoint: Int): FontScript {
        val script = Character.UnicodeScript.of(codePoint)
        return when (script) {
            Character.UnicodeScript.ARABIC -> FontScript.ARABIC
            Character.UnicodeScript.LATIN,
            Character.UnicodeScript.GREEK,
            Character.UnicodeScript.CYRILLIC -> FontScript.GOOGLE_SANS
            else -> FontScript.SYSTEM
        }
    }
}

package com.marblemd.app.text

import org.junit.Assert.assertEquals
import org.junit.Test

class UnicodeScriptTest {
    @Test fun persianUsesVazirmatnBucket() {
        assertEquals(FontScript.ARABIC, UnicodeScript.classify('م'.code))
    }

    @Test fun englishUsesGoogleSansBucket() {
        assertEquals(FontScript.GOOGLE_SANS, UnicodeScript.classify('A'.code))
    }

    @Test fun japaneseFallsBackToSystemNotoStack() {
        assertEquals(FontScript.SYSTEM, UnicodeScript.classify('日'.code))
    }
}

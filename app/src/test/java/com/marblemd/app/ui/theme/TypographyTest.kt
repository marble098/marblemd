package com.marblemd.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import org.junit.Assert.assertEquals
import org.junit.Test

class TypographyTest {

    @Test
    fun customFontAppliesToEveryStyleWithoutChangingOtherAttributes() {
        val original = Typography()
        val family = FontFamily.Monospace
        val custom = original.withFontFamily(family)

        styles(original).zip(styles(custom)).forEach { (before, after) ->
            assertEquals(family, after.fontFamily)
            assertEquals(before.copy(fontFamily = family), after)
        }
    }

    @Test
    fun changingFontsReplacesThePreviousFamilyInEveryStyle() {
        val original = Typography()
        val custom = original.withFontFamily(FontFamily.Monospace).withFontFamily(FontFamily.Serif)

        styles(original).zip(styles(custom)).forEach { (before, after) ->
            assertEquals(before.copy(fontFamily = FontFamily.Serif), after)
        }
    }

    private fun styles(typography: Typography): List<TextStyle> = with(typography) {
        listOf(
            displayLarge, displayMedium, displaySmall,
            headlineLarge, headlineMedium, headlineSmall,
            titleLarge, titleMedium, titleSmall,
            bodyLarge, bodyMedium, bodySmall,
            labelLarge, labelMedium, labelSmall
        )
    }
}

package com.marblemd.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

private val Light = lightColorScheme(
    primary = Color(0xFF3B4EE0),
    secondary = Color(0xFF006B5F),
    tertiary = Color(0xFF8A4E00),
    background = Color(0xFFF9F9FF),
    surface = Color(0xFFF9F9FF),
    surfaceVariant = Color(0xFFE5E7F3)
)

private val Dark = darkColorScheme(
    primary = Color(0xFFBBC3FF),
    secondary = Color(0xFF7DDBCA),
    tertiary = Color(0xFFFFB86A),
    background = Color(0xFF11131B),
    surface = Color(0xFF11131B),
    surfaceVariant = Color(0xFF252833)
)

/** Applies a custom font without changing Material 3's type sizes or weights. */
internal fun Typography.withFontFamily(fontFamily: FontFamily): Typography = copy(
    displayLarge = displayLarge.copy(fontFamily = fontFamily),
    displayMedium = displayMedium.copy(fontFamily = fontFamily),
    displaySmall = displaySmall.copy(fontFamily = fontFamily),
    headlineLarge = headlineLarge.copy(fontFamily = fontFamily),
    headlineMedium = headlineMedium.copy(fontFamily = fontFamily),
    headlineSmall = headlineSmall.copy(fontFamily = fontFamily),
    titleLarge = titleLarge.copy(fontFamily = fontFamily),
    titleMedium = titleMedium.copy(fontFamily = fontFamily),
    titleSmall = titleSmall.copy(fontFamily = fontFamily),
    bodyLarge = bodyLarge.copy(fontFamily = fontFamily),
    bodyMedium = bodyMedium.copy(fontFamily = fontFamily),
    bodySmall = bodySmall.copy(fontFamily = fontFamily),
    labelLarge = labelLarge.copy(fontFamily = fontFamily),
    labelMedium = labelMedium.copy(fontFamily = fontFamily),
    labelSmall = labelSmall.copy(fontFamily = fontFamily)
)

/**
 * MarbleMD theme.
 *
 * [typography] lets the user apply an imported `.ttf` to the whole interface;
 * when it is `null` the Material defaults are used.
 */
@Composable
fun MarbleMDTheme(
    typography: Typography? = null,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = typography ?: Typography(),
        content = content
    )
}

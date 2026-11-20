package com.example.sortmate.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = Teal,
    onPrimary = Sand,
    primaryContainer = ColorTokens.tealContainerLight,
    onPrimaryContainer = TealDark,
    secondary = Coral,
    onSecondary = Sand,
    secondaryContainer = CoralSoft,
    onSecondaryContainer = ColorTokens.coralInk,
    background = Sand,
    onBackground = Ink,
    surface = Sand,
    onSurface = Ink,
    surfaceVariant = SandDeep,
    onSurfaceVariant = InkMuted,
    outline = ColorTokens.outlineLight,
    error = Danger,
)

private val DarkColors = darkColorScheme(
    primary = TealLight,
    onPrimary = TealDark,
    primaryContainer = TealDark,
    onPrimaryContainer = ColorTokens.tealContainerDarkText,
    secondary = Coral,
    onSecondary = Night,
    secondaryContainer = ColorTokens.coralContainerDark,
    onSecondaryContainer = CoralSoft,
    background = Night,
    onBackground = Sand,
    surface = Night,
    onSurface = Sand,
    surfaceVariant = NightElevated,
    onSurfaceVariant = NightMuted,
    outline = ColorTokens.outlineDark,
    error = ColorTokens.errorDark,
)

private object ColorTokens {
    val tealContainerLight = androidx.compose.ui.graphics.Color(0xFFCDEDE7)
    val tealContainerDarkText = androidx.compose.ui.graphics.Color(0xFF9FE3DA)
    val coralInk = androidx.compose.ui.graphics.Color(0xFF5B2500)
    val coralContainerDark = androidx.compose.ui.graphics.Color(0xFF5A2D0C)
    val outlineLight = androidx.compose.ui.graphics.Color(0xFFBCC9C5)
    val outlineDark = androidx.compose.ui.graphics.Color(0xFF3B4F4B)
    val errorDark = androidx.compose.ui.graphics.Color(0xFFFFB4AB)
}

@Composable
fun SortMateTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = SortMateTypography,
        content = content,
    )
}

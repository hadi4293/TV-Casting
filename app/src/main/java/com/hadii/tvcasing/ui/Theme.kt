package com.hadii.tvcasing.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val GlassDark = darkColorScheme(
    primary = Color(0xFF5AC8FA),
    onPrimary = Color(0xFF001F2E),
    secondary = Color(0xFF64D2FF),
    background = Color(0xFF0A0A12),
    surface = Color(0x99000000),
    onBackground = Color(0xFFFFFFFF),
    onSurface = Color(0xFFFFFFFF),
)

private val GlassLight = lightColorScheme(
    primary = Color(0xFF007AFF),
    background = Color(0xFFF2F2F7),
    surface = Color(0xCCFFFFFF),
    onBackground = Color(0xFF000000),
    onSurface = Color(0xFF000000),
)

@Composable
fun GlassTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val scheme = if (darkTheme) GlassDark else GlassLight
    MaterialTheme(colorScheme = scheme, content = content)
}

package com.divium.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFE9A283),
    onPrimary = Color(0xFF2B1710),
    background = Color(0xFF11110F),
    onBackground = Color(0xFFECE8DF),
    surface = Color(0xFF191917),
    onSurface = Color(0xFFECE8DF),
    surfaceVariant = Color(0xFF24231F),
    onSurfaceVariant = Color(0xFFC9C4BA),
    outline = Color(0xFF777168),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF934B34),
    onPrimary = Color.White,
    background = Color(0xFFF8F5EF),
    onBackground = Color(0xFF1D1C19),
    surface = Color(0xFFFFFCF7),
    onSurface = Color(0xFF1D1C19),
    surfaceVariant = Color(0xFFEDE7DD),
    onSurfaceVariant = Color(0xFF514C45),
    outline = Color(0xFF847C72),
)

@Composable
fun DiviumTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = MaterialTheme.typography,
        content = content,
    )
}

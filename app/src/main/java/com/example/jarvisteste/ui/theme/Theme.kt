package com.example.jarvisteste.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * O app é sempre escuro: o visual "quase TRON" depende de um fundo quase preto
 * com linhas e brilhos em ciano neon. Por isso a cor dinâmica do Material You
 * fica desligada e o esquema claro não é usado.
 */
private val TronColorScheme = darkColorScheme(
    primary = TronCyan,
    onPrimary = TronInk,
    primaryContainer = TronSurfaceVariant,
    onPrimaryContainer = TronCyanSoft,
    secondary = TronTeal,
    onSecondary = TronInk,
    tertiary = TronBlue,
    onTertiary = Color.White,
    background = TronBlack,
    onBackground = Color(0xFFE8FBFF),
    surface = TronSurface,
    onSurface = Color(0xFFE8FBFF),
    surfaceVariant = TronSurfaceVariant,
    onSurfaceVariant = Color(0xFF9FD8E8),
    outline = TronOutline,
    error = TronPink,
    onError = TronInk
)

@Composable
fun JarvisTesteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TronColorScheme,
        typography = Typography,
        content = content
    )
}

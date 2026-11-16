package com.example.jarvisteste.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Paleta "quase TRON": fundo quase preto, linhas e brilhos em ciano neon e
 * rótulos em caixa alta. Substitui as cores antigas (azul "água") por algo mais
 * elétrico, sem perder o contraste para leitura.
 */
object JarvisColors {
    val BackgroundTop = Color(0xFF02070C)
    val BackgroundMid = Color(0xFF020609)
    val BackgroundBottom = Color(0xFF000000)
    val Surface = Color(0xFF06121A)
    val SurfaceAlt = Color(0xFF050E15)
    val SurfaceSelected = Color(0xFF0A2A38)
    val Border = Color(0xFF0E3A4C)
    val BorderSelected = Color(0xFF2BE7FF)
    val Accent = Color(0xFF2BE7FF)
    val AccentSoft = Color(0xFFB8F6FF)
    val Glow = Color(0xFF00D4FF)
    val Grid = Color(0xFF0B5468)
    val TextPrimary = Color(0xFFE8FBFF)
    val TextSecondary = Color(0xFF9FD8E8)
    val TextMuted = Color(0xFF5E8CA0)
    val Success = Color(0xFF39FFD0)
    val SuccessSurface = Color(0xFF04302A)
    val Danger = Color(0xFFFF5C7A)
    val DangerSurface = Color(0xFF2A0713)
}

@Composable
fun JarvisBackground(content: @Composable BoxScope.() -> Unit) {
    val gradient = Brush.verticalGradient(
        colors = listOf(
            JarvisColors.BackgroundTop,
            JarvisColors.BackgroundMid,
            JarvisColors.BackgroundBottom
        )
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradient)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) { drawTronGrid() }
        content()
    }
}

/** Grade neon + brilho no topo: a marca registrada do visual TRON. */
private fun DrawScope.drawTronGrid() {
    val step = 46.dp.toPx()
    val line = JarvisColors.Grid.copy(alpha = 0.42f)

    var x = 0f
    while (x <= size.width) {
        drawLine(line, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
        x += step
    }
    var y = 0f
    while (y <= size.height) {
        drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        y += step
    }

    drawRect(
        Brush.radialGradient(
            colors = listOf(
                JarvisColors.Glow.copy(alpha = 0.22f),
                JarvisColors.Glow.copy(alpha = 0.05f),
                Color.Transparent
            ),
            center = Offset(size.width / 2f, size.height * 0.16f),
            radius = size.maxDimension * 0.75f
        )
    )
}

@Composable
fun JarvisLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = JarvisColors.Accent,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.5.sp
    )
}

@Composable
fun JarvisPrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        enabled = enabled,
        shape = RoundedCornerShape(6.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = JarvisColors.Accent,
            contentColor = Color(0xFF00151C),
            disabledContainerColor = Color(0xFF0B2733),
            disabledContentColor = JarvisColors.TextMuted
        )
    ) {
        Text(
            text = text.uppercase(),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )
    }
}

@Composable
fun JarvisGhostButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    TextButton(onClick = onClick, modifier = modifier, enabled = enabled) {
        Text(
            text = text.uppercase(),
            color = if (enabled) JarvisColors.Accent else JarvisColors.TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )
    }
}

@Composable
fun SelectableCard(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(6.dp),
        color = if (selected) JarvisColors.SurfaceSelected else JarvisColors.SurfaceAlt,
        border = BorderStroke(
            1.dp,
            if (selected) JarvisColors.BorderSelected else JarvisColors.Border
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
            content = content
        )
    }
}

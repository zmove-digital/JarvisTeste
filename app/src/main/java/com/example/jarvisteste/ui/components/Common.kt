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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

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
    /** Tons extraídos da arte HUD: linhas de instrumento e realces. */
    val HudLine = Color(0xFF00788C)
    val HudBright = Color(0xFF00E0F8)
    val TextPrimary = Color(0xFFE8FBFF)
    val TextSecondary = Color(0xFF9FD8E8)
    val TextMuted = Color(0xFF5E8CA0)
    val Success = Color(0xFF39FFD0)
    val SuccessSurface = Color(0xFF04302A)
    val Danger = Color(0xFFFF5C7A)
    val DangerSurface = Color(0xFF2A0713)
    /** Vermelho intenso usado quando o Jarvis está falando. */
    val DangerStrong = Color(0xFFFF173F)
    val DangerStrongSurface = Color(0xFF5E0018)
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
    Box(modifier = Modifier.fillMaxSize().background(gradient)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawTronGrid()
            drawHudFrame()
        }
        content()
    }
}

/** Grade apagada e brilho central: a base do painel. */
private fun DrawScope.drawTronGrid() {
    val step = 46.dp.toPx()
    val line = JarvisColors.Grid.copy(alpha = 0.11f)

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
                JarvisColors.Glow.copy(alpha = 0.09f),
                JarvisColors.Glow.copy(alpha = 0.02f),
                Color.Transparent
            ),
            center = Offset(size.width / 2f, size.height * 0.38f),
            radius = size.maxDimension * 0.62f
        )
    )
}

/**
 * Moldura de instrumento inspirada na arte HUD: cantos recortados, trilhos
 * superior/inferior e marcas laterais. É desenhada em Compose, então acompanha
 * qualquer tamanho de tela sem distorção.
 */
private fun DrawScope.drawHudFrame() {
    val inset = 12.dp.toPx()
    val length = 30.dp.toPx()
    val line = JarvisColors.HudLine.copy(alpha = 0.55f)
    val faint = JarvisColors.HudLine.copy(alpha = 0.25f)
    val w = size.width
    val h = size.height

    // Cantos em "L".
    listOf(
        Offset(inset, inset) to Pair(1, 1),
        Offset(w - inset, inset) to Pair(-1, 1),
        Offset(inset, h - inset) to Pair(1, -1),
        Offset(w - inset, h - inset) to Pair(-1, -1)
    ).forEach { (corner, dir) ->
        drawLine(line, corner, Offset(corner.x + length * dir.first, corner.y), 1.5f)
        drawLine(line, corner, Offset(corner.x, corner.y + length * dir.second), 1.5f)
    }

    // Trilhos com marcas curtas, como régua de instrumento.
    val railTop = 58.dp.toPx()
    val railBottom = h - 64.dp.toPx()
    drawLine(faint, Offset(inset + length, railTop), Offset(w - inset - length, railTop), 1f)
    drawLine(faint, Offset(inset + length, railBottom), Offset(w - inset - length, railBottom), 1f)

    var x = inset + length + 10.dp.toPx()
    val gap = 18.dp.toPx()
    while (x < w - inset - length) {
        drawLine(faint, Offset(x, railTop - 3.dp.toPx()), Offset(x, railTop + 3.dp.toPx()), 1f)
        drawLine(faint, Offset(x, railBottom - 3.dp.toPx()), Offset(x, railBottom + 3.dp.toPx()), 1f)
        x += gap
    }

    // Marcas laterais em três alturas.
    listOf(h * 0.30f, h * 0.5f, h * 0.7f).forEach { y ->
        drawLine(faint, Offset(inset, y - 8.dp.toPx()), Offset(inset, y + 8.dp.toPx()), 1f)
        drawLine(faint, Offset(w - inset, y - 8.dp.toPx()), Offset(w - inset, y + 8.dp.toPx()), 1f)
    }
}

/** Retículo circular ao redor do orbe: anéis tracejados e marcas radiais. */
@Composable
fun HudReticle(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val radius = minOf(size.width, size.height) / 2f - 2.dp.toPx()
        val center = Offset(size.width / 2f, size.height / 2f)
        val ring = JarvisColors.HudLine.copy(alpha = 0.35f)
        val dash = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 9.dp.toPx()))

        drawCircle(
            center = center,
            radius = radius,
            color = ring.copy(alpha = 0.22f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f)
        )
        drawCircle(
            center = center,
            radius = radius * 0.82f,
            color = ring,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f, pathEffect = dash)
        )
        drawCircle(
            center = center,
            radius = radius * 0.62f,
            color = ring.copy(alpha = 0.5f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f)
        )

        for (index in 0 until 24) {
            val angle = index * Math.PI / 12
            val long = index % 6 == 0
            val inner = radius * (if (long) 0.88f else 0.94f)
            drawLine(
                color = ring.copy(alpha = if (long) 0.8f else 0.4f),
                start = Offset(center.x + cos(angle).toFloat() * inner, center.y + sin(angle).toFloat() * inner),
                end = Offset(center.x + cos(angle).toFloat() * radius, center.y + sin(angle).toFloat() * radius),
                strokeWidth = if (long) 1.5f else 1f
            )
        }
    }
}

/** Faixa de dados com segmentos, como a leitura de instrumentos da arte. */
@Composable
fun HudDataStrip(modifier: Modifier = Modifier, filled: Int = 4, total: Int = 7) {
    Canvas(modifier = modifier) {
        val gap = 4.dp.toPx()
        val width = (size.width - gap * (total - 1)) / total
        for (index in 0 until total) {
            val x = index * (width + gap)
            val active = index < filled
            drawRect(
                color = if (active) JarvisColors.HudBright.copy(alpha = 0.8f)
                else JarvisColors.HudLine.copy(alpha = 0.28f),
                topLeft = Offset(x, if (active) 0f else size.height * 0.25f),
                size = androidx.compose.ui.geometry.Size(width, if (active) size.height else size.height * 0.5f)
            )
        }
    }
}

/** Painel com canto cortado e cabeçalho em fita, no estilo da arte HUD. */
@Composable
fun HudPanel(
    label: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = CutCornerShape(12.dp),
        color = JarvisColors.Surface.copy(alpha = 0.86f),
        border = BorderStroke(1.dp, JarvisColors.HudLine.copy(alpha = 0.6f))
    ) {
        Column {
            if (label != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(JarvisColors.HudLine.copy(alpha = 0.16f))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label.uppercase(),
                        color = JarvisColors.HudBright,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(text = "+", color = JarvisColors.HudLine.copy(alpha = 0.8f), fontSize = 12.sp)
                }
            }
            content()
        }
    }
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
        shape = CutCornerShape(10.dp),
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
        shape = CutCornerShape(10.dp),
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

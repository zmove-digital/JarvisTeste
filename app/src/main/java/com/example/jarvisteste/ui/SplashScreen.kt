package com.example.jarvisteste.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.jarvisteste.R
import com.example.jarvisteste.ui.components.HudDataStrip
import com.example.jarvisteste.ui.components.JarvisBackground
import com.example.jarvisteste.ui.components.JarvisColors
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/**
 * Abertura "laboratório": o ícone dentro de um reator holográfico, com anéis
 * contrarrotativos, varredura de radar, partículas em órbita e telemetria.
 */
@Composable
fun SplashScreen(onDone: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "splash")

    val outer = transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4_800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "outer"
    )
    val inner = transition.animateFloat(
        initialValue = 0f, targetValue = -360f,
        animationSpec = infiniteRepeatable(
            animation = tween(6_400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "inner"
    )
    val radar = transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2_600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "radar"
    )
    val scan = transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(2_000)),
        label = "scan"
    )
    val pulse = transition.animateFloat(
        initialValue = 0.9f, targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_400),
            repeatMode = RepeatMode.Reverse
        ), label = "pulse"
    )
    val progress = transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(1_600)),
        label = "progress"
    )

    LaunchedEffect(Unit) {
        delay(2_800)
        onDone()
    }

    JarvisBackground {
        // --------------------------------------------------- telemetria superior
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(horizontal = 28.dp, vertical = 74.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Telemetry("REATOR // 98,4%")
            Telemetry("LAB.01 // ONLINE")
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.size(320.dp),
                contentAlignment = Alignment.Center
            ) {
                // Brilho do reator, pulsando.
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                JarvisColors.Glow.copy(alpha = 0.28f * pulse.value),
                                JarvisColors.Glow.copy(alpha = 0.06f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = size.minDimension / 2f
                        ),
                        radius = size.minDimension / 2f
                    )
                }

                // Varredura de radar: setor luminoso girando.
                Canvas(
                    modifier = Modifier
                        .size(280.dp)
                        .graphicsLayer { rotationZ = radar.value }
                ) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                Color.Transparent,
                                JarvisColors.HudLine.copy(alpha = 0.05f),
                                JarvisColors.HudBright.copy(alpha = 0.32f)
                            ),
                            center = center
                        ),
                        startAngle = -70f,
                        sweepAngle = 70f,
                        useCenter = true
                    )
                }

                // Anel externo com arcos e régua de marcas.
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { rotationZ = outer.value }
                ) {
                    val stroke = Stroke(width = 2.dp.toPx())
                    drawArc(JarvisColors.HudBright.copy(alpha = 0.85f), 0f, 70f, false, style = stroke)
                    drawArc(JarvisColors.HudBright.copy(alpha = 0.85f), 180f, 70f, false, style = stroke)
                    drawArc(JarvisColors.HudLine.copy(alpha = 0.6f), 90f, 40f, false, style = stroke)
                    drawArc(JarvisColors.HudLine.copy(alpha = 0.6f), 270f, 40f, false, style = stroke)

                    for (index in 0 until 36) {
                        val angle = index * Math.PI / 18
                        val long = index % 3 == 0
                        val innerRadius = size.minDimension / 2f - (if (long) 14.dp.toPx() else 8.dp.toPx())
                        val outerRadius = size.minDimension / 2f - 2.dp.toPx()
                        drawLine(
                            color = JarvisColors.HudLine.copy(alpha = if (long) 0.8f else 0.35f),
                            start = Offset(
                                size.width / 2f + cos(angle).toFloat() * innerRadius,
                                size.height / 2f + sin(angle).toFloat() * innerRadius
                            ),
                            end = Offset(
                                size.width / 2f + cos(angle).toFloat() * outerRadius,
                                size.height / 2f + sin(angle).toFloat() * outerRadius
                            ),
                            strokeWidth = if (long) 1.6f else 1f
                        )
                    }
                }

                // Partículas em órbitas diferentes.
                Canvas(
                    modifier = Modifier
                        .size(270.dp)
                        .graphicsLayer { rotationZ = -inner.value }
                ) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val radii = listOf(size.minDimension * 0.36f, size.minDimension * 0.45f)
                    for (index in 0 until 7) {
                        val angle = index * Math.PI * 2 / 7
                        val radius = radii[index % radii.size]
                        drawCircle(
                            color = if (index % 2 == 0) JarvisColors.HudBright else JarvisColors.AccentSoft,
                            radius = (if (index % 3 == 0) 3.dp else 2.dp).toPx(),
                            center = Offset(
                                center.x + cos(angle).toFloat() * radius,
                                center.y + sin(angle).toFloat() * radius
                            ),
                            alpha = 0.75f
                        )
                    }
                }

                // Mira de quatro cantos ao redor do núcleo.
                Canvas(modifier = Modifier.size(190.dp)) {
                    val inset = 2.dp.toPx()
                    val len = 20.dp.toPx()
                    val color = JarvisColors.HudBright.copy(alpha = 0.7f)
                    val points = listOf(
                        Offset(inset, inset) to Pair(1, 1),
                        Offset(size.width - inset, inset) to Pair(-1, 1),
                        Offset(inset, size.height - inset) to Pair(1, -1),
                        Offset(size.width - inset, size.height - inset) to Pair(-1, -1)
                    )
                    points.forEach { (corner, dir) ->
                        drawLine(color, corner, Offset(corner.x + len * dir.first, corner.y), 2f)
                        drawLine(color, corner, Offset(corner.x, corner.y + len * dir.second), 2f)
                    }
                }

                // Anéis internos contrarrotativos (estilo bobina).
                Canvas(
                    modifier = Modifier
                        .size(220.dp)
                        .graphicsLayer { rotationZ = inner.value }
                ) {
                    val stroke = Stroke(width = 1.5.dp.toPx())
                    for (index in 0 until 8) {
                        drawArc(
                            color = JarvisColors.AccentSoft.copy(alpha = 0.55f),
                            startAngle = index * 45f,
                            sweepAngle = 22f,
                            useCenter = false,
                            style = stroke
                        )
                    }
                }

                Image(
                    painter = painterResource(R.drawable.splash_mark),
                    contentDescription = null,
                    modifier = Modifier.size(112.dp)
                )

                // Linha de varredura subindo pelo reator.
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val y = size.height * (1f - scan.value)
                    drawLine(
                        brush = Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                JarvisColors.HudBright.copy(alpha = 0.55f),
                                Color.Transparent
                            )
                        ),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.5.dp.toPx()
                    )
                }
            }

            Spacer(modifier = Modifier.height(26.dp))

            androidx.compose.material3.Text(
                text = "J.A.R.V.I.S.",
                color = JarvisColors.AccentSoft,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            androidx.compose.material3.Text(
                text = "INICIALIZANDO SISTEMA",
                color = JarvisColors.TextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(22.dp))

            HudDataStrip(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp),
                filled = (progress.value * 7).toInt().coerceIn(1, 7)
            )

            Spacer(modifier = Modifier.height(10.dp))

            androidx.compose.material3.Text(
                text = "SISTEMAS NOMINAIS • AGUARDANDO COMANDO",
                color = JarvisColors.HudLine,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
        }
    }
}

@Composable
private fun Telemetry(text: String) {
    androidx.compose.material3.Text(
        text = text,
        color = JarvisColors.HudLine,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp
    )
}

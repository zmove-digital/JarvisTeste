package com.example.jarvisteste.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Canvas
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
 * Abertura do app: fundo HUD, ícone e dois anéis girando em sentidos opostos,
 * como um holograma sendo montado no laboratório.
 */
@Composable
fun SplashScreen(onDone: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "splash")
    val outer = transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4_800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "outer"
    )
    val inner = transition.animateFloat(
        initialValue = 0f,
        targetValue = -360f,
        animationSpec = infiniteRepeatable(
            animation = tween(6_400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "inner"
    )
    val progress = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(1_600)),
        label = "progress"
    )

    LaunchedEffect(Unit) {
        delay(2_300)
        onDone()
    }

    JarvisBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.size(300.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                JarvisColors.Glow.copy(alpha = 0.22f),
                                JarvisColors.Glow.copy(alpha = 0.05f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = size.minDimension / 2f
                        ),
                        radius = size.minDimension / 2f
                    )
                }

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
                                center.x + cos(angle).toFloat() * innerRadius,
                                center.y + sin(angle).toFloat() * innerRadius
                            ),
                            end = Offset(
                                center.x + cos(angle).toFloat() * outerRadius,
                                center.y + sin(angle).toFloat() * outerRadius
                            ),
                            strokeWidth = if (long) 1.6f else 1f
                        )
                    }
                }

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
            }

            Spacer(modifier = Modifier.height(36.dp))

            androidx.compose.material3.Text(
                text = "J.A.R.V.I.S.",
                color = JarvisColors.AccentSoft,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            androidx.compose.material3.Text(
                text = "INICIALIZANDO SISTEMA",
                color = JarvisColors.TextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(26.dp))

            HudDataStrip(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp),
                filled = (progress.value * 7).toInt().coerceIn(1, 7)
            )
        }
    }
}

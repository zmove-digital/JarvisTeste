package com.example.jarvisteste.ui.avatar

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Avatar vetorial animado. Não usa imagens: tudo é desenhado no Canvas, então
 * funciona em qualquer resolução e reage ao estado da conversa.
 *
 * [AvatarMood.IDLE] dorme/sorri devagar; [AvatarMood.LISTENING] fica atento;
 * [AvatarMood.THINKING] olha para cima e mostra pontinhos; [AvatarMood.SPEAKING]
 * mexe a boca e o corpo como alguém falando.
 */
@Composable
fun JarvisAvatar(
    preset: AvatarPreset,
    mood: AvatarMood,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "avatar")

    val sway by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sway"
    )

    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (mood) {
                    AvatarMood.SPEAKING -> 900
                    AvatarMood.THINKING -> 1500
                    else -> 3000
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val blink by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 5200
                1f at 0
                1f at 4500
                0.06f at 4660
                1f at 4820
                1f at 5200
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "blink"
    )

    val mouth by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 640
                0f at 0
                0.85f at 90
                0.2f at 170
                1f at 270
                0.15f at 380
                0.65f at 500
                0f at 640
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "mouth"
    )

    val dots by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dots"
    )

    Canvas(modifier = modifier) {
        drawAvatar(preset, mood, sway, pulse, blink, mouth, dots)
    }
}

private fun DrawScope.drawAvatar(
    preset: AvatarPreset,
    mood: AvatarMood,
    sway: Float,
    pulse: Float,
    blink: Float,
    mouth: Float,
    dots: Float
) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return

    val palette = preset.palette
    drawRect(Brush.verticalGradient(listOf(palette.backdropTop, palette.backdropBottom)))

    val angle = sway * 2f * PI.toFloat()
    when (preset.style) {
        AvatarStyle.ORB -> drawOrb(preset, mood, w, h, angle, pulse, mouth)
        AvatarStyle.FACE -> drawFace(preset, mood, w, h, angle, pulse, blink, mouth, dots)
    }
}

private fun DrawScope.drawFace(
    preset: AvatarPreset,
    mood: AvatarMood,
    w: Float,
    h: Float,
    angle: Float,
    pulse: Float,
    blink: Float,
    mouth: Float,
    dots: Float
) {
    val p = preset.palette
    val cx = w / 2f
    val headR = min(w, h) * 0.19f
    val cy = h * 0.47f + sin(angle) * h * 0.006f
    val center = Offset(cx, cy)

    // Halo/energia atrás da cabeça.
    val haloR = headR * (2.05f + 0.18f * pulse)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(p.glow.copy(alpha = 0.30f), Color.Transparent),
            center = center,
            radius = haloR
        ),
        radius = haloR,
        center = center
    )
    drawCircle(
        color = p.glow.copy(alpha = 0.16f + 0.18f * pulse),
        radius = haloR * 0.86f,
        center = center,
        style = Stroke(width = headR * 0.035f)
    )
    drawCircle(
        color = p.accent.copy(alpha = 0.08f + 0.12f * pulse),
        radius = haloR * 1.02f,
        center = center,
        style = Stroke(width = headR * 0.02f)
    )

    // Ombros e gola.
    val shoulders = Path().apply {
        moveTo(cx - w * 0.46f, h * 1.02f)
        cubicTo(cx - w * 0.36f, h * 0.74f, cx + w * 0.36f, h * 0.74f, cx + w * 0.46f, h * 1.02f)
        close()
    }
    drawPath(shoulders, p.clothes)
    drawPath(
        path = Path().apply {
            moveTo(cx - headR * 0.62f, h * 0.80f)
            quadraticBezierTo(cx, h * 0.88f, cx + headR * 0.62f, h * 0.80f)
        },
        color = p.accent.copy(alpha = 0.35f),
        style = Stroke(width = headR * 0.045f, cap = StrokeCap.Round)
    )

    // Pescoço.
    drawRoundRect(
        color = p.skinShade,
        topLeft = Offset(cx - headR * 0.30f, cy + headR * 0.62f),
        size = Size(headR * 0.60f, h * 0.20f),
        cornerRadius = CornerRadius(headR * 0.22f)
    )

    // Cabelo atrás da cabeça.
    if (preset.hair == HairStyle.LONG) {
        drawOval(
            color = p.hair,
            topLeft = Offset(cx - headR * 1.35f, cy - headR * 1.45f),
            size = Size(headR * 2.70f, headR * 2.60f)
        )
        drawRoundRect(
            color = p.hair,
            topLeft = Offset(cx - headR * 1.34f, cy - headR * 0.40f),
            size = Size(headR * 0.54f, h * 0.52f),
            cornerRadius = CornerRadius(headR * 0.30f)
        )
        drawRoundRect(
            color = p.hair,
            topLeft = Offset(cx + headR * 0.80f, cy - headR * 0.40f),
            size = Size(headR * 0.54f, h * 0.52f),
            cornerRadius = CornerRadius(headR * 0.30f)
        )
    } else {
        drawOval(
            color = p.hair,
            topLeft = Offset(cx - headR * 1.12f, cy - headR * 1.28f),
            size = Size(headR * 2.24f, headR * 2.20f)
        )
    }

    rotate(degrees = sin(angle) * 1.1f, pivot = center) {
        // Rosto.
        drawOval(
            color = p.skin,
            topLeft = Offset(cx - headR * 0.92f, cy - headR * 1.06f),
            size = Size(headR * 1.84f, headR * 2.12f)
        )
        drawOval(
            color = p.skinShade.copy(alpha = 0.18f),
            topLeft = Offset(cx - headR * 0.92f, cy + headR * 0.10f),
            size = Size(headR * 1.84f, headR * 0.94f)
        )

        // Orelhas.
        drawCircle(p.skinShade, radius = headR * 0.13f, center = Offset(cx - headR * 0.94f, cy + headR * 0.05f))
        drawCircle(p.skinShade, radius = headR * 0.13f, center = Offset(cx + headR * 0.94f, cy + headR * 0.05f))

        // Bochechas.
        drawOval(
            color = p.glow.copy(alpha = 0.13f),
            topLeft = Offset(cx - headR * 0.72f, cy + headR * 0.18f),
            size = Size(headR * 0.36f, headR * 0.17f)
        )
        drawOval(
            color = p.glow.copy(alpha = 0.13f),
            topLeft = Offset(cx + headR * 0.36f, cy + headR * 0.18f),
            size = Size(headR * 0.36f, headR * 0.17f)
        )

        // Franja.
        drawPath(
            path = Path().apply {
                moveTo(cx - headR * 0.98f, cy - headR * 0.15f)
                cubicTo(
                    cx - headR * 1.05f, cy - headR * 1.30f,
                    cx + headR * 1.05f, cy - headR * 1.30f,
                    cx + headR * 0.98f, cy - headR * 0.15f
                )
                cubicTo(
                    cx + headR * 0.55f, cy - headR * 0.52f,
                    cx - headR * 0.15f, cy - headR * 0.62f,
                    cx - headR * 0.98f, cy - headR * 0.15f
                )
                close()
            },
            color = p.hair
        )
        drawPath(
            path = Path().apply {
                moveTo(cx - headR * 0.55f, cy - headR * 0.90f)
                quadraticBezierTo(cx, cy - headR * 1.10f, cx + headR * 0.45f, cy - headR * 0.88f)
            },
            color = p.accent.copy(alpha = 0.20f),
            style = Stroke(width = headR * 0.06f, cap = StrokeCap.Round)
        )

        // Sobrancelhas.
        val browY = cy - headR * 0.34f
        val browLift = if (mood == AvatarMood.THINKING) headR * 0.05f else 0f
        drawLine(
            color = p.hair.copy(alpha = 0.9f),
            start = Offset(cx - headR * 0.58f, browY - browLift),
            end = Offset(cx - headR * 0.22f, browY - headR * 0.03f),
            strokeWidth = headR * 0.05f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = p.hair.copy(alpha = 0.9f),
            start = Offset(cx + headR * 0.22f, browY - headR * 0.03f),
            end = Offset(cx + headR * 0.58f, browY - browLift),
            strokeWidth = headR * 0.05f,
            cap = StrokeCap.Round
        )

        // Olhos (piscam).
        val eyeDx = headR * 0.40f
        val eyeY = cy - headR * 0.12f
        val eyeRx = headR * 0.17f
        val eyeRy = (headR * 0.13f) * blink.coerceAtLeast(0.04f)
        listOf(-1f, 1f).forEach { side ->
            val ex = cx + eyeDx * side
            drawOval(
                color = Color.White.copy(alpha = 0.92f),
                topLeft = Offset(ex - eyeRx, eyeY - eyeRy),
                size = Size(eyeRx * 2f, eyeRy * 2f)
            )
            val irisR = eyeRx * 0.62f * blink.coerceIn(0.12f, 1f)
            val irisY = eyeY - if (mood == AvatarMood.THINKING) eyeRy * 0.25f else 0f
            drawCircle(p.eye, radius = irisR, center = Offset(ex, irisY))
            drawCircle(
                Color.White.copy(alpha = 0.75f),
                radius = irisR * 0.28f,
                center = Offset(ex - irisR * 0.3f, irisY - irisR * 0.3f)
            )
        }

        // Nariz.
        drawPath(
            path = Path().apply {
                moveTo(cx - headR * 0.05f, cy + headR * 0.08f)
                quadraticBezierTo(cx - headR * 0.12f, cy + headR * 0.28f, cx + headR * 0.02f, cy + headR * 0.30f)
            },
            color = p.skinShade.copy(alpha = 0.7f),
            style = Stroke(width = headR * 0.035f, cap = StrokeCap.Round)
        )

        // Boca.
        val mouthY = cy + headR * 0.58f
        val mouthW = headR * 0.42f
        when (mood) {
            AvatarMood.SPEAKING -> {
                val open = 0.18f + mouth * 0.82f
                val mouthH = headR * 0.05f + headR * 0.18f * open
                drawOval(
                    color = p.mouth,
                    topLeft = Offset(cx - mouthW / 2f, mouthY - mouthH / 2f),
                    size = Size(mouthW, mouthH)
                )
                drawOval(
                    color = Color(0xFFF2A0A8).copy(alpha = 0.55f),
                    topLeft = Offset(cx - mouthW * 0.22f, mouthY + mouthH * 0.12f),
                    size = Size(mouthW * 0.44f, mouthH * 0.34f)
                )
            }

            AvatarMood.THINKING -> drawLine(
                color = p.mouth.copy(alpha = 0.9f),
                start = Offset(cx - mouthW * 0.32f, mouthY),
                end = Offset(cx + mouthW * 0.32f, mouthY),
                strokeWidth = headR * 0.045f,
                cap = StrokeCap.Round
            )

            else -> drawPath(
                path = Path().apply {
                    moveTo(cx - mouthW * 0.5f, mouthY - headR * 0.02f)
                    quadraticBezierTo(cx, mouthY + headR * 0.10f, cx + mouthW * 0.5f, mouthY - headR * 0.02f)
                },
                color = p.mouth,
                style = Stroke(width = headR * 0.045f, cap = StrokeCap.Round)
            )
        }
    }

    // Pontinhos de "pensando".
    if (mood == AvatarMood.THINKING) {
        for (index in 0..2) {
            val wave = (sin((dots + index * 0.22f) * 2f * PI.toFloat()) + 1f) / 2f
            drawCircle(
                color = p.accent.copy(alpha = 0.20f + 0.75f * wave),
                radius = headR * 0.055f,
                center = Offset(cx + (index - 1) * headR * 0.24f, cy - headR * 1.62f)
            )
        }
    }
}

private fun DrawScope.drawOrb(
    preset: AvatarPreset,
    mood: AvatarMood,
    w: Float,
    h: Float,
    angle: Float,
    pulse: Float,
    mouth: Float
) {
    val p = preset.palette
    val cx = w / 2f
    val cy = h * 0.47f
    val center = Offset(cx, cy)
    val orbR = min(w, h) * 0.24f

    val glowR = orbR * 2.2f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                p.glow.copy(alpha = 0.32f),
                p.glow.copy(alpha = 0.08f),
                Color.Transparent
            ),
            center = center,
            radius = glowR
        ),
        radius = glowR,
        center = center
    )

    rotate(degrees = angle * 40f, pivot = center) {
        drawOval(
            color = p.glow.copy(alpha = 0.55f),
            topLeft = Offset(cx - orbR * 1.60f, cy - orbR * 0.55f),
            size = Size(orbR * 3.20f, orbR * 1.10f),
            style = Stroke(width = orbR * 0.025f)
        )
    }
    rotate(degrees = -angle * 28f, pivot = center) {
        drawOval(
            color = p.accent.copy(alpha = 0.35f),
            topLeft = Offset(cx - orbR * 1.25f, cy - orbR * 1.30f),
            size = Size(orbR * 2.50f, orbR * 2.60f),
            style = Stroke(width = orbR * 0.02f)
        )
    }

    val coreScale = 1f + 0.04f * pulse +
        if (mood == AvatarMood.SPEAKING) 0.05f * mouth else 0f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.95f),
                p.accent,
                p.glow.copy(alpha = 0.55f),
                Color.Transparent
            ),
            center = Offset(cx - orbR * 0.25f, cy - orbR * 0.30f),
            radius = orbR * 1.40f
        ),
        radius = orbR * coreScale,
        center = center
    )

    rotate(degrees = angle * 80f, pivot = center) {
        drawArc(
            color = p.accent.copy(alpha = 0.40f),
            startAngle = 20f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(cx - orbR * 0.78f, cy - orbR * 0.78f),
            size = Size(orbR * 1.56f, orbR * 1.56f),
            style = Stroke(width = orbR * 0.03f, cap = StrokeCap.Round)
        )
    }

    if (mood == AvatarMood.THINKING) {
        val a = angle * 2f * PI.toFloat()
        drawCircle(
            color = p.accent,
            radius = orbR * 0.09f,
            center = Offset(cx + cos(a) * orbR * 1.5f, cy + sin(a) * orbR * 0.55f)
        )
    }
}

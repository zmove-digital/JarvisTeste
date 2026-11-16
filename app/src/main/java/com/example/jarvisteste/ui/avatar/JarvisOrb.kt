package com.example.jarvisteste.ui.avatar

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.example.jarvisteste.audio.MicLevel
import com.example.jarvisteste.audio.VoiceLevel
import com.example.jarvisteste.audio.VoiceSource
import com.example.jarvisteste.ui.components.JarvisColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.isActive

/**
 * O único avatar do app: um orbe de energia no estilo "quase TRON".
 *
 * Tudo é desenhado no Canvas e reage em tempo real às ondas de voz:
 *
 *  - enquanto **você** fala, o orbe vibra de forma frenética com o nível do
 *    microfone ([MicLevel], vindo do `onRmsChanged` do reconhecedor);
 *  - enquanto o **Jarvis** fala, ele pulsa com a amplitude do áudio
 *    ([VoiceLevel]);
 *  - ao pensar, gira mais rápido; parado, apenas respira devagar.
 *
 * Os anéis giram em velocidades diferentes e a energia abre "raios" ao redor do
 * núcleo, o que dá o movimento inquieto típico do filme.
 */
@Composable
fun JarvisOrb(
    mood: AvatarMood,
    modifier: Modifier = Modifier,
    demo: Boolean = false
) {
    var energy by remember { mutableFloatStateOf(0f) }
    var spin by remember { mutableFloatStateOf(0f) }
    var wobble by remember { mutableFloatStateOf(0f) }
    var time by remember { mutableFloatStateOf(0f) }

    // Um único laço por quadro: lê os níveis atuais, suaviza e avança a rotação.
    // Assim o orbe responde a cada amostra de voz sem recompor a tela inteira.
    LaunchedEffect(mood, demo) {
        var last = 0L
        var t = 0f
        var angle = 0f
        var smoothed = 0f
        while (isActive) {
            val now = withFrameNanos { it }
            if (last == 0L) {
                last = now
                continue
            }
            val dt = ((now - last) / 1_000_000_000.0).toFloat().coerceIn(0f, 0.05f)
            last = now
            t += dt

            val speaking = VoiceLevel.source.value != VoiceSource.NONE
            val raw = when {
                demo -> demoLevel(t, mood)
                speaking -> 0.28f + VoiceLevel.level.value * 0.85f
                mood == AvatarMood.LISTENING -> MicLevel.current()
                mood == AvatarMood.THINKING ->
                    0.30f + 0.22f * (0.5f + 0.5f * sin(t * 3.1f))
                else -> 0.07f + 0.05f * (0.5f + 0.5f * sin(t * 1.6f))
            }.coerceIn(0f, 1f)

            // Ataque rápido e queda mais lenta: a sensação é de "estalo" a cada
            // sílaba, não de um volume que sobe e desce devagar.
            val k = if (raw > smoothed) 30f else 9f
            smoothed += (raw - smoothed) * (1f - exp(-k * dt))

            val speed = when (mood) {
                AvatarMood.LISTENING -> 1.2f + smoothed * 4.4f
                AvatarMood.SPEAKING -> 0.9f + smoothed * 3.4f
                AvatarMood.THINKING -> 2.1f + smoothed * 2.2f
                AvatarMood.IDLE -> 0.35f + smoothed * 1.4f
            }
            angle = (angle + speed * 62f * dt) % 360f

            val freq = 5f + smoothed * 30f
            val jitter = (
                sin(t * freq) * 0.6f +
                    sin(t * freq * 1.7f + 1.3f) * 0.4f
                ) * smoothed

            energy = smoothed
            spin = angle
            wobble = jitter
            time = t
        }
    }

    Canvas(modifier = modifier) {
        drawOrb(mood, energy, spin, wobble, time)
    }
}

/** Envelope sintético usado só na prévia (sem microfone nem voz reais). */
private fun demoLevel(t: Float, mood: AvatarMood): Float = when (mood) {
    AvatarMood.LISTENING -> 0.30f +
        0.70f * (0.5f + 0.5f * sin(t * 7.5f)) * (0.5f + 0.5f * sin(t * 2.3f))
    AvatarMood.SPEAKING -> 0.35f +
        0.65f * (0.5f + 0.5f * sin(t * 9.1f)) * (0.5f + 0.5f * sin(t * 1.7f))
    AvatarMood.THINKING -> 0.38f
    AvatarMood.IDLE -> 0.14f
}.coerceIn(0f, 1f)

private fun DrawScope.drawOrb(
    mood: AvatarMood,
    energy: Float,
    spin: Float,
    wobble: Float,
    time: Float
) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return

    val cx = w / 2f
    val cy = h / 2f
    val center = Offset(cx, cy)
    val baseR = min(w, h) * 0.20f

    val accent = when (mood) {
        AvatarMood.THINKING -> Color(0xFF1E9BFF)
        AvatarMood.SPEAKING -> JarvisColors.AccentSoft
        else -> JarvisColors.Accent
    }

    // O núcleo treme junto com a voz: pequenos deslocamentos a cada sílaba.
    val coreCenter = Offset(
        cx + wobble * baseR * 0.11f,
        cy + cos(time * 7.3f) * wobble * baseR * 0.09f
    )
    val coreR = baseR * (1f + 0.18f * energy)

    // Halo externo.
    val glowR = baseR * (2.5f + 0.8f * energy)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                accent.copy(alpha = 0.26f + 0.34f * energy),
                accent.copy(alpha = 0.08f),
                Color.Transparent
            ),
            center = coreCenter,
            radius = glowR
        ),
        radius = glowR,
        center = coreCenter
    )

    // Raios de energia ao redor do núcleo (ficam mais longos e numerosos com a
    // voz, criando o efeito "frenético").
    if (energy > 0.04f) {
        val spokes = 28
        for (i in 0 until spokes) {
            val a = (i.toFloat() / spokes) * 2f * PI.toFloat() +
                spin * PI.toFloat() / 180f
            val flicker = 0.5f + 0.5f * sin(time * 13f + i * 0.9f)
            val inner = baseR * 1.06f
            val outer = baseR * (1.06f + 0.72f * energy * flicker)
            drawLine(
                color = accent.copy(alpha = 0.08f + 0.34f * energy),
                start = Offset(center.x + cos(a) * inner, center.y + sin(a) * inner),
                end = Offset(center.x + cos(a) * outer, center.y + sin(a) * outer),
                strokeWidth = baseR * 0.022f,
                cap = StrokeCap.Round
            )
        }
    }

    // Anéis TRON: cada um gira em um sentido e velocidade diferentes.
    rotate(degrees = spin, pivot = center) {
        drawArcRing(center, baseR * 1.90f, baseR * 0.032f, accent.copy(alpha = 0.45f), 3, 34f)
    }
    rotate(degrees = -spin * 1.6f, pivot = center) {
        drawArcRing(center, baseR * 1.52f, baseR * 0.028f, accent.copy(alpha = 0.75f), 2, 58f)
    }
    rotate(degrees = spin * 2.3f, pivot = center) {
        drawArcRing(center, baseR * 1.16f, baseR * 0.022f, accent.copy(alpha = 0.6f), 4, 44f)
    }

    // Contorno fino do núcleo.
    drawCircle(
        color = accent.copy(alpha = 0.85f),
        radius = coreR * 1.20f,
        center = coreCenter,
        style = Stroke(width = baseR * 0.018f)
    )

    // Núcleo luminoso.
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.98f),
                JarvisColors.AccentSoft,
                accent,
                accent.copy(alpha = 0.30f),
                Color.Transparent
            ),
            center = Offset(coreCenter.x - coreR * 0.28f, coreCenter.y - coreR * 0.30f),
            radius = coreR * 1.55f
        ),
        radius = coreR,
        center = coreCenter
    )

    // Centro branco que acende com a energia.
    drawCircle(
        color = Color.White.copy(alpha = 0.5f + 0.45f * energy),
        radius = coreR * (0.26f + 0.14f * energy),
        center = coreCenter
    )

    // Traçador girando por cima do núcleo: lembra o "disco" do TRON.
    val tracerAngle = time * (1.4f + energy * 2.6f) * 2f * PI.toFloat()
    val tracerR = coreR * 1.05f
    drawCircle(
        color = Color.White.copy(alpha = 0.75f),
        radius = baseR * 0.035f,
        center = Offset(
            coreCenter.x + cos(tracerAngle) * tracerR,
            coreCenter.y + sin(tracerAngle) * tracerR
        )
    )
}

/** Desenha um anel dividido em arcos com vãos, no estilo das interfaces TRON. */
private fun DrawScope.drawArcRing(
    center: Offset,
    radius: Float,
    width: Float,
    color: Color,
    segments: Int,
    gapDegrees: Float
) {
    val slice = 360f / segments
    val sweep = slice - gapDegrees
    for (i in 0 until segments) {
        drawArc(
            color = color,
            startAngle = i * slice + gapDegrees / 2f,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2f, radius * 2f),
            style = Stroke(width = width, cap = StrokeCap.Round)
        )
    }
}

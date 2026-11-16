package com.example.jarvisteste.ui.avatar

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.jarvisteste.audio.MicLevel
import com.example.jarvisteste.audio.VoiceLevel
import com.example.jarvisteste.audio.VoiceSource
import com.example.jarvisteste.data.SettingsStore
import com.example.jarvisteste.service.JarvisListeningService
import com.example.jarvisteste.service.ListeningPhase
import com.example.jarvisteste.ui.components.JarvisBackground
import com.example.jarvisteste.ui.components.JarvisColors
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun AvatarScreen(
    demo: Boolean,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val settings = remember(context) { SettingsStore(context) }
    val wakeName = remember(settings.wakeWord) {
        settings.wakeWord.replaceFirstChar { it.uppercase(Locale.ROOT) }
    }

    val running by JarvisListeningService.running.collectAsState()
    val status by JarvisListeningService.status.collectAsState()

    var demoMood by remember { mutableStateOf(AvatarMood.LISTENING) }
    LaunchedEffect(demo) {
        if (!demo) return@LaunchedEffect
        while (true) {
            demoMood = AvatarMood.LISTENING
            delay(2800)
            demoMood = AvatarMood.THINKING
            delay(1500)
            demoMood = AvatarMood.SPEAKING
            delay(4600)
        }
    }

    val mood = if (demo) demoMood else status.phase.toAvatarMood()

    // Fecha sozinho quando a conversa volta ao modo de espera pela palavra.
    val waitingForWake = !demo && running && status.phase == ListeningPhase.LISTENING
    LaunchedEffect(waitingForWake) {
        if (waitingForWake) {
            delay(5000)
            onClose()
        }
    }
    LaunchedEffect(demo, running) {
        if (!demo && !running) {
            delay(700)
            onClose()
        }
    }

    BackHandler(onBack = onClose)

    val title = when {
        demo -> when (demoMood) {
            AvatarMood.LISTENING -> "Ouvindo você..."
            AvatarMood.THINKING -> "Pensando..."
            AvatarMood.SPEAKING -> "Falando..."
            AvatarMood.IDLE -> "Pronto para conversar"
        }

        status.phase == ListeningPhase.ERROR -> status.message
        status.phase == ListeningPhase.THINKING -> "Consultando o Gemini..."
        status.phase == ListeningPhase.SPEAKING -> "Falando..."
        status.phase == ListeningPhase.AWAKE -> "Pode falar..."
        else -> "Ouvindo por \"$wakeName\"..."
    }

    JarvisBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 24.dp, vertical = 18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (demo) "PRÉVIA DO ORBE" else wakeName.uppercase(Locale.ROOT),
                        color = JarvisColors.Accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 3.sp
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = title,
                        color = JarvisColors.TextPrimary,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Text(
                    text = "FECHAR",
                    color = JarvisColors.TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    modifier = Modifier
                        .clickable(onClick = onClose)
                        .padding(horizontal = 6.dp, vertical = 10.dp)
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                JarvisOrb(
                    mood = mood,
                    demo = demo,
                    modifier = Modifier
                        .fillMaxWidth(0.86f)
                        .aspectRatio(1f)
                )
            }

            // O medidor ocupa sempre a mesma altura, então nada "pula" quando o
            // Jarvis começa ou termina de falar.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp),
                contentAlignment = Alignment.Center
            ) {
                TronMeter(mood = mood)
            }

            if (!demo && status.transcript.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = JarvisColors.Surface.copy(alpha = 0.72f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        JarvisColors.Border.copy(alpha = 0.9f)
                    )
                ) {
                    Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                        Text(
                            text = "VOCÊ DISSE",
                            color = JarvisColors.Accent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                        Spacer(modifier = Modifier.height(5.dp))
                        Text(
                            text = status.transcript,
                            color = JarvisColors.TextPrimary,
                            fontSize = 16.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Text(
                text = if (demo) {
                    "Toque em FECHAR para sair da prévia."
                } else {
                    "Pergunte sem repetir o nome enquanto a conversa estiver ativa."
                },
                modifier = Modifier.fillMaxWidth(),
                color = JarvisColors.TextMuted,
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Barras de nível que acompanham a voz. Sempre visíveis (na altura mínima
 * quando ninguém fala), para o layout não mudar de tamanho.
 */
@Composable
private fun TronMeter(mood: AvatarMood) {
    val mic by MicLevel.level.collectAsState()
    val voice by VoiceLevel.level.collectAsState()
    val source by VoiceLevel.source.collectAsState()

    val live = when {
        source != VoiceSource.NONE -> (0.28f + voice * 0.85f).coerceIn(0f, 1f)
        mood == AvatarMood.LISTENING -> mic.coerceIn(0f, 1f)
        mood == AvatarMood.THINKING -> 0.35f
        else -> 0.08f
    }

    val transition = rememberInfiniteTransition(label = "tron-meter")
    Row(
        modifier = Modifier.height(30.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(9) { index ->
            val wave by transition.animateFloat(
                initialValue = 0.32f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 260 + index * 55,
                        easing = FastOutSlowInEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "meter-$index"
            )
            val scale = (live * wave).coerceIn(0.08f, 1f)
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(30.dp * scale)
                    .background(
                        JarvisColors.Accent.copy(alpha = 0.4f + 0.55f * live),
                        RoundedCornerShape(2.dp)
                    )
            )
        }
    }
}

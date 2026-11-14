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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.jarvisteste.data.SettingsStore
import com.example.jarvisteste.service.JarvisListeningService
import com.example.jarvisteste.service.ListeningPhase
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
    val preset = remember(settings.avatarId) { Avatars.byId(settings.avatarId) }
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(preset.palette.backdropTop, preset.palette.backdropBottom)
                )
            )
    ) {
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
                        text = if (demo) "PRÉVIA DO AVATAR" else wakeName.uppercase(Locale.ROOT),
                        color = preset.palette.accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 3.sp
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Text(
                    text = "FECHAR",
                    color = JarvisColors.TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
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
                JarvisAvatar(
                    preset = preset,
                    mood = mood,
                    modifier = Modifier
                        .fillMaxWidth(0.90f)
                        .aspectRatio(0.84f)
                )
            }

            if (mood == AvatarMood.SPEAKING) {
                SpeakingBars(
                    color = preset.palette.accent,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (!demo && status.transcript.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = JarvisColors.Surface.copy(alpha = 0.72f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        JarvisColors.Border.copy(alpha = 0.8f)
                    )
                ) {
                    Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                        Text(
                            text = "VOCÊ DISSE",
                            color = preset.palette.accent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                        Spacer(modifier = Modifier.height(5.dp))
                        Text(
                            text = status.transcript,
                            color = Color.White,
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

@Composable
private fun SpeakingBars(
    color: Color,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "speaking-bars")
    Row(
        modifier = modifier.height(30.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(5) { index ->
            val level by transition.animateFloat(
                initialValue = 0.22f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 300 + index * 80,
                        easing = FastOutSlowInEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar-$index"
            )
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height(30.dp * level)
                    .background(color.copy(alpha = 0.85f), RoundedCornerShape(3.dp))
            )
        }
    }
}

package com.example.jarvisteste.ui.components

import android.speech.tts.Voice
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.jarvisteste.audio.VoiceCatalog
import com.example.jarvisteste.data.VoiceProfile
import com.example.jarvisteste.data.VoiceProfiles

@Composable
fun VoicePicker(
    voices: List<Voice>,
    profile: VoiceProfile,
    selectedVoiceName: String?,
    onSelectProfile: (VoiceProfile) -> Unit,
    onSelectVoice: (String?) -> Unit,
    onPreview: () -> Unit
) {
    var showSystemVoices by rememberSaveable { mutableStateOf(false) }

    val brVoices = remember(voices) {
        voices.filter { it.locale.country.equals("BR", ignoreCase = true) }.ifEmpty { voices }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

        JarvisLabel("Estilo da voz")

        VoiceProfiles.all.forEach { option ->
            val selected = option.id == profile.id
            SelectableCard(selected = selected, onClick = { onSelectProfile(option) }) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = option.label,
                            color = JarvisColors.TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = option.description,
                            color = JarvisColors.TextMuted,
                            fontSize = 12.sp
                        )
                    }
                    if (selected) {
                        Text(
                            text = "SELECIONADA",
                            color = JarvisColors.Success,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Button(
            onClick = onPreview,
            modifier = Modifier.height(48.dp),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
                containerColor = JarvisColors.SurfaceSelected,
                contentColor = JarvisColors.AccentSoft
            )
        ) {
            Text(
                text = "OUVIR EXEMPLO",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // -------------------------------------------- voz específica (opcional)
        SelectableCard(
            selected = selectedVoiceName != null,
            onClick = { showSystemVoices = !showSystemVoices }
        ) {
            Text(
                text = "Voz do sistema",
                color = JarvisColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = selectedVoiceName ?: "Automática — melhor voz para o estilo",
                color = JarvisColors.TextMuted,
                fontSize = 12.sp
            )
            Text(
                text = if (showSystemVoices) "Ocultar opções" else "Escolher uma voz específica",
                color = JarvisColors.Accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        if (showSystemVoices) {
            if (brVoices.isEmpty()) {
                Text(
                    text = "Nenhuma voz em português encontrada no aparelho. " +
                        "O Jarvis vai usar a voz padrão do sistema.",
                    color = JarvisColors.TextMuted,
                    fontSize = 12.sp
                )
            } else {
                SelectableCard(
                    selected = selectedVoiceName == null,
                    onClick = { onSelectVoice(null) }
                ) {
                    Text(
                        text = "Automática",
                        color = JarvisColors.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                brVoices.take(MAX_VOICES).forEach { voice ->
                    SelectableCard(
                        selected = selectedVoiceName == voice.name,
                        onClick = { onSelectVoice(voice.name) }
                    ) {
                        Text(
                            text = voice.name,
                            color = JarvisColors.TextPrimary,
                            fontSize = 14.sp
                        )
                        Text(
                            text = VoiceCatalog.details(voice),
                            color = JarvisColors.TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }

                if (brVoices.size > MAX_VOICES) {
                    Text(
                        text = "Mostrando $MAX_VOICES de ${brVoices.size} vozes.",
                        color = JarvisColors.TextMuted,
                        fontSize = 11.sp
                    )
                }
            }
        }

        Text(
            text = "A voz é gerada pelo motor do seu celular; as opções variam de aparelho " +
                "para aparelho. Use \"Ouvir exemplo\" para conferir antes de concluir.",
            color = JarvisColors.TextMuted,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

private const val MAX_VOICES = 10

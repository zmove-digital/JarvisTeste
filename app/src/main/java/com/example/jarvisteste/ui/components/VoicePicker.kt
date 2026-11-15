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
import com.example.jarvisteste.data.GeminiVoice
import com.example.jarvisteste.data.GeminiVoices
import com.example.jarvisteste.data.VoiceEngine
import com.example.jarvisteste.data.VoiceGender
import com.example.jarvisteste.data.VoiceProfile
import com.example.jarvisteste.data.VoiceProfiles

@Composable
fun VoicePicker(
    voices: List<Voice>,
    engine: VoiceEngine,
    geminiVoiceId: String,
    profile: VoiceProfile,
    selectedVoiceName: String?,
    onSelectEngine: (VoiceEngine) -> Unit,
    onSelectGeminiVoice: (String) -> Unit,
    onSelectProfile: (VoiceProfile) -> Unit,
    onSelectVoice: (String?) -> Unit,
    onPreview: () -> Unit
) {
    var showSystemVoices by rememberSaveable { mutableStateOf(false) }

    val brVoices = remember(voices) {
        voices.filter { it.locale.country.equals("BR", ignoreCase = true) }.ifEmpty { voices }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

        JarvisLabel("Motor de voz")

        SelectableCard(
            selected = engine == VoiceEngine.GEMINI,
            onClick = { onSelectEngine(VoiceEngine.GEMINI) }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Vozes do Jarvis",
                        color = JarvisColors.TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Online, naturais e expressivas",
                        color = JarvisColors.TextMuted,
                        fontSize = 12.sp
                    )
                }
                if (engine == VoiceEngine.GEMINI) {
                    Text(
                        text = "RECOMENDADA",
                        color = JarvisColors.Success,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }

        SelectableCard(
            selected = engine == VoiceEngine.SYSTEM,
            onClick = { onSelectEngine(VoiceEngine.SYSTEM) }
        ) {
            Text(
                text = "Voz do aparelho",
                color = JarvisColors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Offline, usa as vozes instaladas no celular",
                color = JarvisColors.TextMuted,
                fontSize = 12.sp
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        when (engine) {
            VoiceEngine.GEMINI -> {
                JarvisLabel("Escolha a voz")
                Text(
                    text = "Doze timbres brasileiros. Toque em uma opção e use " +
                        "\"Ouvir exemplo\" para comparar.",
                    color = JarvisColors.TextMuted,
                    fontSize = 12.sp
                )

                GeminiVoices.all.chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        pair.forEach { option ->
                            GeminiVoiceCard(
                                option = option,
                                selected = option.id == geminiVoiceId,
                                onClick = { onSelectGeminiVoice(option.id) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (pair.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            VoiceEngine.SYSTEM -> {
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

                // ---------------------------------------- voz específica (opcional)
                SelectableCard(
                    selected = selectedVoiceName != null,
                    onClick = { showSystemVoices = !showSystemVoices }
                ) {
                    Text(
                        text = "Voz específica",
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

        Text(
            text = if (engine == VoiceEngine.GEMINI) {
                "As vozes do Jarvis são geradas pelo Gemini: precisam de internet e " +
                    "consomem sua cota da API. Se falharem, ele usa a voz do aparelho " +
                    "automaticamente."
            } else {
                "A voz é gerada pelo motor do seu celular; as opções variam de aparelho " +
                    "para aparelho. Use \"Ouvir exemplo\" para conferir antes de concluir."
            },
            color = JarvisColors.TextMuted,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun GeminiVoiceCard(
    option: GeminiVoice,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    SelectableCard(selected = selected, onClick = onClick, modifier = modifier) {
        Text(
            text = option.label,
            color = JarvisColors.TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = option.description,
            color = JarvisColors.TextMuted,
            fontSize = 11.sp
        )
        Text(
            text = when (option.gender) {
                VoiceGender.FEMALE -> "FEMININA"
                VoiceGender.MALE -> "MASCULINA"
                VoiceGender.ANY -> ""
            },
            color = if (selected) JarvisColors.Success else JarvisColors.TextMuted,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

private const val MAX_VOICES = 10

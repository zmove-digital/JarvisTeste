package com.example.jarvisteste.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.speech.tts.Voice
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.jarvisteste.audio.VoiceCatalog
import com.example.jarvisteste.data.FishAudioConfig
import com.example.jarvisteste.data.FishAudioModels
import com.example.jarvisteste.data.GeminiVoice
import com.example.jarvisteste.data.GeminiVoices
import com.example.jarvisteste.data.VoiceEngine
import com.example.jarvisteste.data.VoiceGender
import com.example.jarvisteste.data.VoiceProfile
import com.example.jarvisteste.data.VoiceProfiles
import com.example.jarvisteste.data.parseFishVoiceId

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
    onPreview: () -> Unit,
    fishAudio: FishAudioConfig = FishAudioConfig(),
    showFishAudio: Boolean = true,
    onFishAudioChange: (FishAudioConfig) -> Unit = {}
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
                        text = "Vozes do Gemini",
                        color = JarvisColors.TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "As originais do Jarvis, online e naturais",
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

        if (showFishAudio) {
            SelectableCard(
                selected = engine == VoiceEngine.FISH_AUDIO,
                onClick = { onSelectEngine(VoiceEngine.FISH_AUDIO) }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Voz personalizada",
                            color = JarvisColors.TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Sua voz clonada ou uma voz do Fish Audio",
                            color = JarvisColors.TextMuted,
                            fontSize = 12.sp
                        )
                    }
                    if (engine == VoiceEngine.FISH_AUDIO) {
                        Text(
                            text = "ESCOLHIDA",
                            color = JarvisColors.Success,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        } else if (engine == VoiceEngine.FISH_AUDIO) {
            SelectableCard(selected = true, onClick = {}) {
                Text(
                    text = "Voz personalizada",
                    color = JarvisColors.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Configurada nos Ajustes do app",
                    color = JarvisColors.TextMuted,
                    fontSize = 12.sp
                )
            }
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

            VoiceEngine.FISH_AUDIO -> {
                if (showFishAudio) {
                    JarvisLabel("Voz do Fish Audio")
                    Text(
                        text = "Cole o link da voz (fish.audio/m/...) ou o ID de 32 " +
                            "caracteres. Pode ser uma voz do catálogo ou uma clonagem " +
                            "feita por você.",
                        color = JarvisColors.TextMuted,
                        fontSize = 12.sp
                    )

                    FishAudioFields(
                        config = fishAudio,
                        onConfigChange = onFishAudioChange
                    )
                } else {
                    Text(
                        text = "A voz personalizada é configurada em Ajustes → " +
                            "Motor de voz → Voz personalizada.",
                        color = JarvisColors.TextMuted,
                        fontSize = 12.sp
                    )
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
                text = if (engine == VoiceEngine.FISH_AUDIO) {
                    "TESTAR VOZ PERSONALIZADA"
                } else {
                    "OUVIR EXEMPLO"
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
        }

        Text(
            text = when (engine) {
                VoiceEngine.GEMINI ->
                    "As vozes do Jarvis são geradas pelo Gemini: precisam de internet e " +
                        "consomem sua cota da API. Se falharem, ele usa a voz do aparelho " +
                        "automaticamente."

                VoiceEngine.FISH_AUDIO ->
                    "A voz é gerada pelo Fish Audio com a sua chave. O texto da resposta " +
                        "é enviado ao Fish Audio; a chave fica cifrada neste aparelho. " +
                        "Se falhar, o Jarvis usa o Gemini ou a voz do aparelho."

                VoiceEngine.SYSTEM ->
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
private fun FishAudioFields(
    config: FishAudioConfig,
    onConfigChange: (FishAudioConfig) -> Unit
) {
    val context = LocalContext.current
    var showKey by rememberSaveable { mutableStateOf(false) }
    val parsedId = remember(config.voiceId) { parseFishVoiceId(config.voiceId) }

    OutlinedTextField(
        value = config.apiKey,
        onValueChange = { onConfigChange(config.copy(apiKey = it)) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("API key do Fish Audio") },
        singleLine = true,
        visualTransformation = if (showKey) VisualTransformation.None
        else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done
        ),
        trailingIcon = {
            Text(
                text = if (showKey) "OCULTAR" else "VER",
                color = JarvisColors.Accent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable { showKey = !showKey }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        },
        colors = jarvisFieldColors()
    )

    OutlinedTextField(
        value = config.voiceId,
        onValueChange = { onConfigChange(config.copy(voiceId = it)) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Link ou ID da voz") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Done
        ),
        colors = jarvisFieldColors()
    )

    Text(
        text = when {
            config.voiceId.isBlank() ->
                "Ex.: https://fish.audio/m/00000000000000000000000000000000"
            parsedId == null ->
                "Não encontrei um ID de 32 caracteres. Cole o link da voz ou o ID."
            else -> "Voz selecionada: $parsedId"
        },
        color = if (parsedId != null) JarvisColors.Success else JarvisColors.TextMuted,
        fontSize = 11.sp
    )

    JarvisGhostButton(
        text = "Pegar API key no Fish Audio",
        onClick = { openUrl(context, "https://fish.audio/app/api-keys") }
    )
    JarvisGhostButton(
        text = "Criar minha voz clonada",
        onClick = { openUrl(context, "https://fish.audio/voice-clone/") }
    )

    Text(
        text = "A clonagem é feita no site do Fish Audio: envie uma gravação da sua " +
            "voz, confirme que ela é sua e cole aqui o link do modelo criado.",
        color = JarvisColors.TextMuted,
        fontSize = 11.sp
    )

    JarvisLabel("Modelo")
    FishAudioModels.all.forEach { option ->
        SelectableCard(
            selected = option.id == config.model,
            onClick = { onConfigChange(config.copy(model = option.id)) }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
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
                }
                if (option.free) {
                    Text(
                        text = "GRÁTIS",
                        color = JarvisColors.Success,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun jarvisFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = JarvisColors.TextPrimary,
    unfocusedTextColor = JarvisColors.TextPrimary,
    focusedContainerColor = JarvisColors.Surface,
    unfocusedContainerColor = JarvisColors.Surface,
    focusedBorderColor = JarvisColors.BorderSelected,
    unfocusedBorderColor = JarvisColors.Border,
    focusedLabelColor = JarvisColors.Accent,
    unfocusedLabelColor = JarvisColors.TextMuted,
    cursorColor = JarvisColors.Accent
)

private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
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

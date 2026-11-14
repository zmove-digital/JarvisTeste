package com.example.jarvisteste.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.jarvisteste.audio.TtsManager
import com.example.jarvisteste.audio.VoiceCatalog
import com.example.jarvisteste.data.ConversationMemory
import com.example.jarvisteste.data.GeminiClient
import com.example.jarvisteste.data.SettingsStore
import com.example.jarvisteste.data.VoiceProfiles
import com.example.jarvisteste.ui.components.JarvisBackground
import com.example.jarvisteste.ui.components.JarvisColors
import com.example.jarvisteste.ui.components.JarvisGhostButton
import com.example.jarvisteste.ui.components.JarvisLabel
import com.example.jarvisteste.ui.components.JarvisPrimaryButton
import com.example.jarvisteste.ui.components.VoicePicker
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    settings: SettingsStore,
    tts: TtsManager,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var apiKey by remember { mutableStateOf(settings.apiKey) }
    var showKey by remember { mutableStateOf(false) }
    var wakeWord by remember { mutableStateOf(settings.wakeWord) }
    var silenceBeeps by remember { mutableStateOf(settings.silenceBeeps) }
    var profileId by rememberSaveable { mutableStateOf(settings.voiceProfileId) }
    var voiceName by remember { mutableStateOf(settings.voiceName) }
    var testing by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var feedbackIsError by remember { mutableStateOf(false) }
    var historyCleared by remember { mutableStateOf(false) }

    val profile = VoiceProfiles.byId(profileId)

    fun save(showConfirmation: Boolean = true) {
        settings.apiKey = apiKey
        settings.wakeWord = wakeWord
        settings.silenceBeeps = silenceBeeps
        settings.voiceProfileId = profile.id
        settings.voiceName = voiceName
        settings.pitch = profile.pitch
        settings.rate = profile.rate
        if (showConfirmation) {
            feedback = "Ajustes salvos."
            feedbackIsError = false
        }
    }

    fun testKey() {
        val trimmed = apiKey.trim()
        if (trimmed.isEmpty()) {
            feedback = "Informe uma chave para testar."
            feedbackIsError = true
            return
        }
        testing = true
        feedback = null
        scope.launch {
            val result = GeminiClient(trimmed).validate()
            testing = false
            result.onSuccess { model ->
                save(showConfirmation = false)
                feedback = "Chave válida! Modelo: $model"
                feedbackIsError = false
            }.onFailure {
                feedback = it.message ?: "Não foi possível validar a chave."
                feedbackIsError = true
            }
        }
    }

    JarvisBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AJUSTES",
                    color = JarvisColors.AccentSoft,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp
                )
                JarvisGhostButton(text = "Voltar", onClick = onBack)
            }

            Spacer(modifier = Modifier.height(18.dp))

            // ---------------------------------------------------------- conta
            JarvisLabel("Conta")
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Sua chave do Google AI Studio. Ela fica cifrada neste aparelho.",
                color = JarvisColors.TextMuted,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = apiKey,
                onValueChange = {
                    apiKey = it
                    feedback = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Chave da API do Gemini") },
                singleLine = true,
                enabled = !testing,
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
                colors = OutlinedTextFieldDefaults.colors(
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
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (testing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = JarvisColors.Accent,
                        strokeWidth = 2.dp
                    )
                } else {
                    JarvisGhostButton(text = "Testar", onClick = { testKey() })
                }
            }

            if (feedback != null) {
                Text(
                    text = feedback!!,
                    color = if (feedbackIsError) JarvisColors.Danger else JarvisColors.Success,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(22.dp))
            HorizontalDivider(color = ColorDivider)
            Spacer(modifier = Modifier.height(22.dp))

            // ------------------------------------------------------ ativação
            JarvisLabel("Ativação")
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Palavra (ou expressão curta) que desperta o Jarvis na escuta contínua. " +
                    "Nomes curtos e distintos funcionam melhor. Padrão: \"jarvis\".",
                color = JarvisColors.TextMuted,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = wakeWord,
                onValueChange = { wakeWord = it.take(20) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Palavra de ativação") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                colors = OutlinedTextFieldDefaults.colors(
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
            )

            Spacer(modifier = Modifier.height(18.dp))

            // ------------------------------------------------ bipes do sistema
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Silenciar bipes do reconhecedor",
                        color = JarvisColors.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "O serviço do Google toca bipes ao iniciar e encerrar a " +
                            "escuta. Esta opção silencia só o canal de notificações por " +
                            "instantes; mídia e a voz do Jarvis não são afetadas.",
                        color = JarvisColors.TextMuted,
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = silenceBeeps,
                    onCheckedChange = { silenceBeeps = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = JarvisColors.Success,
                        checkedTrackColor = JarvisColors.SuccessSurface,
                        uncheckedThumbColor = JarvisColors.TextSecondary,
                        uncheckedTrackColor = JarvisColors.SurfaceAlt,
                        uncheckedBorderColor = JarvisColors.Border
                    )
                )
            }

            Spacer(modifier = Modifier.height(22.dp))
            HorizontalDivider(color = ColorDivider)
            Spacer(modifier = Modifier.height(22.dp))

            // ----------------------------------------------------------- voz
            VoicePicker(
                voices = tts.voices,
                profile = profile,
                selectedVoiceName = voiceName,
                onSelectProfile = {
                    profileId = it.id
                    voiceName = null
                },
                onSelectVoice = { voiceName = it },
                onPreview = {
                    val resolved = VoiceCatalog.selectForProfile(tts.voices, profile, voiceName)?.name
                    tts.speak(
                        text = TtsManager.PREVIEW_TEXT,
                        voiceName = resolved,
                        pitch = profile.pitch,
                        rate = profile.rate
                    )
                }
            )

            Spacer(modifier = Modifier.height(22.dp))
            HorizontalDivider(color = ColorDivider)
            Spacer(modifier = Modifier.height(22.dp))

            // ------------------------------------------------------- conversa
            JarvisLabel("Conversa")
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = if (historyCleared) {
                    "Histórico limpo. O Jarvis começa sem memória da conversa."
                } else {
                    "O Jarvis guarda as últimas mensagens em memória para manter o contexto."
                },
                color = JarvisColors.TextMuted,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            JarvisGhostButton(
                text = "Limpar histórico",
                onClick = {
                    ConversationMemory.clear()
                    historyCleared = true
                }
            )

            Spacer(modifier = Modifier.height(22.dp))
            HorizontalDivider(color = ColorDivider)
            Spacer(modifier = Modifier.height(22.dp))

            JarvisLabel("Sobre")
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Jarvis 1.0 • Assistente de voz com Gemini\n" +
                    "Chamadas diretas do aparelho ao Google, sem servidor intermediário.",
                color = JarvisColors.TextMuted,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(28.dp))

            JarvisPrimaryButton(text = "Salvar ajustes", onClick = { save() })

            Spacer(modifier = Modifier.height(22.dp))
        }
    }
}

private val ColorDivider = androidx.compose.ui.graphics.Color(0xFF263747)

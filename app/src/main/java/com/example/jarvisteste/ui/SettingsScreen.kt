package com.example.jarvisteste.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.jarvisteste.audio.TtsManager
import com.example.jarvisteste.audio.VoiceManager
import com.example.jarvisteste.data.ConversationMemory
import com.example.jarvisteste.data.FishAudioConfig
import com.example.jarvisteste.data.GeminiClient
import com.example.jarvisteste.data.SettingsStore
import com.example.jarvisteste.data.VoiceEngine
import com.example.jarvisteste.data.looksLikeFishKey
import com.example.jarvisteste.data.looksLikeGeminiKey
import com.example.jarvisteste.data.VoiceProfiles
import com.example.jarvisteste.ui.avatar.AvatarActivity
import com.example.jarvisteste.ui.avatar.AvatarMood
import com.example.jarvisteste.ui.avatar.AvatarPresenter
import com.example.jarvisteste.ui.avatar.JarvisOrb
import com.example.jarvisteste.ui.components.HudPanel
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
    voice: VoiceManager,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val fullScreenAllowed = rememberFullScreenIntentAllowed()
    val overlayAllowed = rememberOverlayAllowed()
    val accessibilityAllowed = rememberAccessibilityAllowed()

    var apiKey by remember { mutableStateOf(settings.apiKey) }
    var showKey by remember { mutableStateOf(false) }
    var wakeWord by remember { mutableStateOf(settings.wakeWord) }
    var silenceBeeps by remember { mutableStateOf(settings.silenceBeeps) }
    var profileId by rememberSaveable { mutableStateOf(settings.voiceProfileId) }
    var engineName by rememberSaveable { mutableStateOf(settings.voiceEngine.name) }
    var geminiVoiceId by rememberSaveable { mutableStateOf(settings.geminiVoiceId) }
    var voiceName by remember { mutableStateOf(settings.voiceName) }
    var fishApiKey by remember { mutableStateOf(settings.fishAudioApiKey) }
    var fishVoiceId by rememberSaveable { mutableStateOf(settings.fishAudioVoiceId) }
    var fishVoiceName by rememberSaveable { mutableStateOf(settings.fishAudioVoiceName) }
    var fishModel by rememberSaveable { mutableStateOf(settings.fishAudioModel) }
    var testing by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var feedbackIsError by remember { mutableStateOf(false) }
    var voiceFeedback by remember { mutableStateOf<String?>(null) }
    var voiceFeedbackIsError by remember { mutableStateOf(false) }
    var historyCleared by remember { mutableStateOf(false) }

    val profile = VoiceProfiles.byId(profileId)
    val engine = VoiceEngine.valueOf(engineName)
    val fishConfig = FishAudioConfig(
        apiKey = fishApiKey,
        voiceId = fishVoiceId,
        voiceName = fishVoiceName,
        model = fishModel
    )

    fun save(showConfirmation: Boolean = true) {
        if (looksLikeFishKey(apiKey)) {
            feedback = "Essa é uma chave do Fish Audio, não do Gemini. " +
                "Cole a chave do Gemini (começa com AIza) aqui e use a do Fish Audio " +
                "em Motor de voz → Voz personalizada."
            feedbackIsError = true
            return
        }
        settings.apiKey = apiKey
        settings.wakeWord = wakeWord
        settings.silenceBeeps = silenceBeeps
        settings.voiceEngine = engine
        settings.geminiVoiceId = geminiVoiceId
        settings.fishAudioApiKey = fishApiKey
        settings.fishAudioVoiceId = fishConfig.resolvedVoiceId
        settings.fishAudioVoiceName = fishVoiceName
        settings.fishAudioModel = fishModel
        settings.voiceProfileId = profile.id
        settings.voiceName = voiceName
        settings.pitch = profile.pitch
        settings.rate = profile.rate
        voiceFeedback = null
        // Deixa o "Pois não?" do despertar pronto na voz recém-escolhida.
        voice.prewarmPrompt("Pois não?", settings)
        if (showConfirmation) {
            feedback = "Ajustes salvos."
            feedbackIsError = false
        }
        if (engine == VoiceEngine.FISH_AUDIO && !fishConfig.ready) {
            voiceFeedback = "A voz personalizada precisa de API key e link/ID. " +
                "Salvo mesmo assim: o Jarvis usará o Gemini ou a voz do aparelho."
            voiceFeedbackIsError = true
        }
    }

    fun testKey() {
        val trimmed = apiKey.trim()
        if (trimmed.isEmpty()) {
            feedback = "Informe uma chave para testar."
            feedbackIsError = true
            return
        }
        if (looksLikeFishKey(trimmed)) {
            feedback = "Essa é uma chave do Fish Audio. Ela vai em " +
                "Motor de voz → Voz personalizada; aqui é a chave do Gemini (AIza...)."
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
                text = "Sua chave do Google AI Studio (começa com AIza). Ela fica cifrada " +
                    "neste aparelho. A chave do Fish Audio vai em Motor de voz → Voz " +
                    "personalizada.",
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
            HorizontalDivider(color = JarvisColors.Border)
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
                        checkedThumbColor = JarvisColors.Accent,
                        checkedTrackColor = JarvisColors.SuccessSurface,
                        uncheckedThumbColor = JarvisColors.TextSecondary,
                        uncheckedTrackColor = JarvisColors.SurfaceAlt,
                        uncheckedBorderColor = JarvisColors.Border
                    )
                )
            }

            Spacer(modifier = Modifier.height(22.dp))
            HorizontalDivider(color = JarvisColors.Border)
            Spacer(modifier = Modifier.height(22.dp))

            // ----------------------------------------------------------- voz
            VoicePicker(
                voices = voice.voices,
                engine = engine,
                geminiVoiceId = geminiVoiceId,
                profile = profile,
                selectedVoiceName = voiceName,
                fishAudio = fishConfig,
                onFishAudioChange = {
                    fishApiKey = it.apiKey
                    fishVoiceId = it.voiceId
                    fishVoiceName = it.voiceName
                    fishModel = it.model
                    voiceFeedback = null
                },
                onSelectEngine = {
                    engineName = it.name
                    voiceFeedback = null
                },
                onSelectGeminiVoice = { geminiVoiceId = it },
                onSelectProfile = {
                    profileId = it.id
                    voiceName = null
                },
                onSelectVoice = { voiceName = it },
                onPreview = {
                    val missing = when {
                        engine != VoiceEngine.FISH_AUDIO -> null
                        fishConfig.apiKey.isBlank() -> "Informe a API key do Fish Audio."
                        looksLikeGeminiKey(fishConfig.apiKey) ->
                            "Essa é uma chave do Gemini. Cole aqui a chave do Fish Audio " +
                                "(começa com sk-fish-)."
                        fishConfig.resolvedVoiceId.isBlank() ->
                            "Cole o link ou o ID da voz do Fish Audio."
                        else -> null
                    }
                    if (missing != null) {
                        voiceFeedback = missing
                        voiceFeedbackIsError = true
                    } else {
                        voiceFeedback = null
                        voice.speak(
                            text = TtsManager.PREVIEW_TEXT,
                            engine = engine,
                            geminiVoiceId = geminiVoiceId,
                            profile = profile,
                            voiceName = voiceName,
                            pitch = profile.pitch,
                            rate = profile.rate,
                            fishAudioConfig = fishConfig,
                            onError = {
                                voiceFeedback = it
                                voiceFeedbackIsError = true
                            }
                        )
                    }
                }
            )

            if (voiceFeedback != null) {
                Text(
                    text = voiceFeedback!!,
                    color = if (voiceFeedbackIsError) JarvisColors.Danger else JarvisColors.Success,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(22.dp))
            HorizontalDivider(color = JarvisColors.Border)
            Spacer(modifier = Modifier.height(22.dp))

            // ---------------------------------------------------------- avatar
            JarvisLabel("Avatar")
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "O orbe de energia do Jarvis. Ele vibra de forma frenética com a " +
                    "sua voz e pulsa quando o Jarvis responde.",
                color = JarvisColors.TextMuted,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(14.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp),
                contentAlignment = Alignment.Center
            ) {
                JarvisOrb(
                    mood = AvatarMood.IDLE,
                    modifier = Modifier.size(180.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            JarvisGhostButton(
                text = "Ver em tela cheia",
                onClick = {
                    save(showConfirmation = false)
                    context.startActivity(
                        Intent(context, AvatarActivity::class.java)
                            .putExtra(AvatarActivity.EXTRA_DEMO, true)
                    )
                }
            )

            if (!fullScreenAllowed) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(6.dp),
                    color = JarvisColors.SurfaceAlt,
                    border = BorderStroke(1.dp, JarvisColors.BorderSelected)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Permita acender a tela",
                            color = JarvisColors.AccentSoft,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "No Android 14 ou superior, o sistema precisa autorizar " +
                                "o Jarvis a abrir a tela cheia com o aparelho bloqueado. " +
                                "Ative \"Permitir notificações de tela cheia\".",
                            color = JarvisColors.TextMuted,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        JarvisGhostButton(
                            text = "Autorizar",
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                    )
                                }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))
            HorizontalDivider(color = JarvisColors.Border)
            Spacer(modifier = Modifier.height(22.dp))

            // ------------------------------------------------- ações no aparelho
            JarvisLabel("Ações no aparelho")
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "O Jarvis pode abrir aplicativos e playlists no Spotify, abrir conversas " +
                    "e ligar no WhatsApp, pesquisar na web, fazer chamadas telefônicas em " +
                    "viva-voz, controlar a música e criar alarmes e timers. Telefone e " +
                    "contatos são pedidos ao ligar a Escuta contínua.",
                color = JarvisColors.TextMuted,
                fontSize = 12.sp
            )

            if (overlayAllowed) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Permissão para abrir apps sobre outros apps: ativa.",
                    color = JarvisColors.Success,
                    fontSize = 12.sp
                )
            } else {
                Spacer(modifier = Modifier.height(12.dp))
                HudPanel(label = "Permissão de inicialização", modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Permita abrir apps sobre outros apps",
                            color = JarvisColors.AccentSoft,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Sem isso, o Jarvis só abre aplicativos quando o app já está " +
                                "na tela. Ative \"Aparecer sobre outros apps\".",
                            color = JarvisColors.TextMuted,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        JarvisGhostButton(
                            text = "Autorizar",
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                    )
                                }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (accessibilityAllowed) {
                Text(
                    text = "Acessibilidade para ligar no WhatsApp: ativa.",
                    color = JarvisColors.Success,
                    fontSize = 12.sp
                )
            } else {
                HudPanel(label = "Acessibilidade", modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Acessibilidade para ligar no WhatsApp",
                            color = JarvisColors.AccentSoft,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "O WhatsApp não tem API para iniciar chamadas. Com a " +
                                "Acessibilidade ativa, o Jarvis toca no botão de chamada e " +
                                "no viva-voz por você. Pode falhar se o app mudar de layout.",
                            color = JarvisColors.TextMuted,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        JarvisGhostButton(
                            text = "Autorizar",
                            onClick = {
                                runCatching {
                                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))
            HorizontalDivider(color = JarvisColors.Border)
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
            HorizontalDivider(color = JarvisColors.Border)
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

@Composable
private fun rememberOverlayAllowed(): Boolean {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var allowed by remember {
        mutableStateOf(Settings.canDrawOverlays(context))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                allowed = Settings.canDrawOverlays(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return allowed
}

@Composable
private fun rememberAccessibilityAllowed(): Boolean {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    fun check(): Boolean = runCatching {
        val enabled = Settings.Secure.getInt(
            context.contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            0
        ) == 1
        val services = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        val pkg = context.packageName
        enabled && (
            services.contains("$pkg/.accessibility.WhatsAppCallService") ||
                services.contains("$pkg/$pkg.accessibility.WhatsAppCallService")
            )
    }.getOrDefault(false)

    var allowed by remember { mutableStateOf(check()) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) allowed = check()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return allowed
}

@Composable
private fun rememberFullScreenIntentAllowed(): Boolean {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var allowed by remember {
        mutableStateOf(AvatarPresenter.canUseFullScreenIntent(context))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                allowed = AvatarPresenter.canUseFullScreenIntent(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return allowed
}

package com.example.jarvisteste.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.jarvisteste.audio.TtsManager
import com.example.jarvisteste.audio.VoiceManager
import com.example.jarvisteste.data.GeminiClient
import com.example.jarvisteste.data.SettingsStore
import com.example.jarvisteste.data.VoiceEngine
import com.example.jarvisteste.data.VoiceProfiles
import com.example.jarvisteste.ui.components.JarvisBackground
import com.example.jarvisteste.ui.components.JarvisColors
import com.example.jarvisteste.ui.components.JarvisGhostButton
import com.example.jarvisteste.ui.components.JarvisPrimaryButton
import com.example.jarvisteste.ui.components.VoicePicker
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    settings: SettingsStore,
    voice: VoiceManager,
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var step by rememberSaveable { mutableIntStateOf(0) }
    var apiKey by remember { mutableStateOf(settings.apiKey) }
    var showKey by remember { mutableStateOf(false) }
    var validating by remember { mutableStateOf(false) }
    var keyError by remember { mutableStateOf<String?>(null) }
    var profileId by rememberSaveable { mutableStateOf(settings.voiceProfileId) }
    var engineName by rememberSaveable { mutableStateOf(settings.voiceEngine.name) }
    var geminiVoiceId by rememberSaveable { mutableStateOf(settings.geminiVoiceId) }
    var voiceName by remember { mutableStateOf(settings.voiceName) }

    val profile = VoiceProfiles.byId(profileId)
    val engine = VoiceEngine.valueOf(engineName)

    BackHandler(enabled = step > 0 && !validating) {
        step -= 1
    }

    fun persistKey(value: String) {
        settings.apiKey = value
    }

    fun validateAndContinue() {
        val trimmed = apiKey.trim()
        if (trimmed.isEmpty()) {
            keyError = "Cole sua chave da API para continuar."
            return
        }
        keyError = null
        validating = true
        scope.launch {
            val result = GeminiClient(trimmed).validate()
            validating = false
            result.onSuccess {
                persistKey(trimmed)
                step = 2
            }.onFailure {
                keyError = it.message ?: "Não foi possível validar a chave."
            }
        }
    }

    fun finish() {
        settings.apiKey = apiKey
        settings.voiceEngine = engine
        settings.geminiVoiceId = geminiVoiceId
        settings.voiceProfileId = profile.id
        settings.voiceName = voiceName
        settings.pitch = profile.pitch
        settings.rate = profile.rate
        settings.onboarded = true
        voice.stop()
        onFinished()
    }

    fun previewVoice() {
        voice.speak(
            text = TtsManager.PREVIEW_TEXT,
            engine = engine,
            geminiVoiceId = geminiVoiceId,
            profile = profile,
            voiceName = voiceName,
            pitch = profile.pitch,
            rate = profile.rate
        )
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
                    text = "J.A.R.V.I.S.",
                    color = JarvisColors.AccentSoft,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp
                )
                Text(
                    text = "PASSO ${step + 1} DE 3",
                    color = JarvisColors.TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
            }

            Spacer(modifier = Modifier.height(36.dp))

            when (step) {
                0 -> WelcomeStep(onStart = { step = 1 })
                1 -> ApiKeyStep(
                    apiKey = apiKey,
                    onApiKeyChange = {
                        apiKey = it
                        keyError = null
                    },
                    showKey = showKey,
                    onToggleShowKey = { showKey = !showKey },
                    validating = validating,
                    error = keyError,
                    onValidate = { validateAndContinue() },
                    onPaste = {
                        apiKey = readClipboard(context)
                        keyError = null
                    },
                    onOpenAiStudio = {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://aistudio.google.com/apikey")
                                )
                            )
                        }
                    },
                    onSkipValidation = if (apiKey.isNotBlank() && keyError != null) {
                        {
                            persistKey(apiKey)
                            step = 2
                        }
                    } else {
                        null
                    },
                    onBack = { step = 0 }
                )

                else -> VoiceStep(
                    voicePicker = {
                        VoicePicker(
                            voices = voice.voices,
                            engine = engine,
                            geminiVoiceId = geminiVoiceId,
                            profile = profile,
                            selectedVoiceName = voiceName,
                            onSelectEngine = { engineName = it.name },
                            onSelectGeminiVoice = { geminiVoiceId = it },
                            onSelectProfile = {
                                profileId = it.id
                                voiceName = null
                            },
                            onSelectVoice = { voiceName = it },
                            onPreview = { previewVoice() }
                        )
                    },
                    onBack = { step = 1 },
                    onFinish = { finish() }
                )
            }
        }
    }
}

@Composable
private fun WelcomeStep(onStart: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(150.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF245B82),
                            Color(0xFF152D47),
                            Color(0xFF101C2B)
                        )
                    ),
                    shape = CircleShape
                )
                .border(1.dp, Color(0xFF438BB8), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "J",
                color = JarvisColors.AccentSoft,
                fontSize = 62.sp,
                fontWeight = FontWeight.Light
            )
        }

        Spacer(modifier = Modifier.height(30.dp))

        Text(
            text = "Bem-vindo ao Jarvis",
            color = JarvisColors.TextPrimary,
            fontSize = 26.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Seu assistente de voz, direto no celular. Sem servidor, sem intermediários.",
            color = JarvisColors.TextSecondary,
            fontSize = 15.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(34.dp))

        WelcomeBullet(
            title = "Sua chave, seu controle",
            description = "Você usa a sua chave gratuita do Google AI. Ela fica cifrada " +
                "no aparelho e vai apenas para o Google."
        )
        WelcomeBullet(
            title = "A voz é sua",
            description = "Escolha uma voz feminina, masculina ou um estilo de personagem."
        )
        WelcomeBullet(
            title = "\"Jarvis\" para acordar",
            description = "Ele fica ouvindo em segundo plano e só responde quando você chama " +
                "pelo nome."
        )

        Spacer(modifier = Modifier.height(30.dp))

        JarvisPrimaryButton(text = "Começar", onClick = onStart)
    }
}

@Composable
private fun WelcomeBullet(title: String, description: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp)
    ) {
        Text(
            text = "•",
            color = JarvisColors.Accent,
            fontSize = 16.sp,
            modifier = Modifier.padding(end = 10.dp)
        )
        Column {
            Text(
                text = title,
                color = JarvisColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                color = JarvisColors.TextMuted,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun ApiKeyStep(
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    showKey: Boolean,
    onToggleShowKey: () -> Unit,
    validating: Boolean,
    error: String?,
    onValidate: () -> Unit,
    onPaste: () -> Unit,
    onOpenAiStudio: () -> Unit,
    onSkipValidation: (() -> Unit)?,
    onBack: () -> Unit
) {
    Column {
        Text(
            text = "Conecte sua conta do Google",
            color = JarvisColors.TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Crie uma chave gratuita no Google AI Studio e cole abaixo. " +
                "Ela fica cifrada no seu celular e é usada só para falar com o Gemini.",
            color = JarvisColors.TextSecondary,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "aistudio.google.com/apikey",
            color = JarvisColors.Accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.clickable(onClick = onOpenAiStudio)
        )

        Spacer(modifier = Modifier.height(26.dp))

        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Chave da API do Gemini") },
            singleLine = true,
            enabled = !validating,
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
                        .clickable(onClick = onToggleShowKey)
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
            horizontalArrangement = Arrangement.End
        ) {
            JarvisGhostButton(text = "Colar", onClick = onPaste, enabled = !validating)
        }

        if (error != null) {
            Text(
                text = error,
                color = JarvisColors.Danger,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (validating) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = JarvisColors.Accent,
                    strokeWidth = 2.dp
                )
                Text(
                    text = "  Testando sua chave...",
                    color = JarvisColors.TextSecondary,
                    fontSize = 13.sp
                )
            }
        } else {
            JarvisPrimaryButton(text = "Validar e continuar", onClick = onValidate)
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            JarvisGhostButton(text = "Voltar", onClick = onBack, enabled = !validating)
            if (onSkipValidation != null) {
                JarvisGhostButton(
                    text = "Continuar sem testar",
                    onClick = onSkipValidation,
                    enabled = !validating
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Sua chave nunca sai do aparelho, exceto nas chamadas diretas ao Google. " +
                "Você pode trocá-la depois em Ajustes.",
            color = JarvisColors.TextMuted,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun VoiceStep(
    voicePicker: @Composable () -> Unit,
    onBack: () -> Unit,
    onFinish: () -> Unit
) {
    Column {
        Text(
            text = "Escolha a voz do Jarvis",
            color = JarvisColors.TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Dá para mudar quando quiser em Ajustes.",
            color = JarvisColors.TextSecondary,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        voicePicker()

        Spacer(modifier = Modifier.height(24.dp))

        JarvisPrimaryButton(text = "Concluir", onClick = onFinish)

        Spacer(modifier = Modifier.height(6.dp))

        JarvisGhostButton(text = "Voltar", onClick = onBack)
    }
}

private fun readClipboard(context: Context): String {
    return runCatching {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        manager.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty().trim()
    }.getOrDefault("")
}

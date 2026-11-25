package com.example.jarvisteste.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.jarvisteste.actions.JarvisActions
import com.example.jarvisteste.audio.MicLevel
import com.example.jarvisteste.audio.VoiceLevel
import com.example.jarvisteste.audio.VoiceManager
import com.example.jarvisteste.audio.VoiceSource
import com.example.jarvisteste.data.GeminiClient
import com.example.jarvisteste.data.SettingsStore
import com.example.jarvisteste.service.JarvisListeningService
import com.example.jarvisteste.service.ListeningPhase
import com.example.jarvisteste.ui.avatar.AvatarActivity
import com.example.jarvisteste.ui.avatar.AvatarMood
import com.example.jarvisteste.ui.avatar.JarvisOrb
import com.example.jarvisteste.ui.avatar.toAvatarMood
import com.example.jarvisteste.ui.components.HudDataStrip
import com.example.jarvisteste.ui.components.HudPanel
import com.example.jarvisteste.ui.components.HudReticle
import com.example.jarvisteste.ui.components.JarvisBackground
import com.example.jarvisteste.ui.components.JarvisColors
import com.example.jarvisteste.ui.components.VoiceLockPanel
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun HomeScreen(
    settings: SettingsStore,
    voice: VoiceManager,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current
    val scope = rememberCoroutineScope()

    var status by remember { mutableStateOf("Toque na bolinha para falar com JARVIS.") }
    var recognizedText by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }

    val serviceRunning by JarvisListeningService.running.collectAsState()
    val serviceStatus by JarvisListeningService.status.collectAsState()
    val voiceSource by VoiceLevel.source.collectAsState()
    val jarvisSpeaking = voiceSource != VoiceSource.NONE

    val effectiveStatus = if (serviceRunning) serviceStatus.message else status
    val effectiveTranscript = if (serviceRunning) serviceStatus.transcript else recognizedText
    val wakeWord = settings.wakeWord.replaceFirstChar { it.uppercase(Locale.ROOT) }
    val listeningActive = serviceRunning && serviceStatus.phase != ListeningPhase.ERROR
    val avatarMood = when {
        serviceRunning -> serviceStatus.phase.toAvatarMood()
        isListening -> AvatarMood.LISTENING
        else -> AvatarMood.IDLE
    }

    fun askGemini(message: String) {
        if (!settings.hasApiKey) {
            status = "Informe sua chave da API em AJUSTES para conversar."
            return
        }
        status = "Consultando o Gemini..."
        scope.launch {
            val confirmation = JarvisActions.confirmOrCancel(context, message)
            if (confirmation != null) {
                status = confirmation
                voice.speak(confirmation, settings)
                return@launch
            }
            val result = GeminiClient(settings.apiKey).ask(message, JarvisActions.tools(context))
            result.onSuccess { reply ->
                status = reply.text
                voice.speak(reply.text, settings)
            }.onFailure { error ->
                status = "Erro: ${error.message ?: "falha desconhecida"}"
            }
        }
    }

    val recognizer = remember(context, isPreview) {
        if (isPreview || !SpeechRecognizer.isRecognitionAvailable(context)) {
            null
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }
    }

    val speechIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "pt-BR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
    }

    DisposableEffect(recognizer) {
        if (recognizer != null) {
            recognizer.setRecognitionListener(
                object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        status = "Estou ouvindo..."
                        isListening = true
                    }

                    override fun onBeginningOfSpeech() {
                        status = "Pode falar, estou ouvindo."
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        MicLevel.publish(rmsdB)
                    }

                    override fun onBufferReceived(buffer: ByteArray?) = Unit

                    override fun onEndOfSpeech() {
                        status = "Processando sua fala..."
                        isListening = false
                    }

                    override fun onError(error: Int) {
                        isListening = false
                        status = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "Erro no áudio."
                            SpeechRecognizer.ERROR_CLIENT -> "Erro no reconhecimento."
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                                "Permissão do microfone não concedida."
                            SpeechRecognizer.ERROR_NETWORK -> "Erro de conexão."
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Tempo de conexão esgotado."
                            SpeechRecognizer.ERROR_NO_MATCH ->
                                "Não consegui entender. Tente novamente."
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                                "Reconhecimento ocupado. Tente novamente."
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Não detectei sua voz."
                            else -> "Erro ao reconhecer a voz: $error"
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        isListening = false
                        val message = results
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            ?.trim()
                        if (!message.isNullOrEmpty()) {
                            recognizedText = message
                            askGemini(message)
                        } else {
                            status = "Não consegui entender. Tente novamente."
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )
                        if (!matches.isNullOrEmpty()) {
                            recognizedText = matches[0]
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                }
            )
        }
        onDispose { recognizer?.destroy() }
    }

    fun startContinuous() {
        if (!settings.hasApiKey) {
            status = "Informe sua chave da API em AJUSTES antes de ativar a escuta."
            return
        }
        try {
            JarvisListeningService.start(context)
            settings.alwaysListening = true
        } catch (error: Exception) {
            status = "Não foi possível iniciar a escuta: ${error.message}"
        }
    }

    fun stopContinuous() {
        JarvisListeningService.stop(context)
        settings.alwaysListening = false
        status = "Escuta contínua desligada."
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            try {
                recognizedText = ""
                status = "Iniciando microfone..."
                recognizer?.startListening(speechIntent)
            } catch (error: Exception) {
                isListening = false
                status = "Não foi possível iniciar o microfone."
            }
        } else {
            status = "Permissão do microfone negada."
        }
    }

    val servicePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val recordGranted = grants[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        if (recordGranted) startContinuous() else status = "Permissão do microfone negada."
    }

    fun toggleContinuousListening(enable: Boolean) {
        if (!enable) {
            stopContinuous()
            return
        }
        val required = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            required.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = required.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            startContinuous()
        } else {
            servicePermissionLauncher.launch(missing.toTypedArray())
        }
    }

    fun startMicrophone() {
        if (recognizer == null) {
            status = "Reconhecimento de voz indisponível."
            return
        }
        if (!settings.hasApiKey) {
            status = "Informe sua chave da API em AJUSTES para conversar."
            return
        }
        val permission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        )
        if (permission == PackageManager.PERMISSION_GRANTED) {
            try {
                recognizedText = ""
                status = "Iniciando microfone..."
                recognizer.startListening(speechIntent)
            } catch (error: Exception) {
                isListening = false
                status = "Erro ao iniciar o microfone."
            }
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    JarvisBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // ------------------------------------------------------ cabeçalho
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "J.A.R.V.I.S.",
                    color = JarvisColors.AccentSoft,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "AJUSTES",
                        color = JarvisColors.Accent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier
                            .clickable(onClick = onOpenSettings)
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    )

                    Surface(
                        shape = CutCornerShape(6.dp),
                        color = when {
                            listeningActive && jarvisSpeaking -> JarvisColors.DangerStrong
                            listeningActive -> JarvisColors.DangerSurface
                            !settings.hasApiKey -> JarvisColors.DangerSurface
                            else -> JarvisColors.Surface
                        }
                    ) {
                        Text(
                            text = when {
                                listeningActive && jarvisSpeaking -> "  FALANDO  "
                                listeningActive -> "  ESCUTANDO  "
                                !settings.hasApiKey -> "  SEM CHAVE  "
                                else -> "  PRONTO  "
                            },
                            modifier = Modifier.padding(vertical = 7.dp),
                            color = when {
                                listeningActive && jarvisSpeaking -> Color.White
                                listeningActive -> JarvisColors.Danger
                                !settings.hasApiKey -> JarvisColors.Danger
                                else -> JarvisColors.TextSecondary
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            HudDataStrip(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(9.dp),
                filled = if (listeningActive) 7 else 4
            )

            Spacer(modifier = Modifier.height(34.dp))

            // ------------------------------------------------- identidade visual
            Box(
                modifier = Modifier
                    .size(260.dp)
                    .clickable {
                        context.startActivity(
                            Intent(context, AvatarActivity::class.java)
                                .putExtra(AvatarActivity.EXTRA_DEMO, true)
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                HudReticle(modifier = Modifier.fillMaxSize())
                JarvisOrb(
                    mood = avatarMood,
                    modifier = Modifier.fillMaxSize(0.82f)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Olá, estou aqui.",
                color = Color.White,
                fontSize = 27.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Como posso ajudar?",
                color = JarvisColors.TextSecondary,
                fontSize = 16.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            if (!settings.hasApiKey) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenSettings),
                    shape = CutCornerShape(8.dp),
                    color = JarvisColors.DangerSurface
                ) {
                    Text(
                        text = "Falta configurar sua chave do Gemini. Toque em AJUSTES.",
                        modifier = Modifier.padding(14.dp),
                        color = JarvisColors.Danger,
                        fontSize = 12.sp
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))
            }

            // ------------------------------------------------ área de conversa
            HudPanel(label = "Assistente", modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = effectiveStatus,
                        color = Color.White,
                        fontSize = 16.sp
                    )

                    if (effectiveTranscript.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "VOCÊ DISSE:",
                            color = JarvisColors.Accent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = effectiveTranscript,
                            color = Color.White,
                            fontSize = 17.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // ------------------------------------------------ escuta contínua
            HudPanel(label = "Escuta contínua", modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Escuta contínua",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "Sempre ouvindo por \"$wakeWord\", mesmo com a tela apagada.",
                            color = JarvisColors.TextMuted,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = serviceRunning,
                        onCheckedChange = { toggleContinuousListening(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = JarvisColors.DangerStrong,
                            checkedTrackColor = JarvisColors.DangerSurface,
                            uncheckedThumbColor = JarvisColors.TextSecondary,
                            uncheckedTrackColor = JarvisColors.SurfaceAlt,
                            uncheckedBorderColor = JarvisColors.Border
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // ------------------------------------------------ voz autorizada
            VoiceLockPanel(settings = settings)

            Spacer(modifier = Modifier.height(34.dp))

            // ------------------------------------------- botão ON horizontal
            Box(
                modifier = Modifier
                    .width(168.dp)
                    .height(56.dp)
                    .background(
                        when {
                            listeningActive && jarvisSpeaking -> JarvisColors.DangerStrong
                            listeningActive -> JarvisColors.DangerSurface
                            isListening -> JarvisColors.DangerSurface
                            else -> JarvisColors.SurfaceSelected
                        },
                        CutCornerShape(10.dp)
                    )
                    .border(
                        1.dp,
                        when {
                            listeningActive && jarvisSpeaking -> Color.White
                            listeningActive -> JarvisColors.Danger
                            isListening -> JarvisColors.Danger
                            else -> JarvisColors.Accent
                        },
                        CutCornerShape(10.dp)
                    )
                    .clickable {
                        when {
                            serviceRunning -> stopContinuous()
                            isListening -> {
                                recognizer?.stopListening()
                                isListening = false
                                status = "Escuta interrompida."
                            }

                            else -> startMicrophone()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 6.dp, height = 22.dp)
                            .background(
                                when {
                                    listeningActive && jarvisSpeaking -> Color.White
                                    listeningActive -> JarvisColors.DangerStrong
                                    isListening -> JarvisColors.Danger
                                    else -> JarvisColors.Accent
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = when {
                            listeningActive && jarvisSpeaking -> "ON  FALANDO"
                            listeningActive -> "ON"
                            isListening -> "..."
                            else -> "MIC"
                        },
                        color = when {
                            listeningActive && jarvisSpeaking -> Color.White
                            listeningActive -> JarvisColors.Danger
                            else -> Color.White
                        },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(15.dp))

            Text(
                text = if (listeningActive) "ESCUTA CONTÍNUA ATIVA" else "CONTROLE DE VOZ",
                color = when {
                    listeningActive && jarvisSpeaking -> JarvisColors.DangerStrong
                    listeningActive -> JarvisColors.Danger
                    else -> JarvisColors.AccentSoft
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = when {
                    listeningActive && settings.voiceLockEnabled ->
                        "Diga \"$wakeWord\" com sua voz cadastrada. Toque em ON para desligar."
                    listeningActive ->
                        "Diga \"$wakeWord\" e faça seu pedido. Toque em ON para desligar."
                    isListening -> "JARVIS está ouvindo você."
                    serviceRunning && serviceStatus.phase == ListeningPhase.ERROR ->
                        serviceStatus.message
                    else -> "Toque na bolinha para falar com JARVIS."
                },
                color = JarvisColors.TextMuted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(38.dp))

            HorizontalDivider(color = JarvisColors.Border)

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "SEU ASSISTENTE. SUAS REGRAS.",
                color = JarvisColors.TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.5.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(15.dp))

            Text(
                text = "JARVIS  •  GEMINI DIRETO NO APARELHO",
                color = JarvisColors.TextSecondary,
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

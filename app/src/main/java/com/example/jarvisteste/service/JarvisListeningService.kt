package com.example.jarvisteste.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.jarvisteste.MainActivity
import com.example.jarvisteste.R
import com.example.jarvisteste.actions.JarvisActions
import com.example.jarvisteste.audio.MicCaptureBuffer
import com.example.jarvisteste.audio.MicLevel
import com.example.jarvisteste.audio.RecognitionBeepSilencer
import com.example.jarvisteste.audio.VoiceFeatures
import com.example.jarvisteste.audio.VoiceManager
import com.example.jarvisteste.audio.VoiceprintMatcher
import com.example.jarvisteste.data.GeminiClient
import com.example.jarvisteste.data.SettingsStore
import com.example.jarvisteste.data.VoiceprintStore
import com.example.jarvisteste.ui.avatar.AvatarPresenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.util.Locale

enum class ListeningPhase { STOPPED, LISTENING, AWAKE, THINKING, SPEAKING, ERROR }

data class ListeningStatus(
    val phase: ListeningPhase,
    val message: String,
    val transcript: String = ""
)

/**
 * Serviço em primeiro plano que mantém o microfone ativo (inclusive com a tela
 * apagada) até ouvir a palavra de ativação. Só manda texto ao Gemini depois que
 * o usuário chama o Jarvis pelo nome, e fala a resposta antes de voltar a ouvir.
 */
class JarvisListeningService : Service(), RecognitionListener {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var recognizer: SpeechRecognizer? = null
    private var voice: VoiceManager? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var beepSilencer: RecognitionBeepSilencer? = null
    private var avatarPresenter: AvatarPresenter? = null
    private var micCapture: MicCaptureBuffer? = null
    private var pendingVoiceSnapshot: FloatArray? = null

    private var awake = false
    private var speaking = false
    private var verifying = false
    private var destroyed = false
    private var speechToken = 0
    private var lastTranscript = ""

    private val restartRunnable = Runnable { startListening() }

    /**
     * Encerra o modo "acordado": depois de uma resposta (ou de um "Pois não?")
     * o Jarvis aceita a próxima fala sem exigir o nome, mas só por alguns
     * segundos — evita mandar conversa ambiente ao Gemini para sempre.
     */
    private val endAwakeRunnable = Runnable {
        if (destroyed || speaking || verifying || !awake) return@Runnable
        awake = false
        Log.i(TAG, "Janela de conversa encerrada; voltando à palavra de ativação")
        avatarPresenter?.hide()
        publishStatus(ListeningPhase.LISTENING, wakeListeningMessage())
    }

    /**
     * O reconhecedor encerra sozinho após ~5 s de silêncio e toca o bipe de
     * "falha" ANTES do callback onError. Esta tarefa adianta o silêncio para
     * cobrir esse bipe.
     */
    private val preemptiveBeepMute = Runnable {
        if (!destroyed && !speaking && SettingsStore(this).silenceBeeps) {
            beepSilencer?.suppress(PREEMPTIVE_WINDOW_MS)
        }
    }

    private val speechRecognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "pt-BR")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        // Mantém a sessão de escuta aberta por mais tempo em silêncio.
        // Sem isso o reconhecedor do Google encerra a cada ~6 s, e cada
        // reinício toca o "bip" de início/fim (efeito de metralhadora).
        putExtra(
            RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
            SILENCE_LENGTH_MS
        )
        putExtra(
            RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
            SILENCE_LENGTH_MS
        )
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForegroundCompat(notificationFor(currentStatus()))

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Jarvis::ListeningWakeLock"
        ).apply {
            setReferenceCounted(false)
            acquire()
        }

        voice = VoiceManager(this) { SettingsStore(this).apiKey }
        // Deixa o "Pois não?" do despertar já sintetizado para responder sem espera.
        voice?.prewarmPrompt("Pois não?", SettingsStore(this))
        configureVoiceCapture()
        beepSilencer = RecognitionBeepSilencer(this)
        avatarPresenter = AvatarPresenter(this)

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            failAndStop("Reconhecimento de voz indisponível neste aparelho.")
            return
        }

        recognizer = SpeechRecognizer.createSpeechRecognizer(this).also {
            it.setRecognitionListener(this)
        }

        _running.value = true
        publishStatus(ListeningPhase.LISTENING, wakeListeningMessage())
        startListening()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        // O usuário pode ativar/desativar a voz autorizada com o serviço ligado.
        configureVoiceCapture()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        destroyed = true
        mainHandler.removeCallbacksAndMessages(null)
        runCatching { recognizer?.destroy() }
        recognizer = null
        runCatching { voice?.shutdown() }
        voice = null
        micCapture?.stop()
        micCapture = null
        runCatching { beepSilencer?.release() }
        beepSilencer = null
        avatarPresenter?.hide()
        avatarPresenter = null
        runCatching {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        }
        wakeLock = null
        scope.cancel()
        MicLevel.reset()
        _running.value = false
        _status.value = ListeningStatus(ListeningPhase.STOPPED, "Escuta desligada")
        super.onDestroy()
    }

    // ---------------------------------------------------------------- escuta

    private fun startListening() {
        if (destroyed || speaking || verifying) return
        val engine = recognizer ?: return
        pendingVoiceSnapshot = null
        mainHandler.removeCallbacks(restartRunnable)
        mainHandler.removeCallbacks(preemptiveBeepMute)
        if (SettingsStore(this).silenceBeeps) {
            // O bipe de "abrindo" toca ~300 ms depois do startListening.
            beepSilencer?.suppress(START_BEEP_WINDOW_MS)
            mainHandler.postDelayed(preemptiveBeepMute, PREEMPTIVE_DELAY_MS)
        }
        try {
            engine.startListening(speechRecognizerIntent)
        } catch (error: Exception) {
            Log.w(TAG, "Falha ao iniciar a escuta", error)
            scheduleRestart(1500)
        }
    }

    private fun scheduleRestart(delayMs: Long) {
        if (destroyed || speaking || verifying) return
        mainHandler.removeCallbacks(restartRunnable)
        mainHandler.postDelayed(restartRunnable, delayMs)
    }

    override fun onReadyForSpeech(params: Bundle?) {
        publishStatus(
            if (awake) ListeningPhase.AWAKE else ListeningPhase.LISTENING,
            if (awake) "Pode falar..." else wakeListeningMessage()
        )
    }

    override fun onBeginningOfSpeech() = Unit

    override fun onRmsChanged(rmsdB: Float) {
        MicLevel.publish(rmsdB)
    }

    override fun onBufferReceived(buffer: ByteArray?) = Unit

    override fun onEndOfSpeech() {
        // Guarda o áudio assim que a fala termina: o resultado do reconhecedor
        // pode chegar bem depois, e o buffer circular já terá avançado.
        if (SettingsStore(this).voiceLockEnabled) {
            pendingVoiceSnapshot = micCapture?.snapshot()
        }
        // O bipe de "sucesso" toca quando o serviço entende que a fala acabou.
        if (SettingsStore(this).silenceBeeps) {
            beepSilencer?.suppress(END_OF_SPEECH_WINDOW_MS)
        }
    }

    override fun onError(error: Int) {
        if (destroyed) return
        // O bipe de "falha" toca logo após o erro; o de "abrindo" vem no
        // reinício seguinte e é coberto pela janela do startListening.
        if (SettingsStore(this).silenceBeeps) beepSilencer?.suppress(TERMINAL_WINDOW_MS)
        when (error) {
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> scheduleRestart(400)

            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> scheduleRestart(1500)

            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> {
                publishStatus(ListeningPhase.ERROR, "Sem conexão com o serviço de voz. Tentando de novo...")
                scheduleRestart(3000)
            }

            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                failAndStop("Permissão do microfone não concedida.")

            SpeechRecognizer.ERROR_AUDIO -> scheduleRestart(1500)

            else -> scheduleRestart(1000)
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val text = partialResults
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.trim()
            .orEmpty()
        if (text.isNotEmpty()) {
            lastTranscript = text
            val status = currentStatus()
            _status.value = status.copy(transcript = text)
        }
    }

    override fun onResults(results: Bundle?) {
        if (destroyed || verifying) return
        // O bipe de "sucesso" pode tocar pouco antes deste callback.
        if (SettingsStore(this).silenceBeeps) {
            beepSilencer?.suppress(TERMINAL_WINDOW_MS)
        }

        val text = results
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.trim()
            .orEmpty()

        if (text.isEmpty()) {
            scheduleRestart(300)
            return
        }

        lastTranscript = text
        Log.i(TAG, "Transcrição final: \"$text\" (awake=$awake)")

        val settings = SettingsStore(this)
        val normalized = normalize(text)
        val tokens = normalized.split(' ').filter { it.isNotBlank() }
        val wakeTokens = normalize(settings.wakeWord)
            .split(' ')
            .filter { it.isNotBlank() }
        val wakeRange = findWakeRange(tokens, wakeTokens)

        if (awake) {
            verifyVoiceThen(settings) {
                // Modo de conversa: aceita a fala sem exigir o nome. Se a pessoa
                // repetir o nome no começo ("Rafinha, ..."), ele é removido.
                mainHandler.removeCallbacks(endAwakeRunnable)
                avatarPresenter?.show(force = false)
                val command = if (wakeRange != null && wakeRange.first == 0) {
                    tokens.drop(wakeRange.last + 1).joinToString(" ").trim()
                } else {
                    text.trim()
                }
                Log.i(TAG, "Comando (modo conversa): \"$command\"")
                if (command.isEmpty()) {
                    awake = true
                    speakPrompt("Pois não?")
                } else {
                    handleCommand(command)
                }
            }
            return
        }

        if (wakeRange != null) Log.i(TAG, "Palavra de ativação detectada em: \"$text\"")

        if (wakeRange == null) {
            // Não era para o Jarvis: ignora e volta a ouvir.
            publishStatus(
                ListeningPhase.LISTENING,
                wakeListeningMessage(),
                transcript = text
            )
            scheduleRestart(200)
            return
        }

        verifyVoiceThen(settings) {
            // Acordou: acende a tela e mostra o avatar antes de consultar o Gemini.
            avatarPresenter?.show(force = true)

            val command = tokens.drop(wakeRange.last + 1).joinToString(" ").trim()
            if (command.isEmpty()) {
                awake = true
                speakPrompt("Pois não?")
            } else {
                handleCommand(command)
            }
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) = Unit

    // --------------------------------------------------------------- comando

    private fun handleCommand(command: String) {
        mainHandler.removeCallbacks(endAwakeRunnable)
        speaking = true
        awake = false
        // O microfone não está mais alimentando o orbe: zera o nível.
        MicLevel.reset()
        publishStatus(ListeningPhase.THINKING, "Consultando o Gemini...", transcript = command)
        Log.i(TAG, "Enviando comando ao Gemini: \"$command\"")

        scope.launch {
            val settings = SettingsStore(this@JarvisListeningService)
            // Uma confirmação pendente é respondida localmente, sem ida ao Gemini.
            val confirmation = JarvisActions.confirmOrCancel(this@JarvisListeningService, command)
            if (confirmation != null) {
                withContext(Dispatchers.Main.immediate) { speakAnswer(confirmation, settings) }
                return@launch
            }
            if (!settings.hasApiKey) {
                speakAnswer(
                    "Ainda não tenho uma chave da API configurada. Abra o aplicativo e informe sua chave.",
                    settings
                )
                return@launch
            }

            val result = GeminiClient(settings.apiKey)
                .ask(command, JarvisActions.tools(this@JarvisListeningService))
            withContext(Dispatchers.Main.immediate) {
                result.onSuccess { reply ->
                    Log.i(TAG, "Resposta do Gemini (${reply.text.length} caracteres)")
                    speakAnswer(reply.text, settings)
                }.onFailure { error ->
                    Log.w(TAG, "Falha na consulta", error)
                    speakAnswer(
                        error.message ?: "Não consegui consultar o Gemini agora.",
                        settings
                    )
                }
            }
        }
    }

    private fun speakAnswer(text: String, settings: SettingsStore) {
        val token = ++speechToken
        publishStatus(ListeningPhase.SPEAKING, "Falando...", transcript = lastTranscript)
        speakInternal(text, settings) { finishSpeech(token, resetAwake = true) }
    }

    private fun speakPrompt(text: String) {
        val settings = SettingsStore(this)
        publishStatus(ListeningPhase.AWAKE, text)
        val token = ++speechToken
        speakInternal(text, settings) { finishSpeech(token, resetAwake = false) }
    }

    private fun speakInternal(text: String, settings: SettingsStore, onDone: () -> Unit) {
        speaking = true
        // Garante que nenhum canal continue mudo enquanto o Jarvis fala.
        beepSilencer?.restore()
        val manager = voice
        if (manager == null) {
            onDone()
            return
        }
        manager.speak(
            text = text,
            settings = settings,
            onDone = onDone
        )
    }

    private fun finishSpeech(token: Int, resetAwake: Boolean) {
        if (destroyed || token != speechToken) return
        speaking = false
        // Continua aceitando fala sem o nome: mais tempo depois de "Pois não?"
        // (esperando o comando) e uma janela curta depois de cada resposta.
        awake = true
        val window = when {
            JarvisActions.hasPendingContact() -> CONFIRM_WINDOW_MS
            resetAwake -> FOLLOW_UP_WINDOW_MS
            else -> PROMPT_WINDOW_MS
        }
        scheduleEndAwake(window)
        publishStatus(ListeningPhase.AWAKE, "Pode falar...")
        startListening()
    }

    private fun scheduleEndAwake(delayMs: Long) {
        mainHandler.removeCallbacks(endAwakeRunnable)
        mainHandler.postDelayed(endAwakeRunnable, delayMs)
    }

    // ------------------------------------------------------------ utilidades

    /** Liga/desliga a captura de áudio usada apenas pela verificação de voz. */
    private fun configureVoiceCapture() {
        val settings = SettingsStore(this)
        val needed = settings.voiceLockEnabled && VoiceprintStore(this).hasEnrollment
        if (!needed) {
            micCapture?.stop()
            micCapture = null
            return
        }
        if (micCapture == null) micCapture = MicCaptureBuffer(this)
        if (micCapture?.start() == true) {
            Log.i(TAG, "Captura para verificação de voz ativa")
        } else {
            Log.w(TAG, "Não foi possível capturar áudio para verificar a voz")
        }
    }

    /**
     * Confere o áudio recente com a voz cadastrada antes de atender. Vale para
     * a palavra de ativação e para cada fala da janela de conversa, para que
     * outra pessoa não consiga comandar o Jarvis depois que ele acordou.
     */
    private fun verifyVoiceThen(settings: SettingsStore, onAccepted: () -> Unit) {
        if (!settings.voiceLockEnabled) {
            onAccepted()
            return
        }

        val store = VoiceprintStore(this)
        if (!store.hasEnrollment) {
            Log.w(TAG, "Voz autorizada ligada sem cadastro; ignorando comando")
            publishStatus(
                ListeningPhase.LISTENING,
                "Voz autorizada ativada, mas sem cadastro. Cadastre sua voz no aplicativo.",
                lastTranscript
            )
            scheduleRestart(600)
            return
        }

        if (micCapture == null) configureVoiceCapture()
        // Prefere o áudio capturado no fim da fala: o callback final pode vir
        // muito depois e o buffer já não conteria mais a palavra dita.
        val snapshot = pendingVoiceSnapshot ?: micCapture?.snapshot()
        pendingVoiceSnapshot = null
        if (snapshot == null) {
            Log.w(TAG, "Sem áudio recente para verificar a voz; ignorando comando")
            publishStatus(
                ListeningPhase.LISTENING,
                "Não consegui verificar sua voz. Permita o microfone e reinicie a escuta.",
                lastTranscript
            )
            scheduleRestart(800)
            return
        }

        verifying = true
        publishStatus(ListeningPhase.THINKING, "Verificando sua voz...", lastTranscript)
        scope.launch(Dispatchers.Default) {
            var delivered = false
            try {
                val result = runCatching {
                    VoiceprintMatcher.bestScore(
                        snapshot,
                        VoiceFeatures.SAMPLE_RATE,
                        store.templates
                    )
                }
                result.onFailure { Log.w(TAG, "Falha ao verificar a voz", it) }
                val score = result.getOrDefault(0f)
                val limit = VoiceprintMatcher.threshold(settings.voiceLockStrictness)
                val accepted = result.isSuccess && score >= limit
                Log.i(
                    TAG,
                    String.format(
                        Locale.ROOT,
                        "Voz: score=%.3f limite=%.3f aceito=%s",
                        score,
                        limit,
                        accepted
                    )
                )
                withContext(Dispatchers.Main.immediate) {
                    delivered = true
                    if (destroyed) return@withContext
                    verifying = false
                    if (accepted) {
                        onAccepted()
                    } else {
                        awake = false
                        avatarPresenter?.hide()
                        mainHandler.removeCallbacks(endAwakeRunnable)
                        publishStatus(
                            ListeningPhase.LISTENING,
                            "Não reconheci sua voz (${(score * 100).toInt()}%). " +
                                "Diga \"${settings.wakeWord}\" com a voz cadastrada.",
                            lastTranscript
                        )
                        scheduleRestart(300)
                    }
                }
            } finally {
                withContext(NonCancellable + Dispatchers.Main.immediate) {
                    if (!delivered && !destroyed) {
                        verifying = false
                        publishStatus(
                            ListeningPhase.LISTENING,
                            "Não consegui verificar sua voz. Tentando de novo...",
                            lastTranscript
                        )
                        scheduleRestart(500)
                    }
                }
            }
        }
    }

    private fun wakeListeningMessage(): String {
        val wake = SettingsStore(this).wakeWord.replaceFirstChar { it.uppercase() }
        return "Ouvindo por \"$wake\"..."
    }

    private fun normalize(input: String): String =
        Normalizer.normalize(input, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()

    /**
     * Retorna o intervalo de tokens da palavra de ativação (do primeiro ao
     * último), ou null se não foi chamado. Aceita nomes com uma ou várias
     * palavras e tolera:
     *  - a transcrição separando o nome em pedaços (ex.: "jar vis");
     *  - a transcrição juntando tudo em um token (ex.: "eijarvis");
     *  - um erro de transcrição (distância de edição 1).
     */
    private fun findWakeRange(tokens: List<String>, wakeTokens: List<String>): IntRange? {
        if (wakeTokens.isEmpty() || tokens.isEmpty()) return null
        val wakeJoined = wakeTokens.joinToString("")
        if (wakeJoined.isEmpty()) return null

        // Janelas de 1 até (nº de palavras + 1) tokens cobrem nome separado,
        // nome inteiro num único token e nome com uma palavra a mais.
        val maxWindow = (wakeTokens.size + 1).coerceAtMost(3)
        for (size in 1..maxWindow) {
            if (tokens.size < size) break
            for (start in 0..(tokens.size - size)) {
                val windowJoined = tokens.subList(start, start + size).joinToString("")
                val exact = windowJoined == wakeJoined
                val close = wakeJoined.length >= 4 &&
                    levenshtein(windowJoined, wakeJoined) <= 1
                if (exact || close) return start..(start + size - 1)
            }
        }
        return null
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(
                    current[j - 1] + 1,
                    previous[j] + 1,
                    previous[j - 1] + cost
                )
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }

    private fun publishStatus(
        phase: ListeningPhase,
        message: String,
        transcript: String = ""
    ) {
        mainHandler.post {
            _status.value = ListeningStatus(phase, message, transcript)
            updateNotification()
        }
    }

    private fun currentStatus(): ListeningStatus = _status.value

    private fun failAndStop(message: String) {
        _status.value = ListeningStatus(ListeningPhase.ERROR, message)
        updateNotification()
        mainHandler.postDelayed({ stopSelf() }, 1500)
    }

    // --------------------------------------------------------- notificação

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Escuta do Jarvis",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Mantém o Jarvis ouvindo a palavra de ativação."
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun notificationFor(status: ListeningStatus): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopAction = PendingIntent.getService(
            this,
            2,
            Intent(this, JarvisListeningService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_jarvis)
            .setContentTitle("Jarvis")
            .setContentText(status.message)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "Desligar", stopAction)
            .build()
    }

    private fun startForegroundCompat(notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (error: Exception) {
            Log.e(TAG, "Não foi possível entrar em primeiro plano", error)
            failAndStop("Não foi possível manter a escuta ativa.")
        }
    }

    private fun updateNotification() {
        runCatching {
            val manager = getSystemService(NotificationManager::class.java)
            manager?.notify(NOTIFICATION_ID, notificationFor(currentStatus()))
        }
    }

    companion object {
        private const val TAG = "JarvisService"
        private const val CHANNEL_ID = "jarvis_listening"
        private const val NOTIFICATION_ID = 42
        private const val ACTION_START = "com.example.jarvisteste.action.START"
        private const val ACTION_STOP = "com.example.jarvisteste.action.STOP"

        /** Tempo de silêncio tolerado antes de o reconhecedor encerrar a sessão. */
        private const val SILENCE_LENGTH_MS = 120_000L

        // Janelas de silêncio dos bipes do reconhecedor (nunca no canal de mídia).
        // O bipe de "abrindo" vem ~300 ms após startListening.
        private const val START_BEEP_WINDOW_MS = 650L
        // O timeout de silêncio costuma ocorrer entre 5,1 s e 8,5 s; a janela
        // preventiva cobre essa faixa e é encurtada quando o callback chega.
        private const val PREEMPTIVE_DELAY_MS = 4_600L
        private const val PREEMPTIVE_WINDOW_MS = 5_000L
        private const val END_OF_SPEECH_WINDOW_MS = 900L
        private const val TERMINAL_WINDOW_MS = 450L

        // Tempo em que o Jarvis aceita fala sem repetir a palavra de ativação.
        private const val CONFIRM_WINDOW_MS = 25_000L
        private const val FOLLOW_UP_WINDOW_MS = 20_000L
        private const val PROMPT_WINDOW_MS = 12_000L

        private val _status = MutableStateFlow(
            ListeningStatus(ListeningPhase.STOPPED, "Escuta desligada")
        )
        val status: StateFlow<ListeningStatus> = _status.asStateFlow()

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, JarvisListeningService::class.java)
                .setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, JarvisListeningService::class.java))
        }

        /** Aplica mudanças de configuração sem pedir para o usuário religar. */
        fun restart(context: Context) {
            stop(context)
            // Dá tempo ao onDestroy da instância anterior para liberar o
            // microfone antes de criar a nova sessão de escuta.
            Handler(Looper.getMainLooper()).postDelayed({ start(context) }, 300)
        }
    }
}

package com.example.jarvisteste.audio

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.jarvisteste.data.VoiceGender
import com.example.jarvisteste.data.VoiceProfile
import com.example.jarvisteste.data.sanitizeForSpeech
import java.util.Locale
import java.util.UUID

/**
 * Envolve o TextToSpeech do Android: escolhe a voz do sistema, aplica pitch/rate
 * e avisa quando terminou de falar (necessário para retomar a escuta).
 */
class TtsManager(context: Context) {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val completions = mutableMapOf<String, () -> Unit>()

    private var tts: TextToSpeech? = null
    private var pendingSpeak: (() -> Unit)? = null

    var ready by mutableStateOf(false)
        private set

    var voices by mutableStateOf<List<Voice>>(emptyList())
        private set

    fun init() {
        if (tts != null) return

        tts = TextToSpeech(appContext) { status ->
            mainHandler.post {
                if (status != TextToSpeech.SUCCESS) {
                    Log.e(TAG, "TextToSpeech indisponível (status=$status)")
                    ready = false
                    return@post
                }
                val engine = tts ?: return@post

                val br = engine.setLanguage(Locale("pt", "BR"))
                if (br == TextToSpeech.LANG_MISSING_DATA || br == TextToSpeech.LANG_NOT_SUPPORTED) {
                    engine.setLanguage(Locale("pt", "PT"))
                }

                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit

                    override fun onDone(utteranceId: String?) {
                        finish(utteranceId)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        finish(utteranceId)
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        Log.w(TAG, "Erro do TTS (código $errorCode)")
                        finish(utteranceId)
                    }
                })

                voices = engine.voices
                    ?.filter { it.locale.language.equals("pt", ignoreCase = true) }
                    ?.sortedWith(
                        compareByDescending<Voice> { it.locale.country.equals("BR", ignoreCase = true) }
                            .thenBy { it.name }
                    )
                    .orEmpty()

                voices.forEach { voice ->
                    Log.d(TAG, "Voz disponível: ${voice.name} features=${voice.features}")
                }

                ready = true
                val pending = pendingSpeak
                pendingSpeak = null
                pending?.invoke()
            }
        }
    }

    private fun finish(utteranceId: String?) {
        val callback = synchronized(completions) { completions.remove(utteranceId) }
        if (callback != null) mainHandler.post { callback() }
    }

    /**
     * Fala [text] com a voz e os ajustes escolhidos. [onDone] roda na thread principal.
     */
    fun speak(
        text: String,
        voiceName: String?,
        pitch: Float,
        rate: Float,
        onDone: (() -> Unit)? = null
    ) {
        val safeText = sanitizeForSpeech(text)
        if (safeText.isBlank()) {
            onDone?.invoke()
            return
        }

        if (!ready) {
            pendingSpeak = { speak(safeText, voiceName, pitch, rate, onDone) }
            return
        }

        val engine = tts
        if (engine == null) {
            onDone?.invoke()
            return
        }

        if (!voiceName.isNullOrBlank()) {
            val voice = engine.voices?.firstOrNull { it.name == voiceName }
            if (voice != null) {
                runCatching { engine.voice = voice }
            }
        }
        engine.setPitch(pitch.coerceIn(0.1f, 2.0f))
        engine.setSpeechRate(rate.coerceIn(0.3f, 2.5f))

        Log.d(TAG, "Falando (voz=${voiceName ?: "padrão"}, pitch=$pitch, rate=$rate)")
        val utteranceId = "jarvis-" + UUID.randomUUID()
        if (onDone != null) {
            synchronized(completions) { completions[utteranceId] = onDone }
        }

        val result = engine.speak(safeText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        if (result != TextToSpeech.SUCCESS) {
            synchronized(completions) { completions.remove(utteranceId) }
            onDone?.invoke()
        }
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        synchronized(completions) { completions.clear() }
        pendingSpeak = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
        voices = emptyList()
    }

    companion object {
        private const val TAG = "TtsManager"
        const val PREVIEW_TEXT =
            "Olá! Eu sou o Jarvis, seu assistente pessoal. Posso responder suas perguntas e ajudar no seu dia."
    }
}

/** Escolha de voz do sistema a partir dos perfis oferecidos ao usuário. */
object VoiceCatalog {

    fun selectForProfile(
        voices: List<Voice>,
        profile: VoiceProfile,
        preferredName: String?
    ): Voice? {
        if (!preferredName.isNullOrBlank()) {
            voices.firstOrNull { it.name == preferredName }?.let { return it }
        }

        val byGender = when (profile.gender) {
            VoiceGender.FEMALE -> voices.firstOrNull { genderOf(it) == VoiceGender.FEMALE }
            VoiceGender.MALE -> voices.firstOrNull { genderOf(it) == VoiceGender.MALE }
            VoiceGender.ANY -> null
        }
        if (byGender != null) return byGender

        // Sem indicação de gênero na voz: prefere a melhor voz brasileira
        // disponível (rede costuma ter qualidade superior à embutida).
        return voices.firstOrNull {
            it.locale.country.equals("BR", ignoreCase = true) && it.name.contains("network")
        } ?: voices.firstOrNull { it.locale.country.equals("BR", ignoreCase = true) }
            ?: voices.firstOrNull()
    }

    fun genderOf(voice: Voice): VoiceGender? {
        val features = voice.features?.map { it.lowercase() }.orEmpty()
        return when {
            features.any { it.contains("female") } -> VoiceGender.FEMALE
            features.any { it.contains("male") } -> VoiceGender.MALE
            else -> null
        }
    }

    fun details(voice: Voice): String {
        val features = voice.features?.map { it.lowercase() }.orEmpty()
        val kind = when {
            features.any { it == "network" } || voice.name.contains("network") -> "online"
            features.any { it == "embedded" } || voice.name.contains("local") -> "no aparelho"
            else -> ""
        }
        val gender = when (genderOf(voice)) {
            VoiceGender.FEMALE -> "feminina"
            VoiceGender.MALE -> "masculina"
            else -> ""
        }
        return listOf(voice.locale.toLanguageTag(), kind, gender)
            .filter { it.isNotBlank() }
            .joinToString(" • ")
    }
}

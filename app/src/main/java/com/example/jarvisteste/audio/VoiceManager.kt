package com.example.jarvisteste.audio

import android.content.Context
import android.util.Log
import com.example.jarvisteste.data.GeminiVoices
import com.example.jarvisteste.data.SettingsStore
import com.example.jarvisteste.data.VoiceEngine
import com.example.jarvisteste.data.VoiceProfile

/**
 * Ponto único de fala do app. Usa as vozes naturais do Gemini quando
 * disponíveis e cai automaticamente para a voz do aparelho se não houver
 * internet, chave ou cota.
 */
class VoiceManager(
    context: Context,
    private val apiKeyProvider: () -> String
) {

    private val tts = TtsManager(context).also { it.init() }
    private val gemini = GeminiTtsPlayer(context, apiKeyProvider)

    /** Evita que um callback atrasado de uma fala antiga feche a boca da nova. */
    @Volatile private var speechToken = 0

    val ready: Boolean get() = tts.ready
    val voices get() = tts.voices

    /** Fala usando as preferências atuais do Jarvis. */
    fun speak(text: String, settings: SettingsStore, onDone: (() -> Unit)? = null) {
        speak(
            text = text,
            engine = settings.voiceEngine,
            geminiVoiceId = settings.geminiVoiceId,
            profile = settings.profile,
            voiceName = settings.voiceName,
            pitch = settings.pitch,
            rate = settings.rate,
            onDone = onDone
        )
    }

    /** Versão usada por prévias e pelo onboarding, antes de salvar os ajustes. */
    fun speak(
        text: String,
        engine: VoiceEngine,
        geminiVoiceId: String,
        profile: VoiceProfile,
        voiceName: String?,
        pitch: Float,
        rate: Float,
        onDone: (() -> Unit)? = null
    ) {
        if (text.isBlank()) {
            onDone?.invoke()
            return
        }

        val token = ++speechToken
        Log.d(TAG, "Falar: engine=$engine voz=$geminiVoiceId texto=${text.take(60)}")

        if (engine == VoiceEngine.GEMINI) {
            val apiKey = apiKeyProvider()
            if (apiKey.isNotBlank()) {
                val voice = GeminiVoices.byId(geminiVoiceId)
                Log.d(TAG, "Gemini TTS: voz=${voice.id} estilo=${voice.style}")
                VoiceLevel.beginGemini()
                gemini.speak(
                    text = text,
                    voiceId = voice.id,
                    style = voice.style,
                    onDone = {
                        if (token == speechToken) VoiceLevel.finish()
                        onDone?.invoke()
                    },
                    onFailure = {
                        Log.w(TAG, "Voz online indisponível; usando a voz do aparelho", it)
                        if (token == speechToken) {
                            VoiceLevel.finish()
                            speakSystem(text, profile, voiceName, pitch, rate, onDone, token)
                        }
                    }
                )
                return
            }
            Log.i(TAG, "Sem chave da API; usando a voz do aparelho")
        }

        speakSystem(text, profile, voiceName, pitch, rate, onDone, token)
    }

    private fun speakSystem(
        text: String,
        profile: VoiceProfile,
        voiceName: String?,
        pitch: Float,
        rate: Float,
        onDone: (() -> Unit)?,
        token: Int
    ) {
        val resolved = VoiceCatalog.selectForProfile(tts.voices, profile, voiceName)?.name
        VoiceLevel.beginSystem()
        tts.speak(
            text = text,
            voiceName = resolved,
            pitch = pitch,
            rate = rate,
            onDone = {
                if (token == speechToken) VoiceLevel.finish()
                onDone?.invoke()
            }
        )
    }

    /**
     * Deixa no cache uma frase curta e fixa (como "Pois não?") para que ela
     * saia na hora no próximo despertar. Não faz nada na voz do aparelho.
     */
    fun prewarmPrompt(text: String, settings: SettingsStore) {
        if (settings.voiceEngine != VoiceEngine.GEMINI) return
        if (apiKeyProvider().isBlank()) return
        val voice = GeminiVoices.byId(settings.geminiVoiceId)
        gemini.prewarm(text, voice.id, voice.style)
    }

    fun stop() {
        speechToken++
        VoiceLevel.finish()
        gemini.stop()
        tts.stop()
    }

    fun shutdown() {
        speechToken++
        VoiceLevel.finish()
        gemini.shutdown()
        tts.shutdown()
    }

    companion object {
        private const val TAG = "VoiceManager"
    }
}

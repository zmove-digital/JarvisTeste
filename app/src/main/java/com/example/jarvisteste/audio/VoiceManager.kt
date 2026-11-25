package com.example.jarvisteste.audio

import android.content.Context
import android.util.Log
import com.example.jarvisteste.data.FishAudioConfig
import com.example.jarvisteste.data.GeminiVoices
import com.example.jarvisteste.data.SettingsStore
import com.example.jarvisteste.data.VoiceEngine
import com.example.jarvisteste.data.VoiceProfile

/**
 * Ponto único de fala do app.
 *
 * Motor escolhido pelo usuário:
 * - [VoiceEngine.GEMINI]: vozes naturais do Gemini;
 * - [VoiceEngine.SYSTEM]: vozes instaladas no aparelho (offline);
 * - [VoiceEngine.FISH_AUDIO]: voz clonada/personalizada do Fish Audio.
 *
 * Se o motor online falhar (sem chave, sem cota, sem rede), cai
 * automaticamente para o Gemini ou para a voz do aparelho.
 */
class VoiceManager(
    context: Context,
    private val apiKeyProvider: () -> String
) {

    private val tts = TtsManager(context).also { it.init() }
    private val gemini = GeminiTtsPlayer(context, apiKeyProvider)
    private val fish = FishAudioTtsPlayer(context)

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
            fishAudioConfig = settings.fishAudioConfig,
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
        fishAudioConfig: FishAudioConfig = FishAudioConfig(),
        onDone: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        if (text.isBlank()) {
            onDone?.invoke()
            return
        }

        val token = ++speechToken
        Log.d(TAG, "Falar: engine=$engine voz=$geminiVoiceId texto=${text.take(60)}")

        when (engine) {
            VoiceEngine.FISH_AUDIO -> speakFish(
                text = text,
                geminiVoiceId = geminiVoiceId,
                config = fishAudioConfig,
                profile = profile,
                voiceName = voiceName,
                pitch = pitch,
                rate = rate,
                onDone = onDone,
                onError = onError,
                token = token
            )

            VoiceEngine.GEMINI -> speakGemini(
                text = text,
                geminiVoiceId = geminiVoiceId,
                profile = profile,
                voiceName = voiceName,
                pitch = pitch,
                rate = rate,
                onDone = onDone,
                token = token
            )

            VoiceEngine.SYSTEM -> speakSystem(
                text = text,
                profile = profile,
                voiceName = voiceName,
                pitch = pitch,
                rate = rate,
                onDone = onDone,
                token = token
            )
        }
    }

    private fun speakFish(
        text: String,
        geminiVoiceId: String,
        config: FishAudioConfig,
        profile: VoiceProfile,
        voiceName: String?,
        pitch: Float,
        rate: Float,
        onDone: (() -> Unit)?,
        onError: ((String) -> Unit)?,
        token: Int
    ) {
        if (!config.ready) {
            onError?.invoke("Informe a API key e o link da voz do Fish Audio.")
            speakGeminiOrSystem(
                text, geminiVoiceId, profile, voiceName, pitch, rate, onDone, token
            )
            return
        }

        Log.d(TAG, "Fish Audio: voz=${config.resolvedVoiceId} modelo=${config.model}")
        fish.speak(
            text = text,
            voiceId = config.resolvedVoiceId,
            model = config.model,
            apiKey = config.apiKey,
            onDone = {
                if (token == speechToken) onDone?.invoke()
            },
            onFailure = { error ->
                if (token == speechToken) {
                    Log.w(TAG, "Voz personalizada indisponível; usando outra", error)
                    onError?.invoke(error.message ?: "Falha na voz personalizada.")
                    speakGeminiOrSystem(
                        text, geminiVoiceId, profile, voiceName, pitch, rate, onDone, token
                    )
                }
            }
        )
    }

    private fun speakGeminiOrSystem(
        text: String,
        geminiVoiceId: String,
        profile: VoiceProfile,
        voiceName: String?,
        pitch: Float,
        rate: Float,
        onDone: (() -> Unit)?,
        token: Int
    ) {
        if (apiKeyProvider().isNotBlank()) {
            speakGemini(
                text, geminiVoiceId, profile, voiceName, pitch, rate, onDone, token
            )
        } else {
            Log.i(TAG, "Sem chave do Gemini; usando a voz do aparelho")
            speakSystem(text, profile, voiceName, pitch, rate, onDone, token)
        }
    }

    private fun speakGemini(
        text: String,
        geminiVoiceId: String,
        profile: VoiceProfile,
        voiceName: String?,
        pitch: Float,
        rate: Float,
        onDone: (() -> Unit)?,
        token: Int
    ) {
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
     * saia na hora no próximo despertar.
     */
    fun prewarmPrompt(text: String, settings: SettingsStore) {
        when (settings.voiceEngine) {
            VoiceEngine.GEMINI -> {
                if (apiKeyProvider().isBlank()) return
                val voice = GeminiVoices.byId(settings.geminiVoiceId)
                gemini.prewarm(text, voice.id, voice.style)
            }

            VoiceEngine.FISH_AUDIO -> {
                val config = settings.fishAudioConfig
                if (!config.ready) return
                fish.prewarm(text, config.resolvedVoiceId, config.model, config.apiKey)
            }

            VoiceEngine.SYSTEM -> Unit
        }
    }

    fun stop() {
        speechToken++
        VoiceLevel.finish()
        gemini.stop()
        fish.stop()
        tts.stop()
    }

    fun shutdown() {
        speechToken++
        VoiceLevel.finish()
        gemini.shutdown()
        fish.shutdown()
        tts.shutdown()
    }

    companion object {
        private const val TAG = "VoiceManager"
    }
}

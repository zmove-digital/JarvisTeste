package com.example.jarvisteste.audio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sin

/** De onde vem o áudio que está tocando agora. */
enum class VoiceSource { NONE, GEMINI, SYSTEM }

/**
 * Nível de voz em tempo real usado pelo avatar para mexer a boca junto com o
 * áudio.
 *
 * As vozes do Gemini chegam em PCM, então a amplitude é medida a cada trecho
 * escrito no [android.media.AudioTrack]. O TextToSpeech do aparelho não expõe o
 * áudio; nesse caso um envelope procedural imita o ritmo de sílabas e frases.
 */
object VoiceLevel {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var systemJob: Job? = null

    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level.asStateFlow()

    private val _source = MutableStateFlow(VoiceSource.NONE)
    val source: StateFlow<VoiceSource> = _source.asStateFlow()

    /** Prepara o avatar para receber amplitudes reais do Gemini. */
    fun beginGemini() {
        stopSystem()
        _source.value = VoiceSource.GEMINI
        _level.value = 0f
    }

    /** Amplitude medida (0..1) de um trecho PCM do Gemini. */
    fun publish(level: Float) {
        if (_source.value != VoiceSource.GEMINI) return
        _level.value = level.coerceIn(0f, 1f)
    }

    /**
     * A voz do aparelho não fornece amostras; gera um envelope com sílabas e
     * pausas curtas para a boca continuar viva durante a fala.
     */
    fun beginSystem() {
        stopSystem()
        _source.value = VoiceSource.SYSTEM
        _level.value = 0f
        systemJob = scope.launch {
            var t = 0f
            var elapsed = 0f
            while (isActive && elapsed < MAX_SYSTEM_SECONDS) {
                val syllable = abs(sin(t * 9.1f)) * 0.55f + abs(sin(t * 23.7f)) * 0.25f
                val phrase = 0.45f + 0.55f * abs(sin(t * 1.7f))
                _level.value = (syllable * phrase).coerceIn(0f, 1f)
                t += 0.06f
                elapsed += 0.06f
                delay(60)
            }
            if (_source.value == VoiceSource.SYSTEM) {
                _source.value = VoiceSource.NONE
                _level.value = 0f
            }
        }
    }

    /** Encerra a fala atual e fecha a boca do avatar. */
    fun finish() {
        stopSystem()
        _source.value = VoiceSource.NONE
        _level.value = 0f
    }

    private fun stopSystem() {
        systemJob?.cancel()
        systemJob = null
    }

    private const val MAX_SYSTEM_SECONDS = 90f
}

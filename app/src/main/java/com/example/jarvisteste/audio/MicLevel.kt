package com.example.jarvisteste.audio

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Nível da voz do usuário em tempo real, de 0 a 1, medido a partir do RMS em dB
 * entregue por [android.speech.RecognitionListener.onRmsChanged].
 *
 * É o equivalente "de entrada" do [VoiceLevel] (que mede a fala do Jarvis). O
 * orbe usa os dois para vibrar junto com quem fala.
 */
object MicLevel {

    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level.asStateFlow()

    private var lastUpdateMs = 0L

    /** Recebe o RMS em dB do reconhecedor e normaliza para 0..1. */
    fun publish(rmsdB: Float) {
        lastUpdateMs = SystemClock.elapsedRealtime()
        _level.value = ((rmsdB - MIN_DB) / (MAX_DB - MIN_DB)).coerceIn(0f, 1f)
    }

    /**
     * Nível atual. Se o reconhecedor parou de reportar (silêncio, pausa ou fim
     * da sessão), volta a zero — senão o orbe ficaria "aceso" para sempre.
     */
    fun current(): Float =
        if (SystemClock.elapsedRealtime() - lastUpdateMs < HOLD_MS) _level.value else 0f

    /** Zera o nível (usado ao começar a pensar/falar e ao parar a escuta). */
    fun reset() {
        lastUpdateMs = 0L
        _level.value = 0f
    }

    // O onRmsChanged costuma variar entre -2 e 12 dB (alguns aparelhos chegam a
    // 20). A janela abaixo deixa o orbe vivo já com voz baixa.
    private const val MIN_DB = -2f
    private const val MAX_DB = 12f
    private const val HOLD_MS = 320L
}

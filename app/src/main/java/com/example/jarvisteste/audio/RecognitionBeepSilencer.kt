package com.example.jarvisteste.audio

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * O serviço de reconhecimento de voz do Google toca "bipes" de início e fim de
 * escuta no canal de notificações (AudioAttributes: USAGE_NOTIFICATION_EVENT).
 * Como a escuta contínua reinicia a sessão a cada poucos segundos de silêncio,
 * esses bipes viram uma metralhadora.
 *
 * Esta classe silencia apenas os canais de notificação/sistema por janelas
 * curtas (nunca o canal de mídia), então a voz do Jarvis, músicas e vídeos
 * continuam funcionando normalmente. O volume anterior é restaurado pelo
 * ADJUST_UNMUTE do próprio sistema.
 */
class RecognitionBeepSilencer(context: Context) {

    private val audioManager =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())

    // USAGE_NOTIFICATION_EVENT costuma cair em STREAM_NOTIFICATION; alguns
    // aparelhos roteiam para STREAM_SYSTEM. Silenciar os dois cobre ambos.
    private val streams = intArrayOf(
        AudioManager.STREAM_NOTIFICATION,
        AudioManager.STREAM_SYSTEM
    )

    private var generation = 0
    private var muted = false

    /** Silencia os canais por [durationMs] (estende a janela se já estava mudo). */
    fun suppress(durationMs: Long = DEFAULT_WINDOW_MS) {
        if (!muted) {
            streams.forEach { stream ->
                runCatching {
                    audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_MUTE, 0)
                }
            }
            muted = true
            val state = streams.joinToString { stream ->
                val isMuted = runCatching { audioManager.isStreamMute(stream) }.getOrDefault(false)
                "$stream=$isMuted"
            }
            Log.d(TAG, "Bipes silenciados por ${durationMs}ms ($state)")
        }

        val token = ++generation
        handler.postDelayed({
            if (token == generation) restore()
        }, durationMs)
    }

    /** Devolve o som dos canais imediatamente. */
    fun restore() {
        if (!muted) return
        generation++
        streams.forEach { stream ->
            runCatching {
                audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_UNMUTE, 0)
            }
        }
        muted = false
        Log.d(TAG, "Som dos canais restaurado")
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        restore()
    }

    companion object {
        private const val TAG = "BeepSilencer"
        const val DEFAULT_WINDOW_MS = 700L
    }
}

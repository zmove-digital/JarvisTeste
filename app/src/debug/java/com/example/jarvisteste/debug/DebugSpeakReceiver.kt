package com.example.jarvisteste.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.jarvisteste.audio.TtsManager
import com.example.jarvisteste.audio.VoiceManager
import com.example.jarvisteste.data.SettingsStore

/**
 * Receptor exclusivo da variante debug. Fala uma frase usando os ajustes salvos
 * para testar o motor de voz sem depender da interface:
 *
 *   adb shell am broadcast -a com.example.jarvisteste.DEBUG_SPEAK \
 *       -n com.example.jarvisteste/.debug.DebugSpeakReceiver
 *
 * Opcional: `--es text "outra frase"` e `--es voice Lia` (força uma voz Gemini).
 */
class DebugSpeakReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val settings = SettingsStore(context)
        val text = intent.getStringExtra("text") ?: TtsManager.PREVIEW_TEXT
        val forcedVoice = intent.getStringExtra("voice")

        Log.i(
            TAG,
            "Pedido de teste: engine=${settings.voiceEngine} voz=${forcedVoice ?: settings.geminiVoiceId} " +
                "chave=${if (settings.apiKey.isNotBlank()) "ok" else "vazia"} texto=\"$text\""
        )

        val appContext = context.applicationContext
        val manager = VoiceManager(appContext) { settings.apiKey }
        manager.speak(
            text = text,
            engine = settings.voiceEngine,
            geminiVoiceId = forcedVoice ?: settings.geminiVoiceId,
            profile = settings.profile,
            voiceName = settings.voiceName,
            pitch = settings.pitch,
            rate = settings.rate,
            onDone = {
                Log.i(TAG, "Fala concluída")
                manager.shutdown()
            }
        )
    }

    companion object {
        private const val TAG = "DebugSpeak"
    }
}

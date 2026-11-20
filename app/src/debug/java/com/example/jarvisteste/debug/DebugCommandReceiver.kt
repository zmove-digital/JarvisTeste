package com.example.jarvisteste.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.jarvisteste.actions.JarvisActions
import com.example.jarvisteste.data.GeminiClient
import com.example.jarvisteste.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receptor exclusivo da variante debug. Envia um comando pelo mesmo caminho da
 * escuta contínua — inclusive as ferramentas — para testar as ações do Jarvis
 * sem precisar falar:
 *
 *   adb shell am broadcast -a com.example.jarvisteste.DEBUG_COMMAND \
 *       -n com.example.jarvisteste/.debug.DebugCommandReceiver \
 *       --es text "abra o Spotify"
 *
 * O resultado é escrito no logcat (tag "DebugCommand").
 */
class DebugCommandReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val text = intent.getStringExtra("text")?.trim().orEmpty()
        if (text.isEmpty()) return

        val appContext = context.applicationContext
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            val settings = SettingsStore(appContext)
            Log.i(TAG, "Comando de teste: \"$text\"")
            val confirmation = JarvisActions.confirmOrCancel(appContext, text)
            if (confirmation != null) {
                Log.i(TAG, "Resposta: $confirmation")
                return@launch
            }
            val result = GeminiClient(settings.apiKey)
                .ask(text, JarvisActions.tools(appContext))
            result
                .onSuccess { Log.i(TAG, "Resposta: ${it.text}") }
                .onFailure { Log.w(TAG, "Falha: ${it.message}", it) }
        }
    }

    companion object {
        private const val TAG = "DebugCommand"
    }
}

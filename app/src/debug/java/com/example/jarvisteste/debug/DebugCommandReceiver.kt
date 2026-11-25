package com.example.jarvisteste.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.jarvisteste.actions.JarvisActions
import com.example.jarvisteste.actions.WeatherService
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
        val appContext = context.applicationContext
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        // Teste isolado do clima, sem consumir cota do Gemini.
        if (intent.action == ACTION_WEATHER) {
            val cidade = intent.getStringExtra("cidade")
            scope.launch {
                Log.i(TAG, "Clima ($cidade): ${WeatherService.forecast(appContext, cidade)}")
            }
            return
        }

        // Lista os modelos disponíveis para a chave, sem gastar cota de conversa.
        if (intent.action == ACTION_MODELS) {
            scope.launch {
                val settings = SettingsStore(appContext)
                GeminiClient(settings.apiKey).listModels()
                    .onSuccess { Log.i(TAG, "Modelos disponíveis: $it") }
                    .onFailure { Log.w(TAG, "Falha ao listar modelos: ${it.message}", it) }
            }
            return
        }

        // Testa rapidamente quais modelos têm cota livre para esta chave.
        if (intent.action == ACTION_PROBE) {
            scope.launch {
                val settings = SettingsStore(appContext)
                val client = GeminiClient(settings.apiKey)
                val candidates = listOf(
                    "gemini-2.5-flash-lite",
                    "gemini-2.5-flash",
                    "gemini-2.0-flash-lite",
                    "gemini-3.1-flash-lite",
                    "gemini-flash-lite-latest",
                    "gemini-3.5-flash-lite",
                    "gemini-3.5-flash"
                )
                for (model in candidates) {
                    client.probe(model)
                        .onSuccess { Log.i(TAG, "PROBE OK: $model") }
                        .onFailure { Log.w(TAG, "PROBE FALHOU: $model -> ${it.message}") }
                }
            }
            return
        }

        // Testa a Interactions API, a interface recomendada para projetos novos.
        if (intent.action == ACTION_INTERACTIONS) {
            scope.launch {
                val settings = SettingsStore(appContext)
                val client = GeminiClient(settings.apiKey)
                val candidates = listOf(
                    "gemini-flash-lite-latest",
                    "gemini-3.5-flash",
                    "gemini-3.6-flash",
                    "gemini-3.5-flash-lite",
                    "gemini-4-hydrogen"
                )
                for (model in candidates) {
                    client.probeInteractions(model)
                        .onSuccess { Log.i(TAG, "INTERACTIONS OK: $model") }
                        .onFailure { Log.w(TAG, "INTERACTIONS FALHOU: $model -> ${it.message}") }
                }
            }
            return
        }

        val text = intent.getStringExtra("text")?.trim().orEmpty()
        if (text.isEmpty()) return

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
        private const val ACTION_WEATHER = "com.example.jarvisteste.DEBUG_WEATHER"
        private const val ACTION_MODELS = "com.example.jarvisteste.DEBUG_MODELS"
        private const val ACTION_PROBE = "com.example.jarvisteste.DEBUG_PROBE"
        private const val ACTION_INTERACTIONS = "com.example.jarvisteste.DEBUG_INTERACTIONS"
    }
}

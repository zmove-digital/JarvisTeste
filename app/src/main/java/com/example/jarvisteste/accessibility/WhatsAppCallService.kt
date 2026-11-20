package com.example.jarvisteste.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.Normalizer

/**
 * Ponte entre as ações do Jarvis e o Serviço de Acessibilidade.
 *
 * O WhatsApp não oferece API pública para iniciar chamadas; este serviço dirige
 * a interface: toca no botão de chamada de voz e, em seguida, no viva-voz.
 */
object WhatsAppCallBridge {

    private data class Request(val displayName: String, val stage: Stage, val at: Long)

    enum class Stage { CALL, SPEAKER }

    /** Só é verdadeiro quando o usuário ativou o serviço nas configurações. */
    @Volatile
    var enabled: Boolean = false

    @Volatile
    private var request: Request? = null

    @Volatile
    private var busy: Boolean = false

    private const val TIMEOUT_MS = 30_000L

    fun requestCall(displayName: String) {
        busy = false
        request = Request(displayName, Stage.CALL, System.currentTimeMillis())
    }

    fun requestSpeaker(displayName: String) {
        busy = false
        request = Request(displayName, Stage.SPEAKER, System.currentTimeMillis())
    }

    fun currentStage(): Pair<String, Stage>? {
        val current = request ?: return null
        if (System.currentTimeMillis() - current.at > TIMEOUT_MS) {
            request = null
            busy = false
            return null
        }
        return current.displayName to current.stage
    }

    /** Marca o estágio atual como concluído para não repetir o clique. */
    fun finish(stage: Stage) {
        if (request?.stage == stage) {
            request = null
            busy = false
        }
    }

    fun takeBusy(): Boolean {
        if (busy) return false
        busy = true
        return true
    }

    fun releaseBusy() {
        busy = false
    }
}

/**
 * Serviço de Acessibilidade (Nível 3). Fica ativo apenas nos pacotes do
 * WhatsApp e só age quando há uma chamada pendente pedida pelo Jarvis.
 */
class WhatsAppCallService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onServiceConnected() {
        super.onServiceConnected()
        WhatsAppCallBridge.enabled = true
        Log.i(TAG, "Servico de acessibilidade do WhatsApp conectado")
        serviceInfo = serviceInfo?.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = flags or AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            packageNames = arrayOf("com.whatsapp", "com.whatsapp.w4b")
            notificationTimeout = 150
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val stage = WhatsAppCallBridge.currentStage() ?: return
        if (!WhatsAppCallBridge.takeBusy()) return

        val root = rootInActiveWindow
        if (root == null) {
            WhatsAppCallBridge.releaseBusy()
            return
        }

        scope.launch {
            try {
                val keywords = when (stage.second) {
                    WhatsAppCallBridge.Stage.CALL -> CALL_WORDS
                    WhatsAppCallBridge.Stage.SPEAKER -> SPEAKER_WORDS
                }
                val node = findClickable(root, keywords)
                Log.i(TAG, "Estagio ${stage.second}: node=${node != null} pacote=${event?.packageName}")
                if (node != null && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    Log.i(TAG, "Clique realizado no estagio ${stage.second}")
                    WhatsAppCallBridge.finish(stage.second)
                    if (stage.second == WhatsAppCallBridge.Stage.CALL) {
                        // A tela da chamada demora a aparecer; só então o viva-voz.
                        delay(3_000)
                        WhatsAppCallBridge.requestSpeaker(stage.first)
                    }
                } else {
                    WhatsAppCallBridge.releaseBusy()
                }
            } catch (error: Exception) {
                WhatsAppCallBridge.releaseBusy()
            }
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        WhatsAppCallBridge.enabled = false
        WhatsAppCallBridge.releaseBusy()
        scope.cancel()
        super.onDestroy()
    }

    /** Procura um botão clicável cujo texto/descrição combine com as palavras. */
    private fun findClickable(
        node: AccessibilityNodeInfo?,
        keywords: List<String>
    ): AccessibilityNodeInfo? {
        if (node == null) return null
        val label = normalize(node.contentDescription?.toString().orEmpty() + " " +
            node.text?.toString().orEmpty())
        if (node.isClickable && node.isEnabled && keywords.any { label.contains(it) }) {
            return node
        }
        for (index in 0 until node.childCount) {
            findClickable(node.getChild(index), keywords)?.let { return it }
        }
        return null
    }

    private fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9 ]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    companion object {
        private const val TAG = "WhatsAppCallService"
        private val CALL_WORDS = listOf(
            "chamada de voz", "chamada de audio", "voice call", "audio call",
            "ligar", "chamar", "call"
        )
        private val SPEAKER_WORDS = listOf(
            "viva voz", "viva-voz", "speaker", "ativar alto falante",
            "ativar viva", "alto falante"
        )
    }
}

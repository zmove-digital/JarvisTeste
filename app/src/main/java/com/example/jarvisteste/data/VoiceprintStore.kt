package com.example.jarvisteste.data

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.jarvisteste.audio.VoiceFeatures
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Guarda as impressões vocais do usuário cifradas com o Android Keystore.
 *
 * Nada é enviado para a internet: o padrão é usado apenas dentro do aparelho
 * para decidir se quem falou é a mesma pessoa que fez o cadastro.
 */
class VoiceprintStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    val hasEnrollment: Boolean get() = templates.isNotEmpty()

    var templates: List<FloatArray>
        get() = decode(SecureStore.decrypt(prefs.getString(KEY_TEMPLATES, null)))
        set(value) {
            if (value.isEmpty()) {
                clear()
                return
            }
            val encrypted = SecureStore.encrypt(encode(value))
            if (encrypted.isNullOrBlank()) {
                // Não apaga um cadastro válido por uma falha transitória do Keystore.
                Log.w(TAG, "Falha ao cifrar a impressão vocal; mantendo o cadastro anterior")
                return
            }
            prefs.edit().putString(KEY_TEMPLATES, encrypted).apply()
        }

    fun save(items: List<FloatArray>) {
        templates = items
    }

    fun clear() = prefs.edit().remove(KEY_TEMPLATES).apply()

    private fun encode(items: List<FloatArray>): String {
        val samples = items.filter { it.size == FEATURE_SIZE }
        val buffer = ByteBuffer.allocate(HEADER_BYTES + samples.size * FEATURE_SIZE * Float.SIZE_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(VERSION)
        buffer.putInt(samples.size)
        buffer.putInt(VoiceFeatures.FRAMES)
        buffer.putInt(VoiceFeatures.DIMS)
        samples.forEach { sample ->
            sample.forEach { buffer.putFloat(it) }
        }
        return Base64.encodeToString(buffer.array(), Base64.NO_WRAP)
    }

    private fun decode(payload: String?): List<FloatArray> {
        if (payload.isNullOrBlank()) return emptyList()
        return runCatching {
            val bytes = Base64.decode(payload, Base64.NO_WRAP)
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            if (buffer.remaining() < HEADER_BYTES) return@runCatching emptyList()
            val version = buffer.int
            val count = buffer.int
            val frames = buffer.int
            val dims = buffer.int
            if (version != VERSION || frames != VoiceFeatures.FRAMES ||
                dims != VoiceFeatures.DIMS || count <= 0 ||
                count > MAX_SAMPLES
            ) {
                return@runCatching emptyList()
            }
            val expected = HEADER_BYTES + count * FEATURE_SIZE * Float.SIZE_BYTES
            if (bytes.size < expected) return@runCatching emptyList()
            List(count) {
                FloatArray(FEATURE_SIZE) { buffer.getFloat() }
            }
        }.getOrElse { emptyList() }
    }

    companion object {
        private const val TAG = "VoiceprintStore"
        private const val PREFS_NAME = "jarvis_voiceprint"
        private const val KEY_TEMPLATES = "voice_templates_encrypted"
        private const val VERSION = 1
        private const val MAX_SAMPLES = 12
        private const val HEADER_BYTES = Int.SIZE_BYTES * 4
        private val FEATURE_SIZE = VoiceFeatures.FRAMES * VoiceFeatures.DIMS
    }
}

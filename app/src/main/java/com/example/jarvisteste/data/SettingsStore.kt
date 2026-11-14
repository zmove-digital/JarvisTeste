package com.example.jarvisteste.data

import android.content.Context

enum class VoiceGender { FEMALE, MALE, ANY }

/**
 * Perfis de voz oferecidos no onboarding.
 * O "gênero" é apenas uma dica para escolher a melhor voz do sistema;
 * o ajuste fino vem de pitch/rate, que funciona em qualquer aparelho.
 */
data class VoiceProfile(
    val id: String,
    val label: String,
    val description: String,
    val pitch: Float,
    val rate: Float,
    val gender: VoiceGender
)

object VoiceProfiles {

    val all = listOf(
        VoiceProfile(
            id = "feminina",
            label = "Feminina",
            description = "Natural e acolhedora",
            pitch = 1.06f,
            rate = 1.0f,
            gender = VoiceGender.FEMALE
        ),
        VoiceProfile(
            id = "masculina",
            label = "Masculina",
            description = "Grave e tranquila",
            pitch = 0.86f,
            rate = 0.98f,
            gender = VoiceGender.MALE
        ),
        VoiceProfile(
            id = "narrador",
            label = "Narrador",
            description = "Estilo documentário, ritmo contido",
            pitch = 0.94f,
            rate = 0.93f,
            gender = VoiceGender.ANY
        ),
        VoiceProfile(
            id = "robo",
            label = "Robô",
            description = "Metálica e sintética",
            pitch = 0.62f,
            rate = 0.92f,
            gender = VoiceGender.ANY
        ),
        VoiceProfile(
            id = "animado",
            label = "Animado",
            description = "Rápida e expressiva",
            pitch = 1.22f,
            rate = 1.14f,
            gender = VoiceGender.ANY
        )
    )

    fun byId(id: String?): VoiceProfile =
        all.firstOrNull { it.id == id } ?: all.first()

    const val DEFAULT_ID = "feminina"
}

/**
 * Preferências locais do Jarvis. A chave da API nunca é gravada em texto puro:
 * vai para o SharedPreferences cifrada pelo [SecureStore].
 */
class SettingsStore(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var apiKey: String
        get() = SecureStore.decrypt(prefs.getString(KEY_API_KEY, null)).orEmpty()
        set(value) {
            val trimmed = value.trim()
            val encrypted = if (trimmed.isEmpty()) null else SecureStore.encrypt(trimmed)
            prefs.edit().putString(KEY_API_KEY, encrypted).apply()
        }

    val hasApiKey: Boolean get() = apiKey.isNotBlank()

    var onboarded: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDED, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDED, value).apply()

    var voiceProfileId: String
        get() = prefs.getString(KEY_VOICE_PROFILE, VoiceProfiles.DEFAULT_ID)
            ?: VoiceProfiles.DEFAULT_ID
        set(value) = prefs.edit().putString(KEY_VOICE_PROFILE, value).apply()

    var voiceName: String?
        get() = prefs.getString(KEY_VOICE_NAME, null)
        set(value) {
            prefs.edit().apply {
                if (value.isNullOrBlank()) remove(KEY_VOICE_NAME) else putString(KEY_VOICE_NAME, value)
            }.apply()
        }

    val profile: VoiceProfile get() = VoiceProfiles.byId(voiceProfileId)

    var pitch: Float
        get() = prefs.getFloat(KEY_PITCH, profile.pitch)
        set(value) = prefs.edit().putFloat(KEY_PITCH, value.coerceIn(0.1f, 2.0f)).apply()

    var rate: Float
        get() = prefs.getFloat(KEY_RATE, profile.rate)
        set(value) = prefs.edit().putFloat(KEY_RATE, value.coerceIn(0.5f, 2.0f)).apply()

    var wakeWord: String
        get() = prefs.getString(KEY_WAKE_WORD, DEFAULT_WAKE_WORD) ?: DEFAULT_WAKE_WORD
        set(value) = prefs.edit()
            .putString(KEY_WAKE_WORD, value.trim().lowercase().ifBlank { DEFAULT_WAKE_WORD })
            .apply()

    var alwaysListening: Boolean
        get() = prefs.getBoolean(KEY_ALWAYS_LISTENING, false)
        set(value) = prefs.edit().putBoolean(KEY_ALWAYS_LISTENING, value).apply()

    /**
     * Silencia os "bipes" do serviço de reconhecimento do Google (início/fim de
     * escuta) durante janelas curtas. Não afeta mídia nem a voz do Jarvis.
     */
    var silenceBeeps: Boolean
        get() = prefs.getBoolean(KEY_SILENCE_BEEPS, true)
        set(value) = prefs.edit().putBoolean(KEY_SILENCE_BEEPS, value).apply()

    /**
     * Avatar animado exibido na tela inicial e na tela cheia quando o Jarvis
     * conversa. O id corresponde a um item de [com.example.jarvisteste.ui.avatar.Avatars].
     */
    var avatarId: String
        get() = prefs.getString(KEY_AVATAR_ID, DEFAULT_AVATAR_ID) ?: DEFAULT_AVATAR_ID
        set(value) = prefs.edit().putString(KEY_AVATAR_ID, value).apply()

    companion object {
        private const val PREFS_NAME = "jarvis_settings"
        private const val KEY_API_KEY = "api_key_encrypted"
        private const val KEY_ONBOARDED = "onboarded"
        private const val KEY_VOICE_PROFILE = "voice_profile"
        private const val KEY_VOICE_NAME = "voice_name"
        private const val KEY_PITCH = "voice_pitch"
        private const val KEY_RATE = "voice_rate"
        private const val KEY_WAKE_WORD = "wake_word"
        private const val KEY_ALWAYS_LISTENING = "always_listening"
        private const val KEY_SILENCE_BEEPS = "silence_beeps"
        private const val KEY_AVATAR_ID = "avatar_id"
        const val DEFAULT_WAKE_WORD = "jarvis"
        const val DEFAULT_AVATAR_ID = "aurora"
    }
}

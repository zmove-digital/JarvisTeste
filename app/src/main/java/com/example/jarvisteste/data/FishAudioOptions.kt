package com.example.jarvisteste.data

/**
 * Configuração do motor de voz personalizado (Fish Audio).
 *
 * A chave é do próprio usuário e fica cifrada no aparelho. O [voiceId] aceita
 * tanto o ID puro quanto um link `https://fish.audio/m/...`; o ID é extraído
 * automaticamente antes de falar.
 */
data class FishAudioConfig(
    val apiKey: String = "",
    val voiceId: String = "",
    val voiceName: String = "",
    val model: String = FishAudioModels.DEFAULT
) {
    /** ID de 32 caracteres extraído do link (ou o texto digitado, se já for o ID). */
    val resolvedVoiceId: String
        get() = parseFishVoiceId(voiceId) ?: voiceId.trim()

    val ready: Boolean
        get() = apiKey.isNotBlank() && resolvedVoiceId.isNotBlank()
}

/** Modelos de síntese oferecidos pela API oficial do Fish Audio. */
object FishAudioModels {

    data class Option(
        val id: String,
        val label: String,
        val description: String,
        val free: Boolean
    )

    const val DEFAULT = "s2.1-pro-free"

    val all = listOf(
        Option(
            id = DEFAULT,
            label = "S2.1 Pro Free",
            description = "Grátis para testar, sob uso justo",
            free = true
        ),
        Option(
            id = "s2.1-pro",
            label = "S2.1 Pro",
            description = "Qualidade de produção (pago)",
            free = false
        ),
        Option(
            id = "drama-3",
            label = "Drama 3",
            description = "Modelo usado pelo ATEUS (pago)",
            free = false
        )
    )

    fun byId(id: String?): Option = all.firstOrNull { it.id == id } ?: all.first()
}

private val FISH_VOICE_ID = Regex("([0-9a-fA-F]{32})")

/** Chaves do Gemini começam com "AIza". */
fun looksLikeGeminiKey(input: String?): Boolean =
    input?.trim()?.startsWith("AIza", ignoreCase = true) == true

/** Chaves do Fish Audio começam com "sk-fish-". */
fun looksLikeFishKey(input: String?): Boolean =
    input?.trim()?.startsWith("sk-fish-", ignoreCase = true) == true

/**
 * Extrai o ID do modelo de voz de um link do Fish Audio (ou devolve o próprio
 * ID, se o usuário colou apenas ele).
 */
fun parseFishVoiceId(input: String?): String? {
    val text = input?.trim().orEmpty()
    if (text.isEmpty()) return null
    return FISH_VOICE_ID.find(text)?.groupValues?.getOrNull(1)?.lowercase()
}

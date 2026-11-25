package com.example.jarvisteste.data

/** Motor usado para transformar o texto da resposta em voz. */
enum class VoiceEngine { GEMINI, SYSTEM, FISH_AUDIO }

/**
 * Vozes naturais geradas pelo Gemini TTS.
 *
 * Os ids vêm da Voice Library do Google (pt-BR) e são enviados direto no
 * campo `speech_config.voice`. O [style] é uma orientação curta em inglês,
 * como a própria API recomenda, para manter o timbre estável entre respostas.
 */
data class GeminiVoice(
    val id: String,
    val label: String,
    val description: String,
    val style: String,
    val gender: VoiceGender
)

object GeminiVoices {

    val all = listOf(
        GeminiVoice(
            id = "pt-br-assistant-6",
            label = "Aurora",
            description = "Calorosa e envolvente",
            style = "warm and engaging",
            gender = VoiceGender.FEMALE
        ),
        GeminiVoice(
            id = "pt-br-assistant-5",
            label = "Lia",
            description = "Clara e amigável",
            style = "clear and friendly",
            gender = VoiceGender.FEMALE
        ),
        GeminiVoice(
            id = "pt-br-tutor-4",
            label = "Sofia",
            description = "Calma e acolhedora",
            style = "calm and reassuring",
            gender = VoiceGender.FEMALE
        ),
        GeminiVoice(
            id = "pt-br-concierge-4",
            label = "Bia",
            description = "Jovem e animada",
            style = "bright and youthful",
            gender = VoiceGender.FEMALE
        ),
        GeminiVoice(
            id = "pt-br-podcaster-6",
            label = "Helena",
            description = "Profissional e próxima",
            style = "professional yet approachable",
            gender = VoiceGender.FEMALE
        ),
        GeminiVoice(
            id = "pt-br-advisor-12",
            label = "Clara",
            description = "Madura e confiante",
            style = "warm and confident",
            gender = VoiceGender.FEMALE
        ),
        GeminiVoice(
            id = "pt-br-assistant-11",
            label = "Nexus",
            description = "Grave e serena",
            style = "deep, calm and unhurried",
            gender = VoiceGender.MALE
        ),
        GeminiVoice(
            id = "pt-br-assistant-9",
            label = "Caio",
            description = "Caloroso e empático",
            style = "warm and engaging",
            gender = VoiceGender.MALE
        ),
        GeminiVoice(
            id = "pt-br-podcaster-3",
            label = "Rafael",
            description = "Ressonante e suave",
            style = "resonant and soothing",
            gender = VoiceGender.MALE
        ),
        GeminiVoice(
            id = "pt-br-concierge-3",
            label = "Otávio",
            description = "Elegante e espirituoso",
            style = "resonant and witty",
            gender = VoiceGender.MALE
        ),
        GeminiVoice(
            id = "pt-br-advisor-11",
            label = "Davi",
            description = "Descontraído e tranquilo",
            style = "laid-back and encouraging",
            gender = VoiceGender.MALE
        ),
        GeminiVoice(
            id = "pt-br-tutor-6",
            label = "Miguel",
            description = "Acolhedor e paciente",
            style = "warm and patient",
            gender = VoiceGender.MALE
        )
    )

    val byId: (String?) -> GeminiVoice = { id ->
        all.firstOrNull { it.id == id } ?: all.first()
    }

    const val DEFAULT_ID = "pt-br-assistant-6"

    fun engineOf(value: String?): VoiceEngine = when (value?.lowercase()) {
        "system" -> VoiceEngine.SYSTEM
        "fish_audio", "fish", "custom" -> VoiceEngine.FISH_AUDIO
        else -> VoiceEngine.GEMINI
    }
}

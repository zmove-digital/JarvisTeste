package com.example.jarvisteste.ui.avatar

import com.example.jarvisteste.service.ListeningPhase

/** Estado visual do orbe, derivado do estado da conversa. */
enum class AvatarMood { IDLE, LISTENING, THINKING, SPEAKING }

/** Converte o estado da escuta no humor do orbe. */
fun ListeningPhase.toAvatarMood(): AvatarMood = when (this) {
    ListeningPhase.AWAKE -> AvatarMood.LISTENING
    ListeningPhase.THINKING -> AvatarMood.THINKING
    ListeningPhase.SPEAKING -> AvatarMood.SPEAKING
    else -> AvatarMood.IDLE
}

package com.example.jarvisteste.ui.avatar

import androidx.compose.ui.graphics.Color
import com.example.jarvisteste.service.ListeningPhase

enum class AvatarStyle { FACE, ORB }

enum class HairStyle { LONG, SHORT, NONE }

enum class AvatarMood { IDLE, LISTENING, THINKING, SPEAKING }

data class AvatarPalette(
    val backdropTop: Color,
    val backdropBottom: Color,
    val glow: Color,
    val accent: Color,
    val skin: Color,
    val skinShade: Color,
    val hair: Color,
    val clothes: Color,
    val eye: Color,
    val mouth: Color
)

data class AvatarPreset(
    val id: String,
    val name: String,
    val tagline: String,
    val style: AvatarStyle,
    val hair: HairStyle,
    val palette: AvatarPalette
)

/**
 * Avatares desenhados em Compose (sem imagens externas), então escalam em
 * qualquer tela e se animam em tempo real: piscam, respiram e mexem a boca
 * enquanto o Jarvis fala.
 */
object Avatars {

    const val DEFAULT_ID = "aurora"

    val all = listOf(
        AvatarPreset(
            id = "aurora",
            name = "Aurora",
            tagline = "Acolhedora",
            style = AvatarStyle.FACE,
            hair = HairStyle.LONG,
            palette = AvatarPalette(
                backdropTop = Color(0xFF2A1236),
                backdropBottom = Color(0xFF0B0611),
                glow = Color(0xFFFF7BC8),
                accent = Color(0xFFFFC2E8),
                skin = Color(0xFFF2C3A7),
                skinShade = Color(0xFFD89C82),
                hair = Color(0xFF3A1B4A),
                clothes = Color(0xFF5C2A78),
                eye = Color(0xFF4A2360),
                mouth = Color(0xFF8C3B52)
            )
        ),
        AvatarPreset(
            id = "nexus",
            name = "Nexus",
            tagline = "Tecnológico",
            style = AvatarStyle.FACE,
            hair = HairStyle.SHORT,
            palette = AvatarPalette(
                backdropTop = Color(0xFF071B2C),
                backdropBottom = Color(0xFF02080F),
                glow = Color(0xFF4FD6FF),
                accent = Color(0xFFA7EBFF),
                skin = Color(0xFFE3B58C),
                skinShade = Color(0xFFC08F66),
                hair = Color(0xFF142739),
                clothes = Color(0xFF14476B),
                eye = Color(0xFF1C5A7A),
                mouth = Color(0xFF7A3B45)
            )
        ),
        AvatarPreset(
            id = "orbe",
            name = "Orbe",
            tagline = "Minimalista",
            style = AvatarStyle.ORB,
            hair = HairStyle.NONE,
            palette = AvatarPalette(
                backdropTop = Color(0xFF042126),
                backdropBottom = Color(0xFF01080A),
                glow = Color(0xFF63F2D1),
                accent = Color(0xFFB6FFEE),
                skin = Color(0xFF63F2D1),
                skinShade = Color(0xFF2A9C8A),
                hair = Color(0xFF0E4A47),
                clothes = Color(0xFF0E4A47),
                eye = Color(0xFFB6FFEE),
                mouth = Color(0xFF2A9C8A)
            )
        )
    )

    fun byId(id: String?): AvatarPreset =
        all.firstOrNull { it.id == id } ?: all.first()
}

/** Converte o estado da escuta no humor do avatar. */
fun ListeningPhase.toAvatarMood(): AvatarMood = when (this) {
    ListeningPhase.AWAKE -> AvatarMood.LISTENING
    ListeningPhase.THINKING -> AvatarMood.THINKING
    ListeningPhase.SPEAKING -> AvatarMood.SPEAKING
    else -> AvatarMood.IDLE
}

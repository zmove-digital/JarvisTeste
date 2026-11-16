package com.example.jarvisteste.ui.avatar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

/**
 * Desenha o avatar escolhido: foto animada quando houver uma, senão o avatar
 * vetorial correspondente ao id.
 */
@Composable
fun AvatarView(
    preset: AvatarPreset,
    photo: PhotoAvatar?,
    mood: AvatarMood,
    modifier: Modifier = Modifier
) {
    if (photo != null) {
        PhotoAvatarView(photo = photo, mood = mood, modifier = modifier)
    } else {
        JarvisAvatar(preset = preset, mood = mood, modifier = modifier)
    }
}

/**
 * Carrega o retrato salvo apenas quando ele é o avatar escolhido. O retorno
 * inclui o preset (nome e paleta) para a interface usar as mesmas cores.
 */
@Composable
fun rememberAvatar(avatarId: String): Pair<AvatarPreset, PhotoAvatar?> {
    val context = LocalContext.current
    val photo = remember(avatarId, context) {
        if (avatarId == PhotoAvatar.ID) PhotoAvatarStore(context).load() else null
    }
    return (photo?.preset ?: Avatars.byId(avatarId)) to photo
}

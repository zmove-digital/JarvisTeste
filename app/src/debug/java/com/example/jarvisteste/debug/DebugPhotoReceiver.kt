package com.example.jarvisteste.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.jarvisteste.data.SettingsStore
import com.example.jarvisteste.ui.avatar.PhotoAvatar
import com.example.jarvisteste.ui.avatar.PhotoAvatarImporter
import com.example.jarvisteste.ui.avatar.PhotoAvatarStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * Receptor exclusivo da variante debug. Importa uma foto direto de um arquivo
 * do aparelho, sem passar pelo seletor de imagens:
 *
 *   adb push rosto.jpg /sdcard/Android/data/com.example.jarvisteste/files/rosto.jpg
 *   adb shell am broadcast -a com.example.jarvisteste.DEBUG_PHOTO \
 *       -n com.example.jarvisteste/.debug.DebugPhotoReceiver \
 *       --es path /sdcard/Android/data/com.example.jarvisteste/files/rosto.jpg
 *
 * Ao terminar, a foto vira o avatar escolhido. Use `--ez select false` para
 * analisar sem trocar o avatar atual.
 */
class DebugPhotoReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val path = intent.getStringExtra("path")
        if (path.isNullOrBlank()) {
            Log.e(TAG, "Informe --es path <arquivo>")
            return
        }
        val select = intent.getBooleanExtra("select", true)
        val appContext = context.applicationContext
        val pending = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val result = PhotoAvatarImporter(appContext).importFile(File(path))
                result.onSuccess { imported ->
                    val saved = PhotoAvatarStore(appContext).save(imported)
                    if (saved && select) {
                        SettingsStore(appContext).avatarId = PhotoAvatar.ID
                    }
                    Log.i(
                        TAG,
                        "Foto ${imported.width}x${imported.height} importada. " +
                            "salva=$saved avatar=${SettingsStore(appContext).avatarId}"
                    )
                }.onFailure { error ->
                    Log.e(TAG, "Falha ao importar foto: ${error.message}", error)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "DebugPhoto"
    }
}

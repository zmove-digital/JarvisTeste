package com.example.jarvisteste.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.jarvisteste.ui.avatar.AvatarPresenter

/**
 * Receptor exclusivo da variante debug. Dispare pelo ADB:
 *
 *   adb shell am broadcast -a com.example.jarvisteste.DEBUG_AVATAR \
 *       -n com.example.jarvisteste/.debug.DebugAvatarReceiver
 *
 * Use `--ez demo true` para manter a tela aberta na prévia (sem serviço).
 */
class DebugAvatarReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val demo = intent.getBooleanExtra("demo", false)
        AvatarPresenter(context).show(force = true, demo = demo)
    }
}

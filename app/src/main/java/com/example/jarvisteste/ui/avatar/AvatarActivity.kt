package com.example.jarvisteste.ui.avatar

import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.jarvisteste.ui.theme.JarvisTesteTheme

/**
 * Tela cheia com o avatar animado. É aberta pelo [AvatarPresenter] quando o
 * Jarvis é chamado (inclusive com o aparelho bloqueado) e se fecha sozinha
 * alguns segundos depois que a conversa volta ao modo de espera.
 */
class AvatarActivity : ComponentActivity() {

    private var demo = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        demo = intent?.getBooleanExtra(EXTRA_DEMO, false) ?: false
        prepareWindow()

        setContent {
            JarvisTesteTheme {
                AvatarScreen(demo = demo, onClose = { finish() })
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        prepareWindow()
        // Uma nova chamada com a tela apagada precisa acendê-la de novo.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) setTurnScreenOn(true)
        runCatching {
            getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
        }
    }

    override fun onResume() {
        super.onResume()
        AvatarPresenter.isVisible = true
        // A atividade já está na frente; a notificação de tela cheia cumpriu
        // seu papel. Sem isso ela ficaria fixa na barra de status.
        runCatching {
            getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
        }
    }

    override fun onPause() {
        AvatarPresenter.isVisible = false
        super.onPause()
    }

    override fun onStop() {
        AvatarPresenter.isVisible = false
        super.onStop()
    }

    override fun onDestroy() {
        AvatarPresenter.isVisible = false
        runCatching {
            getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
        }
        super.onDestroy()
    }

    private fun prepareWindow() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    companion object {
        const val EXTRA_DEMO = "com.example.jarvisteste.extra.AVATAR_DEMO"
        private const val NOTIFICATION_ID = 43
    }
}

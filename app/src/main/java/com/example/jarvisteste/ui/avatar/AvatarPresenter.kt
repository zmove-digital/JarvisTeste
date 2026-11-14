package com.example.jarvisteste.ui.avatar

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.jarvisteste.R

/**
 * Acende a tela e traz o avatar para frente quando o Jarvis é chamado.
 *
 * O caminho principal é uma notificação de alta prioridade com "full-screen
 * intent": é o jeito sancionado pelo Android de abrir uma tela sobre a tela de
 * bloqueio. Ela precisa ser publicada com a tela ainda apagada — acordar a tela
 * antes faz o sistema tratar o aparelho como "em uso" e não abrir a atividade.
 *
 * Por isso o despertar direto e a tentativa de abrir a atividade ficam para um
 * segundo momento, apenas como reforço caso a notificação não tenha efeito.
 *
 * No Android 14+ o usuário precisa autorizar "notificações de tela cheia" nos
 * ajustes do sistema; sem isso o full-screen intent vira somente um aviso.
 */
class AvatarPresenter(context: Context) {

    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(NotificationManager::class.java)
    private val handler = Handler(Looper.getMainLooper())

    private var wakeLock: PowerManager.WakeLock? = null
    private var pendingIntentToOpen: Intent? = null

    /**
     * @param force mostra mesmo que a tela esteja ligada e o avatar já esteja
     *   visível. Usado quando a palavra de ativação é dita.
     * @param demo abre a tela no modo de prévia (usado só em testes).
     */
    fun show(force: Boolean, demo: Boolean = false) {
        val power = appContext.getSystemService(PowerManager::class.java)
        val screenOff = power?.isInteractive == false
        // Já está na frente: não repete notificação nem abertura.
        if (isVisible) return
        if (!force && !screenOff) return

        ensureChannel()

        val activityIntent = Intent(appContext, AvatarActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
            if (demo) putExtra(AvatarActivity.EXTRA_DEMO, true)
        }
        pendingIntentToOpen = activityIntent

        val pendingIntent = PendingIntent.getActivity(
            appContext,
            REQUEST_CODE,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_jarvis)
            .setContentTitle("Jarvis")
            .setContentText("Conversando com você")
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setOngoing(true)
            .setOnlyAlertOnce(false)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_ALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        Log.i(
            TAG,
            "show(force=$force, demo=$demo) screenOff=$screenOff " +
                "fsiAllowed=${canUseFullScreenIntent(appContext)} visible=$isVisible"
        )

        runCatching { manager?.notify(NOTIFICATION_ID, notification) }
            .onFailure { Log.w(TAG, "Falha ao publicar a notificação do avatar", it) }

        // Se o full-screen intent abriu a atividade, este reforço não faz nada.
        handler.removeCallbacks(screenFallback)
        handler.postDelayed(screenFallback, FALLBACK_DELAY_MS)
    }

    /** Remove a notificação e solta o wakelock de tela. */
    fun hide() {
        handler.removeCallbacks(screenFallback)
        pendingIntentToOpen = null
        runCatching { manager?.cancel(NOTIFICATION_ID) }
        releaseWakeLock()
    }

    /**
     * Reforço para quando o sistema não abriu a atividade pela notificação.
     * Acende a tela e tenta a abertura direta (que pode ser bloqueada se o
     * aparelho estiver destravado — nesse caso resta o aviso na tela).
     */
    private val screenFallback = object : Runnable {
        override fun run() {
            if (isVisible) return
            val intent = pendingIntentToOpen ?: return
            val power = appContext.getSystemService(PowerManager::class.java)
            if (power?.isInteractive == false) wakeScreen(power)
            runCatching { appContext.startActivity(intent) }
                .onFailure { Log.w(TAG, "Abertura direta do avatar bloqueada", it) }

            if (!isVisible) {
                handler.postDelayed({
                    if (!isVisible) {
                        val again = appContext.getSystemService(PowerManager::class.java)
                        if (again?.isInteractive == false) wakeScreen(again)
                        runCatching { appContext.startActivity(intent) }
                    }
                }, FALLBACK_RETRY_MS)
            }
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Avatar do Jarvis",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Abre o avatar quando o Jarvis é chamado."
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager?.createNotificationChannel(channel)
    }

    @Suppress("DEPRECATION")
    private fun wakeScreen(power: PowerManager?) {
        if (power == null || power.isInteractive) return
        runCatching {
            val lock = wakeLock ?: power.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                    PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    PowerManager.ON_AFTER_RELEASE,
                "Jarvis::AvatarWakeLock"
            ).apply {
                setReferenceCounted(false)
            }.also { wakeLock = it }
            lock.acquire(SCREEN_WAKE_TIMEOUT_MS)
        }.onFailure { Log.w(TAG, "Não foi possível acender a tela", it) }
    }

    private fun releaseWakeLock() {
        val lock = wakeLock ?: return
        wakeLock = null
        runCatching {
            if (lock.isHeld) lock.release()
        }
    }

    companion object {
        private const val TAG = "AvatarPresenter"
        private const val CHANNEL_ID = "jarvis_avatar"
        private const val NOTIFICATION_ID = 43
        private const val REQUEST_CODE = 43
        private const val SCREEN_WAKE_TIMEOUT_MS = 15_000L
        private const val FALLBACK_DELAY_MS = 1_400L
        private const val FALLBACK_RETRY_MS = 1_200L

        /** Verdadeiro enquanto [AvatarActivity] está na frente. */
        @Volatile
        var isVisible: Boolean = false
            internal set

        /** No Android 14+ o sistema exige autorização explícita. */
        fun canUseFullScreenIntent(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
            val notifications =
                context.getSystemService(NotificationManager::class.java) ?: return false
            return notifications.canUseFullScreenIntent()
        }
    }
}

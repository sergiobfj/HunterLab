package dev.hunterlab.app.nav

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import dev.hunterlab.app.HunterLabApplication
import dev.hunterlab.app.MainActivity
import dev.hunterlab.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch

/**
 * Serviço em primeiro plano da navegação (tipos `location` e `connectedDevice`).
 * Mantém GPS e Tripper funcionando com a tela bloqueada — sem localização em segundo plano: só existe enquanto
 * a navegação iniciada pelo usuário está ativa, e a notificação sempre tem **Encerrar**.
 * A lógica fica no [NavigationController]; este serviço só segura o processo em primeiro plano.
 */
class NavigationService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var wakeLock: PowerManager.WakeLock? = null
    private var collecting = false
    private var stopped = false
    private val controller get() = (application as HunterLabApplication).navigation

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            controller.stopAll()
            stopSelfSafely()
            return START_NOT_STICKY
        }
        ensureChannel()
        stopped = false
        var types = 0
        if (controller.wantsLocation && granted(Manifest.permission.ACCESS_FINE_LOCATION)) {
            types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        }
        if (granted(Manifest.permission.BLUETOOTH_CONNECT)) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        if (types == 0) {
            controller.onServiceFailed("Sem permissão de localização nem de Bluetooth: a navegação não fica ativa com a tela bloqueada.")
            stopSelfSafely()
            return START_NOT_STICKY
        }
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, build(controller.notificationText.value), types)
        } catch (e: RuntimeException) {
            // Sem permissão de localização ou app fora do primeiro plano: não dá para navegar com tela bloqueada.
            controller.onServiceFailed("Não foi possível manter a navegação em primeiro plano (${e.javaClass.simpleName}).")
            stopSelfSafely()
            return START_NOT_STICKY
        }
        acquireWakeLock()
        controller.onServiceStarted()
        if (!collecting) {
            collecting = true
            scope.launch {
                controller.notificationText.collect { text ->
                    if (!stopped) getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, build(text))
                }
            }
            scope.launch {
                controller.serviceNeeded.collect { needed -> if (!needed) stopSelfSafely() }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        controller.onServiceStopped()
        scope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    /** O usuário removeu o app da lista de recentes: encerrar com segurança (o Tripper volta ao relógio). */
    override fun onTaskRemoved(rootIntent: Intent?) {
        controller.stopAll()
        stopSelfSafely()
    }

    private fun granted(permission: String) = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun stopSelfSafely() {
        if (stopped) return
        stopped = true
        collecting = false
        scope.coroutineContext.cancelChildren()
        controller.onServiceStopped()
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HunterLab:navegacao")
            ?.apply {
                setReferenceCounted(false)
                acquire(MAX_WAKE_MS)
            }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Navegação", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Navegação ativa e conexão com o Tripper"
                    setShowBadge(false)
                },
            )
        }
    }

    private fun build(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, NavigationService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Hunter Lab — navegação")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(open)
            .addAction(0, "Encerrar", stop)
            .build()
    }

    companion object {
        private const val CHANNEL = "navegacao"
        private const val NOTIFICATION_ID = 42
        private const val ACTION_STOP = "dev.hunterlab.app.nav.STOP"
        private const val MAX_WAKE_MS = 6 * 60 * 60 * 1000L

        fun start(context: Context) {
            context.startForegroundService(Intent(context, NavigationService::class.java))
        }
    }
}

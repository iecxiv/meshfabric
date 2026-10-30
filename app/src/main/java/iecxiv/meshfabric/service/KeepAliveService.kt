package iecxiv.meshfabric.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import iecxiv.meshfabric.MainActivity
import iecxiv.meshfabric.R

class KeepAliveService : Service() {
    private val statusHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var statusNotification: Notification? = null
    private val statusUpdater = object : Runnable {
        override fun run() {
            if (!isServiceRunning) return
            val previous = statusNotification
            val icon = currentStatusIcon()
            if (previous != null && previous.smallIcon?.resId != icon) {
                val updated = Notification.Builder.recoverBuilder(this@KeepAliveService, previous)
                    .setSmallIcon(icon).setOnlyAlertOnce(true).build()
                getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, updated)
                statusNotification = updated
            }
            statusHandler.postDelayed(this, 1000L)
        }
    }

    private fun currentStatusIcon(): Int {
        return try {
            val manager = getSystemService(NotificationManager::class.java) ?: return R.drawable.ic_stat_remote
            val active = manager.activeNotifications.map { it.notification }.filter { n ->
                n.channelId == "file_downloads" &&
                    (n.extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0) > 0 ||
                        n.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false))
            }
            val icons = active.mapNotNull { it.smallIcon }.filter { it.type == android.graphics.drawable.Icon.TYPE_RESOURCE }.map { it.resId }
            when {
                android.R.drawable.stat_sys_download in icons -> android.R.drawable.stat_sys_download
                android.R.drawable.stat_sys_upload in icons -> android.R.drawable.stat_sys_upload
                else -> R.drawable.ic_stat_remote
            }
        } catch (_: Exception) { R.drawable.ic_stat_remote }
    }


    companion object {
        const val CHANNEL_ID = "mesh_keep_alive_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "iecxiv.meshfabric.action.START_KEEP_ALIVE"
        const val ACTION_STOP = "iecxiv.meshfabric.action.STOP_KEEP_ALIVE"
        const val EXTRA_HAS_ACTIVE_CONNECTED = "extra_has_active_connected"
        const val EXTRA_HAS_RECOVERABLE_DESKTOP = "extra_has_recoverable_desktop"
        const val EXTRA_HAS_DISCONNECTED_BUFFER = "extra_has_disconnected_buffer"

        var isServiceRunning = false
            private set

        var onTaskRemovedListener: (() -> Unit)? = null

        fun startService(
            context: Context,
            hasActiveConnected: Boolean = true,
            hasDisconnectedBuffer: Boolean = false,
            hasRecoverableDesktop: Boolean = false
        ) {
            val intent = Intent(context, KeepAliveService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_HAS_ACTIVE_CONNECTED, hasActiveConnected)
                putExtra(EXTRA_HAS_DISCONNECTED_BUFFER, hasDisconnectedBuffer)
                putExtra(EXTRA_HAS_RECOVERABLE_DESKTOP, hasRecoverableDesktop)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun updateNotification(
            context: Context,
            hasActiveConnected: Boolean = true,
            hasDisconnectedBuffer: Boolean = false,
            hasRecoverableDesktop: Boolean = false
        ) {
            if (isServiceRunning) {
                startService(context, hasActiveConnected, hasDisconnectedBuffer, hasRecoverableDesktop)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, KeepAliveService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            statusHandler.removeCallbacks(statusUpdater)
            statusNotification = null
            isServiceRunning = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val hasActiveConnected = intent?.getBooleanExtra(EXTRA_HAS_ACTIVE_CONNECTED, true) ?: true
        val hasDisconnectedBuffer = intent?.getBooleanExtra(EXTRA_HAS_DISCONNECTED_BUFFER, false) ?: false
        val notification = createNotification(hasActiveConnected, hasDisconnectedBuffer, intent?.getBooleanExtra(EXTRA_HAS_RECOVERABLE_DESKTOP, false) ?: false)

        statusNotification = notification
        if (isServiceRunning) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.notify(NOTIFICATION_ID, notification)
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    try {
                        startForeground(
                            NOTIFICATION_ID,
                            notification,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                        )
                    } catch (_: Exception) {
                        startForeground(NOTIFICATION_ID, notification)
                    }
                } else {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                }
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            isServiceRunning = true
        }

        statusHandler.removeCallbacks(statusUpdater)
        statusHandler.postDelayed(statusUpdater, 1000L)
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        statusHandler.removeCallbacks(statusUpdater)
        statusNotification = null
        isServiceRunning = false
        onTaskRemovedListener?.invoke()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        statusHandler.removeCallbacks(statusUpdater)
        statusNotification = null
        isServiceRunning = false
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Sesiones Activas MeshFabric",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mantiene las sesiones de MeshFabric activas en segundo plano"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(
        hasActiveConnected: Boolean,
        hasDisconnectedBuffer: Boolean,
        hasRecoverableDesktop: Boolean
    ): Notification {
        val titleText = "MeshFabric"
        val contentText = if (hasRecoverableDesktop && !hasDisconnectedBuffer) getString(R.string.notification_desktop_sessions_connected) else try {
            val app = iecxiv.meshfabric.MeshFabricApp.instance
            when {
                hasActiveConnected && hasDisconnectedBuffer -> app.getString(R.string.notification_sessions_mixed)
                hasDisconnectedBuffer -> app.getString(R.string.notification_sessions_disconnected)
                else -> app.getString(R.string.notification_sessions_active)
            }
        } catch (_: Exception) {
            when {
                hasActiveConnected && hasDisconnectedBuffer -> "sesiones activas en segundo plano + sesiones desconectadas guardadas"
                hasDisconnectedBuffer -> "sesiones desconectadas, cambio de red / tunel cerrado"
                else -> "sesiones activas en segundo plano"
            }
        }

        val returnIntent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val returnPendingIntent = PendingIntent.getActivity(
            this, NOTIFICATION_ID, returnIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(currentStatusIcon())
            .setGroup("meshfabric_sessions_and_transfers")
            .setGroupSummary(true)
            .setOnlyAlertOnce(true)
            .setContentTitle(titleText)
            .setContentText(contentText)
            .setContentIntent(returnPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()
    }
}

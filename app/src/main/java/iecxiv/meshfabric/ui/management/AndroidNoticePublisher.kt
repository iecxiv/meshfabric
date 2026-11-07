package iecxiv.meshfabric.ui.management

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import iecxiv.meshfabric.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal object NoticeVisibility {
    @Volatile var visible = true
}

internal object NoticeNavigation {
    private val pending = MutableStateFlow<String?>(null)
    private val active = MutableStateFlow<String?>(null)
    val request = pending.asStateFlow()
    val activeScope = active.asStateFlow()
    fun bind(scope: String) { active.value = scope }
    fun release(scope: String) { active.compareAndSet(scope, null) }
    fun receive(intent: Intent?) {
        if (intent?.action == "iecxiv.meshfabric.OPEN_NOTICES") {
            pending.value = intent.getStringExtra("notice_scope")
            intent.removeExtra("notice_scope")
        }
    }
    fun consume(scope: String) { pending.compareAndSet(scope, null) }
}

internal class AndroidNoticePublisher(context: Context) {
    private val app = context.applicationContext
    private val manager = app.getSystemService(NotificationManager::class.java)
    private val posted = mutableSetOf<Pair<String, Int>>()

    private fun channel(sound: Boolean): String {
        val id = if (sound) "meshfabric_notices_sound_v1" else "meshfabric_notices_silent_v1"
        val es = app.resources.configuration.locales[0].language == "es"
        val name = if (sound) {
            if (es) "Avisos con sonido" else "Notices with sound"
        } else if (es) "Avisos silenciosos" else "Silent notices"
        val channel = NotificationChannel(id, name, NotificationManager.IMPORTANCE_DEFAULT)
        if (!sound) { channel.setSound(null, null); channel.enableVibration(false) }
        manager.createNotificationChannel(channel)
        return id
    }

    fun allowed(): Boolean =
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
        NotificationManagerCompat.from(app).areNotificationsEnabled()

    @Synchronized fun post(scope: String, entry: NoticeInboxEntry, options: AccountNoticeOptions) {
        if (NoticeVisibility.visible || !allowed()) return
        val note = entry.notice
        if (note.serial !in 1..Int.MAX_VALUE.toLong()) return
        val es = app.resources.configuration.locales[0].language == "es"
        val title = entry.title.ifBlank {
            when (note.kind) {
                "connect" -> if (es) "Conexión de dispositivo" else "Device connection"
                "disconnect" -> if (es) "Desconexión de dispositivo" else "Device disconnection"
                "amt" -> if (es) "Evento Intel AMT" else "Intel AMT event"
                else -> if (es) "Aviso de MeshFabric" else "MeshFabric notice"
            }
        }
        val text = listOf(note.nodeName, if (options.groupName) note.groupName else "", note.text).filter { it.isNotBlank() }.joinToString(" · ")
        val intent = Intent(app, MainActivity::class.java).apply {
            action = "iecxiv.meshfabric.OPEN_NOTICES"
            putExtra("notice_scope", scope)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            data = android.net.Uri.Builder().scheme("meshfabric-notice").authority("inbox").appendPath(scope).appendPath(note.serial.toString()).build()
        }
        val pending = PendingIntent.getActivity(app, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val id = note.serial.toInt()
        val notification = NotificationCompat.Builder(app, channel(options.sound))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending).setAutoCancel(true).setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(NotificationCompat.Builder(app, "meshfabric_notices_silent_v1")
                .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("MeshFabric").build())
            .build()
        runCatching { manager.notify(scope, id, notification) }.onSuccess { posted.add(scope to id) }
    }

    @Synchronized fun cancel(scope: String, serial: Long) {
        if (serial !in 1..Int.MAX_VALUE.toLong()) return
        val key = scope to serial.toInt()
        runCatching { manager.cancel(key.first, key.second) }
        posted.remove(key)
    }

    @Synchronized fun clear() {
        posted.toList().forEach { (scope, id) -> runCatching { manager.cancel(scope, id) } }
        posted.clear()
    }
}

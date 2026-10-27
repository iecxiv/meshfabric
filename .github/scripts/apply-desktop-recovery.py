from pathlib import Path
import hashlib

root = Path('app/src/main/java/iecxiv/meshfabric')
p = root / 'data/remote/DesktopRelayClient.kt'
s = p.read_text()
assert hashlib.sha256(p.read_bytes()).hexdigest() == '3110c693dbed0cd804f52206ffec745a0bbca8c37597c8651affa224cb1fec1d', 'Desktop source changed; review required'
def replace_once(old, new):
    global s
    assert s.count(old) == 1, old[:100]
    s = s.replace(old, new, 1)
replace_once('    var webSocket: WebSocket? = null\n    var isUserDisconnect = false', '''    @Volatile var webSocket: WebSocket? = null
    @Volatile var isUserDisconnect = true
    private var visible = false
    private var epoch = 0L
    private var retryAt = 0L
    private var attemptStarted = 0L
    private var failures = 0
    private var recoveryJob: Job? = null
    private var tunnelJob: Job? = null
    private var initializationJob: Job? = null''')
a = s.index('    private fun startUnpauseLoop()')
b = s.index('    fun updateRemoteClipboard(', a)
s = s[:a] + '''    private fun nowMs() = System.nanoTime() / 1_000_000L

    private fun current(socket: WebSocket, generation: Long): Boolean =
        !isUserDisconnect && epoch == generation && webSocket === socket

    private fun startUnpauseLoop() {
        stopUnpauseLoop()
        val socket = webSocket ?: return
        val generation = epoch
        if (!visible) return
        unpauseJob = scope.launch {
            while (true) {
                delay(_settings.value.frameRate.delayMs.coerceAtLeast(100L))
                synchronized(this@DesktopSession) {
                    if (current(socket, generation) && visible && isConnected()) sendUnPause()
                }
            }
        }
    }

    @Synchronized
    fun stopUnpauseLoop() {
        unpauseJob?.cancel()
        unpauseJob = null
    }

    @Synchronized
    fun pauseSession() {
        visible = false
        stopUnpauseLoop()
    }

    @Synchronized
    fun resumeSession() {
        if (isUserDisconnect) return
        visible = true
        if (isConnected()) {
            refreshDisplay()
            startUnpauseLoop()
        } else {
            connect()
        }
    }

    @Synchronized
    fun connect() {
        isUserDisconnect = false
        visible = true
        onStateChanged?.invoke()
        if (isConnected()) {
            resumeSession()
            return
        }
        forceReconnect()
    }

    @Synchronized
    fun forceReconnect() {
        if (isUserDisconnect) return
        retireSocket()
        failures = 0
        retryAt = 0L
        if (!meshClient.connectionState.value && !meshClient.isUserLogout) meshClient.reconnectNow()
        if (recoveryJob?.isActive != true) {
            recoveryJob = scope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
                while (true) {
                    synchronized(this@DesktopSession) {
                        if (meshClient.isUserLogout) {
                            disconnect()
                            return@launch
                        }
                        if (!isUserDisconnect) {
                            if (!meshClient.connectionState.value) {
                                if (webSocket != null) retireSocket()
                            } else if (webSocket != null && !isConnected() && nowMs() - attemptStarted >= 20_000L) {
                                Log.w("DesktopSession", "Desktop handshake timed out for $cleanNodeId")
                                retireSocket()
                            }
                            if (meshClient.connectionState.value && webSocket == null && nowMs() >= retryAt) openSocket()
                        }
                    }
                    delay(1000L)
                }
            }
            recoveryJob?.start()
        }
        if (meshClient.connectionState.value) openSocket()
    }

    private fun retireSocket() {
        epoch++
        tunnelJob?.cancel()
        tunnelJob = null
        initializationJob?.cancel()
        initializationJob = null
        stopUnpauseLoop()
        val old = webSocket
        webSocket = null
        updateState(false)
        old?.cancel()
        retryAt = nowMs() + DesktopRecoveryPolicy.retryDelay(failures++)
    }

    private fun openSocket() {
        if (isUserDisconnect || meshClient.isUserLogout || webSocket != null || !meshClient.connectionState.value) return
        val generation = ++epoch
        attemptStarted = nowMs()
        try {
            val token = meshClient.relayAuthCookie.ifEmpty {
                meshClient.userAuthCookie.ifEmpty { meshClient.extractRawToken(meshClient.authCookie) }
            }
            val tunnelId = java.util.UUID.randomUUID().toString()
            val encodedToken = URLEncoder.encode(token, "UTF-8")
            val encodedNode = URLEncoder.encode(fullNodeId, "UTF-8")
            val url = OkHttpHelper.toWebSocketUrl(meshClient.serverUrl,
                "/meshrelay.ashx?p=2&nodeid=$encodedNode&id=$tunnelId&rauth=$encodedToken&auth=$encodedToken")
            val request = Request.Builder().url(url).apply {
                if (meshClient.authCookie.isNotBlank()) header("Cookie", meshClient.authCookie)
            }.build()
            webSocket = OkHttpHelper.createClient(meshClient.ignoreSsl).newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(socket: WebSocket, response: Response) {
                    synchronized(this@DesktopSession) {
                        if (!current(socket, generation)) { socket.cancel(); return }
                        if (!meshClient.connectionState.value) { retireSocket(); return }
                        meshClient.requestTunnel(fullNodeId, 2, tunnelId)
                        tunnelJob = scope.launch {
                            delay(1000L)
                            synchronized(this@DesktopSession) {
                                if (current(socket, generation) && !isConnected() && meshClient.connectionState.value) {
                                    meshClient.requestTunnel(fullNodeId, 2, tunnelId)
                                }
                            }
                        }
                    }
                }

                override fun onMessage(socket: WebSocket, text: String) {
                    synchronized(this@DesktopSession) {
                        if (!current(socket, generation)) return
                        if (text == "c" || text == "cr") {
                            if (isConnected()) return
                            tunnelJob?.cancel()
                            tunnelJob = null
                            failures = 0
                            updateState(true)
                            socket.send("2")
                            initializationJob = scope.launch {
                                for (wait in listOf(100L, 200L, 400L, 800L)) {
                                    delay(wait)
                                    synchronized(this@DesktopSession) {
                                        if (!current(socket, generation)) return@launch
                                        refreshDisplay()
                                        if (wait == 400L) requestRemoteClipboard()
                                    }
                                }
                            }
                            startUnpauseLoop()
                        } else if (text.trim().startsWith("{")) {
                            handleJsonControlMessage(text.trim())
                        }
                    }
                }

                override fun onMessage(socket: WebSocket, bytes: ByteString) {
                    synchronized(this@DesktopSession) {
                        if (current(socket, generation)) handleBinaryFrame(bytes.toByteArray())
                    }
                }

                override fun onClosing(socket: WebSocket, code: Int, reason: String) {
                    synchronized(this@DesktopSession) {
                        if (current(socket, generation)) retireSocket()
                    }
                    socket.cancel()
                }

                override fun onClosed(socket: WebSocket, code: Int, reason: String) {
                    synchronized(this@DesktopSession) {
                        if (current(socket, generation)) retireSocket()
                    }
                }

                override fun onFailure(socket: WebSocket, t: Throwable, response: Response?) {
                    synchronized(this@DesktopSession) {
                        if (current(socket, generation)) {
                            Log.w("DesktopSession", "Desktop transport failed for $cleanNodeId (${t.javaClass.simpleName})")
                            retireSocket()
                        }
                    }
                }
            })
        } catch (e: Exception) {
            Log.w("DesktopSession", "Cannot open desktop transport for $cleanNodeId (${e.javaClass.simpleName})")
            retireSocket()
        }
    }

    @Synchronized
    fun refreshOrReconnect() {
        if (isConnected()) refreshDisplay() else {
            isUserDisconnect = false
            forceReconnect()
        }
    }

''' + s[b:]
a = s.index('    private fun closeSocketOnly()')
b = s.index('\n}\n\nclass DesktopRelayClient', a)
s = s[:a] + '''    @Synchronized
    fun disconnect() {
        if (_settings.value.lockOnDisconnect && isConnected()) sendLockScreen()
        isUserDisconnect = true
        visible = false
        recoveryJob?.cancel()
        recoveryJob = null
        retireSocket()
        _desktopBitmap.value = null
        canvasBitmap = null
        desktopCanvas = null
    }
''' + s[b:]
replace_once('''            when {
                sess.isConnected() -> iecxiv.meshfabric.data.model.SessionStatus.CONNECTED
                sess.desktopBitmap.value != null && !sess.isUserDisconnect -> iecxiv.meshfabric.data.model.SessionStatus.DISCONNECTED_WITH_BUFFER
                else -> iecxiv.meshfabric.data.model.SessionStatus.NONE
            }''', '            DesktopRecoveryPolicy.status(!sess.isUserDisconnect)')
replace_once('.filter { it.value.isConnected() }', '.filter { !it.value.isUserDisconnect }')
replace_once('sessions[cleanKey(nodeId)]?.refreshDisplay()', 'sessions[cleanKey(nodeId)]?.refreshOrReconnect()')
replace_once('return sessions.values.any { it.isConnected() }', 'return sessions.values.any { !it.isUserDisconnect }')
replace_once('''        return when {
            session.isConnected() -> iecxiv.meshfabric.data.model.SessionStatus.CONNECTED
            session.desktopBitmap.value != null && !session.isUserDisconnect -> iecxiv.meshfabric.data.model.SessionStatus.DISCONNECTED_WITH_BUFFER
            else -> iecxiv.meshfabric.data.model.SessionStatus.NONE
        }''', '        return DesktopRecoveryPolicy.status(!session.isUserDisconnect)')
replace_once('return sessions.values.any { !it.isConnected() && it.desktopBitmap.value != null && !it.isUserDisconnect }', 'return false // Desktop transport loss does not create a lost buffered session.')
replace_once('return sessions.values.count { (it.isConnected() || it.desktopBitmap.value != null) && !it.isUserDisconnect }', 'return sessions.values.count { !it.isUserDisconnect }')
replace_once('''            if (!session.isUserDisconnect && !session.isConnected()) {
                session.connect()
            }''', '''            if (!session.isUserDisconnect) {
                session.forceReconnect()
            }''')
s = s.replace('        updateActiveNodes()\n    }\n\n    fun disconnect', '        updateSessionsStatus()\n    }\n\n    fun disconnect')
s = s.replace('import kotlin.random.Random\n', '')
p.write_text(s)

p = root / 'ui/desktop/RemoteDesktopScreen.kt'
s = p.read_text()
assert s.count('        viewModel.resumeSession(nodeId)\n') == 1
p.write_text(s.replace('        viewModel.resumeSession(nodeId)\n', '', 1))

(root / 'data/remote/DesktopRecoveryPolicy.kt').write_text('''package iecxiv.meshfabric.data.remote

import iecxiv.meshfabric.data.model.SessionStatus

internal object DesktopRecoveryPolicy {
    fun status(retained: Boolean): SessionStatus =
        if (retained) SessionStatus.CONNECTED else SessionStatus.NONE

    fun retryDelay(failures: Int): Long =
        (1000L shl failures.coerceIn(0, 5)).coerceAtMost(30_000L)
}
''')
tests = Path('app/src/test/java/iecxiv/meshfabric/data/remote/DesktopRecoveryPolicyTest.kt')
tests.parent.mkdir(parents=True, exist_ok=True)
tests.write_text('''package iecxiv.meshfabric.data.remote

import iecxiv.meshfabric.data.model.SessionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DesktopRecoveryPolicyTest {
    @Test fun retainedDesktopStaysHighlighted() {
        assertEquals(SessionStatus.CONNECTED, DesktopRecoveryPolicy.status(true))
    }
    @Test fun explicitCloseRemovesHighlight() {
        assertEquals(SessionStatus.NONE, DesktopRecoveryPolicy.status(false))
    }
    @Test fun retainedDesktopNeverBecomesLostBuffer() {
        assertNotEquals(SessionStatus.DISCONNECTED_WITH_BUFFER, DesktopRecoveryPolicy.status(true))
    }
    @Test fun retryDelayIsBounded() {
        assertEquals(listOf(1000L, 2000L, 4000L, 8000L, 16000L, 30000L, 30000L),
            (0..6).map { DesktopRecoveryPolicy.retryDelay(it) })
        assertEquals(30000L, DesktopRecoveryPolicy.retryDelay(Int.MAX_VALUE))
    }
}
''')

# Carry the retained desktop flag to the notification without hiding lost terminal/file buffers.
p = root / 'MainActivity.kt'
s = p.read_text()
old = 'KeepAliveService.updateNotification(applicationContext, hasActiveConnected, hasDisconnected)'
assert s.count(old) == 1
s = s.replace(old, 'KeepAliveService.updateNotification(applicationContext, hasActiveConnected, hasDisconnected, desktopClient.hasActiveSessions())')
old = 'KeepAliveService.startService(applicationContext, hasActiveConnected, hasDisconnectedSessions)'
assert s.count(old) == 1
p.write_text(s.replace(old, 'KeepAliveService.startService(applicationContext, hasActiveConnected, hasDisconnectedSessions, desktopClient.hasActiveSessions())'))
p = root / 'service/KeepAliveService.kt'
s = p.read_text()
old = '        const val EXTRA_HAS_ACTIVE_CONNECTED = "extra_has_active_connected"'
assert s.count(old) == 1
s = s.replace(old, old + '\n        const val EXTRA_HAS_RECOVERABLE_DESKTOP = "extra_has_recoverable_desktop"')
old = '            hasDisconnectedBuffer: Boolean = false\n'
assert s.count(old) == 2
s = s.replace(old, '            hasDisconnectedBuffer: Boolean = false,\n            hasRecoverableDesktop: Boolean = false\n')
old = '                putExtra(EXTRA_HAS_DISCONNECTED_BUFFER, hasDisconnectedBuffer)'
assert s.count(old) == 1
s = s.replace(old, old + '\n                putExtra(EXTRA_HAS_RECOVERABLE_DESKTOP, hasRecoverableDesktop)')
old = 'startService(context, hasActiveConnected, hasDisconnectedBuffer)'
assert s.count(old) == 1
s = s.replace(old, 'startService(context, hasActiveConnected, hasDisconnectedBuffer, hasRecoverableDesktop)')
old = 'val notification = createNotification(hasActiveConnected, hasDisconnectedBuffer)'
assert s.count(old) == 1
s = s.replace(old, 'val notification = createNotification(hasActiveConnected, hasDisconnectedBuffer, intent?.getBooleanExtra(EXTRA_HAS_RECOVERABLE_DESKTOP, false) ?: false)')
old = '        hasDisconnectedBuffer: Boolean\n    ): Notification'
assert s.count(old) == 1
s = s.replace(old, '        hasDisconnectedBuffer: Boolean,\n        hasRecoverableDesktop: Boolean\n    ): Notification')
old = '        val contentText = try {'
assert s.count(old) == 1
s = s.replace(old, '        val contentText = if (hasRecoverableDesktop && !hasDisconnectedBuffer) getString(R.string.notification_desktop_sessions_connected) else try {')
p.write_text(s)
res = Path('app/src/main/res')
for folder, text in [('values', 'Connected sessions'), ('values-es', 'Sesiones conectadas')]:
    dest = res / folder / 'desktop_notifications.xml'
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_text('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n    <string name="notification_desktop_sessions_connected">' + text + '</string>\n</resources>\n')
for relative in ['data/remote/DesktopRelayClient.kt', 'ui/desktop/RemoteDesktopScreen.kt', 'MainActivity.kt', 'service/KeepAliveService.kt']:
    p = root / relative
    p.write_text('\n'.join(line.rstrip() for line in p.read_text().splitlines()).rstrip() + '\n')

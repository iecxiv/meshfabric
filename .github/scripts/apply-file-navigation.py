from pathlib import Path
import hashlib
import re

root = Path('app/src/main/java/iecxiv/meshfabric')
p = root / 'data/remote/FileRelayClient.kt'
raw = p.read_bytes()
assert hashlib.sha1(b'blob ' + str(len(raw)).encode() + b'\0' + raw).hexdigest() == '3999cbe42d876544ee856e04878990c33a95e191', 'File relay changed; review required'
s = raw.decode()
def once(old, new):
    global s
    assert s.count(old) == 1, old[:120]
    s = s.replace(old, new, 1)

once('    var webSocket: WebSocket? = null\n    var isUserDisconnect = false', '''    @Volatile var webSocket: WebSocket? = null
    @Volatile var isUserDisconnect = true
    private val navigation = FileNavigationState()
    private var socketEpoch = 0L
    private var attemptStarted = 0L
    private var listingStarted = 0L
    private var retryAt = 0L
    private var failures = 0
    private var recoveryJob: Job? = null
    private var initialListingJob: Job? = null''')
a = s.index('    suspend fun ensureControlSocketConnected()')
b = s.index('        val serverUrl = meshClient.serverUrl', a)
s = s[:a] + '''    private fun nowMs() = System.nanoTime() / 1_000_000L

    private fun current(socket: WebSocket, epoch: Long): Boolean =
        !isUserDisconnect && socketEpoch == epoch && webSocket === socket

    suspend fun ensureControlSocketConnected(): Boolean {
        if (isUserDisconnect || meshClient.isUserLogout) return false
        if (meshClient.connectionState.value) return true
        meshClient.reconnectNow()
        repeat(60) {
            if (isUserDisconnect || meshClient.isUserLogout) return false
            if (meshClient.connectionState.value) return true
            delay(100L)
        }
        return meshClient.connectionState.value
    }

    suspend fun ensureConnected(): Boolean {
        if (isUserDisconnect || !ensureControlSocketConnected()) return false
        synchronized(this) { startRecovery() }
        repeat(80) {
            if (isUserDisconnect || meshClient.isUserLogout) return false
            if (isConnected()) return true
            delay(100L)
        }
        return isConnected()
    }

    @Synchronized
    fun connect() {
        isUserDisconnect = false
        startRecovery()
        if (isConnected()) listDirectory(navigation.desiredPath)
    }

    private fun startRecovery() {
        if (isUserDisconnect || recoveryJob?.isActive == true) return
        if (!meshClient.connectionState.value && !meshClient.isUserLogout) meshClient.reconnectNow()
        recoveryJob = scope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
            while (true) {
                synchronized(this@FileSession) {
                    if (meshClient.isUserLogout) {
                        disconnect()
                        return@launch
                    }
                    if (!isUserDisconnect) {
                        if (!meshClient.connectionState.value && webSocket != null) retireSocket()
                        if (webSocket != null && !isConnected() && nowMs() - attemptStarted >= 20_000L) retireSocket()
                        if (isConnected() && navigation.pending != null && nowMs() - listingStarted >= 15_000L) {
                            _statusMessage.value = "No se ha recibido el listado; recuperando la conexión"
                            retireSocket()
                        }
                        if (meshClient.connectionState.value && webSocket == null && nowMs() >= retryAt) {
                            try { openSocket() } catch (e: Exception) {
                                Log.w("MeshFiles", "Cannot open files transport (${e.javaClass.simpleName})")
                                retireSocket()
                            }
                        }
                    }
                }
                delay(500L)
            }
        }
        recoveryJob?.start()
    }

    private fun retireSocket() {
        socketEpoch++
        initialListingJob?.cancel()
        initialListingJob = null
        val old = webSocket
        webSocket = null
        _connectionState.value = false
        navigation.transportReset()
        old?.cancel()
        retryAt = nowMs() + (1000L shl failures.coerceIn(0, 5)).coerceAtMost(30_000L)
        failures = (failures + 1).coerceAtMost(5)
        onStateChanged?.invoke()
    }

    @Synchronized
    fun refreshSession() {
        if (isUserDisconnect) return
        navigation.request(navigation.desiredPath)
        if (!isConnected()) {
            retireSocket()
            retryAt = 0L
            startRecovery()
        } else pumpListing()
    }

    private fun pumpListing() {
        if (!isConnected() || !meshClient.connectionState.value || isUserDisconnect) return
        val request = navigation.next() ?: return
        val req = JsonObject().apply {
            addProperty("action", "ls")
            addProperty("path", request.path)
            addProperty("reqid", request.id)
        }
        listingStarted = nowMs()
        if (webSocket?.send(req.toString()) != true) retireSocket()
    }

    private fun openSocket() {
        if (isUserDisconnect || meshClient.isUserLogout || !meshClient.connectionState.value || webSocket != null) return
        val epoch = ++socketEpoch
        attemptStarted = nowMs()
''' + s[b:]
once('        Log.d("MeshFiles", "Connecting files WS for $cleanNodeId to: $wsUrl")', '        Log.d("MeshFiles", "Opening files transport for $cleanNodeId")')
once('        webSocket?.close(1000, "Reconnecting")\n        webSocket = null\n\n', '')
a = s.index('            override fun onOpen(', s.index('    private fun openSocket()'))
b = s.index('            override fun onMessage(', a)
s = s[:a] + '''            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (!meshClient.connectionState.value) { retireSocket(); return }
                meshClient.requestTunnel(nodeId = fullNodeId, protocol = 5, tunnelId = tunnelId)
            }

''' + s[b:]
a = s.index('            override fun onClosing(', s.index('    private fun openSocket()'))
b = s.index('        })', a)
s = s[:a] + '''            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                retireSocket()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                retireSocket()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w("MeshFiles", "Files transport failed (${t.javaClass.simpleName})")
                retireSocket()
            }
''' + s[b:]
a = s.index('            override fun onOpen(', s.index('    private fun openSocket()'))
b = s.index('        })', a)
listener = s[a:b]
blocks = re.split(r'(?=            override fun )', listener)
wrapped = []
for block in blocks:
    if not block.strip(): continue
    header, body = block.split('\n', 1)
    assert header.endswith('{')
    closing = body.rfind('            }')
    assert closing >= 0 and not body[closing + len('            }'):].strip()
    body = body[:closing]
    wrapped.append(header + '\n                synchronized(this@FileSession) {\n                    if (!current(webSocket, epoch)) { webSocket.cancel(); return }\n' + body + '                }\n            }\n\n')
assert len(wrapped) == 6
s = s[:a] + ''.join(wrapped) + s[b:]
once('''            _connectionState.value = true
            onStateChanged?.invoke()
            webSocket.send("5")

            scope.launch {
                delay(200)
                val targetPath = _currentPath.value.ifEmpty { "/" }
                listDirectory(targetPath)
            }''', '''            if (_connectionState.value) return
            _connectionState.value = true
            failures = 0
            onStateChanged?.invoke()
            webSocket.send("5")
            val epoch = socketEpoch
            initialListingJob = scope.launch {
                delay(200L)
                synchronized(this@FileSession) {
                    if (current(webSocket, epoch)) pumpListing()
                }
            }''')
once('                val path = json.get("path")?.asString ?: "/"', '''                val responsePath = json.get("path")?.asString?.takeIf { it.isNotEmpty() }
                var path = responsePath ?: navigation.pending?.path ?: return''')
once('                _currentPath.value = path.ifEmpty { "/" }', '''                val hasListing = json.getAsJsonArray("dir") != null ||
                    json.getAsJsonArray("files") != null || json.getAsJsonArray("list") != null
                val acceptedPath = navigation.accept(json.get("reqid")?.asString, responsePath, hasListing)
                pumpListing()
                if (acceptedPath == null) {
                    if (!hasListing) _statusMessage.value = "No se pudo listar la ruta solicitada; se conserva la ubicación anterior"
                    return
                }
                path = acceptedPath
                _currentPath.value = path''')
a = s.index('    fun listDirectory(path: String)')
b = s.index('    fun createFolder(', a)
s = s[:a] + '''    @Synchronized
    fun listDirectory(path: String) {
        if (isUserDisconnect) return
        navigation.request(path)
        startRecovery()
        pumpListing()
    }

''' + s[b:]
once('    fun disconnect(resetPath: Boolean = false) {\n        isUserDisconnect = true', '''    @Synchronized
    fun disconnect(resetPath: Boolean = false) {
        isUserDisconnect = true
        recoveryJob?.cancel()
        recoveryJob = null
        retireSocket()''')
once('            _currentPath.value = "/"\n', '            _currentPath.value = "/"\n            navigation.reset()\n')
once('''    fun onNetworkChanged() {
        if (!isUserDisconnect && !isConnected()) {
            connect()
        }
    }''', '''    @Synchronized
    fun onNetworkChanged() {
        if (isUserDisconnect) return
        retireSocket()
        failures = 0
        retryAt = 0L
        startRecovery()
    }''')
once('''            if (!session.isUserDisconnect && !session.isConnected()) {
                session.onNetworkChanged()
            }''', '''            if (!session.isUserDisconnect) session.onNetworkChanged()''')
once('''    fun listDirectory(nodeId: String, path: String) {''', '''    fun refresh(meshClient: MeshCentralClient, nodeId: String) {
        getSession(meshClient, nodeId).refreshSession()
    }

    fun listDirectory(nodeId: String, path: String) {''')
p.write_text('\n'.join(line.rstrip() for line in s.splitlines()).rstrip() + '\n')
p = root / 'ui/files/FileManagerViewModel.kt'
s = p.read_text()
old = '        fileClient.listDirectory(nodeId, currentPath(nodeId).value)'
assert s.count(old) == 1
p.write_text(s.replace(old, '        fileClient.refresh(meshClient, nodeId)'))

(root / 'data/remote/FileNavigationState.kt').write_text('''package iecxiv.meshfabric.data.remote

internal class FileNavigationState {
    data class Request(val id: Long, val path: String)
    var desiredPath: String = "/"
        private set
    var confirmedPath: String = "/"
        private set
    var pending: Request? = null
        private set
    private var sequence = 0L
    private var dirty = true

    private fun key(path: String) = path.replace('\\\\', '/').trimEnd('/').ifEmpty { "/" }

    fun request(path: String) {
        val target = path.ifEmpty { "/" }
        if (key(target) != key(desiredPath) || pending == null) dirty = true
        desiredPath = target
    }

    fun next(): Request? {
        if (pending != null || !dirty) return null
        dirty = false
        return Request(++sequence, desiredPath).also { pending = it }
    }

    fun accept(id: String?, path: String?, success: Boolean = true): String? {
        val sent = pending ?: return null
        if (id != null && id != sent.id.toString()) return null
        if (path != null && key(path) != key(sent.path)) return null
        pending = null
        if (key(desiredPath) != key(sent.path) || !success) return null
        val accepted = path ?: sent.path
        confirmedPath = accepted
        desiredPath = accepted
        return accepted
    }

    fun transportReset() {
        pending = null
        dirty = true
    }

    fun reset() {
        desiredPath = "/"
        confirmedPath = "/"
        transportReset()
    }
}
''')
tests = Path('app/src/test/java/iecxiv/meshfabric/data/remote/FileNavigationStateTest.kt')
tests.parent.mkdir(parents=True, exist_ok=True)
tests.write_text('''package iecxiv.meshfabric.data.remote

import org.junit.Assert.*
import org.junit.Test

class FileNavigationStateTest {
    @Test fun restoresDesiredPathAfterTransportReset() {
        val n = FileNavigationState()
        n.request("/home/data")
        n.next()
        n.transportReset()
        assertEquals("/home/data", n.next()!!.path)
    }
    @Test fun onlyOneRequestCanBeOutstanding() {
        val n = FileNavigationState()
        n.next()
        assertNull(n.next())
    }
    @Test fun olderNavigationCannotReplaceLatestChoice() {
        val n = FileNavigationState()
        n.request("/a")
        val a = n.next()!!
        n.request("/b")
        assertNull(n.accept(a.id.toString(), "/a"))
        assertEquals("/b", n.next()!!.path)
        assertEquals("/", n.confirmedPath)
    }
    @Test fun oldRequestIdIsIgnoredAfterReconnect() {
        val n = FileNavigationState()
        val old = n.next()!!
        n.transportReset()
        val fresh = n.next()!!
        assertNull(n.accept(old.id.toString(), "/"))
        assertEquals(fresh, n.pending)
    }
    @Test fun missingPathUsesOutstandingRequestNotRoot() {
        val n = FileNavigationState()
        n.request("/data")
        n.next()
        assertEquals("/data", n.accept(null, null))
    }
    @Test fun mismatchedPathCannotChangeLocation() {
        val n = FileNavigationState()
        n.request("/data")
        n.next()
        assertNull(n.accept(null, "/other"))
        assertEquals("/", n.confirmedPath)
    }
    @Test fun failedListingKeepsPreviousConfirmedPath() {
        val n = FileNavigationState()
        n.request("/data")
        n.next()
        n.accept(null, "/data")
        n.request("/missing")
        n.next()
        assertNull(n.accept(null, "/missing", false))
        assertEquals("/data", n.confirmedPath)
        assertEquals("/missing", n.desiredPath)
    }
    @Test fun explicitResetReturnsToRoot() {
        val n = FileNavigationState()
        n.request("/data")
        n.reset()
        assertEquals("/", n.next()!!.path)
    }
}
''')

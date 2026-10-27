from pathlib import Path
import hashlib
root = Path('app/src/main/java/iecxiv/meshfabric/data/remote')
def checked(name, expected):
    p = root / name
    data = p.read_bytes()
    assert hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest() == expected, 'Review changed source: ' + name
    return p, data.decode()
p, s = checked('FileRelayClient.kt', '9d2e336260542602785bc0edbbc759d622552cd3')
def once(old, new):
    global s
    assert s.count(old) == 1, old[:120]
    s = s.replace(old, new, 1)
once('import kotlinx.coroutines.Job', 'import kotlinx.coroutines.Job\nimport kotlinx.coroutines.ensureActive')
a = s.index('class ActiveUploadSession(')
b = s.index('class ActiveCopyMoveSession(', a)
s = s[:a] + '''class ActiveUploadSession(
    val filePath: String,
    val fileName: String,
    var sentBytes: Long,
    var totalSize: Long,
    var uploadJob: Job?,
    val context: Context?
) {
    internal val networkAttempt = DownloadNetworkAttempt()
    @Volatile internal var started = false
    @Volatile internal var completed = false
}

''' + s[b:]
once('    val activeUploads = ConcurrentHashMap<String, ActiveUploadSession>()', '    val activeUploads = ConcurrentHashMap<String, ActiveUploadSession>()\n    private val uploadMutex = kotlinx.coroutines.sync.Mutex()')
a = s.index('    fun uploadRawFileToPath(')
b = s.index('    private fun getFileNameFromUri(', a)
worker = r'''    fun uploadRawFileToPath(fullPath: String, bytes: ByteArray, context: Context? = null) {
        val snapshot = bytes.copyOf()
        runRecoverableUpload(fullPath, context) { destination -> destination.writeBytes(snapshot) }
    }

    fun uploadUri(uri: Uri, context: Context, customFileName: String? = null) {
        val name = customFileName?.trim()?.ifBlank { null } ?: getFileNameFromUri(context, uri)
        uploadUriToPath(UploadWire.join(UploadWire.key(_currentPath.value), name), uri, context)
    }

    fun uploadUriToPath(fullPath: String, uri: Uri, context: Context) {
        val appContext = context.applicationContext
        runRecoverableUpload(fullPath, appContext) { destination ->
            val input = appContext.contentResolver.openInputStream(uri)
                ?: throw java.io.IOException("No se puede abrir el origen de la subida")
            input.use { source ->
                destination.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        val count = source.read(buffer)
                        if (count < 0) break
                        if (count > 0) output.write(buffer, 0, count)
                    }
                }
            }
        }
    }

    private fun runRecoverableUpload(fullPath: String, context: Context?, prepare: suspend (File) -> Unit) {
        if (isUserDisconnect) { _statusMessage.value = "Abre la sesión de Archivos antes de subir"; return }
        val target = UploadWire.key(fullPath)
        val name = UploadWire.name(target)
        val parent = try {
            validateRemoteEntryName(name)
            remoteParent(target)
        } catch (e: IllegalArgumentException) { _statusMessage.value = e.message; return }
        val ctx = (context ?: iecxiv.meshfabric.MeshFabricApp.instance).applicationContext
        val session = ActiveUploadSession(target, name, 0L, 0L, null, ctx)
        if (activeUploads.putIfAbsent(target, session) != null) {
            _statusMessage.value = "Ya hay una subida pendiente para ese destino"
            return
        }
        _activeTransfers.update { entries ->
            entries.filterNot { it.isUpload && it.filePath == target } +
                TransferProgress(fileName = name, filePath = target, isUpload = true)
        }
        session.uploadJob = scope.launch(Dispatchers.IO, start = kotlinx.coroutines.CoroutineStart.LAZY) {
            var source: File? = null
            var locked = false
            var remoteTouched = false
            val publication = UploadPublication(parent, name)
            try {
                val directory = File(ctx.filesDir, "meshfabric-upload-sources")
                check(directory.isDirectory || directory.mkdirs()) { "No se puede preparar el origen local" }
                val staged = File.createTempFile("upload-", ".part", directory)
                source = staged
                prepare(staged)
                coroutineContext.ensureActive()
                session.totalSize = staged.length()
                _activeTransfers.update { entries -> entries.map {
                    if (it.isUpload && it.filePath == target) it.copy(totalBytes = session.totalSize) else it
                } }
                uploadMutex.lock()
                locked = true
                while (coroutineContext.isActive && !isUserDisconnect && activeUploads[target] === session) {
                    if (!ensureConnected()) { delay(750L); continue }
                    val epoch = session.networkAttempt.current()
                    try {
                        session.started = true
                        session.sentBytes = 0L
                        _activeTransfers.update { entries -> entries.map {
                            if (it.isUpload && it.filePath == target) it.copy(bytesTransferred = 0L,
                                totalBytes = session.totalSize, isCompleted = false) else it
                        } }
                        val verified = UploadChannel.connect(meshClient, fullNodeId, session.networkAttempt, epoch).use { relay ->
                            remoteTouched = true
                            publication.transfer(relay, staged) { confirmed ->
                                if (!session.networkAttempt.isCurrent(epoch) || activeUploads[target] !== session) {
                                    throw java.io.IOException("Intento de subida retirado")
                                }
                                session.sentBytes = confirmed
                                _activeTransfers.update { entries -> entries.map {
                                    if (it.isUpload && it.filePath == target) it.copy(bytesTransferred = confirmed,
                                        totalBytes = session.totalSize) else it
                                } }
                                updateUploadNotification(ctx, name, target, confirmed, session.totalSize)
                            }
                            relay.list(parent).firstOrNull { it.name == name && !it.directory && it.size == staged.length() }
                                ?: throw java.io.IOException("El tamaño remoto no está confirmado")
                        }
                        val accepted = synchronized(this@FileSession) {
                            if (isUserDisconnect || activeUploads[target] !== session || !session.networkAttempt.isCurrent(epoch)) false
                            else {
                                session.completed = true
                                session.sentBytes = verified.size
                                savedFileContents.remove(target)
                                if (_fileContent.value?.first == target) _fileContent.value = null
                                if (UploadWire.key(_currentPath.value) == UploadWire.key(parent)) {
                                    _fileList.update { entries ->
                                        val item = FileItem(name, false, verified.size, verified.modified, target)
                                        (entries.filterNot { UploadWire.key(it.path) == target } + item)
                                            .sortedWith(compareByDescending<FileItem> { it.isDir }.thenBy { it.name.lowercase() })
                                    }
                                }
                                navigation.refresh()
                                pumpListing()
                                true
                            }
                        }
                        if (!accepted) throw java.io.IOException("La sesión cambió antes de confirmar la subida")
                        _activeTransfers.update { entries -> entries.map {
                            if (it.isUpload && it.filePath == target) it.copy(bytesTransferred = verified.size,
                                totalBytes = verified.size, isCompleted = true) else it
                        } }
                        showUploadCompleteNotification(ctx, name, target, verified.size)
                        _statusMessage.value = "Subida completada: $name"
                        Log.d("MeshFilesUpload", "stage=VERIFIED_COMPLETE bytes=${verified.size}")
                        delay(1200L)
                        break
                    } catch (e: CancellationException) { throw e }
                    catch (e: UploadRejected) { _statusMessage.value = "El agente ha rechazado la subida"; break }
                    catch (e: UploadConflict) { _statusMessage.value = e.message; break }
                    catch (e: java.io.IOException) {
                        if (activeUploads[target] !== session || isUserDisconnect) break
                        Log.w("MeshFilesUpload", "stage=RESTART_FROM_ZERO type=${e.javaClass.simpleName}")
                        showUploadReconnectToast(ctx, "retry")
                        delay(2000L)
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                Log.e("MeshFilesUpload", "stage=SOURCE_OR_WORKER_FAILED type=${e.javaClass.simpleName}")
                _statusMessage.value = "No se pudo completar la subida: ${e.javaClass.simpleName}"
            } finally {
                session.networkAttempt.invalidate()
                try {
                    if (locked && remoteTouched && !session.completed) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) {
                            try {
                                val cleaned = kotlinx.coroutines.withTimeoutOrNull(8000L) {
                                    if (!meshClient.connectionState.value || meshClient.isUserLogout) return@withTimeoutOrNull false
                                    val cleanupGate = DownloadNetworkAttempt()
                                    UploadChannel.connect(meshClient, fullNodeId, cleanupGate, cleanupGate.current()).use {
                                        publication.cancelCleanup(it)
                                    }
                                    true
                                }
                                if (cleaned != true) Log.w("MeshFilesUpload", "stage=OWNED_REMOTE_CLEANUP_PENDING")
                            } catch (e: Exception) {
                                Log.w("MeshFilesUpload", "stage=OWNED_REMOTE_CLEANUP_PENDING type=${e.javaClass.simpleName}")
                            }
                        }
                    }
                } finally {
                    if (locked) uploadMutex.unlock()
                    source?.delete()
                    if (activeUploads.remove(target, session)) {
                        _activeTransfers.update { entries -> entries.filterNot { it.isUpload && it.filePath == target } }
                    }
                    if (!session.completed && activeUploads[target] == null) {
                        (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)?.cancel(target.hashCode())
                    }
                }
            }
        }
        session.uploadJob?.start()
    }

    @Synchronized
    private fun showUploadReconnectToast(context: Context, source: String) {
        val pending = activeUploads.values.toList().filter {
            it.started && !it.completed && it.uploadJob?.isActive != false
        }.map { it to it.networkAttempt.current() }
        val ctx = context.applicationContext
        scope.launch(Dispatchers.Main.immediate) {
            if (isUserDisconnect) return@launch
            val claimed = pending.filter { (session, epoch) ->
                activeUploads[session.filePath] === session && !session.completed &&
                    session.uploadJob?.isActive != false && session.networkAttempt.takeReconnectNotice(epoch)
            }
            if (claimed.isEmpty()) return@launch
            try {
                Toast.makeText(ctx, ctx.getString(R.string.unstable_network_reconnecting_upload), Toast.LENGTH_SHORT).show()
                Log.d("MeshUploadToast", "SHOW_REQUESTED source=$source count=${claimed.size}")
            } catch (e: Exception) {
                claimed.forEach { (session, epoch) -> session.networkAttempt.releaseReconnectNotice(epoch) }
                Log.w("MeshUploadToast", "SHOW_FAILED source=$source type=${e.javaClass.simpleName}")
            }
        }
    }

'''
s = s[:a] + worker + s[b:]
a = s.index('    fun cancelUpload(')
b = s.index('    private fun ensureNotificationChannel(', a)
s = s[:a] + '''    fun cancelUpload(targetFilePath: String? = null) {
        val key = targetFilePath ?: activeUploads.keys.firstOrNull() ?: return
        val session = activeUploads[key] ?: return
        if (session.completed || !activeUploads.remove(key, session)) return
        session.networkAttempt.invalidate()
        session.uploadJob?.cancel()
        _activeTransfers.update { entries -> entries.filterNot { it.isUpload && it.filePath == key } }
        val ctx = session.context
        (ctx?.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)?.cancel(key.hashCode())
        _statusMessage.value = "Subida cancelada: ${session.fileName}"
    }

''' + s[b:]
once('        activeDownloads.keys.toList().forEach { cancelDownload(it) }', '        activeDownloads.keys.toList().forEach { cancelDownload(it) }\n        activeUploads.keys.toList().forEach { cancelUpload(it) }')
once('        activeDownloads.values.toList().forEach { it.networkAttempt.invalidate() }', '''        activeDownloads.values.toList().forEach { it.networkAttempt.invalidate() }
        activeUploads.values.toList().filter { it.started && !it.completed }.forEach { it.networkAttempt.invalidate() }
        activeUploads.values.firstOrNull()?.context?.let { showUploadReconnectToast(it, "network-change") }''')
a = s.index('    fun saveFileContent(filePath: String, newContent: String)')
b = s.index('    fun cancelFileReading(', a)
s = s[:a] + '''    fun saveFileContent(filePath: String, newContent: String) {
        uploadRawFileToPath(filePath, newContent.toByteArray(Charsets.UTF_8))
        _statusMessage.value = "Guardando archivo..."
    }

''' + s[b:]
p.write_text('\n'.join(line.rstrip() for line in s.splitlines()).rstrip() + '\n')
p, n = checked('FileNavigationState.kt', 'bc0cd56508ad28d1d75c6fc144053b31368b3e2e')
needle = '    fun next(): Request? {'
assert n.count(needle) == 1
n = n.replace(needle, '    fun refresh() { dirty = true }\n\n' + needle, 1)
needle = '        if (key(desiredPath) != key(sent.path) || !success) return null'
assert n.count(needle) == 1
n = n.replace(needle, '        if (dirty || key(desiredPath) != key(sent.path) || !success) return null', 1)
p.write_text(n)
p, u = checked('RecoverableUpload.kt', 'fe560c82f9793a3f1f7f9baa2d04fb2611328627')
needle = '    fun parent(path: String): String = key(path).substringBeforeLast(\'/\', "").ifEmpty { "/" }'
assert u.count(needle) == 1
u = u.replace(needle, '''    fun parent(path: String): String {
        val parent = key(path).substringBeforeLast('/', "").ifEmpty { "/" }
        return if (Regex("^[A-Za-z]:$").matches(parent)) "$parent/" else parent
    }''', 1)
p.write_text(u)
test = Path('app/src/test/java/iecxiv/meshfabric/data/remote/UploadIntegrationTest.kt')
test.parent.mkdir(parents=True, exist_ok=True)
tests = r'''package iecxiv.meshfabric.data.remote

import org.junit.Assert.*
import org.junit.Test

class UploadIntegrationTest {
    @Test fun escapesLeadingZeroWithoutDroppingPayload() {
        assertArrayEquals(byteArrayOf(0, 0, 7), UploadWire.encode(byteArrayOf(0, 7)))
    }
    @Test fun escapesJsonMarker() {
        assertArrayEquals(byteArrayOf(0, 123, 125), UploadWire.encode(byteArrayOf(123, 125)))
    }
    @Test fun ordinaryBinaryAndEmptyDataRemainUnchanged() {
        assertArrayEquals(byteArrayOf(1, 2, 3), UploadWire.encode(byteArrayOf(1, 2, 3)))
        assertArrayEquals(byteArrayOf(), UploadWire.encode(byteArrayOf()))
    }
    @Test fun confirmedSizeComparisonRejectsIncompleteRemoteFile() {
        val local = RemoteUploadEntry("staged", 100, 10, false)
        assertFalse(UploadWire.same(local, RemoteUploadEntry("target", 0, 10, false)))
        assertFalse(UploadWire.same(local, RemoteUploadEntry("target", 99, 10, false)))
        assertTrue(UploadWire.same(local, RemoteUploadEntry("target", 100, 10, false)))
    }
    @Test fun emptyFileIsLegitimatelyZeroBytes() {
        assertTrue(UploadWire.same(RemoteUploadEntry("part", 0, 10, false), RemoteUploadEntry("final", 0, 10, false)))
    }
    @Test fun changedMetadataOrDirectoryCannotBePublishedAsVerifiedFile() {
        val part = RemoteUploadEntry("part", 100, 10, false)
        assertFalse(UploadWire.same(part, RemoteUploadEntry("final", 100, 11, false)))
        assertFalse(UploadWire.same(part, RemoteUploadEntry("final", 100, 10, true)))
        assertFalse(UploadWire.same(part, null))
    }
    @Test fun pathHandlingPreservesWindowsDriveRoot() {
        assertEquals("C:/", UploadWire.parent("C:\\file.bin"))
        assertEquals("file.bin", UploadWire.name("C:\\file.bin"))
        assertEquals("/", UploadWire.parent("/file.bin"))
        assertEquals("/data/file.bin", UploadWire.join("/data", "file.bin"))
    }
    @Test fun refreshAfterUploadRejectsOldZeroByteListing() {
        val nav = FileNavigationState()
        nav.request("/data")
        val stale = nav.next()!!
        nav.refresh()
        assertNull(nav.accept(stale.id.toString(), "/data"))
        val fresh = nav.next()!!
        assertEquals("/data", nav.accept(fresh.id.toString(), "/data"))
    }
    @Test fun refreshPreservesDirectorySelectedDuringUpload() {
        val nav = FileNavigationState()
        nav.request("/other")
        nav.refresh()
        assertEquals("/other", nav.next()!!.path)
    }
    @Test fun retiredUploadAttemptCannotReuseOldSocketOrNotice() {
        val gate = DownloadNetworkAttempt()
        val old = gate.current()
        gate.invalidate()
        var cancelled = false
        assertFalse(gate.bind(old, Any()) { cancelled = true })
        assertTrue(cancelled)
        assertFalse(gate.takeReconnectNotice(old))
        assertTrue(gate.takeReconnectNotice())
    }
}
'''
test.write_text(tests)

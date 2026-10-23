package iecxiv.meshfabric.data.remote

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.google.gson.JsonObject
import iecxiv.meshfabric.data.model.FileItem
import iecxiv.meshfabric.data.model.SessionStatus
import iecxiv.meshfabric.data.model.TransferProgress
import iecxiv.meshfabric.data.model.TransferType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.log10
import kotlin.math.pow
import kotlin.random.Random

class ActiveDownloadSession(
    val filePath: String,
    val fileName: String,
    val reqId: String,
    var totalSize: Long,
    var receivedBytes: Long,
    val outputStream: OutputStream,
    val context: Context
)

class ActiveUploadSession(
    val filePath: String,
    val fileName: String,
    var sentBytes: Long,
    val totalSize: Long,
    var uploadJob: Job?,
    val context: Context?
)

class ActiveCopyMoveSession(
    val filePath: String,
    val fileName: String,
    val isMove: Boolean,
    var transferredBytes: Long,
    val totalSize: Long,
    var job: Job?,
    val context: Context?
)

class FileSession(
    val nodeId: String,
    val meshClient: MeshCentralClient,
    private val scope: CoroutineScope
) {
    val cleanNodeId = if (nodeId.startsWith("node//")) {
        nodeId.substring(6)
    } else if (nodeId.startsWith("node/")) {
        nodeId.substring(5)
    } else {
        nodeId
    }.trimStart('/').trim()

    val fullNodeId = "node//$cleanNodeId"

    private val gson = Gson()
    var webSocket: WebSocket? = null
    var isUserDisconnect = false
    var onStateChanged: (() -> Unit)? = null

    private val _connectionState = MutableStateFlow(false)
    val connectionState: StateFlow<Boolean> = _connectionState.asStateFlow()

    private val _currentPath = MutableStateFlow("/")
    val currentPath: StateFlow<String> = _currentPath.asStateFlow()

    private val _fileList = MutableStateFlow<List<FileItem>>(emptyList())
    val fileList: StateFlow<List<FileItem>> = _fileList.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _clipboardItem = MutableStateFlow<FileItem?>(null)
    val clipboardItem: StateFlow<FileItem?> = _clipboardItem.asStateFlow()

    private val _isCutMode = MutableStateFlow(false)
    val isCutMode: StateFlow<Boolean> = _isCutMode.asStateFlow()

    private var clipboardNodeId: String? = null

    private val _fileContent = MutableStateFlow<Pair<String, String>?>(null)
    val fileContent: StateFlow<Pair<String, String>?> = _fileContent.asStateFlow()

    private val _fileReadingProgress = MutableStateFlow<Float?>(null)
    val fileReadingProgress: StateFlow<Float?> = _fileReadingProgress.asStateFlow()

    val activeDownloads = ConcurrentHashMap<String, ActiveDownloadSession>()
    val activeUploads = ConcurrentHashMap<String, ActiveUploadSession>()
    val activeCopyMoveSessions = ConcurrentHashMap<String, ActiveCopyMoveSession>()
    val savedFileContents = ConcurrentHashMap<String, String>()
    private val _activeTransfers = MutableStateFlow<List<TransferProgress>>(emptyList())
    val activeTransfers: StateFlow<List<TransferProgress>> = _activeTransfers.asStateFlow()

    val activeTransfer: StateFlow<TransferProgress?> = _activeTransfers
        .map { it.lastOrNull() }
        .stateIn(scope, SharingStarted.Eagerly, null)

    // Internal Copy-Paste Streaming State
    private var copyingDirectorySource: String? = null
    private var recursiveCopyTargetDir: String? = null

    private var copyingSourcePath: String? = null
    private var copyingDestPath: String? = null
    private var copyingTotalSize: Long = 0L
    private var copyingReceivedBytes: Long = 0L
    private var copyingTempFile: File? = null
    private var copyingFileOutputStream: FileOutputStream? = null

    // Internal File Reading State
    private var readingFilePath: String? = null
    private var readingTotalSize: Long = 0L
    private var readingReceivedBytes: Long = 0L
    private var readingBuffer: ByteArrayOutputStream? = null

    // Download State
    private var lastProgressNotificationTime = 0L

    fun isConnected(): Boolean {
        return _connectionState.value && webSocket != null
    }

    private fun isProtocolJsonMessage(text: String): Boolean {
        val trimmed = text.trim()
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return false
        return try {
            val json = gson.fromJson(trimmed, JsonObject::class.java) ?: return false
            val action = json.get("action")?.asString ?: ""
            if (action in setOf("downloadStart", "downloadEnd", "downloadError")) {
                return true
            }
            action in setOf("ls", "dir", "download", "read", "readfile", "msg", "upload", "mkdir", "rm", "deletefile", "deletefolder", "rmdir", "rmfile", "rename", "zip", "unzip") ||
                    (json.has("path") && (json.has("dir") || json.has("files") || json.has("list")))
        } catch (e: Exception) {
            false
        }
    }

    suspend fun ensureControlSocketConnected(): Boolean {
        if (meshClient.connectionState.value) return true
        Log.d("MeshFiles", "Control socket not connected, triggering immediate reconnect...")
        meshClient.reconnectNow()
        var waitMs = 0
        while (!meshClient.connectionState.value && waitMs < 6000) {
            delay(100)
            waitMs += 100
        }
        return meshClient.connectionState.value
    }

    suspend fun ensureConnected(): Boolean {
        ensureControlSocketConnected()
        if (_connectionState.value && webSocket != null) return true

        if (webSocket == null || isUserDisconnect) {
            Log.d("MeshFiles", "Auto-reconnecting file relay for node '$cleanNodeId'...")
            connect()
        }

        var waitMs = 0
        while (!_connectionState.value && waitMs < 6000) {
            delay(100)
            waitMs += 100
        }
        return _connectionState.value
    }

    fun connect() {
        isUserDisconnect = false

        if (isConnected()) {
            Log.d("MeshFiles", "Already connected to node: $cleanNodeId. Reusing files session at path: ${_currentPath.value}")
            listDirectory(_currentPath.value)
            return
        }

        val serverUrl = meshClient.serverUrl
        val cookie = meshClient.authCookie
        val relayToken = meshClient.relayAuthCookie.ifEmpty {
            meshClient.userAuthCookie.ifEmpty {
                meshClient.extractRawToken(cookie)
            }
        }
        val encodedRelayToken = try { URLEncoder.encode(relayToken, "UTF-8") } catch (e: Exception) { relayToken }
        val ignoreSsl = meshClient.ignoreSsl

        val client = OkHttpHelper.createClient(ignoreSsl)
        val tunnelId = Random.nextInt(100000, 999999).toString()
        val encodedNodeId = try { URLEncoder.encode(fullNodeId, "UTF-8") } catch (e: Exception) { fullNodeId }

        val wsUrl = OkHttpHelper.toWebSocketUrl(
            serverUrl,
            "/meshrelay.ashx?p=5&nodeid=$encodedNodeId&id=$tunnelId&rauth=$encodedRelayToken&auth=$encodedRelayToken"
        )

        Log.d("MeshFiles", "Connecting files WS for $cleanNodeId to: $wsUrl")

        val requestBuilder = Request.Builder().url(wsUrl)
        if (cookie.isNotBlank()) {
            requestBuilder.addHeader("Cookie", cookie)
        }

        webSocket?.close(1000, "Reconnecting")
        webSocket = null

        webSocket = client.newWebSocket(requestBuilder.build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("MeshFiles", "WebSocket onOpen for $cleanNodeId. Ensuring control socket is ready...")
                scope.launch(Dispatchers.IO) {
                    ensureControlSocketConnected()
                    Log.d("MeshFiles", "Control socket ready. Requesting tunnel ID $tunnelId...")
                    meshClient.requestTunnel(nodeId = fullNodeId, protocol = 5, tunnelId = tunnelId)
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d("MeshFilesRecvRawText", "WS Text: $text")
                if (text == "c" || text == "cr") {
                    handleIncomingMessage(webSocket, text)
                    return
                }
                if (isProtocolJsonMessage(text)) {
                    handleIncomingMessage(webSocket, text)
                    processJsonControlMessage(text)
                } else {
                    val activeDl = activeDownloads.values.firstOrNull()
                    if (activeDl != null) {
                        val chunkBytes = text.toByteArray(Charsets.UTF_8)
                        activeDl.outputStream.write(chunkBytes)
                        activeDl.receivedBytes += chunkBytes.size
                        _activeTransfers.value = _activeTransfers.value.map {
                            if (it.filePath == activeDl.filePath) {
                                it.copy(
                                    bytesTransferred = activeDl.receivedBytes,
                                    totalBytes = if (activeDl.totalSize > 0) activeDl.totalSize else activeDl.receivedBytes
                                )
                            } else {
                                it
                            }
                        }
                        if (activeDl.totalSize > 0 && activeDl.receivedBytes >= activeDl.totalSize) {
                            finishDownload(activeDl.filePath)
                        } else if (activeDl.context != null) {
                            updateDownloadNotification(
                                activeDl.context,
                                activeDl.fileName,
                                activeDl.filePath,
                                activeDl.receivedBytes,
                                activeDl.totalSize
                            )
                        }
                    } else if (readingFilePath != null) {
                        val chunkBytes = text.toByteArray(Charsets.UTF_8)
                        readingBuffer?.write(chunkBytes)
                        readingReceivedBytes += chunkBytes.size
                        val progress = if (readingTotalSize > 0) {
                            (readingReceivedBytes.toFloat() / readingTotalSize.toFloat()).coerceIn(0.05f, 1.0f)
                        } else {
                            0.5f
                        }
                        _fileReadingProgress.value = progress
                        if (readingTotalSize > 0 && readingReceivedBytes >= readingTotalSize) {
                            finishReadingFile()
                        }
                    } else {
                        handleIncomingMessage(webSocket, text)
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val textCandidate = try { bytes.utf8().trim() } catch (_: Exception) { "" }
                val isJson = isProtocolJsonMessage(textCandidate)

                if (isJson || textCandidate == "c") {
                    Log.d("MeshFilesRecvJsonBytes", "WS JSON/Control Bytes: $textCandidate")
                    handleIncomingMessage(webSocket, textCandidate)
                    processJsonControlMessage(textCandidate)
                    return
                }

                Log.d("MeshFilesRecvDataBytes", "WS Data Bytes: ${bytes.size} bytes")
                val byteArray = bytes.toByteArray()

                val activeDl = activeDownloads.values.firstOrNull()
                if (activeDl != null) {
                    try {
                        activeDl.outputStream.write(byteArray)
                        activeDl.receivedBytes += byteArray.size

                        _activeTransfers.value = _activeTransfers.value.map {
                            if (it.filePath == activeDl.filePath) {
                                it.copy(
                                    bytesTransferred = activeDl.receivedBytes,
                                    totalBytes = if (activeDl.totalSize > 0) activeDl.totalSize else activeDl.receivedBytes
                                )
                            } else {
                                it
                            }
                        }

                        if (activeDl.context != null) {
                            updateDownloadNotification(
                                activeDl.context,
                                activeDl.fileName,
                                activeDl.filePath,
                                activeDl.receivedBytes,
                                activeDl.totalSize
                            )
                        }

                        if (activeDl.totalSize > 0 && activeDl.receivedBytes >= activeDl.totalSize) {
                            finishDownload(activeDl.filePath)
                        }
                        return
                    } catch (e: Exception) {
                        Log.e("MeshFilesDownload", "Error writing download chunk: ${e.localizedMessage}", e)
                    }
                } else if (copyingSourcePath != null) {
                    try {
                        copyingFileOutputStream?.write(byteArray)
                        copyingReceivedBytes += byteArray.size
                        val progress = if (copyingTotalSize > 0) {
                            (copyingReceivedBytes * copyingTotalSize) / (copyingTotalSize * 2)
                        } else {
                            copyingReceivedBytes / 2
                        }
                        val destPath = copyingDestPath
                        _activeTransfers.value = _activeTransfers.value.map {
                            if (it.filePath == destPath) it.copy(bytesTransferred = progress) else it
                        }
                        if (copyingTotalSize > 0 && copyingReceivedBytes >= copyingTotalSize) {
                            finishCopyingFile()
                        }
                        return
                    } catch (e: Exception) {
                        Log.e("MeshFilesCopy", "Error buffering copy chunk: ${e.localizedMessage}", e)
                    }
                } else if (readingFilePath != null) {
                    try {
                        readingBuffer?.write(byteArray)
                        readingReceivedBytes += byteArray.size
                        val progress = if (readingTotalSize > 0) {
                            (readingReceivedBytes.toFloat() / readingTotalSize.toFloat()).coerceIn(0.05f, 1.0f)
                        } else {
                            0.5f
                        }
                        _fileReadingProgress.value = progress

                        if (readingTotalSize > 0 && readingReceivedBytes >= readingTotalSize) {
                            finishReadingFile()
                        }
                        return
                    } catch (e: Exception) {
                        Log.e("MeshFilesRead", "Error buffering read chunk: ${e.localizedMessage}", e)
                    }
                }

                handleIncomingMessage(webSocket, textCandidate)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                _connectionState.value = false
                onStateChanged?.invoke()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _connectionState.value = false
                onStateChanged?.invoke()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("MeshFiles", "onFailure for $cleanNodeId: ${t.localizedMessage}", t)
                _connectionState.value = false
                onStateChanged?.invoke()
            }
        })
    }

    private fun handleIncomingMessage(webSocket: WebSocket, text: String) {
        Log.d("MeshFilesRecv", "Files WS Recv (${text.length} chars): $text")
        if (text == "c" || text == "cr") {
            _connectionState.value = true
            onStateChanged?.invoke()
            webSocket.send("5")

            scope.launch {
                delay(200)
                val targetPath = _currentPath.value.ifEmpty { "/" }
                listDirectory(targetPath)
            }
        } else {
            handleJsonMessage(text)
        }
    }

    private fun processJsonControlMessage(text: String) {
        if (!isProtocolJsonMessage(text)) return
        try {
            val json = gson.fromJson(text, JsonObject::class.java) ?: return
            val action = json.get("action")?.asString ?: ""
            val subaction = json.get("subaction")?.asString ?: ""

            if (action == "downloadEnd" || subaction == "end" || subaction == "close" || subaction == "complete") {
                if (activeDownloads.isNotEmpty()) finishDownload()
                if (copyingSourcePath != null) finishCopyingFile()
                if (readingFilePath != null) finishReadingFile()
                return
            }

            if (action == "downloadStart" || subaction == "start") {
                val reqid = json.get("reqid")?.asString ?: json.get("id")?.asString ?: ""
                val size = json.get("size")?.asLong ?: 0L
                val activeDl = activeDownloads.values.firstOrNull()
                if (activeDl != null && size > 0) {
                    activeDl.totalSize = size
                }
                val pullReq = JsonObject().apply {
                    addProperty("action", "download")
                    addProperty("subaction", "download")
                    if (reqid.isNotEmpty()) {
                        addProperty("reqid", reqid)
                        addProperty("id", reqid)
                    }
                }
                webSocket?.send(pullReq.toString())
                return
            }

            val data = json.get("data")?.asString
                ?: json.get("content")?.asString
                ?: json.get("text")?.asString

            if (data != null) {
                val chunkBytes = try {
                    android.util.Base64.decode(data, android.util.Base64.DEFAULT)
                } catch (_: Exception) {
                    data.toByteArray(Charsets.UTF_8)
                }

                val activeDl = activeDownloads.values.firstOrNull()
                if (activeDl != null) {
                    activeDl.outputStream.write(chunkBytes)
                    activeDl.receivedBytes += chunkBytes.size
                    _activeTransfers.value = _activeTransfers.value.map {
                        if (it.filePath == activeDl.filePath) {
                            it.copy(
                                bytesTransferred = activeDl.receivedBytes,
                                totalBytes = if (activeDl.totalSize > 0) activeDl.totalSize else activeDl.receivedBytes
                            )
                        } else {
                            it
                        }
                    }
                    if (activeDl.totalSize > 0 && activeDl.receivedBytes >= activeDl.totalSize) {
                        finishDownload(activeDl.filePath)
                    } else if (action == "readfile") {
                        finishDownload(activeDl.filePath)
                    } else if (activeDl.context != null) {
                        updateDownloadNotification(
                            activeDl.context,
                            activeDl.fileName,
                            activeDl.filePath,
                            activeDl.receivedBytes,
                            activeDl.totalSize
                        )
                    }
                    return
                }

                if (readingFilePath != null) {
                    readingBuffer?.write(chunkBytes)
                    readingReceivedBytes += chunkBytes.size
                    finishReadingFile()
                    return
                }

                if (copyingSourcePath != null) {
                    try {
                        copyingFileOutputStream?.write(chunkBytes)
                        copyingReceivedBytes += chunkBytes.size
                        val progress = if (copyingTotalSize > 0) {
                            (copyingReceivedBytes * copyingTotalSize) / (copyingTotalSize * 2)
                        } else {
                            copyingReceivedBytes / 2
                        }
                        val destPath = copyingDestPath
                        _activeTransfers.value = _activeTransfers.value.map {
                            if (it.filePath == destPath) it.copy(bytesTransferred = progress) else it
                        }
                        if (copyingTotalSize > 0 && copyingReceivedBytes >= copyingTotalSize) {
                            finishCopyingFile()
                        }
                        return
                    } catch (e: Exception) {
                        Log.e("MeshFilesCopy", "Error writing copy chunk: ${e.localizedMessage}", e)
                    }
                }
            } else {
                val size = json.get("size")?.asLong ?: 0L
                if (size > 0) {
                    val activeDl = activeDownloads.values.firstOrNull()
                    if (activeDl != null) {
                        activeDl.totalSize = size
                        _activeTransfers.value = _activeTransfers.value.map {
                            if (it.filePath == activeDl.filePath) it.copy(totalBytes = size) else it
                        }
                    } else if (copyingSourcePath != null) {
                        copyingTotalSize = size
                    } else if (readingFilePath != null) {
                        readingTotalSize = size
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MeshFiles", "Error parsing control JSON: ${e.localizedMessage}", e)
        }
    }

    private fun handleJsonMessage(text: String) {
        try {
            val json = gson.fromJson(text, JsonObject::class.java) ?: return
            val action = json.get("action")?.asString ?: ""
            val isDirectoryListing = action == "ls" || action == "dir" ||
                    (json.has("path") && (json.has("dir") || json.has("files") || json.has("list")))

            if (isDirectoryListing) {
                val path = json.get("path")?.asString ?: "/"

                val recursiveTarget = recursiveCopyTargetDir
                if (recursiveTarget != null && path == copyingDirectorySource) {
                    recursiveCopyTargetDir = null
                    copyingDirectorySource = null

                    val dirArray = json.getAsJsonArray("dir")
                        ?: json.getAsJsonArray("files")
                        ?: json.getAsJsonArray("list")

                    if (dirArray != null) {
                        for (elem in dirArray) {
                            if (!elem.isJsonObject) continue
                            val obj = elem.asJsonObject
                            val name = obj.get("name")?.asString ?: obj.get("n")?.asString ?: continue
                            val t = obj.get("t")?.asInt ?: 0
                            val isDir = (t == 1 || t == 2) || (t == 0 && name.endsWith("/"))
                            val cleanName = name.removeSuffix("/")
                            val srcItemPath = "$path/$cleanName"
                            val destItemPath = "$recursiveTarget/$cleanName"

                            if (isDir) {
                                val req = JsonObject().apply {
                                    addProperty("action", "mkdir")
                                    addProperty("path", destItemPath)
                                }
                                webSocket?.send(req.toString())
                            } else {
                                val fileSize = try { obj.get("s")?.asLong ?: 0L } catch (e: Exception) { 0L }
                                startFileCopyStream(srcItemPath, destItemPath, fileSize)
                            }
                        }
                    }
                    _statusMessage.value = "Copiados elementos de carpeta"
                    scope.launch {
                        delay(600)
                        listDirectory(_currentPath.value)
                    }
                    return
                }

                _currentPath.value = path.ifEmpty { "/" }

                val items = mutableListOf<FileItem>()
                val dirArray = json.getAsJsonArray("dir")
                    ?: json.getAsJsonArray("files")
                    ?: json.getAsJsonArray("list")

                if (dirArray != null) {
                    for (elem in dirArray) {
                        if (!elem.isJsonObject) continue
                        val obj = elem.asJsonObject
                        val name = obj.get("name")?.asString
                            ?: obj.get("n")?.asString
                            ?: continue
                        val t = obj.get("t")?.asInt ?: 0
                        val isDir = (t == 1 || t == 2) || (t == 0 && name.endsWith("/"))

                        var size = try {
                            obj.get("s")?.asLong ?: obj.get("size")?.asLong ?: 0L
                        } catch (e: Exception) { 0L }

                        val modified = try {
                            obj.get("d")?.asLong ?: obj.get("date")?.asLong ?: 0L
                        } catch (e: Exception) { 0L }

                        val cleanName = name.removeSuffix("/")
                        val cleanPath = path.trimEnd('/')
                        val itemPath = if (cleanPath.isEmpty()) "/$cleanName" else "$cleanPath/$cleanName"

                        if (!isDir && size == 0L) {
                            val saved = savedFileContents[itemPath]
                            if (saved != null && saved.isNotEmpty()) {
                                size = saved.toByteArray(Charsets.UTF_8).size.toLong()
                            } else {
                                val cached = _fileContent.value
                                if (cached != null && cached.first == itemPath && cached.second.isNotEmpty()) {
                                    size = cached.second.toByteArray(Charsets.UTF_8).size.toLong()
                                } else {
                                    val existing = _fileList.value.firstOrNull { it.path == itemPath }
                                    if (existing != null && existing.size > 0L) {
                                        size = existing.size
                                    }
                                }
                            }
                        }

                        items.add(
                            FileItem(
                                name = cleanName,
                                isDir = isDir,
                                size = size,
                                modifiedTime = modified,
                                path = itemPath
                            )
                        )
                    }
                }

                val sortedItems = items.sortedWith(
                    compareByDescending<FileItem> { it.isDir }
                        .thenBy { it.name.lowercase() }
                )
                _fileList.value = sortedItems
                onStateChanged?.invoke()
                Log.d("MeshFiles", "Successfully parsed ${sortedItems.size} items for path '$path' on $cleanNodeId")
            } else if (action == "download" || action == "read" || action == "readfile") {
                val path = json.get("path")?.asString ?: ""
                val content = json.get("data")?.asString
                    ?: json.get("content")?.asString
                    ?: json.get("text")?.asString

                if (path.isNotEmpty() && content != null) {
                    readingFilePath = null
                    readingBuffer = null
                    _fileReadingProgress.value = null
                    _fileContent.value = Pair(path, content)
                }
            } else if (action == "rm" || action == "rename" || action == "mkdir") {
                scope.launch {
                    delay(300)
                    listDirectory(_currentPath.value)
                }
            } else if (action == "msg") {
                val msg = json.get("value")?.asString
                if (msg != null) {
                    _statusMessage.value = msg
                }
            }
        } catch (e: Exception) {
            Log.e("MeshFiles", "Error parsing JSON: ${e.localizedMessage}", e)
        }
    }

    private fun createDownloadOutputStream(context: Context, fileName: String): OutputStream {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, getMimeType(fileName))
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    val os = resolver.openOutputStream(uri)
                    if (os != null) return os
                }
            } catch (e: Exception) {
                Log.e("MeshFilesDownload", "ContentResolver download insert failed, falling back: ${e.localizedMessage}")
            }
        }

        val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs()
        }
        val destFile = File(downloadsDir, fileName)
        return FileOutputStream(destFile)
    }

    private fun getMimeType(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "pdf" -> "application/pdf"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "txt", "log", "conf", "cfg" -> "text/plain"
            "html", "htm" -> "text/html"
            "json" -> "application/json"
            "zip" -> "application/zip"
            "apk" -> "application/vnd.android.package-archive"
            "mp3" -> "audio/mpeg"
            "mp4" -> "video/mp4"
            else -> "application/octet-stream"
        }
    }

    fun downloadFileToDownloads(
        context: Context,
        item: FileItem
    ) {
        val meshId = meshClient.getNodeMeshId(nodeId)
        val serverUrl = meshClient.serverUrl.trimEnd('/')

        val cookieToken = meshClient.userAuthCookie.ifEmpty {
            meshClient.relayAuthCookie.ifEmpty {
                meshClient.extractRawToken(meshClient.authCookie)
            }
        }
        val sessionCookie = meshClient.authCookie.trim()

        Toast.makeText(context, "Iniciando descarga de ${item.name}...", Toast.LENGTH_SHORT).show()

        val progress = TransferProgress(
            fileName = item.name,
            filePath = item.path,
            isUpload = false,
            bytesTransferred = 0L,
            totalBytes = item.size
        )

        _activeTransfers.value = _activeTransfers.value
            .filter { it.filePath != item.path } + progress

        updateDownloadNotification(context, item.name, item.path, 0L, item.size)

        val reqIdStr = Random.nextInt(100000, 999999).toString()

        scope.launch(Dispatchers.IO) {
            try {
                val outputStream = createDownloadOutputStream(context, item.name)
                val client = OkHttpHelper.createClient(meshClient.ignoreSsl)

                val encodedC = try { URLEncoder.encode(cookieToken, "UTF-8") } catch (_: Exception) { cookieToken }
                val encodedM = try { URLEncoder.encode(meshId, "UTF-8") } catch (_: Exception) { meshId }
                val encodedN = try { URLEncoder.encode(cleanNodeId, "UTF-8") } catch (_: Exception) { cleanNodeId }

                val cleanPath = item.path.removePrefix("/")
                val encodedF1 = try { URLEncoder.encode(cleanPath, "UTF-8") } catch (_: Exception) { cleanPath }
                val encodedF2 = try { URLEncoder.encode(item.path, "UTF-8") } catch (_: Exception) { item.path }

                var downloadUrl = "$serverUrl/devicefile.ashx?c=$encodedC&m=$encodedM&n=$encodedN&f=$encodedF1"
                var reqBuilder = Request.Builder().url(downloadUrl).get()
                if (sessionCookie.isNotBlank()) reqBuilder.addHeader("Cookie", sessionCookie)

                var response = client.newCall(reqBuilder.build()).execute()

                if (!response.isSuccessful) {
                    downloadUrl = "$serverUrl/devicefile.ashx?c=$encodedC&m=$encodedM&n=$encodedN&f=$encodedF2"
                    reqBuilder = Request.Builder().url(downloadUrl).get()
                    if (sessionCookie.isNotBlank()) reqBuilder.addHeader("Cookie", sessionCookie)
                    response = client.newCall(reqBuilder.build()).execute()
                }

                if (response.isSuccessful && response.body != null) {
                    val body = response.body!!
                    val inputStream = body.byteStream()
                    val contentLength = body.contentLength()
                    val totalSize = if (contentLength > 0) contentLength else item.size

                    var receivedBytes = 0L
                    val buffer = ByteArray(64 * 1024)

                    inputStream.use { input ->
                        outputStream.use { output ->
                            while (true) {
                                val bytesRead = input.read(buffer)
                                if (bytesRead == -1) break

                                output.write(buffer, 0, bytesRead)
                                receivedBytes += bytesRead

                                val current = receivedBytes
                                _activeTransfers.value = _activeTransfers.value.map {
                                    if (it.filePath == item.path) {
                                        it.copy(
                                            bytesTransferred = current,
                                            totalBytes = if (totalSize > 0) totalSize else current
                                        )
                                    } else {
                                        it
                                    }
                                }
                                updateDownloadNotification(context, item.name, item.path, receivedBytes, totalSize)
                            }
                            output.flush()
                        }
                    }

                    _activeTransfers.value = _activeTransfers.value.map {
                        if (it.filePath == item.path) {
                            it.copy(
                                bytesTransferred = receivedBytes,
                                totalBytes = if (totalSize > 0) totalSize else receivedBytes,
                                isCompleted = true
                            )
                        } else {
                            it
                        }
                    }

                    showDownloadCompleteNotification(context, item.name, item.path, receivedBytes)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            "Descarga completada: ${item.name} (${formatSize(receivedBytes)}) en Descargas",
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    delay(1200)
                    _activeTransfers.value = _activeTransfers.value.filter { it.filePath != item.path }
                    return@launch
                }
            } catch (e: Exception) {
                Log.e("MeshFilesDownload", "Error in HTTP download: ${e.localizedMessage}", e)
            }

            // Fallback to WS readfile / download
            try {
                if (!isConnected()) {
                    connect()
                    var waitMs = 0
                    while (!_connectionState.value && waitMs < 5000) {
                        delay(100)
                        waitMs += 100
                    }
                }

                val outputStream = createDownloadOutputStream(context, item.name)

                val session = ActiveDownloadSession(
                    filePath = item.path,
                    fileName = item.name,
                    reqId = reqIdStr,
                    totalSize = item.size,
                    receivedBytes = 0L,
                    outputStream = outputStream,
                    context = context
                )
                activeDownloads[item.path] = session

                val readReq = JsonObject().apply {
                    addProperty("action", "readfile")
                    addProperty("path", item.path)
                    addProperty("reqid", reqIdStr)
                    addProperty("id", reqIdStr)
                }

                webSocket?.send(readReq.toString())
            } catch (e: Exception) {
                Log.e("MeshFilesDownload", "Error in WS fallback: ${e.localizedMessage}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error al descargar: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun cancelCopyMove(targetFilePath: String? = null) {
        try {
            val key = targetFilePath ?: activeCopyMoveSessions.keys.firstOrNull()
            val session = if (key != null) activeCopyMoveSessions.remove(key) else null

            val name = session?.fileName ?: "archivo"
            session?.job?.cancel()

            if (copyingDestPath == key) {
                try {
                    copyingFileOutputStream?.flush()
                    copyingFileOutputStream?.close()
                } catch (_: Exception) {}
                copyingFileOutputStream = null
                copyingTempFile?.delete()
                copyingTempFile = null
                copyingSourcePath = null
                copyingDestPath = null
            }

            _activeTransfers.value = _activeTransfers.value.filter { it.filePath != key }

            val label = if (session?.isMove == true) "Movimiento cancelado" else "Copia cancelada"
            _statusMessage.value = "$label: $name"
        } catch (e: Exception) {
            Log.e("MeshFilesCopy", "Error canceling copy/move: ${e.localizedMessage}", e)
        }
    }

    fun cancelTransfer(filePath: String) {
        if (activeCopyMoveSessions.containsKey(filePath)) {
            cancelCopyMove(filePath)
        } else if (activeDownloads.containsKey(filePath)) {
            cancelDownload(filePath)
        } else if (activeUploads.containsKey(filePath)) {
            cancelUpload(filePath)
        } else {
            cancelCopyMove(filePath)
            cancelDownload(filePath)
            cancelUpload(filePath)
        }
    }

    fun cancelDownload(targetFilePath: String? = null) {
        try {
            val key = targetFilePath ?: activeDownloads.keys.firstOrNull()
            val session = if (key != null) activeDownloads.remove(key) else null

            val name = session?.fileName ?: "archivo"
            val ctx = session?.context

            session?.outputStream?.flush()
            session?.outputStream?.close()

            if (session != null) {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val destFile = File(downloadsDir, session.fileName)
                if (destFile.exists()) {
                    destFile.delete()
                }

                val notificationManager = ctx?.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                notificationManager?.cancel(session.filePath.hashCode())
            }

            _activeTransfers.value = _activeTransfers.value.filter { it.filePath != key }

            if (ctx != null) {
                scope.launch(Dispatchers.Main) {
                    Toast.makeText(ctx, "Descarga cancelada: $name", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e("MeshFilesDownload", "Error canceling download: ${e.localizedMessage}", e)
        }
    }

    fun cancelUpload(targetFilePath: String? = null) {
        try {
            val key = targetFilePath ?: activeUploads.keys.firstOrNull()
            val session = if (key != null) activeUploads.remove(key) else null

            val name = session?.fileName ?: "archivo"
            val ctx = session?.context

            session?.uploadJob?.cancel()

            if (session != null) {
                val notificationManager = ctx?.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                notificationManager?.cancel(session.filePath.hashCode())
            }

            _activeTransfers.value = _activeTransfers.value.filter { it.filePath != key }

            if (ctx != null) {
                scope.launch(Dispatchers.Main) {
                    Toast.makeText(ctx, "Subida cancelada: $name", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e("MeshFilesUpload", "Error canceling upload: ${e.localizedMessage}", e)
        }
    }

    private fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val channel = NotificationChannel(
                "file_downloads",
                "Descargas y Subidas de Archivos",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificaciones de progreso de transferencias de MeshCentral"
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun updateDownloadNotification(
        context: Context,
        fileName: String,
        filePath: String,
        received: Long,
        total: Long
    ) {
        val now = System.currentTimeMillis()
        if (now - lastProgressNotificationTime < 250 && received < total && received > 0) return
        lastProgressNotificationTime = now

        try {
            ensureNotificationChannel(context)
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            val notificationId = filePath.hashCode()
            val progressPercent = if (total > 0) ((received * 100) / total).toInt().coerceIn(0, 100) else 0

            val cancelIntent = Intent(context, DownloadCancelReceiver::class.java).apply {
                action = "ACTION_CANCEL_TRANSFER"
                putExtra("FILE_PATH", filePath)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                filePath.hashCode(),
                cancelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, "file_downloads")
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("Descargando $fileName")
                .setContentText("${formatSize(received)} / ${formatSize(total)} ($progressPercent%)")
                .setProgress(100, progressPercent, total <= 0)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "Cancelar",
                    pendingIntent
                )

            notificationManager.notify(notificationId, builder.build())
        } catch (e: Exception) {
            Log.e("MeshFilesDownload", "Error showing progress notification: ${e.localizedMessage}", e)
        }
    }

    private fun updateUploadNotification(
        context: Context,
        fileName: String,
        filePath: String,
        sent: Long,
        total: Long
    ) {
        val now = System.currentTimeMillis()
        if (now - lastProgressNotificationTime < 250 && sent < total && sent > 0) return
        lastProgressNotificationTime = now

        try {
            ensureNotificationChannel(context)
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            val notificationId = filePath.hashCode()
            val progressPercent = if (total > 0) ((sent * 100) / total).toInt().coerceIn(0, 100) else 0

            val cancelIntent = Intent(context, DownloadCancelReceiver::class.java).apply {
                action = "ACTION_CANCEL_TRANSFER"
                putExtra("FILE_PATH", filePath)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                filePath.hashCode(),
                cancelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, "file_downloads")
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setContentTitle("Subiendo $fileName")
                .setContentText("${formatSize(sent)} / ${formatSize(total)} ($progressPercent%)")
                .setProgress(100, progressPercent, total <= 0)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "Cancelar",
                    pendingIntent
                )

            notificationManager.notify(notificationId, builder.build())
        } catch (e: Exception) {
            Log.e("MeshFilesUpload", "Error showing upload notification: ${e.localizedMessage}", e)
        }
    }

    private fun showDownloadCompleteNotification(context: Context, fileName: String, filePath: String, total: Long) {
        try {
            ensureNotificationChannel(context)
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            val notificationId = filePath.hashCode()

            val builder = NotificationCompat.Builder(context, "file_downloads")
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Descarga completada")
                .setContentText("$fileName (${formatSize(total)}) guardado en Descargas")
                .setProgress(0, 0, false)
                .setOngoing(false)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)

            notificationManager.notify(notificationId, builder.build())
        } catch (e: Exception) {
            Log.e("MeshFilesDownload", "Error showing completion notification: ${e.localizedMessage}", e)
        }
    }

    private fun showUploadCompleteNotification(context: Context, fileName: String, filePath: String, total: Long) {
        try {
            ensureNotificationChannel(context)
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            val notificationId = filePath.hashCode()

            val builder = NotificationCompat.Builder(context, "file_downloads")
                .setSmallIcon(android.R.drawable.stat_sys_upload_done)
                .setContentTitle("Subida completada")
                .setContentText("$fileName (${formatSize(total)}) subido con éxito")
                .setProgress(0, 0, false)
                .setOngoing(false)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)

            notificationManager.notify(notificationId, builder.build())
        } catch (e: Exception) {
            Log.e("MeshFilesUpload", "Error showing completion notification: ${e.localizedMessage}", e)
        }
    }

    private fun finishDownload(targetFilePath: String? = null) {
        try {
            val key = targetFilePath ?: activeDownloads.keys.firstOrNull() ?: return
            val session = activeDownloads.remove(key) ?: return

            session.outputStream.flush()
            session.outputStream.close()

            val fileName = session.fileName
            val totalSize = session.receivedBytes
            val ctx = session.context

            _activeTransfers.value = _activeTransfers.value.map {
                if (it.filePath == key) {
                    it.copy(bytesTransferred = totalSize, totalBytes = totalSize, isCompleted = true)
                } else {
                    it
                }
            }

            scope.launch {
                delay(1200)
                _activeTransfers.value = _activeTransfers.value.filter { it.filePath != key }
            }

            if (ctx != null) {
                showDownloadCompleteNotification(ctx, fileName, key, totalSize)
                scope.launch(Dispatchers.Main) {
                    Toast.makeText(
                        ctx,
                        "Descarga completada: $fileName (${formatSize(totalSize)}) en carpeta Downloads",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        } catch (e: Exception) {
            Log.e("MeshFilesDownload", "Error finishing download: ${e.localizedMessage}", e)
        }
    }

    private fun startFileCopyStream(srcPath: String, destPath: String, size: Long, context: Context? = null) {
        val targetName = destPath.substringAfterLast('/')
        if (size == 0L) {
            uploadRawFileToPath(destPath, ByteArray(0), context)
            _statusMessage.value = "Copiado exitosamente: $targetName"
            _clipboardItem.value = null
            return
        }

        val progress = TransferProgress(
            fileName = targetName,
            filePath = destPath,
            isUpload = false,
            bytesTransferred = 0L,
            totalBytes = size,
            type = TransferType.COPY
        )
        _activeTransfers.value = _activeTransfers.value.filter { it.filePath != destPath } + progress

        copyingSourcePath = srcPath
        copyingDestPath = destPath
        copyingTotalSize = size
        copyingReceivedBytes = 0L

        try {
            copyingTempFile = File.createTempFile("mesh_copy_", ".tmp", context?.cacheDir)
            copyingFileOutputStream = FileOutputStream(copyingTempFile)
        } catch (e: Exception) {
            Log.e("MeshFilesCopy", "Error creating copy temp file: ${e.localizedMessage}", e)
        }

        val session = ActiveCopyMoveSession(
            filePath = destPath,
            fileName = targetName,
            isMove = false,
            transferredBytes = 0L,
            totalSize = size,
            job = null,
            context = context
        )
        activeCopyMoveSessions[destPath] = session

        val reqId = Random.nextInt(100000, 999999)

        val parentDir = if (srcPath.contains('/')) {
            srcPath.substringBeforeLast('/').ifEmpty { "/" }
        } else {
            _currentPath.value
        }
        val fileName = srcPath.substringAfterLast('/')

        val req1 = JsonObject().apply {
            addProperty("action", "download")
            addProperty("path", parentDir)
            addProperty("file", fileName)
            addProperty("subaction", "start")
            addProperty("id", reqId)
        }
        webSocket?.send(req1.toString())

        scope.launch(Dispatchers.IO) {
            delay(50)
            val req2 = JsonObject().apply {
                addProperty("action", "download")
                addProperty("path", parentDir)
                addProperty("file", fileName)
                addProperty("subaction", "download")
                addProperty("id", reqId)
            }
            webSocket?.send(req2.toString())
        }
    }

    private fun finishCopyingFile() {
        try {
            val destPath = copyingDestPath ?: return
            val targetName = destPath.substringAfterLast('/')
            val tempFile = copyingTempFile

            try {
                copyingFileOutputStream?.flush()
                copyingFileOutputStream?.close()
            } catch (_: Exception) {}
            copyingFileOutputStream = null

            copyingSourcePath = null
            copyingDestPath = null
            copyingTotalSize = 0L
            copyingReceivedBytes = 0L
            copyingTempFile = null

            if (tempFile != null && tempFile.exists() && activeCopyMoveSessions.containsKey(destPath)) {
                scope.launch(Dispatchers.IO) {
                    try {
                        val fileLen = tempFile.length()
                        val totalSize = fileLen.coerceAtLeast(1L)
                        val uploadReq = JsonObject().apply {
                            addProperty("action", "upload")
                            addProperty("path", destPath)
                            addProperty("size", fileLen)
                        }
                        webSocket?.send(uploadReq.toString())
                        delay(100)

                        FileInputStream(tempFile).use { input ->
                            val buffer = ByteArray(64 * 1024)
                            var uploaded = 0L
                            while (activeCopyMoveSessions.containsKey(destPath)) {
                                val read = input.read(buffer)
                                if (read == -1) break
                                val chunk = ByteString.of(*buffer.copyOfRange(0, read))
                                webSocket?.send(chunk)
                                uploaded += read

                                val prog = (totalSize / 2) + if (fileLen > 0) (uploaded * totalSize) / (fileLen * 2) else 0L
                                _activeTransfers.value = _activeTransfers.value.map {
                                    if (it.filePath == destPath) it.copy(bytesTransferred = prog.coerceAtMost(totalSize), totalBytes = totalSize) else it
                                }
                                delay(10)
                            }
                        }

                        if (activeCopyMoveSessions.containsKey(destPath)) {
                            _activeTransfers.value = _activeTransfers.value.map {
                                if (it.filePath == destPath) it.copy(bytesTransferred = totalSize, isCompleted = true) else it
                            }
                            _clipboardItem.value = null
                            clipboardNodeId = null
                            _statusMessage.value = "Copiado exitosamente: $targetName"
                            activeCopyMoveSessions.remove(destPath)
                            delay(1200)
                            _activeTransfers.value = _activeTransfers.value.filter { it.filePath != destPath }
                            if (isConnected()) {
                                listDirectory(_currentPath.value)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("MeshFilesCopy", "Error finishing WS copy: ${e.localizedMessage}", e)
                    } finally {
                        tempFile.delete()
                    }
                }
            } else {
                tempFile?.delete()
                activeCopyMoveSessions.remove(destPath)
                _activeTransfers.value = _activeTransfers.value.filter { it.filePath != destPath }
            }
        } catch (e: Exception) {
            Log.e("MeshFilesCopy", "Error finishing copy: ${e.localizedMessage}", e)
        }
    }

    private fun finishReadingFile() {
        try {
            val path = readingFilePath ?: return
            val bytes = readingBuffer?.toByteArray() ?: ByteArray(0)
            val textContent = String(bytes, Charsets.UTF_8)

            scope.launch {
                _fileReadingProgress.value = 1.0f
                delay(150)
                readingFilePath = null
                readingBuffer = null
                readingTotalSize = 0L
                readingReceivedBytes = 0L
                _fileReadingProgress.value = null

                _fileContent.value = Pair(path, textContent)
            }
        } catch (e: Exception) {
            Log.e("MeshFilesRead", "Error finishing read: ${e.localizedMessage}", e)
            _fileReadingProgress.value = null
        }
    }

    fun listDirectory(path: String) {
        val targetPath = if (path.isEmpty()) "/" else path
        val req = JsonObject().apply {
            addProperty("action", "ls")
            addProperty("path", targetPath)
        }
        scope.launch(Dispatchers.IO) {
            ensureConnected()
            webSocket?.send(req.toString())
        }
    }

    fun createFolder(folderName: String) {
        val path = _currentPath.value.trimEnd('/')
        val fullPath = if (path.isEmpty()) "/$folderName" else "$path/$folderName"
        val req = JsonObject().apply {
            addProperty("action", "mkdir")
            addProperty("path", fullPath)
        }
        scope.launch(Dispatchers.IO) {
            ensureConnected()
            webSocket?.send(req.toString())
            delay(200)
            listDirectory(_currentPath.value)
        }
    }

    fun createFile(fileName: String, content: String = "") {
        val path = _currentPath.value.trimEnd('/')
        val fullPath = if (path.isEmpty()) "/$fileName" else "$path/$fileName"
        val req = JsonObject().apply {
            addProperty("action", "upload")
            addProperty("path", fullPath)
            addProperty("size", content.toByteArray().size)
        }
        scope.launch(Dispatchers.IO) {
            ensureConnected()
            webSocket?.send(req.toString())
            if (content.isNotEmpty()) {
                webSocket?.send(content)
            }
            delay(200)
            listDirectory(_currentPath.value)
        }
    }

    fun uploadRawFile(fileName: String, bytes: ByteArray, context: Context? = null) {
        val path = _currentPath.value.trimEnd('/')
        val fullPath = if (path.isEmpty()) "/$fileName" else "$path/$fileName"
        uploadRawFileToPath(fullPath, bytes, context)
    }

    fun uploadRawFileToPath(fullPath: String, bytes: ByteArray, context: Context? = null) {
        val fileName = fullPath.substringAfterLast('/')

        val session = ActiveUploadSession(
            filePath = fullPath,
            fileName = fileName,
            sentBytes = 0L,
            totalSize = bytes.size.toLong(),
            uploadJob = null,
            context = context
        )
        activeUploads[fullPath] = session

        val progress = TransferProgress(
            fileName = fileName,
            filePath = fullPath,
            isUpload = true,
            bytesTransferred = 0L,
            totalBytes = bytes.size.toLong()
        )
        _activeTransfers.value = _activeTransfers.value.filter { it.filePath != fullPath } + progress

        if (context != null) {
            updateUploadNotification(context, fileName, fullPath, 0L, bytes.size.toLong())
        }

        session.uploadJob = scope.launch(Dispatchers.IO) {
            try {
                val req = JsonObject().apply {
                    addProperty("action", "upload")
                    addProperty("path", fullPath)
                    addProperty("size", bytes.size)
                }
                webSocket?.send(req.toString())

                delay(100)

                val chunkSize = 64 * 1024
                var offset = 0
                val total = bytes.size

                if (total == 0) {
                    webSocket?.send(ByteString.EMPTY)
                } else {
                    while (offset < total && activeUploads.containsKey(fullPath)) {
                        val length = minOf(chunkSize, total - offset)
                        val chunk = ByteString.of(*bytes.copyOfRange(offset, offset + length))
                        webSocket?.send(chunk)
                        offset += length

                        session.sentBytes = offset.toLong()
                        val currOffset = offset.toLong()
                        _activeTransfers.value = _activeTransfers.value.map {
                            if (it.filePath == fullPath) it.copy(bytesTransferred = currOffset) else it
                        }

                        if (context != null) {
                            updateUploadNotification(context, fileName, fullPath, currOffset, total.toLong())
                        }
                        delay(10)
                    }
                }

                if (activeUploads.containsKey(fullPath)) {
                    _activeTransfers.value = _activeTransfers.value.map {
                        if (it.filePath == fullPath) it.copy(bytesTransferred = total.toLong(), isCompleted = true) else it
                    }

                    if (context != null) {
                        showUploadCompleteNotification(context, fileName, fullPath, total.toLong())
                    }

                    delay(1500)
                    activeUploads.remove(fullPath)
                    _activeTransfers.value = _activeTransfers.value.filter { it.filePath != fullPath }
                    listDirectory(_currentPath.value)
                }
            } catch (e: Exception) {
                Log.e("MeshFilesUpload", "Error uploading file: ${e.localizedMessage}", e)
                activeUploads.remove(fullPath)
                _activeTransfers.value = _activeTransfers.value.filter { it.filePath != fullPath }
            }
        }
    }

    fun saveFileContent(filePath: String, newContent: String) {
        val bytes = newContent.toByteArray(Charsets.UTF_8)
        val newSize = bytes.size.toLong()

        savedFileContents[filePath] = newContent
        _fileContent.value = Pair(filePath, newContent)
        _fileList.value = _fileList.value.map { item ->
            if (item.path == filePath) {
                item.copy(size = newSize)
            } else {
                item
            }
        }

        uploadRawFileToPath(filePath, bytes)
        _statusMessage.value = "Archivo guardado exitosamente"
    }

    fun cancelFileReading() {
        readingFilePath = null
        readingBuffer = null
        readingTotalSize = 0L
        readingReceivedBytes = 0L
        _fileReadingProgress.value = null
    }

    fun readFileContent(item: FileItem) {
        val savedText = savedFileContents[item.path]
        if (savedText != null && savedText.isNotEmpty()) {
            _fileContent.value = Pair(item.path, savedText)
            _fileReadingProgress.value = null
            return
        }

        val cachedContent = _fileContent.value
        if (cachedContent != null && cachedContent.first == item.path && cachedContent.second.isNotEmpty()) {
            _fileContent.value = Pair(item.path, cachedContent.second)
            _fileReadingProgress.value = null
            return
        }

        if (item.size == 0L && (savedText == null || savedText.isEmpty())) {
            _fileContent.value = Pair(item.path, "")
            _fileReadingProgress.value = null
            return
        }

        readingFilePath = item.path
        readingTotalSize = item.size
        readingReceivedBytes = 0L
        readingBuffer = ByteArrayOutputStream()
        _fileReadingProgress.value = 0.05f

        val meshId = meshClient.getNodeMeshId(cleanNodeId)
        val serverUrl = meshClient.serverUrl.trimEnd('/')

        val cookieToken = meshClient.userAuthCookie.ifEmpty {
            meshClient.relayAuthCookie.ifEmpty {
                meshClient.extractRawToken(meshClient.authCookie)
            }
        }
        val sessionCookie = meshClient.authCookie.trim()

        scope.launch(Dispatchers.IO) {
            try {
                val client = OkHttpHelper.createClient(meshClient.ignoreSsl)
                val encodedC = try { URLEncoder.encode(cookieToken, "UTF-8") } catch (_: Exception) { cookieToken }
                val encodedM = try { URLEncoder.encode(meshId, "UTF-8") } catch (_: Exception) { meshId }
                val encodedN = try { URLEncoder.encode(cleanNodeId, "UTF-8") } catch (_: Exception) { cleanNodeId }

                val cleanPath = item.path.removePrefix("/")
                val encodedF1 = try { URLEncoder.encode(cleanPath, "UTF-8") } catch (_: Exception) { cleanPath }
                val encodedF2 = try { URLEncoder.encode(item.path, "UTF-8") } catch (_: Exception) { item.path }

                var downloadUrl = "$serverUrl/devicefile.ashx?c=$encodedC&m=$encodedM&n=$encodedN&f=$encodedF1"
                var reqBuilder = Request.Builder().url(downloadUrl).get()
                if (sessionCookie.isNotBlank()) reqBuilder.addHeader("Cookie", sessionCookie)

                var response = client.newCall(reqBuilder.build()).execute()

                if (!response.isSuccessful) {
                    downloadUrl = "$serverUrl/devicefile.ashx?c=$encodedC&m=$encodedM&n=$encodedN&f=$encodedF2"
                    reqBuilder = Request.Builder().url(downloadUrl).get()
                    if (sessionCookie.isNotBlank()) reqBuilder.addHeader("Cookie", sessionCookie)

                    response = client.newCall(reqBuilder.build()).execute()
                }

                if (response.isSuccessful && response.body != null) {
                    val bytes = response.body!!.bytes()
                    val textContent = String(bytes, Charsets.UTF_8)

                    if (bytes.isEmpty() && item.size > 0L && savedText != null) {
                        _fileReadingProgress.value = 1.0f
                        delay(150)
                        readingFilePath = null
                        readingBuffer = null
                        _fileReadingProgress.value = null
                        _fileContent.value = Pair(item.path, savedText)
                        return@launch
                    }

                    savedFileContents[item.path] = textContent
                    _fileReadingProgress.value = 1.0f
                    delay(150)

                    readingFilePath = null
                    readingBuffer = null
                    _fileReadingProgress.value = null
                    _fileContent.value = Pair(item.path, textContent)
                    return@launch
                }
            } catch (e: Exception) {
                Log.e("MeshFilesRead", "Error in HTTP read: ${e.localizedMessage}", e)
            }

            // WS readfile fallback
            try {
                if (!isConnected()) {
                    connect()
                    var waitMs = 0
                    while (!_connectionState.value && waitMs < 5000) {
                        delay(100)
                        waitMs += 100
                    }
                }

                val reqIdStr = Random.nextInt(100000, 999999).toString()
                val readReq = JsonObject().apply {
                    addProperty("action", "readfile")
                    addProperty("path", item.path)
                    addProperty("reqid", reqIdStr)
                    addProperty("id", reqIdStr)
                }
                webSocket?.send(readReq.toString())
            } catch (e: Exception) {
                Log.e("MeshFilesRead", "Error in WS readfile fallback: ${e.localizedMessage}", e)
                _fileReadingProgress.value = null
                _statusMessage.value = "Error al leer archivo (${item.name})"
            }
        }
    }

    fun clearFileContent() {
        _fileContent.value = null
        _fileReadingProgress.value = null
    }

    private fun remoteParent(path: String): String {
        val clean = path.trimEnd('/', '\\')
        require(clean.isNotEmpty()) { "No se permite operar sobre la raíz" }
        require(!Regex("^[A-Za-z]:$").matches(clean)) { "No se permite operar sobre la raíz de una unidad" }

        val separatorIndex = maxOf(clean.lastIndexOf('/'), clean.lastIndexOf('\\'))
        require(separatorIndex >= 0) { "Se esperaba una ruta remota absoluta: $path" }

        if (separatorIndex == 0) return "/"
        val parent = clean.substring(0, separatorIndex)
        return if (Regex("^[A-Za-z]:$").matches(parent)) "$parent/" else parent
    }

    private fun validateRemoteEntryName(name: String) {
        require(name.isNotBlank()) { "El nombre está vacío" }
        require(name != "." && name != "..") { "Nombre no permitido" }
        require('/' !in name && '\\' !in name && '\u0000' !in name) { "Se esperaba un nombre, no una ruta" }
    }

    fun copyItem(item: FileItem) {
        if (item.isDir) {
            _statusMessage.value = "La función de copiar solo está disponible para archivos"
            return
        }
        if (!_connectionState.value) {
            _statusMessage.value = "No hay una sesión de archivos activa"
            return
        }

        clipboardNodeId = cleanNodeId
        _clipboardItem.value = item
        _isCutMode.value = false

        _statusMessage.value = "Seleccionado para copiar: ${item.name}"
    }

    fun cutItem(item: FileItem) {
        if (item.isDir) {
            _statusMessage.value = "La función de cortar solo está disponible para archivos"
            return
        }
        if (!_connectionState.value) {
            _statusMessage.value = "No hay una sesión de archivos activa"
            return
        }

        clipboardNodeId = cleanNodeId
        _clipboardItem.value = item
        _isCutMode.value = true

        _statusMessage.value = "Seleccionado para mover: ${item.name}"
    }

    fun clearClipboard() {
        clipboardNodeId = null
        _clipboardItem.value = null
        _isCutMode.value = false
    }

    fun pasteItem(customTargetName: String? = null, context: Context? = null) {
        val source = _clipboardItem.value ?: run {
            _statusMessage.value = "El portapapeles está vacío"
            return
        }
        val cut = _isCutMode.value
        val sourceNode = clipboardNodeId ?: run {
            _statusMessage.value = "Vuelve a seleccionar el elemento"
            return
        }

        val targetNode = cleanNodeId
        val destination = _currentPath.value

        scope.launch(Dispatchers.IO) {
            try {
                require(!source.isDir) { "Las operaciones de copiar y cortar solo están permitidas para archivos" }
                check(sourceNode == targetNode) { "Este portapapeles solo permite operaciones dentro del mismo equipo remoto" }

                if (!isConnected()) {
                    connect()
                    var waitMs = 0
                    while (!_connectionState.value && waitMs < 5000) {
                        delay(100)
                        waitMs += 100
                    }
                }

                check(_connectionState.value && webSocket != null) { "El túnel de archivos no está conectado" }

                validateRemoteEntryName(source.name)
                val targetName = customTargetName?.takeIf { it.isNotBlank() } ?: source.name
                validateRemoteEntryName(targetName)

                val sourceDirectory = remoteParent(source.path)
                val reqIdStr = Random.nextInt(100000, 999999).toString()

                val cleanDest = if (destination == "/") "/" else destination.trimEnd('/')
                val destPath = if (cleanDest == "/") "/$targetName" else "$cleanDest/$targetName"

                if (cut) {
                    val progress = TransferProgress(
                        fileName = targetName,
                        filePath = destPath,
                        isUpload = false,
                        bytesTransferred = 0L,
                        totalBytes = source.size,
                        type = TransferType.MOVE
                    )
                    _activeTransfers.value = _activeTransfers.value.filter { it.filePath != destPath } + progress

                    val session = ActiveCopyMoveSession(
                        filePath = destPath,
                        fileName = targetName,
                        isMove = true,
                        transferredBytes = 0L,
                        totalSize = source.size,
                        job = coroutineContext[Job],
                        context = context
                    )
                    activeCopyMoveSessions[destPath] = session

                    val moveReq = JsonObject().apply {
                        addProperty("action", "move")
                        addProperty("reqid", reqIdStr)
                        addProperty("id", reqIdStr)
                        addProperty("scpath", sourceDirectory)
                        addProperty("dspath", destination)
                        add("names", gson.toJsonTree(listOf(source.name)))
                        add("files", gson.toJsonTree(listOf(source.name)))
                        addProperty("file", source.name)
                        addProperty("destname", targetName)
                        addProperty("newname", targetName)
                    }
                    webSocket?.send(moveReq.toString())

                    val renameReq = JsonObject().apply {
                        addProperty("action", "rename")
                        addProperty("reqid", reqIdStr)
                        addProperty("id", reqIdStr)
                        addProperty("path", sourceDirectory)
                        addProperty("oldfile", source.path)
                        addProperty("newfile", destPath)
                        addProperty("oldpath", source.path)
                        addProperty("newpath", destPath)
                        addProperty("oldname", source.name)
                        addProperty("newname", targetName)
                        addProperty("scpath", sourceDirectory)
                        addProperty("dspath", destination)
                    }
                    webSocket?.send(renameReq.toString())

                    _clipboardItem.value = null
                    clipboardNodeId = null
                    _statusMessage.value = "Movido: $targetName"

                    _activeTransfers.value = _activeTransfers.value.map {
                        if (it.filePath == destPath) it.copy(bytesTransferred = source.size, isCompleted = true) else it
                    }
                    activeCopyMoveSessions.remove(destPath)
                    delay(1200)
                    _activeTransfers.value = _activeTransfers.value.filter { it.filePath != destPath }
                    if (isConnected() && _currentPath.value == destination) {
                        listDirectory(destination)
                    }
                } else {
                    if (sourceDirectory == destination || targetName != source.name) {
                        copyFileToDestination(source, destPath, targetName, context)
                    } else {
                        val progress = TransferProgress(
                            fileName = targetName,
                            filePath = destPath,
                            isUpload = false,
                            bytesTransferred = 0L,
                            totalBytes = source.size,
                            type = TransferType.COPY
                        )
                        _activeTransfers.value = _activeTransfers.value.filter { it.filePath != destPath } + progress

                        val session = ActiveCopyMoveSession(
                            filePath = destPath,
                            fileName = targetName,
                            isMove = false,
                            transferredBytes = 0L,
                            totalSize = source.size,
                            job = coroutineContext[Job],
                            context = context
                        )
                        activeCopyMoveSessions[destPath] = session

                        val copyReq = JsonObject().apply {
                            addProperty("action", "copy")
                            addProperty("reqid", reqIdStr)
                            addProperty("id", reqIdStr)
                            addProperty("scpath", sourceDirectory)
                            addProperty("dspath", destination)
                            add("names", gson.toJsonTree(listOf(source.name)))
                            add("files", gson.toJsonTree(listOf(source.name)))
                            addProperty("file", source.name)
                            addProperty("destname", targetName)
                            addProperty("newname", targetName)
                        }
                        webSocket?.send(copyReq.toString())

                        _clipboardItem.value = null
                        clipboardNodeId = null
                        _statusMessage.value = "Copiado: $targetName"

                        _activeTransfers.value = _activeTransfers.value.map {
                            if (it.filePath == destPath) it.copy(bytesTransferred = source.size, isCompleted = true) else it
                        }
                        activeCopyMoveSessions.remove(destPath)
                        delay(1200)
                        _activeTransfers.value = _activeTransfers.value.filter { it.filePath != destPath }
                        if (isConnected() && _currentPath.value == destination) {
                            listDirectory(destination)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MeshFilesPaste", "Error al solicitar copiar/mover ${source.path}", e)
                _statusMessage.value = "No se pudo pegar: ${e.localizedMessage}"
                _activeTransfers.value = _activeTransfers.value.filter { it.filePath != source.path }
            }
        }
    }

    private fun copyFileToDestination(
        source: FileItem,
        destPath: String,
        targetName: String,
        context: Context? = null
    ) {
        if (source.size == 0L) {
            uploadRawFileToPath(destPath, ByteArray(0), context)
            _clipboardItem.value = null
            clipboardNodeId = null
            _statusMessage.value = "Copiado exitosamente: $targetName"
            return
        }

        val progress = TransferProgress(
            fileName = targetName,
            filePath = destPath,
            isUpload = false,
            bytesTransferred = 0L,
            totalBytes = source.size,
            type = TransferType.COPY
        )
        _activeTransfers.value = _activeTransfers.value.filter { it.filePath != destPath } + progress

        val meshId = meshClient.getNodeMeshId(cleanNodeId)
        val serverUrl = meshClient.serverUrl.trimEnd('/')
        val cookieToken = meshClient.userAuthCookie.ifEmpty {
            meshClient.relayAuthCookie.ifEmpty {
                meshClient.extractRawToken(meshClient.authCookie)
            }
        }
        val sessionCookie = meshClient.authCookie.trim()

        val copyJob = scope.launch(Dispatchers.IO) {
            var tempFile: File? = null
            var httpSuccess = false

            try {
                tempFile = try {
                    File.createTempFile("mesh_copy_", ".tmp", context?.cacheDir)
                } catch (_: Exception) {
                    File.createTempFile("mesh_copy_", ".tmp")
                }

                val client = OkHttpHelper.createClient(meshClient.ignoreSsl)
                val encodedC = try { URLEncoder.encode(cookieToken, "UTF-8") } catch (_: Exception) { cookieToken }
                val encodedM = try { URLEncoder.encode(meshId, "UTF-8") } catch (_: Exception) { meshId }
                val encodedN = try { URLEncoder.encode(cleanNodeId, "UTF-8") } catch (_: Exception) { cleanNodeId }

                val cleanPath = source.path.removePrefix("/")
                val encodedF1 = try { URLEncoder.encode(cleanPath, "UTF-8") } catch (_: Exception) { cleanPath }
                val encodedF2 = try { URLEncoder.encode(source.path, "UTF-8") } catch (_: Exception) { source.path }

                var downloadUrl = "$serverUrl/devicefile.ashx?c=$encodedC&m=$encodedM&n=$encodedN&f=$encodedF1"
                var reqBuilder = Request.Builder().url(downloadUrl).get()
                if (sessionCookie.isNotBlank()) reqBuilder.addHeader("Cookie", sessionCookie)

                var response = client.newCall(reqBuilder.build()).execute()
                if (!response.isSuccessful) {
                    downloadUrl = "$serverUrl/devicefile.ashx?c=$encodedC&m=$encodedM&n=$encodedN&f=$encodedF2"
                    reqBuilder = Request.Builder().url(downloadUrl).get()
                    if (sessionCookie.isNotBlank()) reqBuilder.addHeader("Cookie", sessionCookie)
                    response = client.newCall(reqBuilder.build()).execute()
                }

                if (response.isSuccessful && response.body != null) {
                    val body = response.body!!
                    val totalSize = if (body.contentLength() > 0) body.contentLength() else source.size
                    val inputStream = body.byteStream()

                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var received = 0L
                        while (activeCopyMoveSessions.containsKey(destPath)) {
                            val read = inputStream.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            received += read

                            val prog = if (totalSize > 0) (received * totalSize) / (totalSize * 2) else received / 2
                            _activeTransfers.value = _activeTransfers.value.map {
                                if (it.filePath == destPath) it.copy(bytesTransferred = prog, totalBytes = totalSize) else it
                            }
                        }
                        output.flush()
                    }
                    httpSuccess = tempFile.length() > 0 || source.size == 0L
                }
            } catch (e: Exception) {
                Log.e("MeshFilesCopy", "HTTP copy fetch error: ${e.localizedMessage}", e)
            }

            if (httpSuccess && tempFile != null && tempFile.exists() && activeCopyMoveSessions.containsKey(destPath)) {
                try {
                    val totalSize = source.size.coerceAtLeast(tempFile.length())
                    val uploadReq = JsonObject().apply {
                        addProperty("action", "upload")
                        addProperty("path", destPath)
                        addProperty("size", tempFile.length())
                    }
                    webSocket?.send(uploadReq.toString())
                    delay(100)

                    FileInputStream(tempFile).use { input ->
                        val buffer = ByteArray(64 * 1024)
                        var uploaded = 0L
                        val fileLen = tempFile.length()
                        while (activeCopyMoveSessions.containsKey(destPath)) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            val chunk = ByteString.of(*buffer.copyOfRange(0, read))
                            webSocket?.send(chunk)
                            uploaded += read

                            val prog = (totalSize / 2) + if (fileLen > 0) (uploaded * totalSize) / (fileLen * 2) else 0L
                            _activeTransfers.value = _activeTransfers.value.map {
                                if (it.filePath == destPath) it.copy(bytesTransferred = prog.coerceAtMost(totalSize), totalBytes = totalSize) else it
                            }
                            delay(10)
                        }
                    }

                    if (activeCopyMoveSessions.containsKey(destPath)) {
                        _activeTransfers.value = _activeTransfers.value.map {
                            if (it.filePath == destPath) it.copy(bytesTransferred = totalSize, isCompleted = true) else it
                        }
                        _clipboardItem.value = null
                        clipboardNodeId = null
                        _statusMessage.value = "Copiado exitosamente: $targetName"
                        activeCopyMoveSessions.remove(destPath)
                        delay(1200)
                        _activeTransfers.value = _activeTransfers.value.filter { it.filePath != destPath }
                        if (isConnected()) {
                            listDirectory(_currentPath.value)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MeshFilesCopy", "Error uploading copied temp file: ${e.localizedMessage}", e)
                    activeCopyMoveSessions.remove(destPath)
                    _activeTransfers.value = _activeTransfers.value.filter { it.filePath != destPath }
                } finally {
                    tempFile.delete()
                }
                return@launch
            }

            // Fallback WS readfile
            try {
                if (!isConnected()) {
                    connect()
                    var waitMs = 0
                    while (!_connectionState.value && waitMs < 5000) {
                        delay(100)
                        waitMs += 100
                    }
                }

                copyingSourcePath = source.path
                copyingDestPath = destPath
                copyingTotalSize = source.size
                copyingReceivedBytes = 0L
                copyingTempFile = tempFile ?: try {
                    File.createTempFile("mesh_copy_", ".tmp", context?.cacheDir)
                } catch (_: Exception) {
                    File.createTempFile("mesh_copy_", ".tmp")
                }
                copyingFileOutputStream = FileOutputStream(copyingTempFile)

                val reqIdStr = Random.nextInt(100000, 999999).toString()
                val readReq = JsonObject().apply {
                    addProperty("action", "readfile")
                    addProperty("path", source.path)
                    addProperty("reqid", reqIdStr)
                    addProperty("id", reqIdStr)
                }
                webSocket?.send(readReq.toString())
            } catch (e: Exception) {
                Log.e("MeshFilesCopy", "WS copy fetch error: ${e.localizedMessage}", e)
                _statusMessage.value = "Error al copiar: ${e.localizedMessage}"
                activeCopyMoveSessions.remove(destPath)
                _activeTransfers.value = _activeTransfers.value.filter { it.filePath != destPath }
                tempFile?.delete()
            }
        }

        val session = ActiveCopyMoveSession(
            filePath = destPath,
            fileName = targetName,
            isMove = false,
            transferredBytes = 0L,
            totalSize = source.size,
            job = copyJob,
            context = context
        )
        activeCopyMoveSessions[destPath] = session
    }

    fun compressZip(item: FileItem, customZipName: String? = null) {
        val parentDir = if (item.path.contains('/')) {
            item.path.substringBeforeLast('/').ifEmpty { "/" }
        } else if (item.path.contains('\\')) {
            item.path.substringBeforeLast('\\').ifEmpty { "\\" }
        } else {
            _currentPath.value
        }
        val cleanParent = if (parentDir == "/") "/" else parentDir.trimEnd('/')

        val rawZipName = customZipName?.ifBlank { null } ?: item.name
        val cleanZipName = if (rawZipName.endsWith(".zip", ignoreCase = true)) {
            rawZipName
        } else {
            "$rawZipName.zip"
        }

        val reqIdStr = Random.nextInt(100000, 999999).toString()
        val filesArray = gson.toJsonTree(listOf(item.name))

        val zipReq = JsonObject().apply {
            addProperty("action", "zip")
            addProperty("path", cleanParent)
            add("files", filesArray)
            addProperty("file", cleanZipName)
            addProperty("dest", cleanZipName)
            addProperty("target", cleanZipName)
            addProperty("output", cleanZipName)
            addProperty("rec", 1)
            addProperty("recurse", 1)
            addProperty("recursive", 1)
            addProperty("includeempty", 1)
            addProperty("emptydirs", 1)
            addProperty("folders", 1)
            addProperty("all", 1)
            addProperty("dirs", 1)
            addProperty("emptyDirs", true)
            addProperty("includeDirs", true)
            addProperty("reqid", reqIdStr)
            addProperty("id", reqIdStr)
        }
        webSocket?.send(zipReq.toString())

        _statusMessage.value = "Comprimiendo ${item.name} a $cleanZipName..."
        scope.launch {
            delay(1500)
            listDirectory(_currentPath.value)
        }
    }

    fun decompressZip(item: FileItem) {
        val parentDir = if (item.path.contains('/')) {
            item.path.substringBeforeLast('/').ifEmpty { "/" }
        } else if (item.path.contains('\\')) {
            item.path.substringBeforeLast('\\').ifEmpty { "\\" }
        } else {
            _currentPath.value
        }

        val req = JsonObject().apply {
            addProperty("action", "unzip")
            addProperty("path", parentDir)
            addProperty("file", item.name)
            addProperty("src", item.name)
            addProperty("dest", parentDir)
            addProperty("target", parentDir)
        }
        webSocket?.send(req.toString())
        _statusMessage.value = "Descomprimiendo ${item.name}..."
        scope.launch {
            delay(1200)
            listDirectory(_currentPath.value)
        }
    }

    fun openOnRemoteDevice(item: FileItem) {
        val parentDir = if (item.path.contains('/')) {
            item.path.substringBeforeLast('/').ifEmpty { "/" }
        } else if (item.path.contains('\\')) {
            item.path.substringBeforeLast('\\').ifEmpty { "\\" }
        } else {
            _currentPath.value
        }

        val req1 = JsonObject().apply {
            addProperty("action", "open")
            addProperty("path", parentDir)
            addProperty("file", item.name)
            addProperty("target", item.path)
        }
        webSocket?.send(req1.toString())

        val req2 = JsonObject().apply {
            addProperty("action", "open")
            addProperty("path", item.path)
            addProperty("target", item.path)
        }
        webSocket?.send(req2.toString())

        val req3 = JsonObject().apply {
            addProperty("action", "openfile")
            addProperty("path", item.path)
            addProperty("file", item.name)
        }
        webSocket?.send(req3.toString())

        _statusMessage.value = "Abriendo ${item.name} en el equipo remoto..."
    }

    fun deleteItem(item: FileItem) {
        scope.launch(Dispatchers.IO) {
            try {
                validateRemoteEntryName(item.name)
                val parent = remoteParent(item.path)

                if (!isConnected()) {
                    connect()
                    var waitMs = 0
                    while (!_connectionState.value && waitMs < 5000) {
                        delay(100)
                        waitMs += 100
                    }
                }

                check(_connectionState.value && webSocket != null) { "El túnel de archivos no está conectado" }

                val reqIdStr = Random.nextInt(100000, 999999).toString()

                val rmReq = JsonObject().apply {
                    addProperty("action", "rm")
                    addProperty("reqid", reqIdStr)
                    addProperty("id", reqIdStr)
                    addProperty("path", parent)
                    addProperty("file", item.name)
                    add("files", gson.toJsonTree(listOf(item.name)))
                    add("delfiles", gson.toJsonTree(listOf(item.name)))
                    if (item.isDir) {
                        addProperty("rec", 1)
                        addProperty("recursive", true)
                        addProperty("recurse", true)
                    }
                }
                webSocket?.send(rmReq.toString())

                val dfReq = JsonObject().apply {
                    addProperty("action", if (item.isDir) "deletefolder" else "deletefile")
                    addProperty("path", item.path)
                    addProperty("reqid", reqIdStr)
                    addProperty("id", reqIdStr)
                    if (item.isDir) {
                        addProperty("recursive", true)
                    }
                }
                webSocket?.send(dfReq.toString())

                savedFileContents.remove(item.path)
                _statusMessage.value = "Eliminación solicitada: ${item.name}"

                delay(600)
                if (isConnected()) {
                    listDirectory(_currentPath.value)
                }
            } catch (e: Exception) {
                Log.e("MeshFilesDelete", "No se pudo solicitar el borrado de ${item.path}", e)
                _statusMessage.value = "Error al eliminar: ${e.localizedMessage}"
            }
        }
    }

    fun renameItem(item: FileItem, newName: String) {
        val parentDir = if (item.path.contains('/')) {
            item.path.substringBeforeLast('/').ifEmpty { "/" }
        } else if (item.path.contains('\\')) {
            item.path.substringBeforeLast('\\')
        } else {
            _currentPath.value
        }
        val cleanParent = if (parentDir == "/") "/" else parentDir.trimEnd('/')
        val newPath = if (cleanParent == "/") "/$newName" else "$cleanParent/$newName"

        scope.launch(Dispatchers.IO) {
            try {
                validateRemoteEntryName(item.name)
                validateRemoteEntryName(newName)

                if (!isConnected()) {
                    connect()
                    var waitMs = 0
                    while (!_connectionState.value && waitMs < 5000) {
                        delay(100)
                        waitMs += 100
                    }
                }

                check(_connectionState.value && webSocket != null) { "El túnel de archivos no está conectado" }

                val reqIdStr = Random.nextInt(100000, 999999).toString()

                val renameReq = JsonObject().apply {
                    addProperty("action", "rename")
                    addProperty("reqid", reqIdStr)
                    addProperty("id", reqIdStr)
                    addProperty("path", cleanParent.ifEmpty { "/" })
                    addProperty("oldfile", item.path)
                    addProperty("newfile", newPath)
                    addProperty("oldpath", item.path)
                    addProperty("newpath", newPath)
                    addProperty("oldname", item.name)
                    addProperty("newname", newName)
                }
                webSocket?.send(renameReq.toString())

                val savedContent = savedFileContents.remove(item.path)
                if (savedContent != null) {
                    savedFileContents[newPath] = savedContent
                }

                _statusMessage.value = "Renombrado a: $newName"
                delay(600)
                if (isConnected()) {
                    listDirectory(_currentPath.value)
                }
            } catch (e: Exception) {
                Log.e("MeshFilesRename", "Error al renombrar ${item.path} a $newName", e)
                _statusMessage.value = "Error al renombrar: ${e.localizedMessage}"
            }
        }
    }

    fun clearStatus() {
        _statusMessage.value = null
    }

    fun disconnect(resetPath: Boolean = false) {
        isUserDisconnect = true
        copyingSourcePath = null
        copyingDestPath = null
        try {
            copyingFileOutputStream?.close()
        } catch (_: Exception) {}
        copyingFileOutputStream = null
        copyingTempFile?.delete()
        copyingTempFile = null
        readingFilePath = null
        readingBuffer = null
        _fileReadingProgress.value = null
        _activeTransfers.value = emptyList()

        webSocket?.close(1000, "Disconnecting files")
        webSocket = null
        _connectionState.value = false
        _fileList.value = emptyList()
        if (resetPath) {
            _currentPath.value = "/"
        }
        _clipboardItem.value = null
        clipboardNodeId = null
        _fileContent.value = null
        onStateChanged?.invoke()
    }

    fun onNetworkChanged() {
        if (!isUserDisconnect && !isConnected()) {
            connect()
        }
    }

    private fun formatSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (log10(size.toDouble()) / log10(1024.0)).toInt()
        return String.format(Locale.getDefault(), "%.1f %s", size / 1024.0.pow(digitGroups.toDouble()), units[digitGroups])
    }
}

class FileRelayClient {

    companion object {
        var activeInstance: FileRelayClient? = null
    }

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val sessions = ConcurrentHashMap<String, FileSession>()

    private val _activeNodes = MutableStateFlow<Set<String>>(emptySet())
    val activeNodesFlow: StateFlow<Set<String>> = _activeNodes.asStateFlow()

    private val _sessionsStatus = MutableStateFlow<Map<String, SessionStatus>>(emptyMap())
    val fileSessionsStatusFlow: StateFlow<Map<String, SessionStatus>> = _sessionsStatus.asStateFlow()

    init {
        activeInstance = this
    }

    private fun cleanKey(nodeId: String): String {
        return if (nodeId.startsWith("node//")) {
            nodeId.substring(6)
        } else if (nodeId.startsWith("node/")) {
            nodeId.substring(5)
        } else {
            nodeId
        }.trimStart('/').trim()
    }

    fun updateSessionsStatus() {
        val map = sessions.mapKeys { cleanKey(it.key) }.mapValues { entry ->
            val sess = entry.value
            when {
                sess.isConnected() -> SessionStatus.CONNECTED
                sess.fileList.value.isNotEmpty() && !sess.isUserDisconnect -> SessionStatus.DISCONNECTED_WITH_BUFFER
                else -> SessionStatus.NONE
            }
        }
        _sessionsStatus.value = map
        updateActiveNodes()
    }

    fun updateActiveNodes() {
        val active = sessions.entries
            .filter { it.value.isConnected() }
            .map { cleanKey(it.key) }
            .toSet()
        _activeNodes.value = active
    }

    @Synchronized
    fun getSession(meshClient: MeshCentralClient, nodeId: String): FileSession {
        val key = cleanKey(nodeId)
        return sessions.getOrPut(key) {
            FileSession(nodeId = key, meshClient = meshClient, scope = scope).also { sess ->
                sess.onStateChanged = { updateSessionsStatus() }
            }
        }
    }

    fun isConnectedToNode(nodeId: String): Boolean {
        val session = sessions[cleanKey(nodeId)]
        return session?.isConnected() ?: false
    }

    fun getConnectionState(meshClient: MeshCentralClient, nodeId: String): StateFlow<Boolean> {
        return getSession(meshClient, nodeId).connectionState
    }

    fun getCurrentPath(meshClient: MeshCentralClient, nodeId: String): StateFlow<String> {
        return getSession(meshClient, nodeId).currentPath
    }

    fun getFileList(meshClient: MeshCentralClient, nodeId: String): StateFlow<List<FileItem>> {
        return getSession(meshClient, nodeId).fileList
    }

    fun getStatusMessage(meshClient: MeshCentralClient, nodeId: String): StateFlow<String?> {
        return getSession(meshClient, nodeId).statusMessage
    }

    fun getClipboardItem(meshClient: MeshCentralClient, nodeId: String): StateFlow<FileItem?> {
        return getSession(meshClient, nodeId).clipboardItem
    }

    fun getIsCutMode(meshClient: MeshCentralClient, nodeId: String): StateFlow<Boolean> {
        return getSession(meshClient, nodeId).isCutMode
    }

    fun getFileContent(meshClient: MeshCentralClient, nodeId: String): StateFlow<Pair<String, String>?> {
        return getSession(meshClient, nodeId).fileContent
    }

    fun getFileReadingProgress(meshClient: MeshCentralClient, nodeId: String): StateFlow<Float?> {
        return getSession(meshClient, nodeId).fileReadingProgress
    }

    fun getActiveTransfers(meshClient: MeshCentralClient, nodeId: String): StateFlow<List<TransferProgress>> {
        return getSession(meshClient, nodeId).activeTransfers
    }

    fun getActiveTransfer(meshClient: MeshCentralClient, nodeId: String): StateFlow<TransferProgress?> {
        return getSession(meshClient, nodeId).activeTransfer
    }

    fun connect(meshClient: MeshCentralClient, nodeId: String) {
        val session = getSession(meshClient, nodeId)
        session.connect()
        updateSessionsStatus()
    }

    fun disconnectSession(nodeId: String) {
        val key = cleanKey(nodeId)
        val session = sessions.remove(key)
        session?.disconnect(resetPath = true)
        updateSessionsStatus()
    }

    fun disconnectAll() {
        for (session in sessions.values) {
            session.disconnect(resetPath = true)
        }
        sessions.clear()
        updateSessionsStatus()
    }

    fun disconnect() {
        disconnectAll()
    }

    fun hasActiveSessions(): Boolean {
        return sessions.values.any { it.isConnected() }
    }

    fun getSessionStatus(nodeId: String): SessionStatus {
        val session = sessions[cleanKey(nodeId)] ?: return SessionStatus.NONE
        return when {
            session.isConnected() -> SessionStatus.CONNECTED
            session.fileList.value.isNotEmpty() && !session.isUserDisconnect -> SessionStatus.DISCONNECTED_WITH_BUFFER
            else -> SessionStatus.NONE
        }
    }

    fun hasDisconnectedWithBuffer(): Boolean {
        return sessions.values.any { !it.isConnected() && it.fileList.value.isNotEmpty() && !it.isUserDisconnect }
    }

    fun hasActiveOrBufferedSession(): Boolean {
        return sessions.values.any { (it.isConnected() || it.fileList.value.isNotEmpty()) && !it.isUserDisconnect }
    }

    fun getSessionCount(): Int {
        return sessions.values.count { (it.isConnected() || it.fileList.value.isNotEmpty()) && !it.isUserDisconnect }
    }

    fun onNetworkChanged() {
        for (session in sessions.values) {
            if (!session.isUserDisconnect && !session.isConnected()) {
                session.onNetworkChanged()
            }
        }
    }

    fun cancelTransfer(filePath: String) {
        for (session in sessions.values) {
            session.cancelTransfer(filePath)
        }
    }

    fun cancelDownload() {
        for (session in sessions.values) {
            session.cancelDownload()
        }
    }

    fun listDirectory(nodeId: String, path: String) {
        sessions[cleanKey(nodeId)]?.listDirectory(path)
    }

    fun createFolder(nodeId: String, folderName: String) {
        sessions[cleanKey(nodeId)]?.createFolder(folderName)
    }

    fun createFile(nodeId: String, fileName: String, content: String = "") {
        sessions[cleanKey(nodeId)]?.createFile(fileName, content)
    }

    fun uploadRawFile(nodeId: String, fileName: String, bytes: ByteArray, context: Context? = null) {
        sessions[cleanKey(nodeId)]?.uploadRawFile(fileName, bytes, context)
    }

    fun downloadFileToDownloads(context: Context, meshClient: MeshCentralClient, nodeId: String, item: FileItem) {
        val session = getSession(meshClient, nodeId)
        session.downloadFileToDownloads(context, item)
    }

    fun readFileContent(meshClient: MeshCentralClient, nodeId: String, item: FileItem) {
        val session = getSession(meshClient, nodeId)
        session.readFileContent(item)
    }

    fun cancelFileReading(nodeId: String) {
        sessions[cleanKey(nodeId)]?.cancelFileReading()
    }

    fun saveFileContent(nodeId: String, filePath: String, newContent: String) {
        sessions[cleanKey(nodeId)]?.saveFileContent(filePath, newContent)
    }

    fun clearFileContent(nodeId: String) {
        sessions[cleanKey(nodeId)]?.clearFileContent()
    }

    fun copyItem(nodeId: String, item: FileItem) {
        sessions[cleanKey(nodeId)]?.copyItem(item)
    }

    fun cutItem(nodeId: String, item: FileItem) {
        sessions[cleanKey(nodeId)]?.cutItem(item)
    }

    fun clearClipboard(nodeId: String) {
        sessions[cleanKey(nodeId)]?.clearClipboard()
    }

    fun cancelCopyMove(filePath: String) {
        for (session in sessions.values) {
            session.cancelCopyMove(filePath)
        }
    }

    fun pasteItem(nodeId: String, customTargetName: String? = null, context: Context? = null) {
        sessions[cleanKey(nodeId)]?.pasteItem(customTargetName, context)
    }

    fun compressZip(nodeId: String, item: FileItem, customZipName: String? = null) {
        sessions[cleanKey(nodeId)]?.compressZip(item, customZipName)
    }

    fun decompressZip(nodeId: String, item: FileItem) {
        sessions[cleanKey(nodeId)]?.decompressZip(item)
    }

    fun openOnRemoteDevice(nodeId: String, item: FileItem) {
        sessions[cleanKey(nodeId)]?.openOnRemoteDevice(item)
    }

    fun deleteItem(meshClient: MeshCentralClient, nodeId: String, item: FileItem) {
        val session = getSession(meshClient, nodeId)
        session.deleteItem(item)
    }

    fun renameItem(meshClient: MeshCentralClient, nodeId: String, item: FileItem, newName: String) {
        val session = getSession(meshClient, nodeId)
        session.renameItem(item, newName)
    }

    fun clearStatus(nodeId: String) {
        sessions[cleanKey(nodeId)]?.clearStatus()
    }
}

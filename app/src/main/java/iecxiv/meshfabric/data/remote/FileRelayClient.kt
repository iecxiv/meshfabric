package iecxiv.meshfabric.data.remote

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.google.gson.JsonObject
import iecxiv.meshfabric.data.model.FileItem
import iecxiv.meshfabric.data.model.TransferProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow
import kotlin.random.Random

import android.content.ContentValues
import android.provider.MediaStore
import java.io.OutputStream
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import java.util.concurrent.ConcurrentHashMap

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

class FileRelayClient {

    companion object {
        var activeInstance: FileRelayClient? = null
    }

    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var webSocket: WebSocket? = null

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

    private val _fileContent = MutableStateFlow<Pair<String, String>?>(null) // Pair(path, text)
    val fileContent: StateFlow<Pair<String, String>?> = _fileContent.asStateFlow()

    private val _fileReadingProgress = MutableStateFlow<Float?>(null) // null = not reading, 0.0f..1.0f = progress
    val fileReadingProgress: StateFlow<Float?> = _fileReadingProgress.asStateFlow()

    private val activeDownloads = ConcurrentHashMap<String, ActiveDownloadSession>()
    private val activeUploads = ConcurrentHashMap<String, ActiveUploadSession>()
    private val savedFileContents = ConcurrentHashMap<String, String>()
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
    private var copyingBuffer: ByteArrayOutputStream? = null

    // Internal File Reading State
    private var readingFilePath: String? = null
    private var readingTotalSize: Long = 0L
    private var readingReceivedBytes: Long = 0L
    private var readingBuffer: ByteArrayOutputStream? = null
    private var currentReadingReqId: Int = 0

    // Download State
    private var downloadingPath: String? = null
    private var downloadingName: String? = null
    private var downloadingTotalSize: Long = 0L
    private var downloadingReceivedBytes: Long = 0L
    private var downloadOutputStream: FileOutputStream? = null
    private var downloadContext: Context? = null
    private var lastProgressNotificationTime = 0L

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

    var activeNodeId: String = ""
        private set

    private val _activeNodes = MutableStateFlow<Set<String>>(emptySet())
    val activeNodesFlow: StateFlow<Set<String>> = _activeNodes.asStateFlow()

    private fun cleanKey(nodeId: String): String {
        return if (nodeId.startsWith("node//")) {
            nodeId.substring(6)
        } else if (nodeId.startsWith("node/")) {
            nodeId.substring(5)
        } else {
            nodeId
        }.trimStart('/').trim()
    }

    fun updateActiveNodes() {
        _activeNodes.value = if (_connectionState.value && activeNodeId.isNotEmpty()) {
            setOf(cleanKey(activeNodeId))
        } else {
            emptySet()
        }
    }

    private var activeMeshClient: MeshCentralClient? = null

    suspend fun ensureControlSocketConnected(meshClient: MeshCentralClient): Boolean {
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
        val client = activeMeshClient ?: return false
        ensureControlSocketConnected(client)

        if (_connectionState.value && webSocket != null) return true
        val nodeId = activeNodeId.ifEmpty { return false }
        Log.d("MeshFiles", "Auto-reconnecting file relay for node '$nodeId'...")
        connect(client, nodeId)
        var waitMs = 0
        while (!_connectionState.value && waitMs < 4000) {
            delay(100)
            waitMs += 100
        }
        return _connectionState.value
    }

    fun isConnectedToNode(nodeId: String): Boolean {
        val cleanCurrent = cleanKey(activeNodeId)
        val cleanArg = cleanKey(nodeId)
        return cleanCurrent == cleanArg && _connectionState.value && webSocket != null
    }

    fun connect(
        meshClient: MeshCentralClient,
        nodeId: String
    ) {
        activeInstance = this
        activeMeshClient = meshClient

        val cleanNodeId = cleanKey(nodeId)
        val fullNodeId = "node//$cleanNodeId"

        if (activeNodeId.isNotEmpty() && activeNodeId != cleanNodeId) {
            _currentPath.value = "/"
        }

        if (isConnectedToNode(nodeId)) {
            Log.d("MeshFiles", "Already connected to node: $nodeId. Reusing files session at path: ${_currentPath.value}")
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

        activeNodeId = cleanNodeId

        val wsUrl = OkHttpHelper.toWebSocketUrl(
            serverUrl,
            "/meshrelay.ashx?p=5&nodeid=$encodedNodeId&id=$tunnelId&rauth=$encodedRelayToken&auth=$encodedRelayToken"
        )

        Log.d("MeshFiles", "Connecting files WS to: $wsUrl")

        val requestBuilder = Request.Builder().url(wsUrl)
        if (cookie.isNotBlank()) {
            requestBuilder.addHeader("Cookie", cookie)
        }

        disconnect(resetPath = false)

        webSocket = client.newWebSocket(requestBuilder.build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("MeshFiles", "WebSocket onOpen. Ensuring control socket is ready...")
                scope.launch(Dispatchers.IO) {
                    ensureControlSocketConnected(meshClient)
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

                // Raw binary file bytes
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

                        Log.d("MeshFilesDownload", "Chunk written: ${byteArray.size} B (${activeDl.receivedBytes} / ${activeDl.totalSize})")

                        if (activeDl.totalSize > 0 && activeDl.receivedBytes >= activeDl.totalSize) {
                            finishDownload(activeDl.filePath)
                        }
                        return
                    } catch (e: Exception) {
                        Log.e("MeshFilesDownload", "Error writing download chunk: ${e.localizedMessage}", e)
                    }
                } else if (copyingSourcePath != null) {
                    try {
                        copyingBuffer?.write(byteArray)
                        copyingReceivedBytes += byteArray.size
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
                updateActiveNodes()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _connectionState.value = false
                updateActiveNodes()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("MeshFiles", "onFailure: ${t.localizedMessage}", t)
                _connectionState.value = false
                updateActiveNodes()
            }
        })
    }

    private fun handleIncomingMessage(webSocket: WebSocket, text: String) {
        Log.d("MeshFilesRecv", "Files WS Recv (${text.length} chars): $text")
        if (text == "c" || text == "cr") {
            _connectionState.value = true
            updateActiveNodes()
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
                Log.d("MeshFilesDownload", "Received downloadStart: size=$size, reqid=$reqid")
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
                Log.d("MeshFilesDownload", "Sending download pull request: $pullReq")
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
                    copyingBuffer?.write(chunkBytes)
                    copyingReceivedBytes += chunkBytes.size
                    if (copyingTotalSize > 0 && copyingReceivedBytes >= copyingTotalSize) {
                        finishCopyingFile()
                    }
                    return
                }
                if (readingFilePath != null) {
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
                    return
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
                Log.d("MeshFilesDownload", "Parsed metadata size=$size")
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
                Log.d("MeshFiles", "Successfully parsed ${sortedItems.size} items for path '$path'")
            } else if (action == "download" || action == "read" || action == "readfile") {
                val path = json.get("path")?.asString ?: ""
                val content = json.get("data")?.asString
                    ?: json.get("content")?.asString
                    ?: json.get("text")?.asString

                if (path.isNotEmpty() && content != null) {
                    Log.d("MeshFilesRead", "Received text file content for path '$path', len=${content.length}")
                    readingFilePath = null
                    readingBuffer = null
                    _fileReadingProgress.value = null
                    _fileContent.value = Pair(path, content)
                }
            } else if (action == "rm" || action == "rename" || action == "mkdir") {
                Log.d("MeshFilesAction", "Received action response for $action: $text")
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
        meshClient: MeshCentralClient,
        nodeId: String,
        item: FileItem
    ) {
        activeInstance = this

        val cleanNodeId = cleanKey(nodeId)
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
            // 1. Try Web Dashboard HTTP Download URL format: /devicefile.ashx?c=...&m=...&n=...&f=...
            try {
                val outputStream = createDownloadOutputStream(context, item.name)
                val client = OkHttpHelper.createClient(meshClient.ignoreSsl)

                val encodedC = try { URLEncoder.encode(cookieToken, "UTF-8") } catch (_: Exception) { cookieToken }
                val encodedM = try { URLEncoder.encode(meshId, "UTF-8") } catch (_: Exception) { meshId }
                val encodedN = try { URLEncoder.encode(cleanNodeId, "UTF-8") } catch (_: Exception) { cleanNodeId }

                val cleanPath = item.path.removePrefix("/")
                val encodedF1 = try { URLEncoder.encode(cleanPath, "UTF-8") } catch (_: Exception) { cleanPath }
                val encodedF2 = try { URLEncoder.encode(item.path, "UTF-8") } catch (_: Exception) { item.path }

                // Attempt 1: Web Dashboard URL format (relative path f)
                var downloadUrl = "$serverUrl/devicefile.ashx?c=$encodedC&m=$encodedM&n=$encodedN&f=$encodedF1"
                Log.d("MeshFilesDownload", "HTTP Download Attempt 1 (Web URL format): $downloadUrl")

                var reqBuilder = Request.Builder().url(downloadUrl).get()
                if (sessionCookie.isNotBlank()) reqBuilder.addHeader("Cookie", sessionCookie)

                var response = client.newCall(reqBuilder.build()).execute()
                Log.d("MeshFilesDownload", "HTTP Response 1: ${response.code} ${response.message}")

                // Attempt 2: Web Dashboard URL format (absolute path f)
                if (!response.isSuccessful) {
                    downloadUrl = "$serverUrl/devicefile.ashx?c=$encodedC&m=$encodedM&n=$encodedN&f=$encodedF2"
                    Log.d("MeshFilesDownload", "HTTP Download Attempt 2 (absolute path): $downloadUrl")

                    reqBuilder = Request.Builder().url(downloadUrl).get()
                    if (sessionCookie.isNotBlank()) reqBuilder.addHeader("Cookie", sessionCookie)

                    response = client.newCall(reqBuilder.build()).execute()
                    Log.d("MeshFilesDownload", "HTTP Response 2: ${response.code} ${response.message}")
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
                    Log.d("MeshFilesDownload", "HTTP Download completed successfully: ${item.name}, $receivedBytes bytes")
                    return@launch
                } else {
                    val bodyText = try { response.peekBody(1024L).string() } catch (e: Exception) { "" }
                    Log.w("MeshFilesDownload", "HTTP Download failed with code ${response.code}, body: $bodyText")
                }
            } catch (e: Exception) {
                Log.e("MeshFilesDownload", "Error in HTTP download: ${e.localizedMessage}", e)
            }

            // 2. Fallback to WS readfile / download
            try {
                if (!isConnectedToNode(nodeId)) {
                    Log.d("MeshFilesDownload", "Not connected to node $nodeId, connecting first...")
                    connect(meshClient, nodeId)
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

                Log.d("MeshFilesDownload", "Sending readfile request over WS: $readReq")
                val sent = webSocket?.send(readReq.toString()) ?: false
                Log.d("MeshFilesDownload", "WS readfile send status: $sent")
            } catch (e: Exception) {
                Log.e("MeshFilesDownload", "Error in WS fallback: ${e.localizedMessage}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error al descargar: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun cancelTransfer(filePath: String) {
        if (activeDownloads.containsKey(filePath)) {
            cancelDownload(filePath)
        } else if (activeUploads.containsKey(filePath)) {
            cancelUpload(filePath)
        } else {
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

    private fun startFileCopyStream(srcPath: String, destPath: String, size: Long) {
        if (size == 0L) {
            uploadRawFileToPath(destPath, ByteArray(0))
            _statusMessage.value = "Copiado exitosamente"
            _clipboardItem.value = null
            return
        }

        copyingSourcePath = srcPath
        copyingDestPath = destPath
        copyingTotalSize = size
        copyingReceivedBytes = 0L
        copyingBuffer = ByteArrayOutputStream()

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

        scope.launch {
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
            val bytes = copyingBuffer?.toByteArray() ?: ByteArray(0)

            copyingSourcePath = null
            copyingDestPath = null
            copyingBuffer = null
            copyingTotalSize = 0L
            copyingReceivedBytes = 0L
            _clipboardItem.value = null

            Log.d("MeshFilesCopy", "Uploading ${bytes.size} copied bytes to target path: '$destPath'")
            uploadRawFileToPath(destPath, bytes)
            _statusMessage.value = "Copiado exitosamente"
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
        Log.d("MeshFilesSend", "Sending ls request for path: '$targetPath'")
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

    fun readFileContent(meshClient: MeshCentralClient, nodeId: String, item: FileItem) {
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

        val cleanNodeId = cleanKey(nodeId)
        val meshId = meshClient.getNodeMeshId(nodeId)
        val serverUrl = meshClient.serverUrl.trimEnd('/')

        val cookieToken = meshClient.userAuthCookie.ifEmpty {
            meshClient.relayAuthCookie.ifEmpty {
                meshClient.extractRawToken(meshClient.authCookie)
            }
        }
        val sessionCookie = meshClient.authCookie.trim()

        scope.launch(Dispatchers.IO) {
            // 1. Try Web Dashboard HTTP Download URL format: /devicefile.ashx?c=...&m=...&n=...&f=...
            try {
                val client = OkHttpHelper.createClient(meshClient.ignoreSsl)
                val encodedC = try { URLEncoder.encode(cookieToken, "UTF-8") } catch (_: Exception) { cookieToken }
                val encodedM = try { URLEncoder.encode(meshId, "UTF-8") } catch (_: Exception) { meshId }
                val encodedN = try { URLEncoder.encode(cleanNodeId, "UTF-8") } catch (_: Exception) { cleanNodeId }

                val cleanPath = item.path.removePrefix("/")
                val encodedF1 = try { URLEncoder.encode(cleanPath, "UTF-8") } catch (_: Exception) { cleanPath }
                val encodedF2 = try { URLEncoder.encode(item.path, "UTF-8") } catch (_: Exception) { item.path }

                // Attempt 1: Web Dashboard URL format (relative path f)
                var downloadUrl = "$serverUrl/devicefile.ashx?c=$encodedC&m=$encodedM&n=$encodedN&f=$encodedF1"
                Log.d("MeshFilesRead", "HTTP Read Attempt 1 (devicefile.ashx relative): $downloadUrl")

                var reqBuilder = Request.Builder().url(downloadUrl).get()
                if (sessionCookie.isNotBlank()) reqBuilder.addHeader("Cookie", sessionCookie)

                var response = client.newCall(reqBuilder.build()).execute()
                Log.d("MeshFilesRead", "HTTP Read Response 1: ${response.code} ${response.message}")

                // Attempt 2: Web Dashboard URL format (absolute path f)
                if (!response.isSuccessful) {
                    downloadUrl = "$serverUrl/devicefile.ashx?c=$encodedC&m=$encodedM&n=$encodedN&f=$encodedF2"
                    Log.d("MeshFilesRead", "HTTP Read Attempt 2 (devicefile.ashx absolute): $downloadUrl")

                    reqBuilder = Request.Builder().url(downloadUrl).get()
                    if (sessionCookie.isNotBlank()) reqBuilder.addHeader("Cookie", sessionCookie)

                    response = client.newCall(reqBuilder.build()).execute()
                    Log.d("MeshFilesRead", "HTTP Read Response 2: ${response.code} ${response.message}")
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
                    Log.d("MeshFilesRead", "HTTP Read completed successfully: ${item.path} (${bytes.size} bytes)")
                    return@launch
                } else {
                    val bodyText = try { response.peekBody(1024L).string() } catch (e: Exception) { "" }
                    Log.w("MeshFilesRead", "HTTP Read failed with code ${response.code}, body: $bodyText")
                }
            } catch (e: Exception) {
                Log.e("MeshFilesRead", "Error in HTTP read: ${e.localizedMessage}", e)
            }

            // 2. WS readfile fallback
            try {
                if (!isConnectedToNode(nodeId)) {
                    Log.d("MeshFilesRead", "Not connected to node $nodeId, connecting first...")
                    connect(meshClient, nodeId)
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
                Log.d("MeshFilesRead", "Sending WS readfile request: $readReq")
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

        require(clean.isNotEmpty()) {
            "No se permite operar sobre la raíz"
        }

        require(!Regex("^[A-Za-z]:$").matches(clean)) {
            "No se permite operar sobre la raíz de una unidad"
        }

        val separatorIndex = maxOf(
            clean.lastIndexOf('/'),
            clean.lastIndexOf('\\')
        )

        require(separatorIndex >= 0) {
            "Se esperaba una ruta remota absoluta: $path"
        }

        if (separatorIndex == 0) return "/"

        val parent = clean.substring(0, separatorIndex)

        return if (Regex("^[A-Za-z]:$").matches(parent)) {
            "$parent/"
        } else {
            parent
        }
    }

    private fun validateRemoteEntryName(name: String) {
        require(name.isNotBlank()) {
            "El nombre está vacío"
        }

        require(name != "." && name != "..") {
            "Nombre no permitido"
        }

        require('/' !in name && '\\' !in name && '\u0000' !in name) {
            "Se esperaba un nombre, no una ruta"
        }
    }

    private fun sendFileOperation(request: JsonObject) {
        val socket = webSocket
            ?: throw IllegalStateException(
                "El túnel de archivos no está disponible"
            )

        check(_connectionState.value) {
            "El túnel de archivos no está conectado"
        }

        Log.d("MeshFilesOperation", "SEND: $request")

        check(socket.send(request.toString())) {
            "No se pudo encolar la operación en el WebSocket"
        }
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

        clipboardNodeId = cleanKey(activeNodeId)
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

        clipboardNodeId = cleanKey(activeNodeId)
        _clipboardItem.value = item
        _isCutMode.value = true

        _statusMessage.value = "Seleccionado para mover: ${item.name}"
    }

    fun clearClipboard() {
        clipboardNodeId = null
        _clipboardItem.value = null
        _isCutMode.value = false
    }

    fun pasteItem(customTargetName: String? = null) {
        val source = _clipboardItem.value ?: run {
            _statusMessage.value = "El portapapeles está vacío"
            return
        }
        val cut = _isCutMode.value

        val sourceNode = clipboardNodeId ?: run {
            _statusMessage.value = "Vuelve a seleccionar el elemento"
            return
        }

        val targetNode = cleanKey(activeNodeId)
        val destination = _currentPath.value

        scope.launch(Dispatchers.IO) {
            try {
                require(!source.isDir) {
                    "Las operaciones de copiar y cortar solo están permitidas para archivos"
                }

                check(sourceNode == targetNode) {
                    "Este portapapeles solo permite operaciones dentro del mismo equipo remoto"
                }

                if (!isConnectedToNode(targetNode)) {
                    val client = activeMeshClient
                    if (client != null) {
                        connect(client, targetNode)
                        var waitMs = 0
                        while (!_connectionState.value && waitMs < 5000) {
                            delay(100)
                            waitMs += 100
                        }
                    }
                }

                check(_connectionState.value && webSocket != null) {
                    "El túnel de archivos no está conectado"
                }

                validateRemoteEntryName(source.name)

                val targetName = customTargetName?.takeIf { it.isNotBlank() } ?: source.name
                validateRemoteEntryName(targetName)

                val sourceDirectory = remoteParent(source.path)
                val reqIdStr = Random.nextInt(100000, 999999).toString()

                val cleanDest = if (destination == "/") "/" else destination.trimEnd('/')
                val destPath = if (cleanDest == "/") "/$targetName" else "$cleanDest/$targetName"

                if (cut) {
                    // Move operation
                    // 1. Move protocol message
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

                    // 2. Fallback rename message for moving/renaming across/within dirs
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
                } else {
                    // File copy
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
                }

                delay(600)

                if (isConnectedToNode(targetNode) && _currentPath.value == destination) {
                    listDirectory(destination)
                }
            } catch (e: Exception) {
                Log.e("MeshFilesPaste", "Error al solicitar copiar/mover ${source.path}", e)
                _statusMessage.value = "No se pudo pegar: ${e.localizedMessage}"
            }
        }
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
        Log.d("MeshFilesZip", "Sending ZIP command: $zipReq")
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

    fun deleteItem(
        meshClient: MeshCentralClient,
        nodeId: String,
        item: FileItem
    ) {
        scope.launch(Dispatchers.IO) {
            try {
                validateRemoteEntryName(item.name)

                val parent = remoteParent(item.path)

                if (!isConnectedToNode(nodeId)) {
                    connect(meshClient, nodeId)
                    var waitMs = 0
                    while (!_connectionState.value && waitMs < 5000) {
                        delay(100)
                        waitMs += 100
                    }
                }

                check(_connectionState.value && webSocket != null) {
                    "El túnel de archivos no está conectado"
                }

                val reqIdStr = Random.nextInt(100000, 999999).toString()

                // Primary rm command with all compatible fields
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
                Log.d("MeshFilesDelete", "Sending RM: $rmReq")
                webSocket?.send(rmReq.toString())

                // Secondary fallback command (deletefolder / deletefile)
                val dfReq = JsonObject().apply {
                    addProperty("action", if (item.isDir) "deletefolder" else "deletefile")
                    addProperty("path", item.path)
                    addProperty("reqid", reqIdStr)
                    addProperty("id", reqIdStr)
                    if (item.isDir) {
                        addProperty("recursive", true)
                    }
                }
                Log.d("MeshFilesDelete", "Sending DF: $dfReq")
                webSocket?.send(dfReq.toString())

                savedFileContents.remove(item.path)

                _statusMessage.value = "Eliminación solicitada: ${item.name}"

                delay(600)

                if (isConnectedToNode(nodeId)) {
                    listDirectory(_currentPath.value)
                }
            } catch (e: Exception) {
                Log.e("MeshFilesDelete", "No se pudo solicitar el borrado de ${item.path}", e)
                _statusMessage.value = "Error al eliminar: ${e.localizedMessage}"
            }
        }
    }

    fun deleteItem(itemName: String) {
        val client = activeMeshClient ?: run {
            _statusMessage.value = "No hay una sesión de archivos activa"
            return
        }

        val item = _fileList.value.firstOrNull {
            it.name == itemName
        } ?: FileItem(
            name = itemName,
            isDir = false,
            size = 0L,
            modifiedTime = 0L,
            path = if (_currentPath.value == "/") "/$itemName" else "${_currentPath.value.trimEnd('/')}/$itemName"
        )

        deleteItem(client, activeNodeId, item)
    }

    fun renameItem(meshClient: MeshCentralClient, nodeId: String, item: FileItem, newName: String) {
        val parentDir = if (item.path.contains('/')) {
            item.path.substringBeforeLast('/').ifEmpty { "/" }
        } else if (item.path.contains('\\')) {
            item.path.substringBeforeLast('\\')
        } else {
            _currentPath.value
        }
        val cleanParent = if (parentDir == "/") "/" else parentDir.trimEnd('/')
        val newPath = if (cleanParent == "/") "/$newName" else "$cleanParent/$newName"
        sendRenameRequest(meshClient, nodeId, cleanParent.ifEmpty { "/" }, item.path, newPath, item.name, newName)
    }

    fun renameItem(oldName: String, newName: String) {
        val path = _currentPath.value.trimEnd('/')
        val oldPath = if (path.isEmpty()) "/$oldName" else "$path/$oldName"
        val newPath = if (path.isEmpty()) "/$newName" else "$path/$newName"
        sendRenameRequest(activeInstanceMeshClient ?: return, activeNodeId, if (path.isEmpty()) "/" else path, oldPath, newPath, oldName, newName)
    }

    private var activeInstanceMeshClient: MeshCentralClient? = null

    private fun sendRenameRequest(meshClient: MeshCentralClient, nodeId: String, parentDir: String, oldPath: String, newPath: String, oldName: String, newName: String) {
        val reqIdStr = Random.nextInt(100000, 999999).toString()
        Log.d("MeshFilesRename", "Renaming '$oldPath' -> '$newPath' in parent '$parentDir'")

        scope.launch(Dispatchers.IO) {
            if (!isConnectedToNode(nodeId)) {
                Log.d("MeshFilesRename", "Not connected to $nodeId, connecting first...")
                connect(meshClient, nodeId)
                var waitMs = 0
                while (!_connectionState.value && waitMs < 5000) {
                    delay(100)
                    waitMs += 100
                }
            }

            val req1 = JsonObject().apply {
                addProperty("action", "rename")
                addProperty("path", parentDir)
                addProperty("oldfile", oldPath)
                addProperty("newfile", newPath)
                addProperty("oldpath", oldPath)
                addProperty("newpath", newPath)
                addProperty("oldname", oldName)
                addProperty("newname", newName)
                addProperty("reqid", reqIdStr)
                addProperty("id", reqIdStr)
            }
            webSocket?.send(req1.toString())

            val req2 = JsonObject().apply {
                addProperty("action", "rename")
                addProperty("oldpath", oldPath)
                addProperty("newpath", newPath)
                addProperty("oldname", oldName)
                addProperty("newname", newName)
                addProperty("reqid", reqIdStr)
                addProperty("id", reqIdStr)
            }
            webSocket?.send(req2.toString())

            _statusMessage.value = "Renombrado a $newName"
            delay(500)
            listDirectory(_currentPath.value)
        }
    }

    fun clearStatus() {
        _statusMessage.value = null
    }

    fun disconnect(resetPath: Boolean = false) {
        if (downloadOutputStream != null) {
            try { downloadOutputStream?.close() } catch (e: Exception) {}
            downloadOutputStream = null
        }
        downloadingPath = null
        downloadingName = null
        copyingSourcePath = null
        copyingDestPath = null
        copyingBuffer = null
        readingFilePath = null
        readingBuffer = null
        _fileReadingProgress.value = null
        _activeTransfers.value = emptyList()

        webSocket?.close(1000, "Disconnecting files")
        webSocket = null
        _connectionState.value = false
        updateActiveNodes()
        _fileList.value = emptyList()
        if (resetPath) {
            _currentPath.value = "/"
        }
        _clipboardItem.value = null
        clipboardNodeId = null
        _fileContent.value = null
        if (activeInstance == this) {
            activeInstance = null
        }
    }

    private fun formatSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (log10(size.toDouble()) / log10(1024.0)).toInt()
        return String.format(Locale.getDefault(), "%.1f %s", size / 1024.0.pow(digitGroups.toDouble()), units[digitGroups])
    }
}
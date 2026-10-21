package iecxiv.meshfabric.ui.files

import android.content.Context
import androidx.lifecycle.ViewModel
import iecxiv.meshfabric.data.model.FileItem
import iecxiv.meshfabric.data.remote.FileRelayClient
import iecxiv.meshfabric.data.remote.MeshCentralClient

class FileManagerViewModel(
    val meshClient: MeshCentralClient,
    val fileClient: FileRelayClient
) : ViewModel() {

    val isConnected = fileClient.connectionState
    val currentPath = fileClient.currentPath
    val fileList = fileClient.fileList
    val statusMessage = fileClient.statusMessage
    val clipboardItem = fileClient.clipboardItem
    val isCutMode = fileClient.isCutMode
    val fileContent = fileClient.fileContent
    val fileReadingProgress = fileClient.fileReadingProgress
    val activeTransfer = fileClient.activeTransfer
    val activeTransfers = fileClient.activeTransfers

    fun startSession(nodeId: String) {
        fileClient.connect(
            meshClient = meshClient,
            nodeId = nodeId
        )
    }

    fun downloadFileToDownloads(context: Context, nodeId: String, item: FileItem) {
        fileClient.downloadFileToDownloads(context, meshClient, nodeId, item)
    }

    fun cancelTransfer(filePath: String) {
        fileClient.cancelTransfer(filePath)
    }

    fun openDirectory(path: String) {
        fileClient.listDirectory(path)
    }

    fun navigateUp() {
        val path = currentPath.value.trimEnd('/')
        if (path.isEmpty() || path == "/") {
            fileClient.listDirectory("/")
            return
        }
        val parentPath = if (path.contains('/')) {
            val parent = path.substringBeforeLast('/')
            if (parent.isEmpty()) "/" else parent
        } else if (path.contains('\\')) {
            path.substringBeforeLast('\\')
        } else {
            "/"
        }
        fileClient.listDirectory(parentPath)
    }

    fun createFolder(folderName: String) {
        fileClient.createFolder(folderName)
    }

    fun createFile(fileName: String, content: String = "") {
        fileClient.createFile(fileName, content)
    }

    fun uploadRawFile(context: Context, fileName: String, bytes: ByteArray) {
        fileClient.uploadRawFile(fileName, bytes, context)
    }

    fun readFileContent(nodeId: String, item: FileItem) {
        fileClient.readFileContent(meshClient, nodeId, item)
    }

    fun cancelFileReading() {
        fileClient.cancelFileReading()
    }

    fun saveFileContent(filePath: String, newContent: String) {
        fileClient.saveFileContent(filePath, newContent)
    }

    fun clearFileContent() {
        fileClient.clearFileContent()
    }

    fun copyItem(item: FileItem) {
        fileClient.copyItem(item)
    }

    fun cutItem(item: FileItem) {
        fileClient.cutItem(item)
    }

    fun clearClipboard() {
        fileClient.clearClipboard()
    }

    fun pasteItem(customTargetName: String? = null) {
        fileClient.pasteItem(customTargetName)
    }

    fun compressZip(item: FileItem, customZipName: String? = null) {
        fileClient.compressZip(item, customZipName)
    }

    fun decompressZip(item: FileItem) {
        fileClient.decompressZip(item)
    }

    fun openOnRemoteDevice(item: FileItem) {
        fileClient.openOnRemoteDevice(item)
    }

    fun deleteItem(nodeId: String, item: FileItem) {
        fileClient.deleteItem(meshClient, nodeId, item)
    }

    fun renameItem(nodeId: String, item: FileItem, newName: String) {
        fileClient.renameItem(meshClient, nodeId, item, newName)
    }

    fun refresh() {
        fileClient.listDirectory(currentPath.value)
    }

    fun stopSession() {
        fileClient.disconnect()
    }

    override fun onCleared() {
        super.onCleared()
        stopSession()
    }
}
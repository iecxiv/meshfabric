package iecxiv.meshfabric.ui.files

import android.content.Context
import androidx.lifecycle.ViewModel
import iecxiv.meshfabric.data.model.FileItem
import iecxiv.meshfabric.data.model.TransferProgress
import iecxiv.meshfabric.data.remote.FileRelayClient
import iecxiv.meshfabric.data.remote.MeshCentralClient
import kotlinx.coroutines.flow.StateFlow

class FileManagerViewModel(
    val meshClient: MeshCentralClient,
    val fileClient: FileRelayClient
) : ViewModel() {

    fun isConnected(nodeId: String): StateFlow<Boolean> = fileClient.getConnectionState(meshClient, nodeId)
    fun currentPath(nodeId: String): StateFlow<String> = fileClient.getCurrentPath(meshClient, nodeId)
    fun fileList(nodeId: String): StateFlow<List<FileItem>> = fileClient.getFileList(meshClient, nodeId)
    fun statusMessage(nodeId: String): StateFlow<String?> = fileClient.getStatusMessage(meshClient, nodeId)
    fun clipboardItem(nodeId: String): StateFlow<FileItem?> = fileClient.getClipboardItem(meshClient, nodeId)
    fun isCutMode(nodeId: String): StateFlow<Boolean> = fileClient.getIsCutMode(meshClient, nodeId)
    fun fileContent(nodeId: String): StateFlow<Pair<String, String>?> = fileClient.getFileContent(meshClient, nodeId)
    fun fileReadingProgress(nodeId: String): StateFlow<Float?> = fileClient.getFileReadingProgress(meshClient, nodeId)
    fun activeTransfers(nodeId: String): StateFlow<List<TransferProgress>> = fileClient.getActiveTransfers(meshClient, nodeId)
    fun activeTransfer(nodeId: String): StateFlow<TransferProgress?> = fileClient.getActiveTransfer(meshClient, nodeId)

    fun startSession(nodeId: String) {
        fileClient.connect(
            meshClient = meshClient,
            nodeId = nodeId
        )
    }

    fun downloadFileToDownloads(context: Context, nodeId: String, item: FileItem) {
        fileClient.downloadFileToDownloads(context, meshClient, nodeId, item)
    }

    fun cancelTransfer(nodeId: String, filePath: String) {
        fileClient.cancelTransfer(filePath)
    }

    fun openDirectory(nodeId: String, path: String) {
        fileClient.listDirectory(nodeId, path)
    }

    fun navigateUp(nodeId: String) {
        val path = currentPath(nodeId).value.trimEnd('/')
        if (path.isEmpty() || path == "/") {
            fileClient.listDirectory(nodeId, "/")
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
        fileClient.listDirectory(nodeId, parentPath)
    }

    fun createFolder(nodeId: String, folderName: String) {
        fileClient.createFolder(nodeId, folderName)
    }

    fun createFile(nodeId: String, fileName: String, content: String = "") {
        fileClient.createFile(nodeId, fileName, content)
    }

    fun uploadRawFile(nodeId: String, context: Context, fileName: String, bytes: ByteArray) {
        fileClient.uploadRawFile(nodeId, fileName, bytes, context)
    }

    fun readFileContent(nodeId: String, item: FileItem) {
        fileClient.readFileContent(meshClient, nodeId, item)
    }

    fun cancelFileReading(nodeId: String) {
        fileClient.cancelFileReading(nodeId)
    }

    fun saveFileContent(nodeId: String, filePath: String, newContent: String) {
        fileClient.saveFileContent(nodeId, filePath, newContent)
    }

    fun clearFileContent(nodeId: String) {
        fileClient.clearFileContent(nodeId)
    }

    fun copyItem(nodeId: String, item: FileItem) {
        fileClient.copyItem(nodeId, item)
    }

    fun cutItem(nodeId: String, item: FileItem) {
        fileClient.cutItem(nodeId, item)
    }

    fun clearClipboard(nodeId: String) {
        fileClient.clearClipboard(nodeId)
    }

    fun pasteItem(nodeId: String, customTargetName: String? = null, context: Context? = null) {
        fileClient.pasteItem(nodeId, customTargetName, context)
    }

    fun compressZip(nodeId: String, item: FileItem, customZipName: String? = null) {
        fileClient.compressZip(nodeId, item, customZipName)
    }

    fun decompressZip(nodeId: String, item: FileItem) {
        fileClient.decompressZip(nodeId, item)
    }

    fun openOnRemoteDevice(nodeId: String, item: FileItem) {
        fileClient.openOnRemoteDevice(nodeId, item)
    }

    fun deleteItem(nodeId: String, item: FileItem) {
        fileClient.deleteItem(meshClient, nodeId, item)
    }

    fun renameItem(nodeId: String, item: FileItem, newName: String) {
        fileClient.renameItem(meshClient, nodeId, item, newName)
    }

    fun refresh(nodeId: String) {
        fileClient.listDirectory(nodeId, currentPath(nodeId).value)
    }

    fun clearStatus(nodeId: String) {
        fileClient.clearStatus(nodeId)
    }

    fun stopSession(nodeId: String) {
        fileClient.disconnectSession(nodeId)
    }

    override fun onCleared() {
        super.onCleared()
        fileClient.disconnectAll()
    }
}

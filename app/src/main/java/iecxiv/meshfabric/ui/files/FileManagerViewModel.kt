package iecxiv.meshfabric.ui.files

import android.content.Context
import android.net.Uri
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

    internal fun drives(nodeId: String) = fileClient.getWindowsDrives(meshClient, nodeId)
    internal fun isWindowsFiles(nodeId: String) = fileClient.isWindowsFiles(meshClient, nodeId)
    private fun roots(nodeId: String) = drives(nodeId).value.map { it.path }
    private fun allowLocation(nodeId: String): Boolean {
        if (!isWindowsFiles(nodeId) || !iecxiv.meshfabric.data.remote.WindowsDriveSupport.selector(currentPath(nodeId).value)) return true
        fileClient.reportDriveStatus(meshClient, nodeId, iecxiv.meshfabric.MeshFabricApp.instance.getString(iecxiv.meshfabric.R.string.files_choose_drive))
        return false
    }
    private fun allowItem(nodeId: String, item: FileItem): Boolean {
        if (!isWindowsFiles(nodeId) || !iecxiv.meshfabric.data.remote.WindowsDriveSupport.protectedRoot(item.path, roots(nodeId))) return true
        fileClient.reportDriveStatus(meshClient, nodeId, iecxiv.meshfabric.MeshFabricApp.instance.getString(iecxiv.meshfabric.R.string.files_drive_navigation_only))
        return false
    }

    fun startSession(nodeId: String) {
        fileClient.connect(
            meshClient = meshClient,
            nodeId = nodeId
        )
    }

    fun downloadFileToDownloads(
        context: Context,
        nodeId: String,
        item: FileItem,
        customName: String? = null,
        overwrite: Boolean = false
    ) {
        if (!allowItem(nodeId, item)) return
        fileClient.downloadFileToDownloads(context, meshClient, nodeId, item, customName, overwrite)
    }

    fun cancelTransfer(nodeId: String, filePath: String) {
        fileClient.cancelTransfer(filePath)
    }

    fun openDirectory(nodeId: String, path: String) {
        fileClient.listDirectory(nodeId, path)
    }

    fun navigateUp(nodeId: String) {
        fileClient.listDirectory(nodeId, iecxiv.meshfabric.data.remote.WindowsDriveSupport.parent(currentPath(nodeId).value, isWindowsFiles(nodeId), roots(nodeId)))
    }

    fun createFolder(nodeId: String, folderName: String) {
        if (!allowLocation(nodeId)) return
        fileClient.createFolder(nodeId, folderName)
    }

    fun createFile(nodeId: String, fileName: String, content: String = "") {
        if (!allowLocation(nodeId)) return
        fileClient.createFile(nodeId, fileName, content)
    }

    fun uploadRawFile(nodeId: String, context: Context, fileName: String, bytes: ByteArray) {
        if (!allowLocation(nodeId)) return
        fileClient.uploadRawFile(nodeId, fileName, bytes, context)
    }

    fun uploadUri(nodeId: String, context: Context, uri: Uri, customFileName: String? = null) {
        if (!allowLocation(nodeId)) return
        fileClient.uploadUri(nodeId, uri, context, customFileName)
    }

    fun readFileContent(nodeId: String, item: FileItem) {
        if (!allowItem(nodeId, item)) return
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
        if (!allowItem(nodeId, item)) return
        fileClient.copyItem(nodeId, item)
    }

    fun cutItem(nodeId: String, item: FileItem) {
        if (!allowItem(nodeId, item)) return
        fileClient.cutItem(nodeId, item)
    }

    fun clearClipboard(nodeId: String) {
        fileClient.clearClipboard(nodeId)
    }

    fun pasteItem(nodeId: String, customTargetName: String? = null, context: Context? = null) {
        if (!allowLocation(nodeId)) return
        fileClient.pasteItem(nodeId, customTargetName, context)
    }

    fun compressZip(nodeId: String, item: FileItem, customZipName: String? = null) {
        if (!allowItem(nodeId, item)) return
        fileClient.compressZip(nodeId, item, customZipName)
    }

    fun decompressZip(nodeId: String, item: FileItem) {
        if (!allowItem(nodeId, item)) return
        fileClient.decompressZip(nodeId, item)
    }

    fun openOnRemoteDevice(nodeId: String, item: FileItem) {
        if (!allowItem(nodeId, item)) return
        fileClient.openOnRemoteDevice(nodeId, item)
    }

    fun deleteItem(nodeId: String, item: FileItem) {
        if (!allowItem(nodeId, item)) return
        fileClient.deleteItem(meshClient, nodeId, item)
    }

    fun renameItem(nodeId: String, item: FileItem, newName: String) {
        if (!allowItem(nodeId, item)) return
        fileClient.renameItem(meshClient, nodeId, item, newName)
    }

    fun refresh(nodeId: String) {
        fileClient.refresh(meshClient, nodeId)
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

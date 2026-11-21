package iecxiv.meshfabric
import android.app.Application
import android.os.Bundle
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import iecxiv.meshfabric.data.local.SessionManager
import iecxiv.meshfabric.data.remote.*
import iecxiv.meshfabric.ui.desktop.RemoteDesktopViewModel
import iecxiv.meshfabric.ui.devices.DeviceListViewModel
import iecxiv.meshfabric.ui.files.FileManagerViewModel
import iecxiv.meshfabric.ui.login.LoginViewModel
import iecxiv.meshfabric.ui.settings.SettingsViewModel
import iecxiv.meshfabric.ui.terminal.TerminalViewModel
internal class RemoteSessionRuntime(application: Application) : AndroidViewModel(application) {
    private val children = ViewModelStore()
    val sessionManager = SessionManager(application)
    val meshClient = MeshCentralClient()
    val desktopClient = DesktopRelayClient()
    val terminalClient = TerminalRelayClient().also {
        it.configureHistory(application, meshClient, sessionManager)
        it.initBuffersFromDisk(application, meshClient, sessionManager.isSaveTerminalBuffersEnabled())
    }
    val fileClient = FileRelayClient()
    private fun <T : ViewModel> owned(type: Class<T>, factory: () -> T): T =
        ViewModelProvider(children, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <V : ViewModel> create(modelClass: Class<V>): V = factory() as V
        })[type]
    val loginViewModel = owned(LoginViewModel::class.java) { LoginViewModel(application, meshClient, sessionManager) }
    val deviceListViewModel = owned(DeviceListViewModel::class.java) { DeviceListViewModel(meshClient, desktopClient, terminalClient, fileClient, sessionManager) }
    val desktopViewModel = owned(RemoteDesktopViewModel::class.java) { RemoteDesktopViewModel(meshClient, desktopClient, sessionManager) }
    val terminalViewModel = owned(TerminalViewModel::class.java) { TerminalViewModel(meshClient, terminalClient) }
    val fileManagerViewModel = owned(FileManagerViewModel::class.java) { FileManagerViewModel(meshClient, fileClient) }
    val settingsViewModel = owned(SettingsViewModel::class.java) { SettingsViewModel(sessionManager) }
    var navigationState: Bundle? = null
        private set
    fun rememberNavigation(state: Bundle?) { if (state != null) navigationState = Bundle(state) }
    private var appInForeground = true
    private var closingSessions = false
    private val networkCallback = OwnedSessionCallback(
        { MeshFabricApp.onNetworkChangedListener }, { MeshFabricApp.onNetworkChangedListener = it }
    ) {
        if (!closingSessions) {
            meshClient.onNetworkChanged()
            terminalClient.onNetworkChanged()
            desktopClient.onNetworkChanged()
            fileClient.onNetworkChanged()
            updateBackgroundSessionService()
        }
    }
    private val taskCallback = OwnedSessionCallback(
        { iecxiv.meshfabric.service.KeepAliveService.onTaskRemovedListener },
        { iecxiv.meshfabric.service.KeepAliveService.onTaskRemovedListener = it }
    ) { onTaskRemoved() }
    private val chatCallback = OwnedSessionCallback(
        { iecxiv.meshfabric.ui.devices.DeviceChatKeepAlive.onChanged },
        { iecxiv.meshfabric.ui.devices.DeviceChatKeepAlive.onChanged = it }
    ) { updateBackgroundSessionService() }
    private fun onTaskRemoved() { closeSessions(chatCallback.ownsSlot) }
    fun onForeground() {
        appInForeground = true
        if (!closingSessions && taskCallback.ownsSlot)
            iecxiv.meshfabric.service.KeepAliveService.stopService(getApplication())
    }
    fun onBackground() {
        appInForeground = false
        if (!closingSessions) {
            terminalClient.checkpointHistory()
            updateBackgroundSessionService()
        }
    }
    fun resumeControlConnection() {
        if (closingSessions || !networkCallback.ownsSlot) return
        val session = sessionManager.getSession()
        if (session != null && session.serverUrl.isNotBlank() && session.authCookie.isNotBlank() && !meshClient.connectionState.value) {
            meshClient.connectControlSocket(rawServerUrl=session.serverUrl, cookie=session.authCookie, ignoreSslCert=session.ignoreSsl)
        }
    }
    private fun updateBackgroundSessionService() {
        if (appInForeground || closingSessions || !taskCallback.ownsSlot) return
        val chatCount = iecxiv.meshfabric.ui.devices.DeviceChatKeepAlive.activeCount()
        val total = terminalClient.getSessionCount() + desktopClient.getSessionCount() + fileClient.getSessionCount() + chatCount
        val connected = terminalClient.hasActiveSessions() || desktopClient.hasActiveSessions() || fileClient.hasActiveSessions() || chatCount > 0
        val disconnected = terminalClient.hasDisconnectedWithBuffer() || desktopClient.hasDisconnectedWithBuffer() || fileClient.hasDisconnectedWithBuffer()
        if (total > 0) iecxiv.meshfabric.service.KeepAliveService.startService(getApplication(), connected, disconnected, desktopClient.hasActiveSessions() && chatCount == 0)
        else iecxiv.meshfabric.service.KeepAliveService.stopService(getApplication())
    }
    private fun closeSessions(closeChats: Boolean) {
        if (closingSessions) return
        closingSessions = true
        if (closeChats) iecxiv.meshfabric.ui.devices.DeviceChatKeepAlive.closeAll()
        terminalViewModel.cancelPendingReconnections()
        meshClient.disconnect()
        terminalClient.saveAllBuffersAndClose(getApplication(), sessionManager.isSaveTerminalBuffersEnabled())
        desktopClient.disconnectAll()
        fileClient.disconnectAll()
    }
    override fun onCleared() {
        val ownsService = taskCallback.ownsSlot
        val ownsChats = chatCallback.ownsSlot
        networkCallback.release(); taskCallback.release(); chatCallback.release()
        try { closeSessions(ownsChats) } finally {
            children.clear()
            navigationState = null
            if (ownsService) iecxiv.meshfabric.service.KeepAliveService.stopService(getApplication())
            super.onCleared()
        }
    }
}

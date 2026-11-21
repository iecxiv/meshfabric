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
    override fun onCleared() {
        terminalClient.saveAllBuffersAndClose(getApplication(), sessionManager.isSaveTerminalBuffersEnabled())
        children.clear()
        meshClient.disconnect()
        desktopClient.disconnectAll()
        fileClient.disconnectAll()
        navigationState = null
        super.onCleared()
    }
}

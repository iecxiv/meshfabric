package iecxiv.meshfabric

import android.app.ActivityManager
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import iecxiv.meshfabric.data.local.SessionManager
import iecxiv.meshfabric.data.remote.DesktopRelayClient
import iecxiv.meshfabric.data.remote.FileRelayClient
import iecxiv.meshfabric.data.remote.MeshCentralClient
import iecxiv.meshfabric.data.remote.TerminalRelayClient
import iecxiv.meshfabric.service.KeepAliveService
import iecxiv.meshfabric.ui.desktop.RemoteDesktopViewModel
import iecxiv.meshfabric.ui.devices.DeviceListViewModel
import iecxiv.meshfabric.ui.files.FileManagerViewModel
import iecxiv.meshfabric.ui.login.LoginViewModel
import iecxiv.meshfabric.ui.navigation.MeshCentralNavGraph
import iecxiv.meshfabric.ui.settings.SettingsViewModel
import iecxiv.meshfabric.ui.terminal.TerminalViewModel
import iecxiv.meshfabric.ui.theme.MeshCentralTheme
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var meshClient: MeshCentralClient
    private lateinit var desktopClient: DesktopRelayClient
    private lateinit var terminalClient: TerminalRelayClient
    private lateinit var fileClient: FileRelayClient

    private lateinit var loginViewModel: LoginViewModel
    private lateinit var deviceListViewModel: DeviceListViewModel
    private lateinit var desktopViewModel: RemoteDesktopViewModel
    private lateinit var terminalViewModel: TerminalViewModel
    private lateinit var fileManagerViewModel: FileManagerViewModel
    private lateinit var settingsViewModel: SettingsViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        sessionManager = SessionManager(applicationContext)
        meshClient = MeshCentralClient()
        desktopClient = DesktopRelayClient()
        terminalClient = TerminalRelayClient()
        fileClient = FileRelayClient()

        loginViewModel = LoginViewModel(application, meshClient, sessionManager)
        deviceListViewModel = DeviceListViewModel(meshClient, desktopClient, terminalClient, fileClient, sessionManager)
        desktopViewModel = RemoteDesktopViewModel(meshClient, desktopClient, sessionManager)
        terminalViewModel = TerminalViewModel(meshClient, terminalClient)
        fileManagerViewModel = FileManagerViewModel(meshClient, fileClient)
        settingsViewModel = SettingsViewModel(sessionManager)

        val isSaveBuffersEnabled = sessionManager.isSaveTerminalBuffersEnabled()
        terminalClient.initBuffersFromDisk(applicationContext, meshClient, isSaveBuffersEnabled)

        KeepAliveService.onTaskRemovedListener = {
            val isSaveBuffersEnabledOnExit = sessionManager.isSaveTerminalBuffersEnabled()
            meshClient.disconnect()
            terminalClient.saveAllBuffersAndClose(applicationContext, isSaveBuffersEnabledOnExit)
            desktopClient.disconnectAll()
            fileClient.disconnectAll()
        }

        MeshFabricApp.onNetworkChangedListener = {
            meshClient.onNetworkChanged()
            terminalClient.onNetworkChanged()
            desktopClient.onNetworkChanged()
            fileClient.onNetworkChanged()

            val hasActiveConnected = terminalClient.hasActiveSessions() ||
                    desktopClient.hasActiveSessions() ||
                    fileClient.hasActiveSessions()

            val hasDisconnected = terminalClient.hasDisconnectedWithBuffer() ||
                    desktopClient.hasDisconnectedWithBuffer() ||
                    fileClient.hasDisconnectedWithBuffer()

            KeepAliveService.updateNotification(applicationContext, hasActiveConnected, hasDisconnected, desktopClient.hasActiveSessions())
        }

        setContent {
            val themeMode by sessionManager.themeModeFlow.collectAsState()
            val appName by sessionManager.appNameFlow.collectAsState()
            val customIconPath by sessionManager.customIconPathFlow.collectAsState()

            LaunchedEffect(appName, customIconPath) {
                title = appName
                updateTaskDescription(appName, customIconPath)
            }

            MeshCentralTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    MeshCentralNavGraph(
                        navController = navController,
                        loginViewModel = loginViewModel,
                        deviceListViewModel = deviceListViewModel,
                        desktopViewModel = desktopViewModel,
                        terminalViewModel = terminalViewModel,
                        fileManagerViewModel = fileManagerViewModel,
                        settingsViewModel = settingsViewModel
                    )
                }
            }
        }
    }

    private fun updateTaskDescription(appName: String, customIconPath: String?) {
        try {
            val bitmap = if (!customIconPath.isNullOrBlank()) {
                try {
                    val file = File(customIconPath)
                    if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
                } catch (_: Exception) { null }
            } else null

            @Suppress("DEPRECATION")
            val taskDesc = ActivityManager.TaskDescription(appName, bitmap)
            setTaskDescription(taskDesc)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onStart() {
        super.onStart()
        KeepAliveService.stopService(applicationContext)
    }

    override fun onResume() {
        super.onResume()
        val session = sessionManager.getSession()
        if (session != null && session.serverUrl.isNotBlank() && session.authCookie.isNotBlank()) {
            if (!meshClient.connectionState.value) {
                meshClient.connectControlSocket(
                    rawServerUrl = session.serverUrl,
                    cookie = session.authCookie,
                    ignoreSslCert = session.ignoreSsl
                )
            }
        }
    }

    override fun onStop() {
        super.onStop()
        val totalActiveSessions = terminalClient.getSessionCount() +
                desktopClient.getSessionCount() +
                fileClient.getSessionCount()

        val hasActiveConnected = terminalClient.hasActiveSessions() ||
                desktopClient.hasActiveSessions() ||
                fileClient.hasActiveSessions()

        val hasDisconnectedSessions = terminalClient.hasDisconnectedWithBuffer() ||
                desktopClient.hasDisconnectedWithBuffer() ||
                fileClient.hasDisconnectedWithBuffer()

        if (totalActiveSessions > 0) {
            KeepAliveService.startService(applicationContext, hasActiveConnected, hasDisconnectedSessions, desktopClient.hasActiveSessions())
        } else {
            KeepAliveService.stopService(applicationContext)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Keep active sessions alive unless destroyed by user action or task removed
    }
}

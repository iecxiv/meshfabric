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
import iecxiv.meshfabric.ui.devices.DeviceChatKeepAlive
import iecxiv.meshfabric.ui.files.FileManagerViewModel
import iecxiv.meshfabric.ui.login.LoginViewModel
import iecxiv.meshfabric.ui.navigation.MeshCentralNavGraph
import iecxiv.meshfabric.ui.settings.SettingsViewModel
import iecxiv.meshfabric.ui.terminal.TerminalViewModel
import iecxiv.meshfabric.ui.theme.MeshCentralTheme
import java.io.File

class MainActivity : ComponentActivity() {
    private val remoteRuntime by lazy { androidx.lifecycle.ViewModelProvider(this)[RemoteSessionRuntime::class.java] }
    private var runtimeReady = false

    internal lateinit var fidoClient: com.yubico.yubikit.fido.android.ui.FidoClient
        private set

    internal var desktopMouseCapture: iecxiv.meshfabric.ui.desktop.DesktopMouseCapture? = null

    internal fun desktopPrimaryMouse(point: iecxiv.meshfabric.ui.desktop.DesktopWheelPoint?, phase: iecxiv.meshfabric.ui.desktop.DesktopPrimaryPhase): Boolean =
        if (runtimeReady) desktopViewModel.desktopPrimaryMouse(point, phase) else false

    private fun cancelDesktopPointerGesture(downTime: Long, eventTime: Long) {
        val cancel = android.view.MotionEvent.obtain(downTime, eventTime,
            android.view.MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
        try { super.dispatchTouchEvent(cancel) } finally { cancel.recycle() }
    }

    override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean {
        if (desktopMouseCapture?.motion(event) == true) return true
        return super.dispatchTouchEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: android.view.MotionEvent): Boolean {
        if (desktopMouseCapture?.motion(event) == true) {
            if (event.actionMasked == android.view.MotionEvent.ACTION_BUTTON_PRESS)
                cancelDesktopPointerGesture(event.downTime, event.eventTime)
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }

    private var desktopWindowKeys: iecxiv.meshfabric.ui.desktop.DesktopMouseWindowCallback? = null

    private fun installDesktopMouseWindowCallback() {
        if (desktopWindowKeys != null) return
        val original = window.callback ?: return
        val callback = iecxiv.meshfabric.ui.desktop.DesktopMouseWindowCallback(
            original = original,
            consume = { event -> desktopMouseCapture?.key(event) == true },
            cancelGesture = { downTime, eventTime -> cancelDesktopPointerGesture(downTime, eventTime) }
        )
        desktopWindowKeys = callback
        window.callback = callback
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) desktopMouseCapture?.reset()
    }


    private val sessionManager get() = remoteRuntime.sessionManager
    private val meshClient get() = remoteRuntime.meshClient
    private val desktopClient get() = remoteRuntime.desktopClient
    private val terminalClient get() = remoteRuntime.terminalClient
    private val fileClient get() = remoteRuntime.fileClient

    private val loginViewModel get() = remoteRuntime.loginViewModel
    private val deviceListViewModel get() = remoteRuntime.deviceListViewModel
    private val desktopViewModel get() = remoteRuntime.desktopViewModel
    private val terminalViewModel get() = remoteRuntime.terminalViewModel
    private val fileManagerViewModel get() = remoteRuntime.fileManagerViewModel
    private val settingsViewModel get() = remoteRuntime.settingsViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fidoClient = com.yubico.yubikit.fido.android.ui.FidoClient(this)
        iecxiv.meshfabric.ui.management.NoticeNavigation.receive(intent)
        enableEdgeToEdge()
        installDesktopMouseWindowCallback()

        remoteRuntime
        runtimeReady = true
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
                    val navController = rememberSessionNavController(remoteRuntime)
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
            run { iecxiv.meshfabric.diagnostics.AppDiagnostics.fault("MainActivity", "MainActivity.kt:138", e); Unit }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        iecxiv.meshfabric.ui.management.NoticeNavigation.receive(intent)
    }

    override fun onStart() {
        super.onStart()
        iecxiv.meshfabric.ui.management.NoticeVisibility.visible = true
        remoteRuntime.onForeground()
    }

    override fun onResume() {
        super.onResume()
        remoteRuntime.resumeControlConnection()
    }

    override fun onStop() {
        desktopMouseCapture?.reset()
        iecxiv.meshfabric.ui.management.NoticeVisibility.visible = false
        super.onStop()
        remoteRuntime.onBackground()
    }

    override fun onDestroy() {
        desktopWindowKeys?.let { callback ->
            if (window.callback === callback) window.callback = callback.original
        }
        desktopWindowKeys = null
        desktopMouseCapture?.reset()
        desktopMouseCapture = null
        super.onDestroy()
        // Keep active sessions alive unless destroyed by user action or task removed
    }
}

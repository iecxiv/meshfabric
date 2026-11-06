package iecxiv.meshfabric.ui.desktop

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import iecxiv.meshfabric.data.local.SessionManager
import iecxiv.meshfabric.data.remote.DesktopRelayClient
import iecxiv.meshfabric.data.remote.DesktopSettings
import iecxiv.meshfabric.data.remote.MeshCentralClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okio.ByteString

enum class MouseMode {
    LEFT_CLICK,
    RIGHT_CLICK,
    MIDDLE_CLICK,
    DRAG_MOVE,
    SCROLL
}

class RemoteDesktopViewModel(
    val meshClient: MeshCentralClient,
    val desktopClient: DesktopRelayClient,
    val sessionManager: SessionManager? = null
) : ViewModel() {

    private val desktopInputs = mutableMapOf<String, DesktopKeyActions>()

    private fun desktopInput(nodeId: String): DesktopKeyActions = desktopInputs.getOrPut(nodeId) {
        DesktopKeyActions(viewModelScope, binding = {
            val session = desktopClient.getSession(meshClient, nodeId)
            synchronized(session) {
                if (session.isConnected() && !session.isUserDisconnect) session.webSocket else null
            }
        }, emit = { origin, key, down ->
            val session = desktopClient.getSession(meshClient, nodeId)
            synchronized(session) {
                if (session.webSocket === origin && session.isConnected() && !session.isUserDisconnect)
                    session.sendKeyEvent(key, down)
            }
        })
    }

    fun heldDesktopModifiers(nodeId: String): StateFlow<Set<Int>> = desktopInput(nodeId).held
    fun toggleDesktopHold(nodeId: String, key: Int) = desktopInput(nodeId).toggle(key)
    fun releaseDesktopHolds(nodeId: String) = desktopInput(nodeId).releaseHolds()
    fun releaseDesktopKeys(nodeId: String) { desktopInputs[nodeId]?.clear() }

    var selectedMouseMode = MouseMode.LEFT_CLICK

    fun isConnected(nodeId: String): StateFlow<Boolean> = desktopClient.getConnectionState(meshClient, nodeId)
    fun screenResolution(nodeId: String): StateFlow<Pair<Int, Int>> = desktopClient.getScreenResolution(meshClient, nodeId)
    fun desktopBitmap(nodeId: String): StateFlow<Bitmap?> = desktopClient.getDesktopBitmap(meshClient, nodeId)
    fun frameVersion(nodeId: String): StateFlow<Long> = desktopClient.getFrameVersion(meshClient, nodeId)
    fun settings(nodeId: String): StateFlow<DesktopSettings> = desktopClient.getSettings(meshClient, nodeId)
    fun remoteClipboardText(nodeId: String): StateFlow<String> = desktopClient.getRemoteClipboardText(meshClient, nodeId)
    fun clipboardReadState(nodeId: String) = desktopClient.getSession(meshClient, nodeId).clipboardReadState
    fun clipboardWriteState(nodeId: String) = desktopClient.getSession(meshClient, nodeId).clipboardWriteState

    fun startSession(nodeId: String) {
        sessionManager?.getDesktopSettings()?.let { savedSettings ->
            desktopClient.updateSettings(nodeId, savedSettings)
        }
        desktopClient.connect(meshClient = meshClient, nodeId = nodeId)
    }

    fun pauseSession(nodeId: String) {
        releaseDesktopKeys(nodeId)
        desktopClient.pauseSession(nodeId)
    }

    fun resumeSession(nodeId: String) {
        desktopClient.resumeSession(nodeId)
    }

    fun handleCanvasTouch(nodeId: String, xPct: Float, yPct: Float, isDown: Boolean, isUp: Boolean) {
        val (resWidth, resHeight) = screenResolution(nodeId).value
        val remoteX = (xPct * resWidth).toInt().coerceIn(0, resWidth)
        val remoteY = (yPct * resHeight).toInt().coerceIn(0, resHeight)

        val s = settings(nodeId).value

        when (selectedMouseMode) {
            MouseMode.LEFT_CLICK -> {
                val downAction = if (s.swapMouseButtons) 0x08 else 0x02
                val upAction = if (s.swapMouseButtons) 0x10 else 0x04

                if (isDown) {
                    desktopClient.sendMouseEvent(nodeId, 0x00, remoteX, remoteY)
                    desktopClient.sendMouseEvent(nodeId, downAction, remoteX, remoteY)
                }
                if (isUp) {
                    desktopClient.sendMouseEvent(nodeId, upAction, remoteX, remoteY)
                }
            }
            MouseMode.RIGHT_CLICK -> {
                val downAction = if (s.swapMouseButtons) 0x02 else 0x08
                val upAction = if (s.swapMouseButtons) 0x04 else 0x10

                if (isDown) {
                    desktopClient.sendMouseEvent(nodeId, 0x00, remoteX, remoteY)
                    desktopClient.sendMouseEvent(nodeId, downAction, remoteX, remoteY)
                }
                if (isUp) {
                    desktopClient.sendMouseEvent(nodeId, upAction, remoteX, remoteY)
                }
            }
            MouseMode.MIDDLE_CLICK -> {
                if (isDown) {
                    desktopClient.sendMouseEvent(nodeId, 0x00, remoteX, remoteY)
                    desktopClient.sendMouseEvent(nodeId, 0x20, remoteX, remoteY)
                }
                if (isUp) {
                    desktopClient.sendMouseEvent(nodeId, 0x40, remoteX, remoteY)
                }
            }
            MouseMode.DRAG_MOVE -> {
                desktopClient.sendMouseEvent(nodeId, 0x00, remoteX, remoteY)
            }
            MouseMode.SCROLL -> {
                if (isUp) {
                    val delta = if (s.reverseMouseWheel) 120 else -120
                    desktopClient.sendMouseScroll(nodeId, remoteX, remoteY, delta = delta)
                }
            }
        }
    }

    fun performMouseClick(nodeId: String, xPct: Float, yPct: Float, isRightClick: Boolean) {
        val (resWidth, resHeight) = screenResolution(nodeId).value
        val remoteX = (xPct * resWidth).toInt().coerceIn(0, resWidth)
        val remoteY = (yPct * resHeight).toInt().coerceIn(0, resHeight)

        val s = settings(nodeId).value

        val downAction = if (isRightClick) {
            if (s.swapMouseButtons) 0x02 else 0x08
        } else {
            if (s.swapMouseButtons) 0x08 else 0x02
        }

        val upAction = if (isRightClick) {
            if (s.swapMouseButtons) 0x04 else 0x10
        } else {
            if (s.swapMouseButtons) 0x10 else 0x04
        }

        desktopClient.sendMouseEvent(nodeId, 0x00, remoteX, remoteY)
        desktopClient.sendMouseEvent(nodeId, downAction, remoteX, remoteY)

        viewModelScope.launch {
            delay(40)
            desktopClient.sendMouseEvent(nodeId, upAction, remoteX, remoteY)
        }
    }

    fun performMiddleClick(nodeId: String, xPct: Float, yPct: Float) {
        val (resWidth, resHeight) = screenResolution(nodeId).value
        val remoteX = (xPct * resWidth).toInt().coerceIn(0, resWidth)
        val remoteY = (yPct * resHeight).toInt().coerceIn(0, resHeight)

        desktopClient.sendMouseEvent(nodeId, 0x00, remoteX, remoteY)
        desktopClient.sendMouseEvent(nodeId, 0x20, remoteX, remoteY)

        viewModelScope.launch {
            delay(40)
            desktopClient.sendMouseEvent(nodeId, 0x40, remoteX, remoteY)
        }
    }

    fun sendMouseScroll(nodeId: String, remoteX: Int, remoteY: Int, delta: Int) {
        desktopClient.sendMouseScroll(nodeId, remoteX, remoteY, delta)
    }

    fun updateSettings(nodeId: String, newSettings: DesktopSettings) {
        desktopClient.updateSettings(nodeId, newSettings)
        sessionManager?.saveDesktopSettings(newSettings)
    }

    fun setDesktopBarCollapsed(collapsed: Boolean) {
        sessionManager?.saveDesktopBarCollapsed(collapsed)
    }

    fun isDesktopBarCollapsed(): Boolean {
        return sessionManager?.isDesktopBarCollapsed() ?: false
    }

    fun sendUnicodeText(nodeId: String, text: String) {
        if (text.isEmpty()) return
        val session = desktopClient.getSession(meshClient, nodeId)
        synchronized(session) {
            if (!session.isConnected() || session.isUserDisconnect) return
            val socket = session.webSocket ?: return
            for (packet in DesktopTextEncoding.packets(text)) {
                if (!socket.send(ByteString.of(*packet))) break
            }
        }
    }

    fun sendDesktopKeyEvent(nodeId: String, keyCode: Int, isDown: Boolean) {
        if (keyCode !in 1..255) return
        desktopInput(nodeId).physical(keyCode, isDown)
    }

    fun sendSingleKey(nodeId: String, keyCode: Int) {
        desktopInput(nodeId).combo(keyCode)
    }

    fun sendKeyCombo(nodeId: String, vararg keyCodes: Int) {
        desktopInput(nodeId).combo(*keyCodes)
    }

    fun sendClipboardText(nodeId: String, text: String) {
        desktopClient.sendClipboardText(meshClient, nodeId, text)
    }

    fun requestRemoteClipboard(nodeId: String) {
        desktopClient.requestRemoteClipboard(meshClient, nodeId)
    }

    fun sendCtrlAltDel(nodeId: String) {
        sendKeyCombo(nodeId, 0x11, 0x12, 0x2E)
    }

    fun sendHotkeyCommand(nodeId: String, command: String) {
        desktopClient.sendHotkeyCommand(nodeId, command)
    }

    fun refreshDisplay(nodeId: String) {
        desktopClient.refreshDisplay(nodeId)
    }

    fun stopSession(nodeId: String) {
        releaseDesktopKeys(nodeId)
        desktopClient.disconnectSession(nodeId)
    }

    override fun onCleared() {
        desktopInputs.values.forEach { it.clear() }
        desktopInputs.clear()
        super.onCleared()
        desktopClient.disconnectAll()
    }
}
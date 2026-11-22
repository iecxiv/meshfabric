package iecxiv.meshfabric.ui.desktop

import android.graphics.Color
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import iecxiv.meshfabric.data.remote.DesktopSession
import iecxiv.meshfabric.data.remote.MeshCentralClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.Request
import okhttp3.WebSocket
import okio.ByteString
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DesktopForegroundRefreshTest {
    @get:Rule val compose = createComposeRule()
    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }
    private class Socket : WebSocket {
        val packets = mutableListOf<ByteString>()
        override fun request(): Request = Request.Builder().url("https://example.invalid/").build()
        override fun queueSize(): Long = 0L
        override fun send(text: String): Boolean = true
        override fun send(bytes: ByteString): Boolean { packets.add(bytes); return true }
        override fun close(code: Int, reason: String?): Boolean = true
        override fun cancel() = Unit
        fun refreshes() = packets.count { it.hex() == "00060004" }
    }
    private fun resumedOwner(): Owner {
        lateinit var owner: Owner
        compose.runOnIdle { owner = Owner(); owner.registry.currentState = Lifecycle.State.RESUMED }
        return owner
    }
    private fun cycle(owner: Owner) {
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.CREATED }
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
    }
    @Test fun refreshesOncePerResumeNotOnInitialAttachmentAndStopsAfterDisposal() {
        val owner = resumedOwner()
        val showing = mutableStateOf(true)
        var calls = 0
        compose.setContent { if (showing.value) DesktopResumeRefreshEffect("desktop", owner) { calls++ } }
        compose.runOnIdle { assertEquals(0, calls) }
        cycle(owner)
        compose.runOnIdle { assertEquals(1, calls) }
        cycle(owner)
        compose.runOnIdle { assertEquals(2, calls); showing.value = false }
        compose.waitForIdle()
        cycle(owner)
        compose.runOnIdle { assertEquals(2, calls) }
    }
    @Test fun actualSessionSendsFullRefreshAndPreservesRetainedBitmapAcrossResume() {
        val owner = resumedOwner()
        val scope = CoroutineScope(Dispatchers.Unconfined + Job())
        val mesh = MeshCentralClient().apply { isUserLogout = true }
        val session = DesktopSession("foreground-fixture", mesh, scope)
        val socket = Socket()
        try {
            compose.runOnIdle {
                session.isUserDisconnect = false
                session.webSocket = socket
                @Suppress("UNCHECKED_CAST")
                val connected = DesktopSession::class.java.getDeclaredField("_connectionState").apply { isAccessible = true }.get(session) as MutableStateFlow<Boolean>
                connected.value = true
                DesktopSession::class.java.getDeclaredMethod("initCanvas", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType).apply { isAccessible = true }.invoke(session, 4, 4)
                requireNotNull(session.desktopBitmap.value).eraseColor(Color.GREEN)
            }
            val retained = requireNotNull(session.desktopBitmap.value)
            compose.setContent { DesktopResumeRefreshEffect("desktop", owner) { session.resumeSession() } }
            compose.runOnIdle { assertEquals(0, socket.refreshes()) }
            cycle(owner)
            compose.runOnIdle {
                assertEquals(1, socket.refreshes())
                assertSame(retained, session.desktopBitmap.value)
                assertEquals(Color.GREEN, retained.getPixel(3, 3))
                assertSame(socket, session.webSocket)
            }
            cycle(owner)
            compose.runOnIdle {
                assertEquals(2, socket.refreshes())
                assertSame(retained, session.desktopBitmap.value)
                assertEquals(Color.GREEN, retained.getPixel(0, 0))
            }
        } finally {
            compose.runOnIdle { session.isUserDisconnect = true; session.webSocket = null }
            scope.cancel()
            (MeshCentralClient::class.java.getDeclaredField("scope").apply { isAccessible = true }.get(mesh) as CoroutineScope).cancel()
        }
    }
}

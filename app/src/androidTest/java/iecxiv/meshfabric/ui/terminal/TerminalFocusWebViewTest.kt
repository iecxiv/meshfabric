package iecxiv.meshfabric.ui.terminal

import android.app.Dialog
import android.view.inputmethod.EditorInfo
import android.webkit.WebView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import iecxiv.meshfabric.data.remote.MeshCentralClient
import iecxiv.meshfabric.data.remote.TerminalSession
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class TerminalFocusWebViewTest {
    @Test fun explicitKeyboardAndDialogFocusAreLifecycleSafe() {
        val scenario = ActivityScenario.launch(TerminalEchoTestActivity::class.java)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        lateinit var renderer: TerminalRenderSession
        lateinit var view: WebView
        var attachment = -1L
        var visibility = -1L
        var dialog: Dialog? = null
        var created = false
        fun state(check: () -> Boolean): Boolean {
            var value = false
            scenario.onActivity { value = check() }
            return value
        }
        fun await(message: String, check: () -> Boolean) {
            val until = android.os.SystemClock.uptimeMillis() + 20000
            while (android.os.SystemClock.uptimeMillis() < until) {
                if (check()) return
                android.os.SystemClock.sleep(50)
            }
            fail(message)
        }
        fun domFocused(): Boolean {
            val latch = CountDownLatch(1)
            var focused = false
            scenario.onActivity {
                view.evaluateJavascript("!!document.activeElement && document.activeElement.classList.contains('xterm-helper-textarea');") {
                    focused = it == "true"; latch.countDown()
                }
            }
            assertTrue(latch.await(10, TimeUnit.SECONDS))
            return focused
        }
        fun imeVisible(): Boolean = state { ViewCompat.getRootWindowInsets(view)?.isVisible(WindowInsetsCompat.Type.ime()) == true }
        try {
            scenario.onActivity {
                val session = TerminalSession("synthetic-focus", MeshCentralClient(), scope)
                renderer = TerminalRenderSession(it, session, scope)
                created = true
                visibility = renderer.bindVisibility()
                renderer.setResumed(visibility, true)
                attachment = renderer.attach(it, it.host)
                view = TerminalRenderSession::class.java.getDeclaredField("webView").apply { isAccessible = true }.get(renderer) as WebView
            }
            await("Renderer did not become ready") { state {
                TerminalRenderSession::class.java.getDeclaredField("ready").apply { isAccessible = true }.getBoolean(renderer) && view.hasWindowFocus()
            } }
            assertFalse("Startup must not request terminal DOM focus", domFocused())
            assertFalse("Startup must not open IME", imeVisible())
            scenario.onActivity { assertTrue(renderer.requestTerminalFocus(false)) }
            await("Restore failed") { domFocused() && !imeVisible() }
            scenario.onActivity {
                val info = EditorInfo()
                assertNotNull(view.onCreateInputConnection(info))
                assertTrue(info.imeOptions and EditorInfo.IME_FLAG_NO_EXTRACT_UI != 0)
                assertTrue(info.imeOptions and EditorInfo.IME_FLAG_NO_FULLSCREEN != 0)
                assertTrue(info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0)
                renderer.setFocusBlocked(true)
                dialog = Dialog(it).apply { setContentView(TextView(it).apply { text = "synthetic dialog" }); show() }
            }
            await("Dialog did not acquire window focus") { state { !view.hasWindowFocus() } }
            scenario.onActivity { assertFalse(renderer.requestTerminalFocus(true)) }
            scenario.onActivity {
                renderer.setFocusBlocked(false)
                assertTrue(renderer.requestTerminalFocus(false))
                dialog!!.dismiss()
                dialog = null
            }
            await("Focus did not return after dialog dismissal") { domFocused() && !imeVisible() }
            scenario.onActivity { renderer.toggleKeyboard() }
            await("Explicit keyboard request failed") { imeVisible() }
            scenario.onActivity { renderer.toggleKeyboard() }
            await("Keyboard did not hide") { !imeVisible() }
            scenario.onActivity { renderer.changeFontSize(1); renderer.setResumed(visibility, false); renderer.setResumed(visibility, true) }
            android.os.SystemClock.sleep(500)
            assertFalse("Resize/resume reopened hidden keyboard", imeVisible())
            scenario.onActivity {
                renderer.setResumed(visibility, false)
                assertFalse(renderer.requestTerminalFocus(true))
                renderer.setResumed(visibility, true)
                renderer.setFocusBlocked(true)
                assertFalse(renderer.requestTerminalFocus(true))
                renderer.setFocusBlocked(false)
                renderer.detach(attachment)
                assertFalse(renderer.requestTerminalFocus(true))
            }
        } finally {
            scenario.onActivity { dialog?.dismiss(); if (created) renderer.destroy() }
            scope.cancel()
            scenario.close()
        }
    }
}

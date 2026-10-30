package iecxiv.meshfabric.ui.terminal

import android.annotation.SuppressLint
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.Gson
import iecxiv.meshfabric.data.remote.MeshCentralClient
import iecxiv.meshfabric.data.remote.TerminalSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import okhttp3.Request
import okhttp3.WebSocket
import okio.ByteString
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class TerminalEchoWebViewTest {
    class EchoBridge {
        val ready = CountDownLatch(1)
        val acknowledgements = LinkedBlockingQueue<String>()
        @JavascriptInterface fun onHtmlReady() { ready.countDown() }
        @JavascriptInterface fun onTerminalResize(cols: Int, rows: Int) {}
        val inputs = LinkedBlockingQueue<String>()
        @JavascriptInterface fun sendInput(text: String) { inputs.offer(text) }
        @JavascriptInterface fun onOutputConsumed(token: String) { acknowledgements.offer(token) }
    }
    private class FakeSocket : WebSocket {
        override fun request(): Request = Request.Builder().url("https://example.invalid/").build()
        override fun queueSize(): Long = 0L
        override fun send(text: String): Boolean = true
        override fun send(bytes: ByteString): Boolean = true
        override fun close(code: Int, reason: String?): Boolean = true
        override fun cancel() {}
    }

    @Test fun binaryFramesRenderAc1b() { verify("ac1b", true) }
    @Test fun textFramesRenderAc1b() { verify("ac1b", false) }
    @Test fun binaryFramesRenderTwoCsAndTwoOnes() { verify("cc11", true) }
    @Test fun textFramesRenderTwoCsAndTwoOnes() { verify("cc11", false) }

    @Test fun specialKeysAndLocalHistory() { verify("ready", true, controls = true) }

    @Test fun pasteSingleLineAddsNoEnter() { verify("ready", true, paste = "ac1b") }
    @Test fun pasteNormalizesCrLf() { verify("ready", true, paste = "one\r\ntwo") }
    @Test fun pasteBracketedMultiline() { verify("ready", true, paste = "one\ntwo\n", bracketed = true) }
    @Test fun pasteRejectsEscapeInjection() { verify("ready", true, paste = 27.toChar().toString() + "[201~", pasteAccepted = false) }

    @Test fun copyExcludesAnsiColorSequences() { verify("\u001b[31mc1\u001b[0m", true, "c1") }
    @Test fun clipboardPayloadIsMarkedSensitive() {
        val clip = terminalCopyClip("ac1b")
        assertEquals("ac1b", clip.getItemAt(0).text.toString())
        assertTrue(clip.description.extras!!.getBoolean("android.content.extra.IS_SENSITIVE"))
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun verify(expected: String, binary: Boolean, displayed: String = expected, paste: String? = null, bracketed: Boolean = false, pasteAccepted: Boolean = true, controls: Boolean = false) {
        val bridge = EchoBridge()
        val errors = ConcurrentLinkedQueue<String>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val session = TerminalSession("synthetic-echo", MeshCentralClient(), scope)
        val socket = FakeSocket()
        session.activeSocket = socket
        val textReceiver = TerminalSession::class.java.getDeclaredMethod(
            "handleIncomingData", java.lang.Long.TYPE, WebSocket::class.java, String::class.java
        ).apply { isAccessible = true }
        val binaryReceiver = TerminalSession::class.java.getDeclaredMethod(
            "handleTerminalOutput", String::class.java
        ).apply { isAccessible = true }
        val delivery = TerminalOutputDelivery().apply { attach(1L) }
        val gson = Gson()
        var webView: WebView? = null
        val scenario = ActivityScenario.launch(TerminalEchoTestActivity::class.java)
        try {
            scenario.onActivity { activity ->
                textReceiver.invoke(session, 0L, socket, "c")
                assertTrue(session.connectionState.value)
                webView = WebView(activity).also { view ->
                    view.settings.javaScriptEnabled = true
                    view.settings.allowFileAccess = true
                    view.webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(message: ConsoleMessage?): Boolean {
                            message?.let { errors.add(it.message()) }
                            return true
                        }
                    }
                    view.addJavascriptInterface(bridge, "AndroidTerminal")
                    activity.host.addView(view, FrameLayout.LayoutParams(-1, -1))
                    view.loadDataWithBaseURL("file:///android_asset/terminal/", HTML, "text/html", "UTF-8", null)
                }
            }
            assertTrue("Runtime did not initialize: $errors", bridge.ready.await(20, TimeUnit.SECONDS))
            for (character in expected) {
                var token = ""
                scenario.onActivity {
                    if (binary) binaryReceiver.invoke(session, character.toString())
                    else textReceiver.invoke(session, 0L, socket, character.toString())
                    val slice = session.readOutput(delivery.cursor)
                    assertFalse(slice.gap)
                    assertEquals(character.toString(), slice.text)
                    val batch = delivery.prepare(1L, slice)!!
                    token = batch.token
                    webView!!.evaluateJavascript(
                        "window.writeTerminalBatch(${gson.toJson(batch.token)},${gson.toJson(batch.text)});", null
                    )
                }
                val acknowledged = bridge.acknowledgements.poll(10, TimeUnit.SECONDS)
                assertEquals("Parser ACK missing: $errors", token, acknowledged)
                scenario.onActivity { assertTrue(delivery.acknowledge(1L, token)) }
            }
            val resultReady = CountDownLatch(1)
            var actual = ""
            scenario.onActivity {
                webView!!.evaluateJavascript("window.__echoTerm.buffer.active.getLine(0).translateToString(true);") { json ->
                    actual = gson.fromJson(json, String::class.java)
                    resultReady.countDown()
                }
            }
            assertTrue(resultReady.await(10, TimeUnit.SECONDS))
            assertEquals("Actual bundled xterm buffer differs", displayed, actual)
            val copyReady = CountDownLatch(1)
            var copyJson = ""
            scenario.onActivity {
                webView!!.evaluateJavascript("window.getTerminalTextForCopy();") { json ->
                    copyJson = json
                    copyReady.countDown()
                }
            }
            assertTrue(copyReady.await(10, TimeUnit.SECONDS))
            val captured = gson.fromJson(copyJson, com.google.gson.JsonObject::class.java)
            assertEquals(displayed, captured.get("text").asString)
            assertFalse(captured.get("truncated").asBoolean)
            if (controls) {
                fun evaluate(script: String): String {
                    val latch = CountDownLatch(1)
                    var result = ""
                    scenario.onActivity { webView!!.evaluateJavascript(script) { result = it; latch.countDown() } }
                    assertTrue(latch.await(10, TimeUnit.SECONDS))
                    return result
                }
                fun write(token: String, data: String) {
                    assertEquals("true", evaluate("window.writeTerminalBatch(${gson.toJson(token)},${gson.toJson(data)});"))
                    assertEquals(token, bridge.acknowledgements.poll(10, TimeUnit.SECONDS))
                }
                fun key(name: String, expectedKey: String) {
                    assertEquals("true", evaluate("window.sendTerminalKey(${gson.toJson(name)});"))
                    assertEquals(expectedKey, bridge.inputs.poll(10, TimeUnit.SECONDS))
                    assertNull(bridge.inputs.poll(50, TimeUnit.MILLISECONDS))
                }
                val esc = 27.toChar().toString()
                mapOf("enter" to 13, "backspace" to 8, "tab" to 9, "esc" to 27).forEach { (name, code) -> key(name, code.toChar().toString()) }
                ('a'..'z').forEach { key("ctrl-" + it, (it.code - 96).toChar().toString()) }
                mapOf("insert" to "2", "delete" to "3", "pageup" to "5", "pagedown" to "6").forEach { (name, number) -> key(name, esc + "[" + number + "~") }
                val cursor = mapOf("up" to "A", "down" to "B", "right" to "C", "left" to "D", "home" to "H", "end" to "F")
                cursor.forEach { (name, suffix) -> key(name, esc + "[" + suffix) }
                write("cursor-on", esc + "[?1h")
                cursor.forEach { (name, suffix) -> key(name, esc + "O" + suffix) }
                write("cursor-off", esc + "[?1l")
                assertEquals("false", evaluate("window.sendTerminalKey('__proto__');"))
                assertNull(bridge.inputs.poll(100, TimeUnit.MILLISECONDS))
                write("history-rows", (0..399).joinToString(13.toChar().toString() + 10.toChar()) { "row$it" })
                assertEquals("true", evaluate("window.__echoTerm.scrollToTop(); window.__echoTerm.buffer.active.viewportY < window.__echoTerm.buffer.active.baseY;"))
                assertEquals("true", evaluate("window.scrollTerminalToLatest(); window.__echoTerm.buffer.active.viewportY === window.__echoTerm.buffer.active.baseY;"))
                assertNull("Local history must not send input", bridge.inputs.poll(100, TimeUnit.MILLISECONDS))
                write("history-alt", esc + "[?1049h")
                assertEquals("false", evaluate("window.scrollTerminalToLatest();"))
                assertNull(bridge.inputs.poll(100, TimeUnit.MILLISECONDS))
                write("history-normal", esc + "[?1049l")
            }
            if (paste != null) {
                if (bracketed) {
                    scenario.onActivity {
                        webView!!.evaluateJavascript("window.writeTerminalBatch('paste-mode', String.fromCharCode(27) + '[?2004h');", null)
                    }
                    assertEquals("paste-mode", bridge.acknowledgements.poll(10, TimeUnit.SECONDS))
                }
                val pasteReady = CountDownLatch(1)
                var accepted = ""
                scenario.onActivity {
                    webView!!.evaluateJavascript("window.pasteTerminalText(${gson.toJson(paste)});") { json ->
                        accepted = json
                        pasteReady.countDown()
                    }
                }
                assertTrue(pasteReady.await(10, TimeUnit.SECONDS))
                assertEquals(pasteAccepted.toString(), accepted)
                if (pasteAccepted) {
                    val normalized = paste.replace("\r\n", "\n").replace("\r", "\n").replace("\n", "\r")
                    val expectedPaste = if (bracketed) "\u001b[200~" + normalized + "\u001b[201~" else normalized
                    assertEquals(expectedPaste, bridge.inputs.poll(10, TimeUnit.SECONDS))
                }
                assertNull("Unexpected extra paste input", bridge.inputs.poll(200, TimeUnit.MILLISECONDS))
            }
        } finally {
            scenario.onActivity {
                session.closeSocketOnly()
                webView?.let { view ->
                    it.host.removeView(view)
                    view.removeJavascriptInterface("AndroidTerminal")
                    view.destroy()
                }
            }
            scope.cancel()
            scenario.close()
        }
    }

    @Test
    @SuppressLint("SetJavaScriptEnabled")
    fun touchScrollOverTextAndBlankUsesNativeViewport() {
        val bridge = EchoBridge()
        val gson = Gson()
        var view: WebView? = null
        val scenario = ActivityScenario.launch(TerminalEchoTestActivity::class.java)
        fun evaluate(script: String): String {
            val ready = CountDownLatch(1)
            var result = ""
            scenario.onActivity {
                view!!.evaluateJavascript(script) { json -> result = json; ready.countDown() }
            }
            assertTrue("JavaScript callback timed out", ready.await(10, TimeUnit.SECONDS))
            return result
        }
        fun write(token: String, data: String) {
            assertEquals("true", evaluate("window.writeTerminalBatch(${gson.toJson(token)},${gson.toJson(data)});"))
            assertEquals(token, bridge.acknowledgements.poll(10, TimeUnit.SECONDS))
        }
        fun classEnabled(): String = evaluate("document.getElementById('terminal-container').classList.contains('mesh-native-touch-scroll');")
        fun swipe(xRatio: Float): Int {
            evaluate("window.__echoTerm.scrollToBottom(); true;")
            android.os.SystemClock.sleep(250)
            val before = evaluate("window.__echoTerm.buffer.active.viewportY;").toInt()
            val location = IntArray(2)
            var width = 0
            var height = 0
            scenario.onActivity { view!!.getLocationOnScreen(location); width = view!!.width; height = view!!.height }
            val down = android.os.SystemClock.uptimeMillis()
            val automation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
            fun inject(action: Int, progress: Float) {
                val event = android.view.MotionEvent.obtain(down, android.os.SystemClock.uptimeMillis(), action,
                    location[0] + width * xRatio, location[1] + height * (0.25f + 0.45f * progress), 0)
                event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
                try { assertTrue(automation.injectInputEvent(event, true)) } finally { event.recycle() }
            }
            inject(android.view.MotionEvent.ACTION_DOWN, 0f)
            for (step in 1..20) {
                android.os.SystemClock.sleep(12)
                inject(android.view.MotionEvent.ACTION_MOVE, step / 20f)
            }
            inject(android.view.MotionEvent.ACTION_UP, 1f)
            android.os.SystemClock.sleep(1200)
            return before - evaluate("window.__echoTerm.buffer.active.viewportY;").toInt()
        }
        try {
            scenario.onActivity { activity ->
                view = WebView(activity).also {
                    it.settings.javaScriptEnabled = true
                    it.settings.allowFileAccess = true
                    it.addJavascriptInterface(bridge, "AndroidTerminal")
                    activity.host.addView(it, FrameLayout.LayoutParams(-1, -1))
                    it.loadDataWithBaseURL("file:///android_asset/terminal/", HTML, "text/html", "UTF-8", null)
                }
            }
            assertTrue(bridge.ready.await(20, TimeUnit.SECONDS))
            assertEquals("true", evaluate("window.matchMedia('(pointer: coarse)').matches;"))
            write("rows", (0..399).joinToString(13.toChar().toString() + 10.toChar()) { "row$it" })
            assertEquals("true", classEnabled())
            assertEquals("true", evaluate("getComputedStyle(document.querySelector('.xterm-screen')).pointerEvents === 'none';"))
            assertEquals("true", evaluate("(function(){var r=document.querySelector('.xterm-screen').getBoundingClientRect();var e=document.elementFromPoint(r.left+5,r.top+5);return !!e && !!e.closest('.xterm-viewport');}());"))
            assertTrue("Scroll starting over text moved fewer than three rows", swipe(0.02f) >= 3)
            assertTrue("Scroll starting over blank area moved fewer than three rows", swipe(0.85f) >= 3)
            val esc = 27.toChar().toString()
            write("mouse-on", esc + "[?1000h")
            assertEquals("false", classEnabled())
            write("mouse-off", esc + "[?1000l")
            assertEquals("true", classEnabled())
            write("alternate-on", esc + "[?1049h")
            assertEquals("false", classEnabled())
            write("alternate-off", esc + "[?1049l")
            assertEquals("true", classEnabled())
        } finally {
            scenario.onActivity {
                view?.let { web -> it.host.removeView(web); web.removeJavascriptInterface("AndroidTerminal"); web.destroy() }
            }
            scenario.close()
        }
    }

    companion object {
        private val HTML = """<!doctype html><html><head>
          <meta name="viewport" content="width=device-width,initial-scale=1">
          <link rel="stylesheet" href="xterm.css">
          <link rel="stylesheet" href="terminal-touch-scroll.css">
          <style>html,body{width:100%;height:100%;margin:0}#terminal-container{width:100%;height:100%}</style>
          <script src="xterm.js"></script>
          <script>
            const OriginalTerminal = window.Terminal;
            window.Terminal = function(options) {
              const instance = new OriginalTerminal(options);
              window.__echoTerm = instance;
              return instance;
            };
          </script>
          <script src="xterm-fit.js"></script><script src="terminal-resize.js"></script><script src="terminal-selection.js"></script><script src="terminal-keys.js"></script>
          </head><body><div id="terminal-container"></div><div id="terminal-error" hidden></div>
          <script src="terminal-runtime.js"></script></body></html>"""
    }
}

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
    private fun verify(expected: String, binary: Boolean, displayed: String = expected, paste: String? = null, bracketed: Boolean = false, pasteAccepted: Boolean = true) {
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

    companion object {
        private val HTML = """<!doctype html><html><head>
          <meta name="viewport" content="width=device-width,initial-scale=1">
          <link rel="stylesheet" href="xterm.css">
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
          <script src="xterm-fit.js"></script><script src="terminal-resize.js"></script><script src="terminal-selection.js"></script>
          </head><body><div id="terminal-container"></div><div id="terminal-error" hidden></div>
          <script src="terminal-runtime.js"></script></body></html>"""
    }
}

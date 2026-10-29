package iecxiv.meshfabric.ui.terminal

import android.annotation.SuppressLint
import android.content.Context
import android.content.MutableContextWrapper
import android.os.Looper
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import com.google.gson.Gson
import iecxiv.meshfabric.data.remote.TerminalSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class TerminalRendererError { OUTPUT_CONTINUITY, RENDERER_STOPPED }

/** UI-thread renderer owner. No callbacks capture a composable or an old Activity. */
@SuppressLint("SetJavaScriptEnabled")
internal class TerminalRenderSession(
    context: Context,
    val session: TerminalSession,
    scope: CoroutineScope
) {
    private val appContext = context.applicationContext
    private val wrapper = MutableContextWrapper(appContext)
    private val gson = Gson()
    private val readyGate = TerminalReadyGate()
    private val page = readyGate.begin()
    private val delivery = TerminalOutputDelivery()
    private val lease = TerminalAttachmentLease()
    private val failure = MutableStateFlow<TerminalRendererError?>(null)
    val error: StateFlow<TerminalRendererError?> = failure.asStateFlow()
    private val webView = WebView(wrapper)
    private var outputJob: Job? = null
    private var ready = false
    private var fontSize = 11
    var isDestroyed = false
        private set
    val isAttached: Boolean get() = lease.current != null

    init {
        checkMainThread()
        webView.setBackgroundColor(0xFF0D1117.toInt())
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            useWideViewPort = false
            loadWithOverviewMode = false
            setSupportZoom(false)
        }
        webView.webViewClient = object : WebViewClient() {
            // The embedded JavaScript bridge must not navigate to arbitrary web content.
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                failure.value = TerminalRendererError.RENDERER_STOPPED
                destroy()
                return true
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(message: ConsoleMessage?): Boolean = true
        }
        webView.addJavascriptInterface(Bridge(), "AndroidTerminal")
        outputJob = scope.launch {
            session.outputRevision.collect {
                if (session.isUserDisconnect) destroy() else pump()
            }
        }
        webView.loadUrl("file:///android_asset/terminal/terminal.html")
    }

    private fun checkMainThread() { check(Looper.myLooper() == Looper.getMainLooper()) }

    fun attach(context: Context, parent: FrameLayout): Long {
        checkMainThread()
        if (isDestroyed) return -1L
        if (webView.parent === parent) return lease.current ?: -1L
        (webView.parent as? ViewGroup)?.removeView(webView)
        wrapper.baseContext = context
        val token = lease.attach()
        parent.addView(webView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        webView.onResume()
        if (ready) fitAndFocus()
        return token
    }
    fun detach(token: Long) {
        checkMainThread()
        if (!lease.release(token)) return
        (webView.parent as? ViewGroup)?.removeView(webView)
        if (!isDestroyed) webView.onPause()
        wrapper.baseContext = appContext
    }
    fun releaseParent(parent: FrameLayout) {
        checkMainThread()
        if (webView.parent === parent) lease.current?.let { detach(it) }
    }
    fun changeFontSize(delta: Int) {
        checkMainThread()
        if (isDestroyed) return
        fontSize = (fontSize + delta).coerceIn(8, 20)
        if (ready) webView.evaluateJavascript("window.setFontSize($fontSize);", null)
    }
    private fun fitAndFocus() {
        if (isDestroyed || !isAttached || failure.value != null) return
        webView.requestFocus()
        webView.evaluateJavascript("window.fitTerminal(); window.focusTerminal();", null)
    }
    private fun stopOutput() {
        if (isDestroyed || failure.value != null) return
        failure.value = TerminalRendererError.OUTPUT_CONTINUITY
        delivery.invalidate()
        webView.evaluateJavascript("window.showTerminalDeliveryError();", null)
    }
    private fun pump() {
        if (isDestroyed || !ready || failure.value != null || delivery.hasPending) return
        val slice = session.readOutput(delivery.cursor)
        if (slice.gap) { stopOutput(); return }
        val batch = delivery.prepare(page, slice) ?: return
        webView.evaluateJavascript("window.writeTerminalBatch(${gson.toJson(batch.token)},${gson.toJson(batch.text)});") { accepted ->
            if (!isDestroyed && accepted != "true") stopOutput()
        }
    }
    fun destroy() {
        checkMainThread()
        if (isDestroyed) return
        isDestroyed = true
        ready = false
        outputJob?.cancel()
        outputJob = null
        readyGate.invalidate()
        delivery.invalidate()
        lease.invalidate()
        (webView.parent as? ViewGroup)?.removeView(webView)
        wrapper.baseContext = appContext
        webView.stopLoading()
        webView.removeJavascriptInterface("AndroidTerminal")
        webView.webChromeClient = null
        webView.webViewClient = WebViewClient()
        webView.destroy()
    }

    inner class Bridge {
        @JavascriptInterface fun sendInput(data: String) {
            webView.post { if (!isDestroyed && failure.value == null) session.sendInput(data) }
        }
        @JavascriptInterface fun onTerminalResize(cols: Int, rows: Int) {
            webView.post { if (!isDestroyed) session.sendResize(cols, rows) }
        }
        @JavascriptInterface fun onHtmlReady() {
            webView.post {
                if (!isDestroyed && readyGate.accept(page)) {
                    ready = true
                    delivery.attach(page)
                    webView.evaluateJavascript("window.setFontSize($fontSize);", null)
                    pump()
                    fitAndFocus()
                }
            }
        }
        @JavascriptInterface fun onOutputConsumed(token: String) {
            webView.post { if (!isDestroyed && delivery.acknowledge(page, token)) pump() }
        }
    }
}

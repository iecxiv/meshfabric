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
import kotlin.coroutines.resume

enum class TerminalRendererError { OUTPUT_CONTINUITY, RENDERER_STOPPED, ACK_TIMEOUT }

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
    private val focusRequests = TerminalFocusRequests()
    private var focusBlocked = false
    private val webView = object : WebView(wrapper) {
        override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
            super.onWindowFocusChanged(hasWindowFocus)
            if (!hasWindowFocus) focusRequests.cancel()
            else post { applyPendingFocus() }
        }
        override fun onCreateInputConnection(info: android.view.inputmethod.EditorInfo): android.view.inputmethod.InputConnection? {
            if (!session.connectionState.value) return null
            val connection = super.onCreateInputConnection(info)
            info.imeOptions = info.imeOptions or android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI or
                android.view.inputmethod.EditorInfo.IME_FLAG_NO_FULLSCREEN or
                android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            return connection
        }
    }
    private var connectionJob: Job? = null
    private var outputJob: Job? = null
    private val watchdogScope = scope
    private val ackWatchdog = TerminalAckWatchdog()
    private val visibility = TerminalAttachmentLease()
    private var resumed = false
    private var ackJob: Job? = null
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
        connectionJob = scope.launch {
            session.connectionState.collect { connected ->
                if (!isDestroyed) {
                    if (ready) webView.evaluateJavascript("window.setTerminalInputEnabled($connected);", null)
                    if (!connected) { focusRequests.cancel(); hideIme() }
                }
            }
        }
        webView.loadUrl("file:///android_asset/terminal/terminal.html")
    }

    private fun checkMainThread() { check(Looper.myLooper() == Looper.getMainLooper()) }

    private fun nowMs(): Long = android.os.SystemClock.elapsedRealtime()
    fun bindVisibility(): Long {
        val token = visibility.attach()
        resumed = false
        focusRequests.cancel()
        updateAckActivity()
        return token
    }
    fun setResumed(token: Long, value: Boolean) {
        if (isDestroyed || visibility.current != token) return
        resumed = value
        if (!value) focusRequests.cancel()
        updateAckActivity()
    }
    fun unbindVisibility(token: Long) {
        if (!visibility.release(token)) return
        resumed = false
        focusRequests.cancel()
        updateAckActivity()
    }
    private fun updateAckActivity() {
        ackWatchdog.setActive(!isDestroyed && isAttached && resumed, nowMs())
        scheduleAckCheck()
    }
    private fun scheduleAckCheck() {
        ackJob?.cancel()
        ackJob = null
        val wait = ackWatchdog.remaining(nowMs()) ?: return
        ackJob = watchdogScope.launch {
            kotlinx.coroutines.delay(wait)
            if (!isDestroyed && ackWatchdog.poll(nowMs()) && failure.value == null) {
                failure.value = TerminalRendererError.ACK_TIMEOUT
            }
        }
    }

    fun attach(context: Context, parent: FrameLayout): Long {
        checkMainThread()
        if (isDestroyed) return -1L
        if (webView.parent === parent) return lease.current ?: -1L
        (webView.parent as? ViewGroup)?.removeView(webView)
        wrapper.baseContext = context
        val token = lease.attach()
        parent.addView(webView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        webView.onResume()
        updateAckActivity()
        if (ready) fitView()
        return token
    }
    fun detach(token: Long) {
        checkMainThread()
        if (!lease.release(token)) return
        focusRequests.cancel()
        if (webView.hasFocus()) hideIme()
        webView.clearFocus()
        if (!isDestroyed && ready) webView.evaluateJavascript("window.blurTerminal();", null)
        updateAckActivity()
        (webView.parent as? ViewGroup)?.removeView(webView)
        if (!isDestroyed) webView.onPause()
        wrapper.baseContext = appContext
    }
    fun releaseParent(parent: FrameLayout) {
        checkMainThread()
        if (webView.parent === parent) lease.current?.let { detach(it) }
    }
    suspend fun copySnapshot(): TerminalCopySnapshot? {
        checkMainThread()
        if (isDestroyed || !ready || failure.value != null) return null
        return kotlinx.coroutines.withTimeoutOrNull(3_000L) {
            kotlinx.coroutines.suspendCancellableCoroutine<TerminalCopySnapshot?> { continuation ->
                webView.evaluateJavascript("window.getTerminalTextForCopy();") { json ->
                    if (!continuation.isActive) return@evaluateJavascript
                    val snapshot = if (isDestroyed) null else try {
                        val value = gson.fromJson(json, com.google.gson.JsonObject::class.java)
                        val text = value?.get("text")?.asString
                        if (text != null && text.length <= 200000) {
                            TerminalCopySnapshot(text, value.get("truncated")?.asBoolean ?: false)
                        } else null
                    } catch (_: Exception) { null }
                    continuation.resume(snapshot)
                }
            }
        }
    }

    suspend fun paste(text: String): TerminalPasteResult {
        checkMainThread()
        if (isDestroyed || !ready || !isAttached || failure.value != null ||
            !session.connectionState.value || TerminalPastePolicy.issue(text) != null) return TerminalPasteResult.REJECTED
        return kotlinx.coroutines.withTimeoutOrNull(3_000L) {
            kotlinx.coroutines.suspendCancellableCoroutine<TerminalPasteResult> { continuation ->
                webView.evaluateJavascript("window.pasteTerminalText(${gson.toJson(text)});") { json ->
                    if (continuation.isActive) continuation.resume(when (json) {
                        "true" -> TerminalPasteResult.DISPATCHED
                        "false" -> TerminalPasteResult.REJECTED
                        else -> TerminalPasteResult.UNKNOWN
                    })
                }
            }
        } ?: TerminalPasteResult.UNKNOWN
    }

    fun changeFontSize(delta: Int) {
        checkMainThread()
        if (isDestroyed) return
        fontSize = (fontSize + delta).coerceIn(8, 20)
        if (ready) webView.evaluateJavascript("window.setFontSize($fontSize);", null)
    }
    private fun focusAllowed(): Boolean = !isDestroyed && ready && isAttached && resumed &&
        !focusBlocked && failure.value == null && session.connectionState.value

    private fun hideIme() {
        val token = webView.windowToken ?: return
        val manager = appContext.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        manager.hideSoftInputFromWindow(token, 0)
    }
    fun setFocusBlocked(value: Boolean) {
        checkMainThread()
        if (isDestroyed || focusBlocked == value) return
        focusBlocked = value
        if (value) {
            focusRequests.cancel()
            if (isAttached && resumed) {
                hideIme()
                webView.clearFocus()
                if (ready) webView.evaluateJavascript("window.blurTerminal();", null)
            }
        }
    }
    fun requestTerminalFocus(showKeyboard: Boolean): Boolean {
        checkMainThread()
        if (!focusAllowed()) { focusRequests.cancel(); return false }
        focusRequests.request(showKeyboard)
        applyPendingFocus()
        return true
    }
    private fun applyPendingFocus() {
        if (!focusAllowed() || !webView.hasWindowFocus()) return
        val ticket = focusRequests.take() ?: return
        if (!webView.requestFocus()) return
        webView.evaluateJavascript("window.focusTerminal();") { result ->
            if (result != "true") return@evaluateJavascript
            webView.post {
                if (!focusRequests.isCurrent(ticket) || !focusAllowed() || !webView.hasWindowFocus()) return@post
                if (ticket.showKeyboard) {
                    val manager = appContext.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                    manager.showSoftInput(webView, 0)
                } else hideIme()
            }
        }
    }
    fun sendSpecialKey(id: String) {
        checkMainThread()
        if (isDestroyed || !ready || !isAttached || !resumed || failure.value != null || !session.connectionState.value) return
        webView.evaluateJavascript("window.sendTerminalKey(${gson.toJson(id)});", null)
    }
    fun scrollToLatest() {
        checkMainThread()
        if (isDestroyed || !ready || !isAttached || !resumed || failure.value != null) return
        webView.evaluateJavascript("window.scrollTerminalToLatest();") { result ->
            if (result == "false" && !isDestroyed && isAttached && resumed) android.widget.Toast.makeText(
                appContext, iecxiv.meshfabric.R.string.terminal_history_unavailable, android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun toggleKeyboard() {
        checkMainThread()
        if (!focusAllowed()) return
        val visible = androidx.core.view.ViewCompat.getRootWindowInsets(webView)
            ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true
        if (visible) { focusRequests.cancel(); hideIme() }
        else requestTerminalFocus(true)
    }
    private fun fitView() {
        if (isDestroyed || !isAttached || failure.value != null) return
        webView.evaluateJavascript("window.fitTerminal();", null)
    }
    private fun stopOutput() {
        if (isDestroyed || failure.value != null) return
        failure.value = TerminalRendererError.OUTPUT_CONTINUITY
        ackWatchdog.reset()
        ackJob?.cancel()
        ackJob = null
        delivery.invalidate()
        webView.evaluateJavascript("window.showTerminalDeliveryError();", null)
    }
    private fun pump() {
        if (isDestroyed || !ready || failure.value != null || delivery.hasPending) return
        val slice = session.readOutput(delivery.cursor)
        if (slice.gap) { stopOutput(); return }
        val batch = delivery.prepare(page, slice) ?: return
        ackWatchdog.sent(batch.token, nowMs())
        scheduleAckCheck()
        webView.evaluateJavascript("window.writeTerminalBatch(${gson.toJson(batch.token)},${gson.toJson(batch.text)});") { accepted ->
            if (!isDestroyed && accepted != "true") stopOutput()
        }
    }
    fun destroy() {
        checkMainThread()
        if (isDestroyed) return
        focusRequests.cancel()
        if (isAttached && webView.hasFocus()) hideIme()
        isDestroyed = true
        visibility.invalidate()
        ackWatchdog.reset()
        ackJob?.cancel()
        ackJob = null
        ready = false
        connectionJob?.cancel()
        connectionJob = null
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
                    webView.evaluateJavascript("window.setTerminalInputEnabled(${session.connectionState.value});", null)
                    delivery.attach(page)
                    webView.evaluateJavascript("window.setFontSize($fontSize);", null)
                    pump()
                    fitView()
                }
            }
        }
        @JavascriptInterface fun onOutputConsumed(token: String) {
            webView.post {
                if (!isDestroyed && failure.value == null && ackWatchdog.acknowledge(token) && delivery.acknowledge(page, token)) {
                    scheduleAckCheck()
                    pump()
                }
            }
        }
    }
}

package iecxiv.meshfabric.ui.management

import android.annotation.SuppressLint
import android.content.Context
import android.content.MutableContextWrapper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import android.widget.FrameLayout
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.gson.Gson
import java.io.ByteArrayInputStream
import kotlin.math.abs

private class ServerConsoleRenderer(context:Context,private val canSend:()->Boolean,private val command:(String)->Boolean,private val status:(Boolean?,String)->Unit) {
    private val appContext=context.applicationContext
    private val wrapper=MutableContextWrapper(appContext)
    private val gate=ServerConsoleSubmissionGate()
    private var destroyed=false
    private var broken=false
    private var started=false
    private var ready=false
    private var busy=false
    private var latest=""
    private var sent=""
    private var sequence=0L
    private var startupDeadline=0L
    private var phase="created"
    private val view=object:WebView(wrapper) {
        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            post { if(!destroyed && !broken) { publish(null,"attached"); startIfMeasured() } }
        }
        override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int) {
            super.onSizeChanged(w,h,oldw,oldh)
            post {
                if(!destroyed && !broken) {
                    publish(null,phase)
                    startIfMeasured()
                    if(ready) evaluateJavascript("window.dispatchEvent(new Event('resize'));",null)
                }
            }
        }
        override fun onCreateInputConnection(info:EditorInfo):InputConnection? {
            val connection=super.onCreateInputConnection(info)
            info.imeOptions=info.imeOptions or EditorInfo.IME_FLAG_NO_EXTRACT_UI or
                EditorInfo.IME_FLAG_NO_FULLSCREEN or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            return connection
        }
        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            if(!destroyed) { onPause(); wrapper.baseContext=appContext }
        }
    }
    init { configure() }
    private fun publish(ok:Boolean?,next:String) {
        if(destroyed) return
        phase=next
        status(ok,"$next · ${view.width}x${view.height} · attached=${view.isAttachedToWindow}")
    }
    private fun hideKeyboard() {
        (view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.hideSoftInputFromWindow(view.windowToken,0)
    }
    private fun fail(stage:String) {
        if(destroyed || broken) return
        broken=true; ready=false; busy=false; gate.close()
        hideKeyboard()
        publish(false,stage)
    }
    fun syncInput() {
        if(destroyed || broken || !ready) return
        val allowed=view.isAttachedToWindow && canSend()
        view.evaluateJavascript("window.serverConsoleSetInputEnabled && window.serverConsoleSetInputEnabled($allowed)") { value->
            if(!destroyed && !broken && value!="true") fail("input-state")
        }
    }
    fun focusInput() {
        if(destroyed || broken || !ready || !view.isAttachedToWindow || !canSend()) return
        view.requestFocus()
        view.evaluateJavascript("window.serverConsoleFocus && window.serverConsoleFocus()") { value->
            if(value=="true") view.post {
                if(!destroyed && !broken && ready && view.isAttachedToWindow && canSend()) {
                    (view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.showSoftInput(view,InputMethodManager.SHOW_IMPLICIT)
                }
            }
        }
    }
    fun paste(text:String) {
        if(destroyed || broken || !ready || !canSend()) return
        if(text.length>1000) { publish(null,"paste-too-long"); return }
        view.evaluateJavascript("window.serverConsolePaste && window.serverConsolePaste(${Gson().toJson(text)})") { value->
            if(!destroyed && !broken) {
                if(value=="true") focusInput() else publish(null,"paste-rejected")
            }
        }
    }
    inner class Bridge {
        @JavascriptInterface fun submit(id:Int,exact:String) {
            view.post {
                if(destroyed || broken) return@post
                var accepted=false
                if(gate.claim(id,exact,ready && view.isAttachedToWindow && canSend())) {
                    try { accepted=command(exact) } catch(_:Exception) { fail("command-result-unknown"); return@post }
                }
                if(!destroyed && !broken) {
                    view.evaluateJavascript("window.serverConsoleSubmitted && window.serverConsoleSubmitted($id,$accepted)",null)
                    syncInput()
                }
            }
        }
    }
    private fun scrollPixels(distance:Float) {
        if(destroyed || broken || !ready || view.height<=0 || !distance.isFinite()) return
        val fraction=distance.toDouble()/view.height
        view.evaluateJavascript("(function(){var v=document.querySelector('#terminal .xterm-viewport');if(v)v.scrollTop += $fraction * window.innerHeight;})()",null)
    }
    @SuppressLint("SetJavaScriptEnabled","ClickableViewAccessibility")
    private fun configure() {
        view.setBackgroundColor(android.graphics.Color.rgb(13,17,23))
        view.isFocusableInTouchMode=true
        view.settings.apply {
            javaScriptEnabled=true
            allowFileAccess=false
            allowContentAccess=false
            domStorageEnabled=false
            javaScriptCanOpenWindowsAutomatically=false
            setSupportMultipleWindows(false)
            mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
            blockNetworkLoads=true
            cacheMode=WebSettings.LOAD_NO_CACHE
            safeBrowsingEnabled=true
            useWideViewPort=false
            loadWithOverviewMode=false
            setSupportZoom(false)
        }
        view.addJavascriptInterface(Bridge(),"AndroidServerConsole")
        view.isVerticalScrollBarEnabled=false
        view.setDownloadListener { _,_,_,_,_-> }
        val slop=ViewConfiguration.get(appContext).scaledTouchSlop
        var downX=0f; var downY=0f; var touchY=0f; var dragging=false
        view.setOnTouchListener { target,event->
            when(event.actionMasked) {
                MotionEvent.ACTION_DOWN->{ downX=event.x; downY=event.y; touchY=event.y; dragging=false; target.parent?.requestDisallowInterceptTouchEvent(true) }
                MotionEvent.ACTION_POINTER_DOWN->dragging=true
                MotionEvent.ACTION_MOVE->{
                    dragging=dragging || abs(event.x-downX)>slop || abs(event.y-downY)>slop
                    if(dragging && event.pointerCount==1) scrollPixels(touchY-event.y)
                    touchY=event.y
                    target.parent?.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_UP->{ target.parent?.requestDisallowInterceptTouchEvent(false); if(!dragging) focusInput(); target.performClick() }
                MotionEvent.ACTION_CANCEL->target.parent?.requestDisallowInterceptTouchEvent(false)
            }
            true
        }
        view.webChromeClient=object:WebChromeClient() {
            override fun onConsoleMessage(consoleMessage:ConsoleMessage)=true
        }
        view.webViewClient=object:WebViewClient() {
            override fun shouldOverrideUrlLoading(webView:WebView,request:WebResourceRequest)=true
            override fun shouldInterceptRequest(webView:WebView,request:WebResourceRequest):WebResourceResponse {
                val path=serverConsoleAsset(request.url.toString()) ?: return blocked()
                return try {
                    val mime=when { path.endsWith(".html")->"text/html"; path.endsWith(".css")->"text/css"; else->"application/javascript" }
                    WebResourceResponse(mime,"utf-8",200,"OK",mapOf("Cache-Control" to "no-store","X-Content-Type-Options" to "nosniff"),appContext.assets.open(path))
                } catch(_:Exception) { blocked() }
            }
            override fun onPageFinished(webView:WebView,url:String) { if(!destroyed && !broken && url==SERVER_CONSOLE_URL) { publish(null,"page-loaded"); checkReady() } }
            override fun onReceivedError(webView:WebView,request:WebResourceRequest,error:WebResourceError) { if(request.isForMainFrame) fail("load") }
            override fun onReceivedHttpError(webView:WebView,request:WebResourceRequest,response:WebResourceResponse) { if(request.isForMainFrame) fail("asset-http") }
            override fun onRenderProcessGone(webView:WebView,detail:RenderProcessGoneDetail):Boolean { fail("webview"); return true }
        }
    }
    fun attach(context:Context,parent:FrameLayout) {
        if(destroyed || broken || view.parent===parent) return
        (view.parent as? ViewGroup)?.removeView(view)
        wrapper.baseContext=context
        parent.addView(view,FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT))
        view.onResume()
        parent.post { if(!destroyed && !broken && view.parent===parent) startIfMeasured() }
    }
    private fun startIfMeasured() {
        if(destroyed || broken || started) return
        if(!view.isAttachedToWindow || view.width<=0 || view.height<=0) { publish(null,"waiting-layout"); return }
        started=true
        startupDeadline=SystemClock.elapsedRealtime()+10000L
        publish(null,"loading-assets")
        view.loadUrl(SERVER_CONSOLE_URL)
        view.postDelayed({ if(!destroyed && !broken && !ready) fail("startup-timeout") },10000L)
    }
    private fun blocked()=WebResourceResponse("text/plain","utf-8",403,"Blocked",emptyMap(),ByteArrayInputStream(ByteArray(0)))
    private fun checkReady() {
        if(destroyed || broken || ready) return
        view.evaluateJavascript("window.serverConsoleFailed ? 'failed' : (window.serverConsoleReady && window.serverConsoleRender ? 'ready' : 'pending')") { value->
            if(!destroyed && !broken && !ready) when {
                value=="\"ready\""->{ ready=true; publish(null,"ready"); syncInput(); deliver() }
                value=="\"failed\""->fail("initialize")
                SystemClock.elapsedRealtime()>=startupDeadline->fail("startup-timeout")
                else->view.postDelayed({ checkReady() },100L)
            }
        }
    }
    fun submit(payload:String) { latest=payload; deliver() }
    private fun deliver() {
        if(destroyed || broken || !ready || busy || latest.isBlank() || latest==sent) return
        val payload=latest
        val token=++sequence
        busy=true
        publish(null,"writing#$token")
        view.evaluateJavascript("window.serverConsoleRender && window.serverConsoleRender($payload,$token)") { value->
            if(!destroyed && !broken) {
                if(value!="true") fail("accept") else checkWritten(token,payload,SystemClock.elapsedRealtime()+5000L)
            }
        }
    }
    private fun checkWritten(token:Long,payload:String,deadline:Long) {
        if(destroyed || broken || token!=sequence) return
        view.evaluateJavascript("window.serverConsoleFailed ? 'failed' : (window.serverConsoleRendered === $token ? 'done' : 'pending')") { value->
            if(!destroyed && !broken && token==sequence) when {
                value=="\"done\""->{ sent=payload; busy=false; view.postInvalidateOnAnimation(); publish(true,"written#$token"); deliver() }
                value=="\"failed\""->fail("write")
                SystemClock.elapsedRealtime()>=deadline->fail("write-timeout")
                else->view.postDelayed({ checkWritten(token,payload,deadline) },100L)
            }
        }
    }
    fun close() {
        if(destroyed) return
        destroyed=true; ready=false; busy=false; latest=""; sent=""; gate.close()
        hideKeyboard()
        view.clearFocus()
        view.removeJavascriptInterface("AndroidServerConsole")
        (view.parent as? ViewGroup)?.removeView(view)
        wrapper.baseContext=appContext
        view.stopLoading(); view.clearHistory(); view.removeAllViews(); view.destroy()
    }
}

@Composable internal fun ServerConsoleTerminal(output:List<String>,onClear:()->Unit,enabled:Boolean=false,canSend:()->Boolean={false},onCommand:(String)->Boolean={false}) {
    val context=LocalContext.current
    val clipboard=LocalClipboardManager.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val es=LocalConfiguration.current.locales[0].language=="es"
    fun label(a:String,b:String)=if(es) a else b
    val snapshot=remember(output) { serverConsoleSnapshot(output) }
    val currentCanSend by rememberUpdatedState(canSend)
    val currentCommand by rememberUpdatedState(onCommand)
    var font by rememberSaveable { mutableIntStateOf(13) }
    var revision by remember { mutableIntStateOf(0) }
    var failed by remember { mutableStateOf(false) }
    var stage by remember { mutableStateOf("created") }
    var loaded by remember { mutableStateOf(false) }
    var showText by remember { mutableStateOf(false) }
    var active by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    val renderer=remember(context,revision) { ServerConsoleRenderer(context.applicationContext,{ currentCanSend() },{ currentCommand(it) }) { ok,reason->if(ok!=null) { loaded=ok; failed=!ok }; stage=reason } }
    DisposableEffect(renderer,lifecycle) {
        val observer=LifecycleEventObserver { _,event->
            when(event) {
                Lifecycle.Event.ON_PAUSE->{ active=false; renderer.close() }
                Lifecycle.Event.ON_RESUME->{ if(!active) { loaded=false; failed=false; stage="created"; revision++ }; active=true }
                else->Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); renderer.close() }
    }
    val payload=remember(snapshot,font,es) { serverConsolePayload(snapshot,label("Consola administrativa MeshCentral","MeshCentral administrative console"),label("Sin salida recibida.","No output received."),font,if(es) "es" else "en") }
    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            TextButton(enabled=font>10,onClick={ font-- },contentPadding=PaddingValues(6.dp)) { Text("A−") }
            TextButton(enabled=font<20,onClick={ font++ },contentPadding=PaddingValues(6.dp)) { Text("A+") }
            TextButton(enabled=snapshot.isNotEmpty(),onClick={ clipboard.setText(AnnotatedString(snapshot)) }) { Text(label("Copiar salida","Copy output")) }
            TextButton(onClick=onClear) { Text(label("Limpiar","Clear")) }
            TextButton(enabled=active && loaded && enabled && !failed,onClick={ clipboard.getText()?.text?.let { renderer.paste(it) } }) { Text(label("Pegar","Paste")) }
        }
        Text(label("Búfer local: ${output.size} entradas · ${snapshot.length} caracteres","Local buffer: ${output.size} entries · ${snapshot.length} characters"),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label("Visor xterm: ","xterm view: ")+stage,style=MaterialTheme.typography.labelSmall,color=if(failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(modifier=Modifier.fillMaxWidth(),shape=MaterialTheme.shapes.medium,color=Color(0xFF0D1117),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
            if(!active) Text(label("Consola local pausada.","Local console paused."),Modifier.padding(12.dp),color=Color(0xFFC9D1D9))
            else if(failed) Column(Modifier.fillMaxWidth().heightIn(min=120.dp).padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(label("Falló el visor local. La salida sigue disponible debajo; no se han reenviado comandos.","Local view failed. Output remains available below; no commands were resent."),color=Color(0xFFFF7B72))
                TextButton(onClick={ loaded=false; failed=false; stage="created"; revision++ }) { Text(label("Recrear solo la vista local","Recreate local view only")) }
            } else key(renderer) {
                AndroidView(modifier=Modifier.fillMaxWidth().height(320.dp),factory={ ctx->FrameLayout(ctx) },onReset=null,onRelease={ renderer.close() },update={ parent->renderer.attach(context,parent); renderer.submit(payload); renderer.syncInput() })
            }
        }
        if(active && !loaded && !failed) LinearProgressIndicator(Modifier.fillMaxWidth())
        TextButton(onClick={ showText=!showText }) { Text(if(showText) label("Ocultar texto","Hide text") else label("Ver texto recibido","View received text")) }
        if(showText || failed) Surface(modifier=Modifier.fillMaxWidth(),shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surfaceVariant,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
            SelectionContainer {
                Text(snapshot.ifBlank { label("Sin salida recibida.","No output received.") },Modifier.fillMaxWidth().heightIn(max=320.dp).verticalScroll(rememberScrollState()).padding(12.dp),fontFamily=FontFamily.Monospace,
                    style=MaterialTheme.typography.bodySmall.copy(fontSize=font.sp,lineHeight=(font+5).sp))
            }
        }
        Text(label("Toca la consola para abrir el teclado.","Tap the console to open the keyboard."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
